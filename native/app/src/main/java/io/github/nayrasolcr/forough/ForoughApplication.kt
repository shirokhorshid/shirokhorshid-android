package io.github.nayrasolcr.forough

import android.app.Application
import android.content.Context
import io.github.nayrasolcr.forough.tunnel.SettingsStore

class ForoughApplication : Application() {

    override fun attachBaseContext(base: Context) {
        val pref = SettingsStore(base).language
        super.attachBaseContext(LocaleHelper.wrap(base, pref))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashLog.install(this)
        // If the previous session died on an uncaught exception, say so in
        // the log view and keep the trace available for the diagnostics copy.
        val crash = CrashLog.read(this)
        if (crash != null) {
            ForoughBus.appendLog("previous session crashed — open the (i) button in the header and share the report")
            ForoughBus.setToast(null)
        }
    }

    companion object {
        @Volatile var instance: ForoughApplication? = null
            private set
    }
}
