package com.htmlplayforge.app.ui

import android.view.View
import android.view.ViewGroup
import com.htmlplayforge.app.settings.AppTheme

/** A way to arrange the editor and the preview (Split or Flip). */
interface PaneLayout {
    /** The view to add to the screen. */
    val root: View
    fun setContent(editor: View, preview: View)
    fun showPreview()
    fun showEditor()
    val isPreviewShown: Boolean
    var onReload: () -> Unit
    var onConsoleToggle: () -> Unit
    var onBackToEditor: () -> Unit
    fun applyTheme(t: AppTheme)
    /** Removes both views from this layout so another layout can use them. */
    fun detach()

    companion object {
        fun removeFromParent(v: View) { (v.parent as? ViewGroup)?.removeView(v) }
    }
}
