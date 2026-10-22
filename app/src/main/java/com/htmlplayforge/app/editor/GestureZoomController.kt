package com.htmlplayforge.app.editor

/**
 * The maths of pinch zoom. Size = start size x (finger distance now / at start),
 * snapped to a whole number and kept between 12 and 28. Pure Kotlin.
 */
class GestureZoomController(private val minSize: Int = 12, private val maxSize: Int = 28) {
    private var startSize = 16
    private var startSpan = 1f
    var active = false
        private set
    var currentSize = 16
        private set

    fun begin(size: Int, span: Float) {
        startSize = size.coerceIn(minSize, maxSize)
        startSpan = if (span <= 0f) 1f else span
        currentSize = startSize
        active = true
    }

    /** Returns the new whole-number size. */
    fun update(span: Float): Int {
        if (!active) return currentSize
        val raw = startSize * (span / startSpan)
        currentSize = Math.round(raw).coerceIn(minSize, maxSize)
        return currentSize
    }

    fun end(): Int {
        active = false
        return currentSize
    }

    fun percent(defaultSize: Int = 16): Int = Math.round(currentSize * 100f / defaultSize)
}
