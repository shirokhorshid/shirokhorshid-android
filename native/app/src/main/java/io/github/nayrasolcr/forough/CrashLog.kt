package io.github.nayrasolcr.forough

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash journal — catches any uncaught exception on any thread and writes
 * the full stack trace to filesDir/last_crash.txt before the platform
 * handler runs. The report surfaces in the diagnostics copy (the (i) button
 * in the header) and as a log line on the next launch, so a crash on a
 * user device can always be reported back with real data instead of a
 * blind "the app crashed".
 */
object CrashLog {

    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val platform = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                write(context, thread, throwable)
            } catch (_: Throwable) {
                // Never let the journal itself fail the process.
            }
            platform?.uncaughtException(thread, throwable)
        }
    }

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun read(context: Context): String? {
        val f = file(context)
        return if (f.exists() && f.length() > 0) {
            try {
                f.readText().take(16000)
            } catch (_: Throwable) {
                null
            }
        } else null
    }

    /** Called after the report has been shared — keeps the journal current. */
    fun clear(context: Context) {
        try {
            file(context).delete()
        } catch (_: Throwable) {
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.appendLine("time: $stamp")
        sb.appendLine("app: Forough Android ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        sb.appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("thread: ${thread.name}")
        sb.appendLine(throwable.stackTraceToString())
        file(context).writeText(sb.toString())
    }
}
