package io.github.nayrasolcr.forough.scanner

import io.github.nayrasolcr.forough.tunnel.TunnelConfigBuilder
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * Manual CDN edge scanner — faithful port of the Forough Linux client's
 * CdnScanner (gui/src/services/cdn-scanner.vala).
 *
 * Samples random IPv4 addresses from the same CDN ranges the bundled
 * tunnel-core uses for its built-in fronting scan (Cloudflare, Fastly,
 * Akamai, CloudFront, Bunny) and probes each sampled IP with a TCP connect
 * plus a TLS handshake presenting a fronting SNI name (identical range and
 * SNI snapshots). Selected IPs are written into the CDN fronting IP
 * candidates setting; on the next connect the tunnel-core scanner dials
 * those candidates first and ranks them by measured real-tunnel
 * establishment time (the fork's quality ranking).
 *
 * Threading model mirrors the desktop: N worker threads pull targets from
 * an atomic index; results are posted back to the caller via callbacks.
 */
class CdnScanner {

    data class Result(
        val ip: String,
        val sni: String,
        val tcpMs: Long,
        val tlsMs: Long
    ) {
        val alive: Boolean get() = tcpMs > 0 && tlsMs > 0
        val tcpOnly: Boolean get() = tcpMs > 0 && tlsMs == 0L
        val totalMs: Long get() = tcpMs + tlsMs
    }

    interface Listener {
        fun onProgress(scanned: Int, total: Int, alive: Int)
        fun onResult(result: Result)
        fun onFinished(alive: Int, total: Int)
    }

    var scanCloudflare = true
    var scanFastly = false
    var scanAkamai = false
    var scanCloudfront = false
    var scanBunny = false
    var customCidrs = ""
    var customSnis = ""
    var sampleCount = 1024
    var concurrency = 96

    private val running = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private var listener: Listener? = null
    private var sniPool: List<String> = emptyList()

    val isRunning: Boolean get() = running.get()

    // ----- Built-in scope data (snapshots from the tunnel-core fork's
    // frontedMeekCDNScanBuiltIn.go; identical to the desktop scanner). -----

    private val CLOUDFLARE_RANGES = listOf(
        "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22",
        "104.16.0.0/13", "104.24.0.0/14", "108.162.192.0/18",
        "131.0.72.0/22", "141.101.64.0/18", "162.158.0.0/15",
        "172.64.0.0/13", "173.245.48.0/20", "188.114.96.0/20",
        "190.93.240.0/20", "197.234.240.0/22", "198.41.128.0/17"
    )

    private val FASTLY_RANGES = listOf(
        "103.244.50.0/24", "103.245.222.0/23", "103.245.224.0/24",
        "104.156.80.0/20", "140.248.128.0/17", "140.248.64.0/18",
        "146.75.0.0/17", "151.101.0.0/16", "157.52.64.0/18",
        "167.82.0.0/17", "167.82.128.0/20", "167.82.160.0/20",
        "167.82.224.0/20", "172.111.64.0/18", "185.31.16.0/22",
        "199.232.0.0/16", "199.27.72.0/21", "23.235.32.0/20",
        "43.249.72.0/22"
    )

    private val AKAMAI_RANGES = listOf(
        "2.16.19.136", "2.16.20.51", "2.16.53.11", "2.16.53.50",
        "2.16.53.57", "2.16.53.65", "2.16.221.37", "2.17.35.24",
        "23.12.147.13", "23.12.147.29", "23.73.207.8", "23.73.207.15",
        "23.212.250.78", "23.212.250.91", "23.215.0.203", "23.215.0.206",
        "92.123.102.43"
    )

    private val CLOUDFRONT_RANGES = listOf(
        "13.32.0.0/15", "13.33.0.0/16", "13.224.0.0/14", "13.249.0.0/16",
        "18.64.0.0/14", "18.154.0.0/15", "18.239.0.0/16", "18.244.0.0/15",
        "52.84.0.0/15", "52.222.128.0/17", "52.46.32.0/22",
        "71.152.0.0/17", "99.86.0.0/16", "108.138.0.0/15",
        "130.176.0.0/16", "143.204.0.0/16", "144.220.112.0/20",
        "205.251.208.0/20"
    )

    private val BUNNY_RANGES = listOf(
        "185.107.56.0/24", "185.107.57.0/24", "89.187.160.0/24",
        "89.187.161.0/24", "89.187.162.0/24", "89.187.163.0/24",
        "89.187.164.0/24", "89.187.165.0/24", "89.187.166.0/24",
        "89.187.167.0/24", "89.187.168.0/24", "89.187.169.0/24",
        "138.199.0.0/24", "138.199.1.0/24", "138.199.2.0/24",
        "138.199.3.0/24", "138.199.4.0/24", "138.199.5.0/24",
        "138.199.6.0/24", "138.199.7.0/24", "138.199.8.0/24",
        "138.199.9.0/24", "138.199.10.0/24", "138.199.11.0/24",
        "138.199.12.0/24", "138.199.13.0/24", "138.199.14.0/24",
        "138.199.15.0/24", "138.199.16.0/24", "138.199.17.0/24",
        "138.199.18.0/24", "138.199.19.0/24", "138.199.20.0/24",
        "138.199.21.0/24", "138.199.22.0/24", "138.199.23.0/24",
        "138.199.24.0/24", "138.199.25.0/24", "138.199.26.0/24",
        "138.199.27.0/24", "138.199.28.0/24", "138.199.29.0/24",
        "138.199.30.0/24", "138.199.31.0/24", "138.199.32.0/24",
        "138.199.33.0/24", "138.199.34.0/24", "138.199.35.0/24",
        "138.199.36.0/24", "138.199.37.0/24", "138.199.38.0/24",
        "138.199.39.0/24", "138.199.40.0/24", "138.199.41.0/24",
        "138.199.42.0/24", "138.199.43.0/24", "138.199.44.0/24",
        "138.199.45.0/24", "138.199.46.0/24", "138.199.47.0/24",
        "138.199.48.0/24", "138.199.49.0/24", "138.199.50.0/24"
    )

    private val CLOUDFLARE_SNIS = listOf(
        "cdnjs.cloudflare.com", "cloudflare.com", "www.cloudflare.com",
        "lickbash-nonal-nonink.psiphon3.net",
        "morer-actocumerl-reless.psiphon3.net",
        "serce-sourite-docurch.psiphon3.net"
    )

    private val FASTLY_SNIS = listOf(
        "actions.githubusercontent.com", "api.github.com",
        "github.com", "prod.global.ssl.fastly.net",
        "quic.map.fastly.net", "fastly.com", "www.fastly.com",
        "theguardian.com", "imgix.net", "nyt.com"
    )

    private val AKAMAI_SNIS = listOf(
        "a.akamaihd.net", "a.akamaized.net", "a248.e.akamai.net",
        "www.akamai.com", "aparat.com", "digikala.com", "snapp.ir",
        "telewebion.com", "varzesh3.com", "bmi.ir"
    )

    private val CLOUDFRONT_SNIS = listOf(
        "d1.awsstatic.com", "d2c02a9g55p4vc.cloudfront.net",
        "d36p7catgf5u26.cloudfront.net", "images-na.ssl-images-amazon.com",
        "dw2bjjbfmlfhy.cloudfront.net"
    )

    private val BUNNY_SNIS = listOf(
        "b-cdn.net", "megram-supg-instem.b-cdn.net",
        "pirtus-moding-hareware.b-cdn.net"
    )

    fun currentSniPool(): List<String> = sniPool

    @Synchronized
    fun start(listener: Listener) {
        check(!running.get()) { "A scan is already running" }

        val ranges = buildRangeList()
        val snis = buildSniPool()
        require(ranges.isNotEmpty()) { "No scan scope selected" }
        require(snis.isNotEmpty()) { "No SNI pool" }

        this.listener = listener
        stopRequested.set(false)
        running.set(true)
        sniPool = snis

        val targets = sampleRanges(ranges, sampleCount)
        if (targets.isEmpty()) {
            running.set(false)
            listener.onFinished(0, 0)
            return
        }

        val nextIndex = AtomicInteger(0)
        val scanned = AtomicInteger(0)
        val aliveCount = AtomicInteger(0)
        val total = targets.size
        val threads = ArrayList<Thread>()
        val workerCount = concurrency.coerceIn(1, 512)

        for (w in 0 until workerCount) {
            val t = Thread {
                while (true) {
                    if (stopRequested.get()) return@Thread
                    val index = nextIndex.getAndIncrement()
                    if (index >= total) return@Thread

                    val ip = targets[index]
                    val sni = snis[index % snis.size]
                    val result = probe(ip, sni)
                    val s = scanned.incrementAndGet()
                    if (result.alive) aliveCount.incrementAndGet()

                    listener.onResult(result)
                    listener.onProgress(s, total, aliveCount.get())

                    if (s >= total) {
                        finish(aliveCount.get(), total)
                        return@Thread
                    }
                }
            }
            t.isDaemon = true
            threads.add(t)
            t.start()
        }
    }

    fun stop() {
        stopRequested.set(true)
        // Safety net: if every worker exits at the loop top the finished
        // signal must still arrive (the desktop v1.5.1 scanner repair).
        Thread {
            Thread.sleep(1500)
            if (running.get()) {
                finish(0, 0)
            }
        }.start()
    }

    private fun finish(alive: Int, total: Int) {
        if (running.compareAndSet(true, false)) {
            listener?.onFinished(alive, total)
            listener = null
        }
    }

    /** TCP connect + TLS handshake (SNI = sni), both with timeouts. */
    private fun probe(ip: String, sni: String): Result {
        val t0 = System.nanoTime()
        var socket: java.net.Socket? = null
        var tcpMs = 0L
        var tlsMs = 0L
        try {
            socket = java.net.Socket()
            socket.connect(InetSocketAddress(ip, 443), CONNECT_TIMEOUT_MS)
            socket.soTimeout = TLS_TIMEOUT_MS
            tcpMs = (System.nanoTime() - t0) / 1_000_000

            val t1 = System.nanoTime()
            val ssl: SSLSocket = SSLContext.getDefault().socketFactory.createSocket(
                socket, sni, 443, true
            ) as SSLSocket
            // Present the fronting SNI while dialing the raw IP.
            val params = ssl.sslParameters
            params.serverNames = listOf(javax.net.ssl.SNIHostName(sni))
            ssl.sslParameters = params
            ssl.startHandshake()
            tlsMs = (System.nanoTime() - t1) / 1_000_000

            try { ssl.close() } catch (_: Exception) {}
            return Result(ip, sni, tcpMs, tlsMs)
        } catch (_: Exception) {
            try { socket?.close() } catch (_: Exception) {}
            return Result(ip, sni, tcpMs, if (tcpMs > 0) 0L else 0L)
        }
    }

    // ----- Range / SNI construction (desktop parity) -----

    private fun buildRangeList(): List<String> {
        val ranges = ArrayList<String>()
        if (scanCloudflare) ranges.addAll(CLOUDFLARE_RANGES)
        if (scanFastly) ranges.addAll(FASTLY_RANGES)
        if (scanAkamai) ranges.addAll(AKAMAI_RANGES)
        if (scanCloudfront) ranges.addAll(CLOUDFRONT_RANGES)
        if (scanBunny) ranges.addAll(BUNNY_RANGES)
        for (cidr in customCidrs.split(' ', '\t', '\r', '\n', ',', ';')) {
            val entry = cidr.trim()
            if (entry.isNotEmpty()) ranges.add(entry)
        }
        return ranges
    }

    private fun buildSniPool(): List<String> {
        val custom = TunnelConfigBuilder.parseCdnFrontingSniList(customSnis)
        if (custom.isNotEmpty()) return custom

        val pool = ArrayList<String>()
        if (scanCloudflare) pool.addAll(CLOUDFLARE_SNIS)
        if (scanFastly) pool.addAll(FASTLY_SNIS)
        if (scanAkamai) pool.addAll(AKAMAI_SNIS)
        if (scanCloudfront) pool.addAll(CLOUDFRONT_SNIS)
        if (scanBunny) pool.addAll(BUNNY_SNIS)
        if (pool.isEmpty()) pool.addAll(CLOUDFLARE_SNIS)
        return pool
    }

    private fun sampleRanges(ranges: List<String>, count: Int): List<String> {
        val targets = LinkedHashSet<String>()
        var attempts = 0
        while (targets.size < count && attempts < count * 4) {
            attempts++
            val cidr = ranges.random()
            val ip = randomIpFromCidr(cidr) ?: continue
            targets.add(ip)
        }
        return targets.toList()
    }

    /** Random IPv4 from "a.b.c.d/p" (single IPs are treated as /32). */
    private fun randomIpFromCidr(cidr: String): String? {
        val parts = cidr.split("/")
        if (parts.isEmpty()) return null
        val octets = parts[0].split(".")
        if (octets.size != 4) return null
        val values = IntArray(4)
        for (i in 0..3) {
            val v = octets[i].toIntOrNull() ?: return null
            if (v !in 0..255) return null
            values[i] = v
        }
        val prefix = if (parts.size == 2) (parts[1].toIntOrNull() ?: return null) else 32
        if (prefix !in 0..32) return null
        if (prefix == 32) return cidr

        var base = 0L
        for (v in values) base = (base shl 8) or v.toLong()
        val mask = (0L.inv() shl (32 - prefix)) and 0xFFFFFFFFL
        val hostBits = 32 - prefix
        val hostMask = (1L shl hostBits) - 1
        val randomHost = (Math.random() * (hostMask + 1)).toLong() and hostMask
        val ipLong = (base and mask) or randomHost
        return "${(ipLong shr 24) and 0xFF}.${(ipLong shr 16) and 0xFF}." +
            "${(ipLong shr 8) and 0xFF}.${ipLong and 0xFF}"
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val TLS_TIMEOUT_MS = 3000
    }
}
