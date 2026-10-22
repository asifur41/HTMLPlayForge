package com.htmlplayforge.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightTest {
    private fun words(text: String, lang: Lang, kind: Kind): List<String> =
        SyntaxHighlighter.highlight(text, lang).filter { it.kind == kind }.map { text.substring(it.start, it.end) }

    @Test fun htmlTagsAttributesAndStrings() {
        val t = "<div class=\"a\">hi</div>"
        assertTrue(words(t, Lang.HTML, Kind.KEYWORD).contains("<div"))
        assertTrue(words(t, Lang.HTML, Kind.ATTR).contains("class"))
        assertTrue(words(t, Lang.HTML, Kind.STRING).contains("\"a\""))
    }

    @Test fun htmlCommentAndDoctype() {
        val t = "<!DOCTYPE html><!-- note -->"
        assertTrue(words(t, Lang.HTML, Kind.SPECIAL).contains("<!DOCTYPE html>"))
        assertTrue(words(t, Lang.HTML, Kind.COMMENT).contains("<!-- note -->"))
    }

    @Test fun scriptInsideHtmlIsColouredAsJs() {
        val t = "<script>let a = 1;</script>"
        assertTrue(words(t, Lang.HTML, Kind.KEYWORD).contains("let"))
        assertTrue(words(t, Lang.HTML, Kind.NUMBER).contains("1"))
    }

    @Test fun styleInsideHtmlIsColouredAsCss() {
        val t = "<style>p { color: red; }</style>"
        assertTrue(words(t, Lang.HTML, Kind.ATTR).contains("color"))
    }

    @Test fun cssPartsAreFound() {
        val t = "a { margin: 10px; color: #fff; }"
        assertTrue(words(t, Lang.CSS, Kind.ATTR).contains("margin"))
        assertTrue(words(t, Lang.CSS, Kind.NUMBER).contains("10px"))
        assertTrue(words(t, Lang.CSS, Kind.NUMBER).contains("#fff"))
    }

    @Test fun jsStringsAndComments() {
        val t = "const s = 'x'; // note\nlet t = `y`;"
        assertTrue(words(t, Lang.JS, Kind.STRING).contains("'x'"))
        assertTrue(words(t, Lang.JS, Kind.STRING).contains("`y`"))
        assertTrue(words(t, Lang.JS, Kind.COMMENT).contains("// note"))
    }

    @Test fun jsonKeysAreAttributes() {
        val t = "{\"name\": \"x\", \"n\": 5}"
        assertTrue(words(t, Lang.JSON, Kind.ATTR).contains("\"name\""))
        assertTrue(words(t, Lang.JSON, Kind.STRING).contains("\"x\""))
    }

    @Test fun veryLongTextIsSkipped() {
        val t = "<p>".repeat(30_000)
        assertEquals(0, SyntaxHighlighter.highlight(t, Lang.HTML).size)
    }

    @Test fun strangeTextNeverThrows() {
        val samples = listOf("<", "<<<<", "<a href=\"", "/*", "'", "{{{{", "&", "<!--", "<script>", "<style>a{")
        for (s in samples) for (l in Lang.values()) SyntaxHighlighter.highlight(s, l)
    }

    @Test fun langFromFileName() {
        assertEquals(Lang.HTML, Lang.fromFileName("a.HTML"))
        assertEquals(Lang.CSS, Lang.fromFileName("x.css"))
        assertEquals(Lang.JS, Lang.fromFileName("x.js"))
        assertEquals(Lang.JSON, Lang.fromFileName("x.json"))
        assertEquals(Lang.PLAIN, Lang.fromFileName("x.txt"))
        assertEquals(Lang.HTML, Lang.fromFileName(null))
    }

    @Test fun lineIndexWorks() {
        val idx = LineIndex("ab\ncd\n")
        assertEquals(1, idx.lineOf(0))
        assertEquals(2, idx.lineOf(3))
        assertEquals(2, idx.colOf(4))
        assertEquals(3, idx.lineCount)
    }
}
