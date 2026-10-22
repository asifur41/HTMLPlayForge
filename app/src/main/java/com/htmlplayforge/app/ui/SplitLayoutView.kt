package com.htmlplayforge.app.ui

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.htmlplayforge.app.R
import com.htmlplayforge.app.settings.AppTheme
import com.htmlplayforge.app.settings.ThemeManager
import com.htmlplayforge.app.utils.Prefs
import com.htmlplayforge.app.utils.Ui

/** Editor on top, preview below, a draggable divider in between. */
class SplitLayoutView(context: Context) : LinearLayout(context), PaneLayout {
    private val editorHost = LinearLayout(context)
    private val previewHost = LinearLayout(context)
    private val divider = LinearLayout(context)
    private val label = TextView(context)
    private var theme: AppTheme = ThemeManager.current()
    private val minPreviewPx = Ui.dp(context, 72)
    private val dividerPx = Ui.dp(context, 40)
    private var ratio = Prefs.splitRatio
    private val tintables = ArrayList<android.widget.ImageButton>()

    override val root: View get() = this
    override var onReload: () -> Unit = {}
    override var onConsoleToggle: () -> Unit = {}
    override var onBackToEditor: () -> Unit = {}
    override val isPreviewShown: Boolean get() = true

    init {
        orientation = VERTICAL
        editorHost.orientation = VERTICAL
        previewHost.orientation = VERTICAL

        divider.orientation = HORIZONTAL
        divider.gravity = Gravity.CENTER_VERTICAL
        divider.setPadding(Ui.dp(context, 12), 0, 0, 0)
        label.text = "PREVIEW"
        label.textSize = 12f
        label.letterSpacing = 0.1f
        divider.addView(label, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val reload = Ui.iconButton(context, R.drawable.ic_refresh, theme.text, "Reload preview") { onReload() }
        val console = Ui.iconButton(context, R.drawable.ic_console, theme.text, "Show or hide console") { onConsoleToggle() }
        tintables.add(reload); tintables.add(console)
        divider.addView(reload)
        divider.addView(console)
        divider.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> true
                MotionEvent.ACTION_MOVE -> { dragTo(ev.rawY); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { Prefs.splitRatio = ratio; true }
                else -> false
            }
        }

        addView(editorHost, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(divider, LayoutParams(LayoutParams.MATCH_PARENT, dividerPx))
        addView(previewHost, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        applyWeights()
        applyTheme(theme)
    }

    private fun applyWeights() {
        (editorHost.layoutParams as LayoutParams).weight = ratio
        (previewHost.layoutParams as LayoutParams).weight = 1f - ratio
        editorHost.requestLayout()
        previewHost.requestLayout()
    }

    private fun dragTo(rawY: Float) {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val total = height - dividerPx
        if (total <= 0) return
        val y = rawY - loc[1] - dividerPx / 2f
        val maxRatio = 1f - minPreviewPx.toFloat() / total
        ratio = (y / total).coerceIn(0.15f, maxOf(0.15f, maxRatio))
        applyWeights()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val total = h - dividerPx
        if (total > 0) {
            val maxRatio = 1f - minPreviewPx.toFloat() / total
            if (ratio > maxRatio && maxRatio > 0.15f) { ratio = maxRatio; applyWeights() }
        }
    }

    override fun setContent(editor: View, preview: View) {
        editorHost.removeAllViews()
        previewHost.removeAllViews()
        PaneLayout.removeFromParent(editor)
        PaneLayout.removeFromParent(preview)
        editorHost.addView(editor, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        previewHost.addView(preview, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun showPreview() {}
    override fun showEditor() {}

    override fun applyTheme(t: AppTheme) {
        theme = t
        setBackgroundColor(t.bg)
        divider.setBackgroundColor(t.editorBg)
        label.setTextColor(t.gutter)
        for (b in tintables) b.imageTintList = android.content.res.ColorStateList.valueOf(t.text)
    }

    override fun detach() {
        editorHost.removeAllViews()
        previewHost.removeAllViews()
    }
}
