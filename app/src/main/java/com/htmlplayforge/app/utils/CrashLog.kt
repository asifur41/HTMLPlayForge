package com.htmlplayforge.app.utils

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A private crash log. It stores only the error class names and code line numbers,
 * never your code or messages. It is never sent anywhere: it is attached to the
 * bug-report email only when you choose to send that email.
 */
object CrashLog {
    private const val FILE = "crash.log"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sb = StringBuilder()
                sb.append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append('\n')
                var t: Throwable? = error
                var depth = 0
                while (t != null && depth < 4) {
                    sb.append(t.javaClass.name).append('\n')
                    for (el in t.stackTrace.take(12)) {
                        sb.append("  at ").append(el.className).append('.').append(el.methodName)
                            .append(':').append(el.lineNumber).append('\n')
                    }
                    t = t.cause; depth++
                }
                File(app.filesDir, FILE).writeText(sb.toString())
            } catch (ignored: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? {
        val f = File(context.filesDir, FILE)
        return if (f.isFile) try { f.readText().take(4000) } catch (e: Exception) { null } else null
    }

    fun clear(context: Context) { File(context.filesDir, FILE).delete() }
}
