package com.htmlplayforge.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorLogicTest {
    @Test fun undoRedoAndMerge() {
        val u = UndoRedoManager()
        u.reset(EditState("a", 0))
        u.record(EditState("ab", 2), 0L)
        u.record(EditState("abc", 3), 100L) // merged with the step before
        assertEquals("a", u.undo()!!.text)
        assertEquals("abc", u.redo()!!.text)
        assertFalse(u.canRedo())
        u.record(EditState("abcd", 4), 5000L)
        assertEquals("abc", u.undo()!!.text)
    }

    @Test fun undoKeeps100Levels() {
        val u = UndoRedoManager()
        u.reset(EditState("0", 0))
        for (i in 1..150) u.record(EditState("t$i", 1), i * 10_000L)
        var n = 0
        while (u.undo() != null) n++
        assertEquals(100, n)
    }

    @Test fun findNextWrapsAndMatchesCase() {
        val t = "Hello hello"
        val a = FindEngine.findNext(t, "hello", false, false, 0)!!
        assertEquals(0, a.first); assertEquals(4, a.last)
        assertEquals(6, FindEngine.findNext(t, "hello", true, false, 0)!!.first)
        assertEquals(0, FindEngine.findNext(t, "hello", false, false, 7)!!.first)
        assertNull(FindEngine.findNext(t, "zzz", false, false, 0))
    }

    @Test fun findPrevAndCount() {
        val t = "ab ab ab"
        assertEquals(3, FindEngine.findPrev(t, "ab", true, false, 6)!!.first)
        assertEquals(3, FindEngine.count(t, "ab", true, false))
    }

    @Test fun replaceAllPlainAndRegex() {
        assertEquals("X abc", FindEngine.replaceAll("a.c abc", "a.c", "X", true, false))
        assertEquals("c! d!", FindEngine.replaceAll("abc abd", "ab(.)", "$1!", true, true))
        assertFalse(FindEngine.isValidRegex("(("))
        assertEquals("keep", FindEngine.replaceAll("keep", "((", "x", true, true))
    }

    @Test fun zoomIsWholeNumberAndClamped() {
        val z = GestureZoomController()
        z.begin(16, 100f)
        assertEquals(28, z.update(200f))
        assertEquals(12, z.update(50f))
        assertEquals(18, z.update(110f))
        assertEquals(18, z.end())
        assertFalse(z.active)
    }

    @Test fun hintsForTagsCssAndJs() {
        assertTrue(CodeHints.suggest("<di", 3, Lang.HTML).any { it.label == "div" })
        assertTrue(CodeHints.suggest("a { col", 7, Lang.CSS).any { it.label == "color" })
        assertTrue(CodeHints.suggest("docu", 4, Lang.JS).any { it.label == "document.getElementById" })
        assertTrue(CodeHints.suggest("hello", 5, Lang.PLAIN).isEmpty())
    }

    @Test fun scannerFindsTagsAndAttributes() {
        val tags = HtmlScanner.scan("<img src=\"a.png\" alt='x'><br/><!-- <p> --><script>if (a<b) {}</script>")
        assertEquals(listOf("img", "br", "script", "script"), tags.map { it.name })
        assertEquals("a.png", tags[0].attrs.first { it.first == "src" }.second)
        assertNotNull(tags[1])
        assertTrue(tags[1].selfClosing)
    }
}
