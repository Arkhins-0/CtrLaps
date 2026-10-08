package com.arkhins.ctrlaps.data

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import com.arkhins.ctrlaps.BuildConfig
import com.arkhins.ctrlaps.Config
import com.arkhins.ctrlaps.CrashActivity
import com.arkhins.ctrlaps.push.Notifications
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** What support needs to know about this phone and this copy of the app, as plain lines to paste into a ticket. */
fun deviceInfo(context: Context): String {
    val locale = context.resources.configuration.locales[0]
    val maker = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.ROOT) }
    return listOf(
        "${Config.APP_NAME} v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})${if (BuildConfig.DEBUG) " · Debug" else ""}",
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "$maker ${Build.MODEL}",
        "Processor ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}",
        "Language ${locale.toLanguageTag()} · ${TimeZone.getDefault().id}",
    ).joinToString("\n")
}

/**
 * After Arkhime's crash screen: when the app stops on screen, the report (this phone, the error, the app's last log
 * lines) is kept and [CrashActivity] opens in its own process with Copy, Share, Raise a ticket and Open again.
 * A crash while the app is in the background goes the phone's usual way (nothing pops up over another app).
 */
object CrashReporter {
    private const val FILE = "crash/last.txt"
    /** Starts the app on the support form with this text in it (from the crash page's Raise a ticket). */
    const val EXTRA_TICKET = "crashTicket"

    /** A crash report waiting to fill in the support form, set as the app opens from the crash page. */
    val ticket = MutableStateFlow<String?>(null)

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            Log.e("CTRLAPS", "The app stopped", error)
            val shown = Notifications.foreground && runCatching {
                save(app, report(app, thread, error))
                app.startActivity(Intent(app, CrashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }.isSuccess
            if (!shown) {
                previous?.uncaughtException(thread, error)
                return@setDefaultUncaughtExceptionHandler
            }
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    fun read(context: Context): String? = runCatching { File(context.filesDir, FILE).readText() }.getOrNull()

    /** What goes in a ticket: room for a line on what was happening, then the phone and the error (the server keeps 5,000). */
    fun ticketText(report: String): String {
        val error = report.substringAfter("\n\n", report).substringBefore("\n\nRecent log:")
        return ("The app stopped while I was using it. What I was doing:\n\n\n—\n" + error).take(4800)
    }

    private fun save(context: Context, report: String) {
        File(context.filesDir, FILE).apply { parentFile?.mkdirs() }.writeText(report)
    }

    private fun report(context: Context, thread: Thread, error: Throwable): String {
        val time = SimpleDateFormat("d MMM yyyy, h:mm:ss a", Locale.ENGLISH).format(Date())
        return buildString {
            append("${Config.APP_NAME} stopped · $time\n\n")
            append(deviceInfo(context)).append('\n')
            append("Thread: ${thread.name}\n\n")
            append(Log.getStackTraceString(error).trimEnd())
            recentLog()?.let { append("\n\nRecent log:\n").append(it) }
        }.take(64_000)
    }

    /** The app's own last log lines (an app may read its own), or null when the phone won't give them. */
    private fun recentLog(): String? = runCatching {
        val p = ProcessBuilder("logcat", "-d", "-t", "200", "--pid", Process.myPid().toString()).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        p.waitFor(2, TimeUnit.SECONDS)
        text.trim().takeIf { it.isNotEmpty() }
    }.getOrNull()
}
