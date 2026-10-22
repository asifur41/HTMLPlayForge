package com.htmlplayforge.app.utils

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.htmlplayforge.app.settings.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Background work: do the job off the UI thread, deliver the result on the main thread. */
object Async {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun <T> run(work: () -> T, done: (Result<T>) -> Unit) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching(work) }
            done(r)
        }
    }
}

/** Small helpers for building screens in code. */
object Ui {
    private val main = Handler(Looper.getMainLooper())

    fun post(delayMs: Long = 0L, r: Runnable) { main.postDelayed(r, delayMs) }
    fun cancel(r: Runnable) { main.removeCallbacks(r) }

    fun dp(context: Context, v: Int): Int = Math.round(v * context.resources.displayMetrics.density)

    fun toast(context: Context, msg: String) = NotificationHelper.toast(context, msg)

    /** Padding for the system bars on the root view (ready for targetSdk 35+). */
    fun applyInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    fun iconButton(context: Context, iconRes: Int, tint: Int, description: String, onClick: () -> Unit): ImageButton {
        val b = ImageButton(context)
        b.setImageResource(iconRes)
        b.imageTintList = ColorStateList.valueOf(tint)
        b.contentDescription = description
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
        b.setBackgroundResource(outValue.resourceId)
        val pad = dp(context, 10)
        b.setPadding(pad, pad, pad, pad)
        b.layoutParams = LinearLayout.LayoutParams(dp(context, 44), dp(context, 44))
        b.setOnClickListener { onClick() }
        return b
    }

    fun roundBackground(color: Int, radiusDp: Int, context: Context): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(context, radiusDp).toFloat()
        return g
    }

    fun dot(context: Context, color: Int): View {
        val v = View(context)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(color)
        v.background = g
        val s = dp(context, 8)
        v.layoutParams = LinearLayout.LayoutParams(s, s).also { it.marginEnd = dp(context, 6); it.gravity = Gravity.CENTER_VERTICAL }
        return v
    }

    /** A switch whose ON and OFF states look different (lesson 3). */
    fun styleSwitch(sw: SwitchCompat, theme: AppTheme) {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val offThumb = if (theme.dark) 0xFF9E9E9E.toInt() else 0xFFBDBDBD.toInt()
        val offTrack = if (theme.dark) 0xFF444444.toInt() else 0xFFDDDDDD.toInt()
        sw.thumbTintList = ColorStateList(states, intArrayOf(theme.accent, offThumb))
        sw.trackTintList = ColorStateList(states, intArrayOf(withAlpha(theme.accent, 0x88), offTrack))
    }

    fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    fun label(context: Context, text: String, sizeSp: Float, color: Int): TextView {
        val t = TextView(context)
        t.text = text
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        t.setTextColor(color)
        return t
    }

    /**
     * Text input dialog. The dialog stays open when [validate] returns an error text
     * (the click is attached in setOnShowListener, lesson 7).
     */
    fun inputDialog(
        activity: Activity,
        title: String,
        hint: String,
        initial: String,
        positive: String,
        validate: (String) -> String?,
        onOk: (String) -> Unit
    ) {
        val input = EditText(activity)
        input.hint = hint
        input.setText(initial)
        input.setSingleLine(true)
        input.setSelection(initial.length)
        val box = LinearLayout(activity)
        val pad = dp(activity, 20)
        box.setPadding(pad, dp(activity, 8), pad, 0)
        box.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val dialog: AlertDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(positive, null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            input.requestFocus()
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString()
                val error = validate(value)
                if (error != null) input.error = error else { dialog.dismiss(); onOk(value) }
            }
        }
        dialog.show()
    }
}
