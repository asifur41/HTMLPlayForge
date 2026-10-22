package com.htmlplayforge.app.utils

import android.content.Context
import android.os.Build
import android.webkit.WebView

/** Facts about this phone and app, used by the bug report and the About dialog. */
object BugReport {
    fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) { "?" }

    fun webViewVersion(): String = try {
        WebView.getCurrentWebViewPackage()?.versionName ?: "unknown"
    } catch (t: Throwable) { "unknown" }

    fun androidVersion(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    fun device(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    fun body(context: Context): String {
        val sb = StringBuilder()
        sb.append("Please describe the problem here:\n\n\n")
        sb.append("---- Details (added by the app) ----\n")
        sb.append("App version: ").append(appVersion(context)).append('\n')
        sb.append(androidVersion()).append('\n')
        sb.append("Device: ").append(device()).append('\n')
        sb.append("WebView: ").append(webViewVersion()).append('\n')
        val crash = CrashLog.read(context)
        if (crash != null) sb.append("\nLast crash log:\n").append(crash).append('\n')
        return sb.toString()
    }

    fun send(context: Context) {
        LinkOpener.email(context, "HTMLPlayForge Bug Report", body(context))
    }
}
