package com.htmlplayforge.app.settings

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.htmlplayforge.app.editor.Kind
import com.htmlplayforge.app.utils.Prefs

/** All colours of one theme. Plain numbers, so it can be tested. */
data class AppTheme(
    val id: String,
    val name: String,
    val dark: Boolean,
    val bg: Int,
    val text: Int,
    val editorBg: Int,
    val accent: Int,
    val keyword: Int,
    val string: Int,
    val comment: Int,
    val number: Int,
    val attr: Int,
    val special: Int,
    val gutter: Int
)

object ThemeManager {
    private const val A = 0xFF000000.toInt()

    val DAY = AppTheme("day", "Day", false, A or 0xFFFFFF, A or 0x000000, A or 0xFAFAFA, A or 0x6200DD,
        A or 0x0000FF, A or 0x006000, A or 0x008000, A or 0x098658, A or 0x267F99, A or 0xAF00DB, A or 0x999999)
    val NIGHT = AppTheme("night", "Night", true, A or 0x1E1E1E, A or 0xE0E0E0, A or 0x2D2D2D, A or 0xBB86FC,
        A or 0x569CD6, A or 0xCE9178, A or 0x6A9955, A or 0xB5CEA8, A or 0x4EC9B0, A or 0xC586C0, A or 0x858585)
    val BLUISH = AppTheme("bluish", "Bluish", true, A or 0x0A1628, A or 0xE6F2FF, A or 0x0F2340, A or 0x2196F3,
        A or 0x7EC8FF, A or 0x98FF98, A or 0x4A84AA, A or 0xFFD479, A or 0x5FD3D3, A or 0xC79CFF, A or 0x4A84AA)
    val GREENISH = AppTheme("greenish", "Greenish", true, A or 0x0D1A0D, A or 0xE6FFE6, A or 0x112211, A or 0x4CAF50,
        A or 0x9CFF9C, A or 0xFFBFFF, A or 0x6AA857, A or 0xFFE08A, A or 0x7FE0C0, A or 0xE0A8FF, A or 0x6AA857)
    val REDDISH = AppTheme("reddish", "Reddish", true, A or 0x1A0A0A, A or 0xFFE6E6, A or 0x221111, A or 0xF44336,
        A or 0xFF9C9C, A or 0xFFFF98, A or 0x8A5757, A or 0xA8E6A1, A or 0xFFB38A, A or 0xFFA8D8, A or 0x8A5757)

    val all = listOf(DAY, NIGHT, BLUISH, GREENISH, REDDISH)

    fun current(): AppTheme = all.firstOrNull { it.id == Prefs.theme } ?: NIGHT

    fun colorFor(kind: Kind, t: AppTheme): Int = when (kind) {
        Kind.KEYWORD -> t.keyword
        Kind.STRING -> t.string
        Kind.COMMENT -> t.comment
        Kind.NUMBER -> t.number
        Kind.ATTR -> t.attr
        Kind.SPECIAL -> t.special
    }

    /** Day uses the light system mode, the other themes use the dark one. */
    fun applyNightMode() {
        AppCompatDelegate.setDefaultNightMode(
            if (current().dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    /** Colours the status bar, navigation bar and window background. */
    fun styleWindow(activity: Activity) {
        val t = current()
        val w = activity.window
        w.setBackgroundDrawable(ColorDrawable(t.bg))
        @Suppress("DEPRECATION")
        w.statusBarColor = t.bg
        @Suppress("DEPRECATION")
        w.navigationBarColor = t.bg
        val c = WindowCompat.getInsetsController(w, w.decorView)
        c.isAppearanceLightStatusBars = !t.dark
        c.isAppearanceLightNavigationBars = !t.dark
    }
}
