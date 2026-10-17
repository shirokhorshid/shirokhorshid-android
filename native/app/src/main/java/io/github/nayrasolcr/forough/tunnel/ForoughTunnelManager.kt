package io.github.nayrasolcr.forough.tunnel

import android.content.Context
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import ca.psiphon.PsiphonTunnel
import io.github.nayrasolcr.forough.CrashLog
import io.github.nayrasolcr.forough.EngineBridge
import io.github.nayrasolcr.forough.ForoughBus
import io.github.nayrasolcr.forough.net.NetTools
import io.github.nayrasolcr.forough.service.ForoughVpnService
import io.github.nayrasolcr.forough.vpn.VpnManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The Forough tunnel engine — Android counterpart of the desktop client's
 * TunnelManager (gui/src/services/tunnel-manager.vala).
 *
 * Parity kept with the desktop: honest status chain (connecting → connected
 * → reconnecting), real protocol display via ActiveTunnel notices, live
 * byte rates from BytesTransferred, end-to-end traffic probe through the
 * local HTTP proxy (same endpoints/success rule, first check 6s after
 * start, every 20s, 8s cap), automatic tunnel restart on dead traffic
 * (thresholds 2 before first success / 3 after, max 2 per session), the
 * 8s-after-connect performance probe (TTFB + 10 MB/15s speed test) and
 * manual speed tests on demand.
 *
 * Android-specific: routing is whole-device via VpnService + tun2socks, so
 * the desktop's TUN watchdog / system-proxy fallback layers are structural
 * here — the routing_status row reports the VPN state instead.
 */
class ForoughTunnelManager(
    private val context: Context
) : PsiphonTunnel.HostService {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val vpnManager = VpnManager.getInstance()
    private val parserState = NoticeParser.ParserState()

    private var tunnel: PsiphonTunnel? = null
    private var options = TunnelOptions()

    // Same tunables as the desktop TunnelManager.
    private val trafficCheckFirstDelayMs = 6_000L
    private val trafficCheckIntervalMs = 20_000L
    private val trafficCheckMaxTimeMs = 8_000
    private val trafficFailRestartThreshold = 2
    private val trafficFailRestartThresholdAfterOk = 3
    private val autoRestartMax = 2
    private val perfProbeDelayMs = 8_000L

    @Volatile private var running = false
    @Volatile private var stopping = false
    private var trafficCheckScheduled = false
    private var trafficInFlight = false
    private var trafficFailures = 0
    private var trafficEverOk = false
    private var autoRestartCount = 0
    private var perfProbeDone = false
    private var perfProbeInFlight = false
    private var lastPerfReport = ""
    private var lastTrafficCode = 0

    private var stats = TunnelStats()
    private val logs = ArrayList<String>()

    val isRunning: Boolean get() = running

    init {
        EngineBridge.manager = this
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Synchronized
    fun start(options: TunnelOptions) {
        if (running) return
        this.options = options
        stopping = false
        trafficFailures = 0
        trafficEverOk = false
        autoRestartCount = 0
        trafficCheckScheduled = false
        trafficInFlight = false
        perfProbeDone = false
        perfProbeInFlight = false
        lastPerfReport = ""
        lastTrafficCode = 0

        stats = TunnelStats(
            status = "connecting",
            startedAtMs = System.currentTimeMillis()
        )
        publish()
        ForoughBus.publishRunning(true)
        appendLog("connecting — Forough Android (engine: psiphon-tunnel-core)")

        try {
            vpnManager.registerHostService(builderProvider)
            vpnManager.setShareProxyOnNetwork(options.enableLan)
            vpnManager.vpnEstablish()

            val newTunnel = PsiphonTunnel.newPsiphonTunnel(this)
            tunnel = newTunnel
            newTunnel.setVpnMode(true)
            // No embedded server entries — the official signed remote server
            // list is used, exactly like the desktop client (Linux parity).
            newTunnel.startTunneling("")
        } catch (e: Exception) {
            appendLog("start failed: ${e.message}")
            ForoughBus.setToast("connect failed: ${e.message}")
            stop()
        }
    }

    @Synchronized
    fun stop() {
        if (!running && tunnel == null) {
            return
        }
        stopping = true
        running = false
        cancelTrafficCheck()
        cancelPerfProbe()

        try {
            vpnManager.stopRouteThroughTunnel()
        } catch (_: Exception) {
        }
        try {
            tunnel?.stop()
        } catch (_: Exception) {
        }
        try {
            vpnManager.vpnTeardown()
        } catch (_: Exception) {
        }
        tunnel = null
        vpnManager.unregisterHostService()
        EngineBridge.manager = null

        stats = TunnelStats.from(stats) {
            it.status = "disconnected"
            it.tunnelCount = 0
            it.tunnelProtocol = "-"
            it.beastMode = "-"
            it.cdnScan = "-"
            it.trafficStatus = "-"
            it.routingStatus = "-"
            it.uptimeMs = 0
            it.connectedAtMs = 0
            it.connectDurationMs = 0
        }
        publish()
        ForoughBus.publishRunning(false)
        appendLog("tunnel stopped")
    }

    /** Restart the core with the same options (desktop trigger_auto_restart). */
    private fun autoRestart(reason: String) {
        if (stopping || autoRestartCount >= autoRestartMax) {
            if (autoRestartCount >= autoRestartMax) {
                appendLog("restart budget exhausted — tunnel stays up as-is; reconnect manually")
            }
            return
        }
        autoRestartCount++
        appendLog("traffic is not going through — restarting the tunnel automatically " +
            "($autoRestartCount/$autoRestartMax) — $reason")
        ForoughBus.setToast("restart tunnel ($autoRestartCount/$autoRestartMax)")
        stats = TunnelStats.from(stats) {
            it.status = "reconnecting"
            it.trafficStatus = "restarting tunnel…"
        }
        publish()

        executor.execute {
            try {
                vpnManager.stopRouteThroughTunnel()
                tunnel?.stop()
            } catch (_: Exception) {
            }
            try {
                vpnManager.vpnTeardown()
            } catch (_: Exception) {
            }

            try {
                vpnManager.registerHostService(builderProvider)
                vpnManager.vpnEstablish()
                val newTunnel = PsiphonTunnel.newPsiphonTunnel(this)
                mainHandler.post {
                    tunnel = newTunnel
                }
                newTunnel.setVpnMode(true)
                newTunnel.startTunneling("")
            } catch (e: Exception) {
                appendLog("auto-restart failed: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------------
    // PsiphonTunnel.HostService
    // ------------------------------------------------------------------

    override fun getContext(): Context = context

    override fun getPsiphonConfig(): String = TunnelConfigBuilder.build(options)

    override fun bindToDevice(fileDescriptor: Long) {
        if (!vpnProtect(fileDescriptor.toInt())) {
            throw RuntimeException("VpnService.protect() failed")
        }
    }

    private fun vpnProtect(fd: Int): Boolean {
        val service = ForoughVpnService.instance ?: return false
        return service.protect(fd)
    }

    private val builderProvider = object : VpnManager.VpnServiceBuilderProvider {
        override fun vpnServiceBuilder(): VpnService.Builder {
            val service = ForoughVpnService.instance
                ?: throw IllegalStateException("VPN service is not running")
            return service.Builder()
        }
    }

    override fun onDiagnosticMessage(message: String) {
        appendLog(message)
        mutate { NoticeParser.apply(message, it, "127.0.0.1", parserState) }
        maybeScheduleAfterConnect()
    }

    override fun onBytesTransferred(sent: Long, received: Long) {
        mutate {
            it.totalDownBytes += received
            it.totalUpBytes += sent
            val now = System.currentTimeMillis()
            if (parserState.lastBytesTimeMs > 0 && (received > 0 || sent > 0)) {
                val elapsedSec = (now - parserState.lastBytesTimeMs) / 1000.0
                if (elapsedSec > 0) {
                    it.downstreamBps = (received / elapsedSec + 0.5).toLong()
                    it.upstreamBps = (sent / elapsedSec + 0.5).toLong()
                }
            }
            parserState.lastBytesTimeMs = now
            true
        }
    }

    override fun onListeningSocksProxyPort(port: Int) {
        // Start whole-device routing as soon as the core's SOCKS listener is
        // up, so traffic flows the moment the tunnel establishes (and on
        // every automatic reconnect).
        appendLog("SOCKS proxy listening on 127.0.0.1:$port")
        executor.execute {
            try {
                vpnManager.routeThroughTunnel(port)
            } catch (e: Exception) {
                appendLog("routing failed: ${e.message}")
            }
        }
    }

    override fun onListeningHttpProxyPort(port: Int) {
        appendLog("HTTP proxy listening on 127.0.0.1:$port")
    }

    override fun onConnecting() {
        mutate {
            it.status = if (it.status == "connected") "reconnecting" else "connecting"
            true
        }
        publish()
    }

    override fun onConnected() {
        mutate {
            it.status = "connected"
            if (it.connectedAtMs == 0L) it.connectedAtMs = System.currentTimeMillis()
            true
        }
        publish()
        scheduleTrafficCheck()
        schedulePerfProbe()
    }

    override fun onAvailableEgressRegions(regions: MutableList<String>?) {
        mutate {
            it.availableRegions = regions?.size ?: 0
            true
        }
        publish()
    }

    override fun onClientRegion(region: String?) {
        mutate {
            it.clientRegion = region ?: "-"
            true
        }
        publish()
    }

    override fun onConnectedServerRegion(region: String?) {
        mutate {
            it.egressRegion = region ?: "-"
            true
        }
        publish()
    }

    override fun onTrafficRateLimits(upstreamBytesPerSecond: Long, downstreamBytesPerSecond: Long) {
        mutate {
            it.downstreamBps = downstreamBytesPerSecond
            it.upstreamBps = upstreamBytesPerSecond
            true
        }
        publish()
    }

    override fun onUpstreamProxyError(message: String?) {
        appendLog("upstream proxy error: ${message ?: "unknown"}")
    }

    override fun onExiting() {
        mutate { it.status = "exiting"; true }
        publish()
    }

    override fun onSocksProxyPortInUse(port: Int) {
        appendLog("SOCKS proxy port $port already in use")
    }

    override fun onHttpProxyPortInUse(port: Int) {
        appendLog("HTTP proxy port $port already in use")
    }

    // ------------------------------------------------------------------
    // Scheduling helpers
    // ------------------------------------------------------------------

    private fun maybeScheduleAfterConnect() {
        if (stats.status == "connected") {
            scheduleTrafficCheck()
            schedulePerfProbe()
        }
    }

    private fun scheduleTrafficCheck() {
        if (trafficCheckScheduled || !running) return
        trafficCheckScheduled = true
        mainHandler.postDelayed(trafficCheckRunnable, trafficCheckFirstDelayMs)
    }

    private fun cancelTrafficCheck() {
        trafficCheckScheduled = false
        trafficInFlight = false
        mainHandler.removeCallbacks(trafficCheckRunnable)
    }

    private val trafficCheckRunnable = object : Runnable {
        override fun run() {
            if (!running || stopping) return
            if (stats.status == "connected") {
                runTrafficCheck()
            }
            if (running) {
                mainHandler.postDelayed(this, trafficCheckIntervalMs)
            }
        }
    }

    private fun runTrafficCheck() {
        if (trafficInFlight) return
        trafficInFlight = true
        mutate { it.trafficStatus = "checking traffic…"; true }
        publish()

        executor.execute {
            val code = NetTools.probeTraffic(8081, trafficCheckMaxTimeMs)
            mainHandler.post { trafficCheckDone(code) }
        }
    }

    private fun trafficCheckDone(code: Int) {
        trafficInFlight = false
        lastTrafficCode = code
        val ok = code in 200..399

        if (!running || stopping) return

        if (ok) {
            trafficFailures = 0
            trafficEverOk = true
            mutate { it.trafficStatus = "online"; true }
        } else {
            trafficFailures++
            val threshold =
                if (trafficEverOk) trafficFailRestartThresholdAfterOk else trafficFailRestartThreshold
            mutate { it.trafficStatus = "no traffic"; true }
            if (trafficFailures >= threshold) {
                trafficFailures = 0
                autoRestart("probe HTTP 0 (all endpoints failed)")
                return
            }
        }
        publish()
    }

    // ------------------------------------------------------------------
    // Performance probe (desktop run_perf_probe parity)
    // ------------------------------------------------------------------

    private val perfProbeRunnable = Runnable {
        if (running && stats.status == "connected" && !perfProbeDone) {
            runSpeedTest(manual = false)
        }
    }

    private fun schedulePerfProbe() {
        if (perfProbeDone || perfProbeInFlight || !running) return
        mainHandler.removeCallbacks(perfProbeRunnable)
        mainHandler.postDelayed(perfProbeRunnable, perfProbeDelayMs)
    }

    private fun cancelPerfProbe() {
        perfProbeInFlight = false
        mainHandler.removeCallbacks(perfProbeRunnable)
    }

    /** Manual or automatic speed test — result lands in logs + diagnostics. */
    fun runSpeedTest(manual: Boolean) {
        if (!running || stats.status != "connected" || perfProbeInFlight) return
        if (manual) perfProbeDone = false
        perfProbeInFlight = true
        appendLog(if (manual) "speed test running — numbers land in the log and diagnostics" else "performance probe running")
        executor.execute {
            val ttfb = NetTools.probeTtfb()
            val speed = NetTools.probeSpeed()
            perfProbeInFlight = false
            if (!perfProbeDone) perfProbeDone = true
            lastPerfReport = ttfb.report() + "\n" + speed.report()
            appendLog(lastPerfReport)
        }
    }

    // ------------------------------------------------------------------
    // IP / diagnostics
    // ------------------------------------------------------------------

    fun fetchPublicIp() {
        if (stats.status != "connected") return
        executor.execute {
            val result = NetTools.fetchPublicIp()
            if (result != null) {
                mutate {
                    it.publicIp = result.first
                    true
                }
                publish()
            }
        }
    }

    fun buildDiagnosticsReport(trigger: String): String {
        val s = stats
        val sb = StringBuilder()
        sb.appendLine("Forough Android diagnostics ($trigger)")
        sb.appendLine("version: 2.0.1")
        sb.appendLine("engine: psiphon-tunnel-core (Forough fork, quality-ranked CDN edges)")
        sb.appendLine("status: ${s.status}")
        sb.appendLine("protocol: ${s.tunnelProtocol}")
        sb.appendLine("selected_protocol_mode: ${options.protocol.configValue}")
        sb.appendLine("egress_region_pref: '${options.region}' (connected: ${s.egressRegion})")
        sb.appendLine("client_region: ${s.clientRegion}")
        sb.appendLine("public_ip: ${s.publicIp}")
        sb.appendLine("beast_mode: ${options.enableBeastMode} (${s.beastMode})")
        sb.appendLine("cdn_scan: ${s.cdnScan}")
        sb.appendLine("traffic_status: ${s.trafficStatus} (last probe code $lastTrafficCode)")
        sb.appendLine("proxies: http ${s.httpProxy}, socks ${s.socksProxy}")
        sb.appendLine("routing: VpnService whole-device (tun2socks)")
        sb.appendLine("traffic: down ${NetTools.humanBytes(s.totalDownBytes)} / up ${NetTools.humanBytes(s.totalUpBytes)}")
        sb.appendLine("auto_restarts: $autoRestartCount/$autoRestartMax")
        if (lastPerfReport.isNotEmpty()) {
            sb.appendLine("perf_probe:")
            sb.appendLine(lastPerfReport)
        } else {
            sb.appendLine("perf_probe: (not run this session)")
        }
        sb.appendLine("log tail:")
        synchronized(logs) {
            for (line in logs.takeLast(40)) {
                sb.appendLine("  $line")
            }
        }
        // Last session's uncaught exception (crash journal) — the real data
        // a user can share when something crashes on their device.
        CrashLog.read(context)?.let { crash ->
            sb.appendLine()
            sb.appendLine("last crash:")
            sb.appendLine(crash)
        }
        return sb.toString()
    }

    fun logText(): String = synchronized(logs) { logs.joinToString("\n") }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun mutate(block: (TunnelStats.Mutable) -> Boolean) {
        synchronized(this) {
            val m = TunnelStats.Mutable(stats)
            if (block(m)) {
                stats = m.build()
            }
        }
    }

    private fun publish() {
        val snapshot = synchronized(this) { stats }
        // Keep the uptime row live like the desktop stats timer.
        if (snapshot.status == "connected" && snapshot.connectedAtMs > 0) {
            ForoughBus.publishStats(
                snapshot.copy(
                    uptimeMs = System.currentTimeMillis() - snapshot.startedAtMs,
                    connectDurationMs = System.currentTimeMillis() - snapshot.connectedAtMs
                )
            )
        } else {
            ForoughBus.publishStats(snapshot)
        }
    }

    private fun appendLog(line: String) {
        ForoughBus.appendLog(line)
        synchronized(logs) {
            logs.add(line)
            if (logs.size > 800) {
                logs.subList(0, logs.size - 700).clear()
            }
        }
    }
}
