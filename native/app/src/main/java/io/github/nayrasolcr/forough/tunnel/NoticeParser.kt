package io.github.nayrasolcr.forough.tunnel

import org.json.JSONObject

/**
 * Tunnel-core notice parser — port of the Forough Linux client's
 * NoticeParser (gui/src/services/notice-parser.vala).
 *
 * The PsiphonTunnel AAR forwards every core notice (as JSON text) to
 * HostService.onDiagnosticMessage; the typed callbacks are used in addition
 * for lifecycle events. State mutations follow the desktop logic exactly:
 * Tunnels>0 = connected, Tunnels==0 after connected = reconnecting (beast
 * mode keeps retrying — showing "disconnecting" here made users believe the
 * VPN had switched itself off), BytesTransferred deltas give live rates.
 */
object NoticeParser {

    fun apply(
        line: String,
        stats: TunnelStats.Mutable,
        proxyHost: String,
        state: ParserState
    ): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        if (!trimmed.startsWith("{")) return false

        return try {
            val root = JSONObject(trimmed)
            val noticeType = root.optString("noticeType", "")
            val data = if (root.has("data") && root.optJSONObject("data") != null) {
                root.getJSONObject("data")
            } else null

            when (noticeType) {
                "RemoteServerListDownloadFailed", "RemoteServerListFetchFailed" -> false

                "ListeningSocksProxyPort" -> {
                    data?.let {
                        stats.socksProxy = "$proxyHost:${it.optInt("port", TunnelConfigBuilder.SOCKS_PROXY_PORT)}"
                        true
                    } ?: false
                }

                "ListeningHttpProxyPort" -> {
                    data?.let {
                        stats.httpProxy = "$proxyHost:${it.optInt("port", TunnelConfigBuilder.HTTP_PROXY_PORT)}"
                        true
                    } ?: false
                }

                "Tunnels" -> {
                    if (data != null) {
                        stats.tunnelCount = data.optInt("count", 0)
                        stats.status = when {
                            stats.tunnelCount > 0 -> "connected"
                            stats.status == "connected" || stats.status == "disconnecting" -> "reconnecting"
                            else -> "connecting"
                        }
                        true
                    } else false
                }

                "ClientRegion" -> {
                    if (data != null) {
                        stats.clientRegion = data.optString("region", "-")
                        true
                    } else false
                }

                "ConnectedServerRegion" -> {
                    if (data != null) {
                        stats.egressRegion = data.optString("serverRegion", "-")
                        true
                    } else false
                }

                "TrafficRateLimits" -> {
                    if (data != null) {
                        stats.downstreamBps = data.optLong("downstreamBytesPerSecond", 0)
                        stats.upstreamBps = data.optLong("upstreamBytesPerSecond", 0)
                        true
                    } else false
                }

                "BytesTransferred" -> {
                    if (data != null) {
                        val received = data.optLong("received", 0)
                        val sent = data.optLong("sent", 0)
                        stats.totalDownBytes += received
                        stats.totalUpBytes += sent

                        val now = System.currentTimeMillis()
                        if (state.lastBytesTimeMs > 0 && (received > 0 || sent > 0)) {
                            val elapsedSec = (now - state.lastBytesTimeMs) / 1000.0
                            if (elapsedSec > 0) {
                                stats.downstreamBps = (received / elapsedSec + 0.5).toLong()
                                stats.upstreamBps = (sent / elapsedSec + 0.5).toLong()
                            }
                        }
                        state.lastBytesTimeMs = now
                        true
                    } else false
                }

                "TotalBytesTransferred" -> {
                    if (data != null) {
                        if (data.has("received")) {
                            stats.totalDownBytes = data.optLong("received", stats.totalDownBytes)
                        }
                        if (data.has("sent")) {
                            stats.totalUpBytes = data.optLong("sent", stats.totalUpBytes)
                        }
                        true
                    } else false
                }

                "ActiveTunnel" -> {
                    if (data != null) {
                        stats.tunnelProtocol = data.optString("protocol", "-")
                        true
                    } else false
                }

                "AvailableEgressRegions" -> {
                    if (data != null && data.has("regions")) {
                        stats.availableRegions = data.getJSONArray("regions").length()
                        true
                    } else false
                }

                "ServerTimestamp" -> {
                    if (data != null) {
                        stats.diagnosticId = data.optString("diagnosticID", "-")
                        true
                    } else false
                }

                "Exiting" -> {
                    stats.status = "exiting"
                    true
                }

                "Info" -> {
                    val message = data?.optString("message", "") ?: ""
                    message.isNotEmpty() && applyDiagnosticLine(message, stats)
                }

                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Diagnostic lines (beast mode / CDN scan status) — same regexes and
     * same status text structure as the desktop NoticeParser.
     */
    fun applyDiagnosticLine(line: String, stats: TunnelStats.Mutable): Boolean {
        return try {
            when {
                "beast mode active (workers:" in line -> {
                    val workers = Regex("\\(workers: (\\d+)\\)").find(line)?.groupValues?.get(1)
                    if (workers != null) {
                        stats.beastMode = "active · $workers workers"
                        true
                    } else false
                }
                "beast mode connected (attempts:" in line -> {
                    val match = Regex("\\(attempts: (\\d+), servers: (\\d+)\\)").find(line)
                    if (match != null) {
                        stats.beastMode = "connected · ${match.groupValues[1]} attempts · ${match.groupValues[2]} servers"
                        true
                    } else false
                }
                "CDN fronting scan mode:" in line -> {
                    val match = Regex("mode: (\\w+), workers: (\\d+)").find(line)
                        ?: Regex("CDN fronting scan mode: (\\w+)").find(line)
                    if (match != null) {
                        stats.cdnScan = "scan active"
                        true
                    } else false
                }
                "scan:" in line && "attempts" in line -> {
                    stats.cdnScan = line.substringAfter("scan:").trim().take(60)
                    true
                }
                "scan finished" in line -> {
                    stats.cdnScan = line.take(80)
                    true
                }
                "scan found" in line -> {
                    stats.cdnScan = line.take(80)
                    true
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    class ParserState {
        var lastBytesTimeMs: Long = 0
    }
}
