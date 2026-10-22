package com.htmlplayforge.app.utils

import android.content.Context
import android.widget.Toast

/**
 * In-app messages only. System notifications need a permission on Android 13+,
 * and this app asks for no permissions at all.
 */
object NotificationHelper {
    fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
