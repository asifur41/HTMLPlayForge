package com.htmlplayforge.app.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Every external link of the app lives here. Change a value here and rebuild. */
object LinkOpener {
    const val WEBSITE = "https://asifur-dev.blogspot.com/p/home.html?m=1&m=1"
    const val DONATE = "https://asifur-dev.blogspot.com/p/donate.html?m=1"
    const val EMAIL = "asifur2580@gmail.com"
    const val RELEASES = "https://github.com/asifur41/HTMLPlayForge/releases/latest"
    const val APK_DOWNLOAD = "https://github.com/asifur41/HTMLPlayForge/releases/latest/download/HTMLPlayForge.apk"
    /** Temporary value: replace with your community link (for example a Telegram group). */
    const val COMMUNITY = WEBSITE
    /** Temporary value: replace with your review link. */
    const val REVIEW = WEBSITE

    const val DEVELOPER_NAME = "Md Asifur Rahman"
    const val COUNTRY = "Bangladesh"

    fun open(context: Context, url: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            i.addCategory(Intent.CATEGORY_BROWSABLE)
            context.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
        }
    }

    fun email(context: Context, subject: String, body: String) {
        try {
            val i = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            context.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No email app found. Write to $EMAIL", Toast.LENGTH_LONG).show()
        }
    }
}
