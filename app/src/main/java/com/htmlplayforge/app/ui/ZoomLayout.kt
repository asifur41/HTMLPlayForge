package com.htmlplayforge.app.ui

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import android.widget.TextView
import com.htmlplayforge.app.editor.GestureZoomController
import com.htmlplayforge.app.utils.Ui

/**
 * Wraps the editor and handles pinch zoom. When the SECOND finger goes down this layout takes over
 * the touch (the editor gets ACTION_CANCEL, so scroll views cannot steal the gesture).
 * Works while the keyboard is open.
 */
class ZoomLayout(context: Context) : FrameLayout(context) {
    private val controller = GestureZoomController()
    private var zooming = false
    private val label = TextView(context)
    private val hideLabel = Runnable { label.visibility = GONE }

    /** Returns the current font size in sp. */
    var currentSize: () -> Int = { 16 }
    /** Called many times while pinching, with a whole-number size. */
    var onSize: (Int) -> Unit = {}
    /** Called once when the gesture ends, with a whole-number size to save. */
    var onEnd: (Int) -> Unit = {}

    private val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
            controller.begin(currentSize(), d.currentSpan)
            return true
        }

        override fun onScale(d: ScaleGestureDetector): Boolean {
            val s = controller.update(d.currentSpan)
            onSize(s)
            showLabel()
            return true
        }

        override fun onScaleEnd(d: ScaleGestureDetector) {
            if (controller.active) onEnd(controller.end())
        }
    })

    init {
        detector.isQuickScaleEnabled = false
        label.setTextColor(0xFFFFFFFF.toInt())
        label.textSize = 14f
        label.background = Ui.roundBackground(0xCC000000.toInt(), 12, context)
        val p = Ui.dp(context, 10)
        label.setPadding(p, p / 2, p, p / 2)
        label.visibility = GONE
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
        lp.setMargins(0, Ui.dp(context, 12), Ui.dp(context, 12), 0)
        addView(label, lp)
    }

    private fun showLabel() {
        label.text = "${controller.percent()}%"
        label.visibility = VISIBLE
        label.bringToFront()
        removeCallbacks(hideLabel)
        postDelayed(hideLabel, 700L)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_POINTER_DOWN && ev.pointerCount >= 2) zooming = true
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) zooming = false
        return zooming
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            zooming = false
            if (controller.active) onEnd(controller.end())
        }
        return true
    }
}
