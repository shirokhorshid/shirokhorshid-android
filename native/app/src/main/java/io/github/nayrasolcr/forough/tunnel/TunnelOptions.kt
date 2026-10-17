package io.github.nayrasolcr.forough.tunnel

/**
 * Protocol selection modes — port of the Forough Linux client's
 * ProtocolMode enum (gui/src/models/protocol-mode.vala).
 */
enum class ProtocolMode(val configValue: String) {
    AUTO("auto"),
    CONDUIT("conduit"),
    DIRECT("direct"),
    CDN_FRONTING("cdn_fronting");

    companion object {
        fun fromConfig(value: String): ProtocolMode =
            entries.firstOrNull { it.configValue == value } ?: AUTO
    }
}

/**
 * Egress regions — the exact list from the Linux client's Region model
 * (gui/src/models/region.vala).
 */
data class Region(val code: String, val name: String) {
    fun flagEmoji(): String = flagForCode(code)

    companion object {
        val ALL = listOf(
            Region("US", "United States"), Region("DE", "Germany"),
            Region("NL", "Netherlands"), Region("CA", "Canada"),
            Region("GB", "United Kingdom"), Region("FR", "France"),
            Region("SG", "Singapore"), Region("JP", "Japan"),
            Region("AU", "Australia"), Region("IN", "India"),
            Region("BR", "Brazil"), Region("PL", "Poland"),
            Region("SE", "Sweden"), Region("CH", "Switzerland"),
            Region("ES", "Spain"), Region("IT", "Italy"),
            Region("RO", "Romania"), Region("CZ", "Czech Republic"),
            Region("AT", "Austria"), Region("BE", "Belgium"),
            Region("HK", "Hong Kong"), Region("KR", "South Korea"),
            Region("IE", "Ireland"), Region("PT", "Portugal"),
            Region("NO", "Norway"), Region("FI", "Finland"),
            Region("UA", "Ukraine"), Region("TR", "Turkey"),
            Region("MX", "Mexico")
        )

        fun flagForCode(code: String): String {
            if (code.length != 2 || !code.all { it in 'A'..'Z' }) return ""
            val base = 0x1F1E6
            val a = code[0] - 'A'
            val b = code[1] - 'A'
            return String(Character.toChars(base + a)) + String(Character.toChars(base + b))
        }
    }
}

/**
 * Connection options — port of the Linux client's TunnelOptions model
 * (gui/src/models/tunnel-options.vala). Desktop-only options (system proxy,
 * TUN routing, binary paths, Fake-IP sing-box experiment) do not exist on
 * Android: the VpnService IS the whole-device routing.
 */
data class TunnelOptions(
    val protocol: ProtocolMode = ProtocolMode.AUTO,
    val region: String = "",
    val upstreamProxyUrl: String = "",
    val enableTimeout: Boolean = false,
    val enableLan: Boolean = false,
    val enableBeastMode: Boolean = true,
    val inproxyCompartmentId: String = "",
    val cdnFrontingIpList: String = "",
    val cdnFrontingSni: String = "",
    val oslUrls: String = "",
    // Set on the final automatic restart: strip QUIC protocols (self-heal
    // for throttled-QUIC networks — same policy as the desktop client).
    val tcpOnlyOverride: Boolean = false
)
