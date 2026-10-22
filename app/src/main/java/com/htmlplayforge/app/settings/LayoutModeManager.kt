package com.htmlplayforge.app.settings

import com.htmlplayforge.app.utils.Prefs

object LayoutModeManager {
    const val SPLIT = "split"
    const val FLIP = "flip"

    val isFlip: Boolean get() = Prefs.layoutMode == FLIP
    fun set(mode: String) { Prefs.layoutMode = if (mode == FLIP) FLIP else SPLIT }
}
