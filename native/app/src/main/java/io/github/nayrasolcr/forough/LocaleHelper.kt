package io.github.nayrasolcr.forough

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Language switching — the Android counterpart of the desktop client's
 * I18n.init: "system" (default) | "fa" | "en".
 *
 * "system" uses the unmodified context: Android then picks values-fa
 * automatically when the device language is Persian (the same outcome as
 * the desktop's LANG inspection). Explicit "fa" forces Persian with full
 * RTL — exactly what the desktop main() does when fa is active.
 */
object LocaleHelper {

    fun wrap(context: Context, languagePref: String): Context {
        return when (languagePref) {
            "fa", "en" -> {
                val locale = Locale(languagePref)
                Locale.setDefault(locale)
                val config = Configuration(context.resources.configuration)
                config.setLocale(locale)
                config.setLayoutDirection(locale)
                context.createConfigurationContext(config)
            }
            else -> context
        }
    }
}
