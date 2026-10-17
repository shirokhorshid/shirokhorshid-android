package io.github.nayrasolcr.forough.tunnel

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists TunnelOptions — the Android counterpart of the Linux client's
 * SettingsService + GSettings storage.
 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("forough_settings", Context.MODE_PRIVATE)

    /** "system" | "fa" | "en" — same surface as the desktop language setting. */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    fun load(): TunnelOptions = TunnelOptions(
        protocol = ProtocolMode.fromConfig(prefs.getString(KEY_PROTOCOL, "auto") ?: "auto"),
        region = prefs.getString(KEY_REGION, "") ?: "",
        upstreamProxyUrl = prefs.getString(KEY_UPSTREAM, "") ?: "",
        enableTimeout = prefs.getBoolean(KEY_TIMEOUT, false),
        enableLan = prefs.getBoolean(KEY_LAN, false),
        enableBeastMode = prefs.getBoolean(KEY_BEAST, true),
        inproxyCompartmentId = prefs.getString(KEY_COMPARTMENT, "") ?: "",
        cdnFrontingIpList = prefs.getString(KEY_CDN_IPS, "") ?: "",
        cdnFrontingSni = prefs.getString(KEY_CDN_SNI, "") ?: "",
        oslUrls = prefs.getString(KEY_OSL, "") ?: ""
    )

    fun save(options: TunnelOptions) {
        prefs.edit()
            .putString(KEY_PROTOCOL, options.protocol.configValue)
            .putString(KEY_REGION, options.region)
            .putString(KEY_UPSTREAM, options.upstreamProxyUrl)
            .putBoolean(KEY_TIMEOUT, options.enableTimeout)
            .putBoolean(KEY_LAN, options.enableLan)
            .putBoolean(KEY_BEAST, options.enableBeastMode)
            .putString(KEY_COMPARTMENT, options.inproxyCompartmentId)
            .putString(KEY_CDN_IPS, options.cdnFrontingIpList)
            .putString(KEY_CDN_SNI, options.cdnFrontingSni)
            .putString(KEY_OSL, options.oslUrls)
            .apply()
    }

    /** Scanner scope switches (same surface as the desktop scanner dialog). */
    var scanCloudflare: Boolean
        get() = prefs.getBoolean(KEY_SCAN_CF, true)
        set(value) = prefs.edit().putBoolean(KEY_SCAN_CF, value).apply()

    var scanFastly: Boolean
        get() = prefs.getBoolean(KEY_SCAN_FASTLY, false)
        set(value) = prefs.edit().putBoolean(KEY_SCAN_FASTLY, value).apply()

    var scanAkamai: Boolean
        get() = prefs.getBoolean(KEY_SCAN_AKAMAI, false)
        set(value) = prefs.edit().putBoolean(KEY_SCAN_AKAMAI, value).apply()

    var scanCloudfront: Boolean
        get() = prefs.getBoolean(KEY_SCAN_CFRT, false)
        set(value) = prefs.edit().putBoolean(KEY_SCAN_CFRT, value).apply()

    var scanBunny: Boolean
        get() = prefs.getBoolean(KEY_SCAN_BUNNY, false)
        set(value) = prefs.edit().putBoolean(KEY_SCAN_BUNNY, value).apply()

    var scanSampleCount: Int
        get() = prefs.getInt(KEY_SCAN_SAMPLE, 1024)
        set(value) = prefs.edit().putInt(KEY_SCAN_SAMPLE, value).apply()

    var scanConcurrency: Int
        get() = prefs.getInt(KEY_SCAN_CONC, 96)
        set(value) = prefs.edit().putInt(KEY_SCAN_CONC, value).apply()

    var scanCustomCidrs: String
        get() = prefs.getString(KEY_SCAN_CIDRS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SCAN_CIDRS, value).apply()

    var scanCustomSnis: String
        get() = prefs.getString(KEY_SCAN_SNIS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SCAN_SNIS, value).apply()

    companion object {
        private const val KEY_LANGUAGE = "language"
        private const val KEY_PROTOCOL = "protocol"
        private const val KEY_REGION = "region"
        private const val KEY_UPSTREAM = "upstream_proxy_url"
        private const val KEY_TIMEOUT = "enable_timeout"
        private const val KEY_LAN = "enable_lan"
        private const val KEY_BEAST = "enable_beast_mode"
        private const val KEY_COMPARTMENT = "inproxy_compartment_id"
        private const val KEY_CDN_IPS = "cdn_fronting_ip_list"
        private const val KEY_CDN_SNI = "cdn_fronting_sni"
        private const val KEY_OSL = "osl_urls"
        private const val KEY_SCAN_CF = "scan_cloudflare"
        private const val KEY_SCAN_FASTLY = "scan_fastly"
        private const val KEY_SCAN_AKAMAI = "scan_akamai"
        private const val KEY_SCAN_CFRT = "scan_cloudfront"
        private const val KEY_SCAN_BUNNY = "scan_bunny"
        private const val KEY_SCAN_SAMPLE = "scan_sample_count"
        private const val KEY_SCAN_CONC = "scan_concurrency"
        private const val KEY_SCAN_CIDRS = "scan_custom_cidrs"
        private const val KEY_SCAN_SNIS = "scan_custom_snis"
    }
}

/** JSON helpers shared by the config builder and diagnostics. */
object JsonUtil {
    fun stringArray(values: List<String>): JSONArray {
        val array = JSONArray()
        for (v in values) array.put(v)
        return array
    }

    fun obj(vararg pairs: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        for ((k, v) in pairs) if (v != null) o.put(k, v)
        return o
    }
}
