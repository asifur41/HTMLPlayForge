package com.htmlplayforge.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorsTest {
    @Test fun dayThemeMatchesTheTable() {
        val t = ThemeManager.DAY
        assertFalse(t.dark)
        assertEquals(0xFFFFFFFF.toInt(), t.bg)
        assertEquals(0xFFFAFAFA.toInt(), t.editorBg)
        assertEquals(0xFF6200DD.toInt(), t.accent)
        assertEquals(0xFF0000FF.toInt(), t.keyword)
        assertEquals(0xFF999999.toInt(), t.gutter)
    }

    @Test fun nightThemeMatchesTheTable() {
        val t = ThemeManager.NIGHT
        assertTrue(t.dark)
        assertEquals(0xFF1E1E1E.toInt(), t.bg)
        assertEquals(0xFFBB86FC.toInt(), t.accent)
        assertEquals(0xFF569CD6.toInt(), t.keyword)
        assertEquals(0xFFCE9178.toInt(), t.string)
        assertEquals(0xFFC586C0.toInt(), t.special)
    }

    @Test fun otherThemesMatchTheTable() {
        assertEquals(0xFF0A1628.toInt(), ThemeManager.BLUISH.bg)
        assertEquals(0xFF7EC8FF.toInt(), ThemeManager.BLUISH.keyword)
        assertEquals(0xFF0D1A0D.toInt(), ThemeManager.GREENISH.bg)
        assertEquals(0xFFFFE08A.toInt(), ThemeManager.GREENISH.number)
        assertEquals(0xFF1A0A0A.toInt(), ThemeManager.REDDISH.bg)
        assertEquals(0xFFF44336.toInt(), ThemeManager.REDDISH.accent)
        assertEquals(5, ThemeManager.all.size)
    }
}
