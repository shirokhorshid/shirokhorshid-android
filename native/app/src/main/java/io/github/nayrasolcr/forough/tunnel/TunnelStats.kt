package io.github.nayrasolcr.forough.tunnel

/**
 * Tunnel stats — direct port of the Forough Linux client's TunnelStats
 * (gui/src/models/tunnel-stats.vala). Field names and defaults match so the
 * Android UI presents exactly the same information surface as the desktop.
 */
data class TunnelStats(
    val protocol: String = "auto",
    val status: String = "disconnected",
    val httpProxy: String = "127.0.0.1:8081",
    val socksProxy: String = "127.0.0.1:1081",
    val tunnelCount: Int = 0,
    val clientRegion: String = "-",
    val egressRegion: String = "-",
    val downstreamBps: Long = 0,
    val upstreamBps: Long = 0,
    val totalDownBytes: Long = 0,
    val totalUpBytes: Long = 0,
    val diagnosticId: String = "-",
    val tunnelProtocol: String = "-",
    val availableRegions: Int = 0,
    val startedAtMs: Long = 0,
    val uptimeMs: Long = 0,
    val connectedAtMs: Long = 0,
    val connectDurationMs: Long = 0,
    val publicIp: String = "-",
    // On Android the VpnService IS the routing — this mirrors the desktop's
    // routing_status row with the Android equivalent text.
    val routingStatus: String = "-",
    val beastMode: String = "-",
    val cdnScan: String = "-",
    // End-to-end traffic probe: "-" | "checking" | "ok" | "failed".
    val trafficStatus: String = "-"
) {
    /** Mutable accumulator mirroring how the desktop NoticeParser mutates stats in place. */
    class Mutable(base: TunnelStats) {
        var protocol: String = base.protocol
        var status: String = base.status
        var httpProxy: String = base.httpProxy
        var socksProxy: String = base.socksProxy
        var tunnelCount: Int = base.tunnelCount
        var clientRegion: String = base.clientRegion
        var egressRegion: String = base.egressRegion
        var downstreamBps: Long = base.downstreamBps
        var upstreamBps: Long = base.upstreamBps
        var totalDownBytes: Long = base.totalDownBytes
        var totalUpBytes: Long = base.totalUpBytes
        var diagnosticId: String = base.diagnosticId
        var tunnelProtocol: String = base.tunnelProtocol
        var availableRegions: Int = base.availableRegions
        var startedAtMs: Long = base.startedAtMs
        var uptimeMs: Long = base.uptimeMs
        var connectedAtMs: Long = base.connectedAtMs
        var connectDurationMs: Long = base.connectDurationMs
        var publicIp: String = base.publicIp
        var routingStatus: String = base.routingStatus
        var beastMode: String = base.beastMode
        var cdnScan: String = base.cdnScan
        var trafficStatus: String = base.trafficStatus

        fun build(): TunnelStats = TunnelStats(
            protocol, status, httpProxy, socksProxy, tunnelCount, clientRegion,
            egressRegion, downstreamBps, upstreamBps, totalDownBytes, totalUpBytes,
            diagnosticId, tunnelProtocol, availableRegions, startedAtMs, uptimeMs,
            connectedAtMs, connectDurationMs, publicIp, routingStatus, beastMode,
            cdnScan, trafficStatus
        )
    }

    companion object {
        fun from(base: TunnelStats, block: (Mutable) -> Unit): TunnelStats {
            val m = Mutable(base)
            block(m)
            return m.build()
        }
    }
}
