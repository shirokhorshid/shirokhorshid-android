package io.github.nayrasolcr.forough.net

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * Network tools — Android counterparts of the desktop client's curl-based
 * probes (tunnel-manager.vala run_perf_probe / run_traffic_check /
 * IpService), speaking to the tunnel's local HTTP proxy exactly like the
 * desktop does. Proxy port defaults mirror Constants (8081).
 */
object NetTools {

    data class TtfbResult(
        val connectMs: Long,
        val appConnectMs: Long,
        val startTransferMs: Long,
        val httpCode: Int,
        val error: String? = null
    ) {
        fun report(): String =
            if (error != null) "TTFB probe failed: $error"
            else "TTFB: connect ${connectMs}ms + TLS ${appConnectMs}ms + first-byte ${startTransferMs}ms (HTTP $httpCode)"
    }

    data class SpeedResult(
        val bytes: Long,
        val speedBps: Long,
        val httpCode: Int,
        val hitCap: Boolean,
        val error: String? = null
    ) {
        fun report(): String =
            if (error != null) "Speed test failed: $error"
            else {
                val cap = if (hitCap) " (hit the 15s cap)" else ""
                "Speed: ${humanSpeed(speedBps)}$cap (HTTP $httpCode)"
            }
    }

    /**
     * First-response latency through the tunnel: HTTP-proxy CONNECT +
     * TLS handshake + first byte for a real page — the same measurements
     * the desktop gets from curl's %{time_connect}|%{time_appconnect}|
     * %{time_starttransfer}|%{http_code}.
     */
    fun probeTtfb(proxyPort: Int = 8081, host: String = "www.wikipedia.org", timeoutMs: Int = 20_000): TtfbResult {
        var raw: Socket? = null
        try {
            val t0 = System.nanoTime()
            raw = Socket()
            raw.connect(InetSocketAddress("127.0.0.1", proxyPort), timeoutMs)
            raw.soTimeout = timeoutMs
            val connectMs = elapsedMs(t0)

            // CONNECT through the local HTTP proxy.
            val out: OutputStream = raw.getOutputStream()
            out.write(
                ("CONNECT $host:443 HTTP/1.1\r\nHost: $host:443\r\n\r\n").toByteArray()
            )
            out.flush()
            val statusLine = readLine(raw.getInputStream()) ?: throw IOException("empty proxy response")
            // Drain the rest of the proxy response headers.
            while (true) {
                val line = readLine(raw.getInputStream()) ?: break
                if (line.isEmpty()) break
            }
            if (!statusLine.contains(" 2")) {
                throw IOException("proxy CONNECT failed: $statusLine")
            }

            // TLS handshake over the tunnel (time_appconnect).
            val t1 = System.nanoTime()
            val ssl: SSLSocket = SSLContext.getDefault().socketFactory.createSocket(
                raw, host, 443, true
            ) as SSLSocket
            ssl.soTimeout = timeoutMs
            ssl.startHandshake()
            val appConnectMs = elapsedMs(t1)

            // Request + first response byte (time_starttransfer).
            val t2 = System.nanoTime()
            val req = "GET / HTTP/1.1\r\nHost: $host\r\nUser-Agent: Forough/2.0\r\nConnection: close\r\n\r\n"
            ssl.getOutputStream().write(req.toByteArray())
            ssl.getOutputStream().flush()
            val first = readLine(ssl.getInputStream())
            val startTransferMs = elapsedMs(t2)
            val code = Regex("\\d{3}").find(first ?: "")?.value?.toIntOrNull() ?: 0

            try { ssl.close() } catch (_: IOException) {}
            return TtfbResult(connectMs, appConnectMs, startTransferMs, code)
        } catch (e: Exception) {
            return TtfbResult(0, 0, 0, 0, error = e.message ?: e.javaClass.simpleName)
        } finally {
            try { raw?.close() } catch (_: IOException) {}
        }
    }

    /**
     * Throughput test through the tunnel — 10 MB from Cloudflare with the
     * same 15-second cap as the desktop speed test.
     */
    fun probeSpeed(
        proxyPort: Int = 8081,
        bytes: Long = 10_000_000L,
        capMs: Long = 15_000
    ): SpeedResult {
        try {
            val url = URL("https://speed.cloudflare.com/__down?bytes=$bytes")
            val conn = url.openConnection(
                Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxyPort))
            ) as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = capMs.toInt() + 2_000
            conn.setRequestProperty("User-Agent", "Forough/2.0")
            val code = conn.responseCode
            if (code !in 200..399) {
                return SpeedResult(0, 0, code, false, error = "HTTP $code")
            }

            val start = System.nanoTime()
            var total = 0L
            val buf = ByteArray(64 * 1024)
            conn.inputStream.use { input ->
                while (true) {
                    val elapsed = (System.nanoTime() - start) / 1_000_000
                    if (elapsed >= capMs) {
                        val secs = elapsed / 1000.0
                        return SpeedResult(
                            total, (total / secs).toLong(), code, hitCap = true
                        )
                    }
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                }
            }
            val elapsed = ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
            val secs = elapsed / 1000.0
            return SpeedResult(total, (total / secs).toLong(), code, hitCap = false)
        } catch (e: Exception) {
            return SpeedResult(0, 0, 0, false, error = e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * End-to-end traffic probe through the tunnel's local HTTP proxy —
     * the same three endpoints and success rule (2xx/3xx, first wins)
     * as the desktop traffic check.
     */
    fun probeTraffic(proxyPort: Int = 8081, timeoutMs: Int = 8_000): Int {
        val urls = listOf(
            "http://www.gstatic.com/generate_204",
            "http://cp.cloudflare.com/generate_204",
            "http://example.com/"
        )
        for (url in urls) {
            val code = httpCodeThroughProxy(url, proxyPort, timeoutMs)
            if (code in 200..399) return code
        }
        return 0
    }

    private fun httpCodeThroughProxy(url: String, proxyPort: Int, timeoutMs: Int): Int {
        return try {
            val conn = URL(url).openConnection(
                Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxyPort))
            ) as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Forough/2.0")
            val code = conn.responseCode
            try { conn.inputStream?.close() } catch (_: Exception) {}
            code
        } catch (_: Exception) {
            0
        }
    }

    /** Public IP + region through the tunnel (desktop IpService parity). */
    fun fetchPublicIp(proxyPort: Int = 8081, timeoutMs: Int = 10_000): Pair<String, String>? {
        val endpoints = listOf(
            "https://ifconfig.me/ip",
            "https://api.ipify.org",
            "https://icanhazip.com"
        )
        for (url in endpoints) {
            try {
                val conn = URL(url).openConnection(
                    Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxyPort))
                ) as HttpURLConnection
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.setRequestProperty("User-Agent", "Forough/2.0")
                val text = conn.inputStream.bufferedReader().use { it.readLine()?.trim() ?: "" }
                if (text.isNotEmpty() && text.length <= 45 && text.firstOrNull()?.isDigit() == true) {
                    return Pair(text, "-")
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var prev = -1
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (prev == '\r'.code && c == '\n'.code) {
                return sb.dropLast(1).toString()
            }
            sb.append(c.toChar())
            prev = c
            if (sb.length > 8192) return sb.toString()
        }
    }

    fun humanSpeed(bps: Long): String {
        val kb = bps / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format("%.2f MB/s", mb)
            kb >= 1.0 -> String.format("%.1f KB/s", kb)
            else -> "$bps B/s"
        }
    }

    fun humanBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }
}
