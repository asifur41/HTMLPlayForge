package com.htmlplayforge.app.utils

import android.app.Activity
import android.view.HapticFeedbackConstants
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** The warning dialogs of the app. Each one gives a short vibration when it opens. */
object WarningDialog {
    const val CLEAR = "This will erase all code. Continue?"
    const val DISCARD = "Unsaved changes will be lost. Continue?"
    const val EXIT = "Unsaved changes. Exit anyway?"
    fun delete(name: String) = "Delete '$name'? Cannot be undone."
    fun overwrite(name: String) = "'$name' exists. Overwrite?"
    fun rename(name: String) = "Rename '$name'? Other files that use the old name will stop finding it."

    fun confirm(activity: Activity, message: String, positive: String = "Continue", onYes: () -> Unit) {
        val d = MaterialAlertDialogBuilder(activity)
            .setMessage(message)
            .setPositiveButton(positive) { _, _ -> onYes() }
            .setNegativeButton("Cancel", null)
            .create()
        d.setOnShowListener { d.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
        d.show()
    }
}
