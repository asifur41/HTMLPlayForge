package com.htmlplayforge.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.htmlplayforge.app.R
import com.htmlplayforge.app.settings.AppTheme
import com.htmlplayforge.app.settings.ThemeManager
import com.htmlplayforge.app.utils.Ui

/** One pane at a time. Run slides the preview in; Back, swipe right or the system Back key return. */
class FlipLayoutView(context: Context) : FrameLayout(context), PaneLayout {
    private val editorHost = FrameLayout(context)
    private val previewPane = LinearLayout(context)
    private val header = LinearLayout(context)
    private val previewHost = FrameLayout(context)
    private val title = TextView(context)
    private val tintables = ArrayList<ImageButton>()
    private var theme: AppTheme = ThemeManager.current()
    private var shown = false

    override val root: View get() = this
    override var onReload: () -> Unit = {}
    override var onConsoleToggle: () -> Unit = {}
    override var onBackToEditor: () -> Unit = {}
    override val isPreviewShown: Boolean get() = shown

    private val swipe = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            val dx = e2.x - (e1?.x ?: e2.x)
            val dy = e2.y - (e1?.y ?: e2.y)
            if (dx > Ui.dp(context, 80) && Math.abs(dx) > Math.abs(dy) * 1.5f && vx > 600f) {
                onBackToEditor()
                return true
            }
            return false
        }
    })

    init {
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val back = Ui.iconButton(context, R.drawable.ic_back, theme.text, "Back to editor") { onBackToEditor() }
        val back2 = TextView(context)
        back2.text = "Back"
        back2.textSize = 15f
        back2.setOnClickListener { onBackToEditor() }
        tintables.add(back)
        header.addView(back)
        header.addView(back2, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        title.text = ""
        val reload = Ui.iconButton(context, R.drawable.ic_refresh, theme.text, "Reload preview") { onReload() }
        val console = Ui.iconButton(context, R.drawable.ic_console, theme.text, "Show or hide console") { onConsoleToggle() }
        tintables.add(reload); tintables.add(console)
        header.addView(reload)
        header.addView(console)
        header.setOnTouchListener { _, ev -> swipe.onTouchEvent(ev); false }
        header.tag = back2

        previewPane.orientation = LinearLayout.VERTICAL
        previewPane.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(context, 48)))
        previewPane.addView(previewHost, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        previewPane.visibility = GONE
        previewPane.isClickable = true // never let touches fall through to the editor
        // thin strip at the left edge: swipe right from here to go back
        val edge = View(context)
        edge.setOnTouchListener { _, ev -> swipe.onTouchEvent(ev); true }

        addView(editorHost, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(previewPane, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(edge, LayoutParams(Ui.dp(context, 20), LayoutParams.MATCH_PARENT, Gravity.START))
        edge.visibility = GONE
        edge.tag = "edge"
        applyTheme(theme)
    }

    override fun setContent(editor: View, preview: View) {
        editorHost.removeAllViews()
        previewHost.removeAllViews()
        PaneLayout.removeFromParent(editor)
        PaneLayout.removeFromParent(preview)
        editorHost.addView(editor, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        previewHost.addView(preview, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun edge(): View? = findViewWithTag("edge")

    override fun showPreview() {
        if (shown) return
        shown = true
        previewPane.visibility = VISIBLE
        edge()?.visibility = VISIBLE
        val w = width.toFloat().coerceAtLeast(1f)
        previewPane.translationX = w
        previewPane.scaleX = 0.96f
        previewPane.scaleY = 0.96f
        previewPane.animate().translationX(0f).scaleX(1f).scaleY(1f)
            .setDuration(450L).setInterpolator(DecelerateInterpolator()).setListener(null).start()
    }

    override fun showEditor() {
        if (!shown) return
        shown = false
        edge()?.visibility = GONE
        val w = width.toFloat().coerceAtLeast(1f)
        previewPane.animate().translationX(w).scaleX(0.96f).scaleY(0.96f)
            .setDuration(300L).setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!shown) previewPane.visibility = GONE
                }
            }).start()
    }

    override fun applyTheme(t: AppTheme) {
        theme = t
        setBackgroundColor(t.bg)
        previewPane.setBackgroundColor(t.bg)
        header.setBackgroundColor(t.editorBg)
        (header.tag as? TextView)?.setTextColor(t.text)
        for (b in tintables) b.imageTintList = android.content.res.ColorStateList.valueOf(t.text)
    }

    override fun detach() {
        editorHost.removeAllViews()
        previewHost.removeAllViews()
    }
}
