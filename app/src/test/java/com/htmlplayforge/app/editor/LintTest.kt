package com.htmlplayforge.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LintTest {
    private val good = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>T</title></head><body><h1>Hi</h1></body></html>"

    @Test fun cleanPageHasNoIssues() {
        assertEquals(0, HtmlLint.lint(good).size)
    }

    @Test fun unclosedTagIsFound() {
        val r = HtmlLint.lint("<div><span>text</div>")
        assertTrue(r.any { it.message.contains("Unclosed <span>") })
    }

    @Test fun unexpectedClosingTag() {
        assertTrue(HtmlLint.lint("<p>a</p></div>").any { it.message.contains("Unexpected </div>") })
    }

    @Test fun missingDoctypeAndTitle() {
        val r = HtmlLint.lint("<html><head></head><body></body></html>")
        assertTrue(r.any { it.message.contains("DOCTYPE") })
        assertTrue(r.any { it.message.contains("<title>") })
    }

    @Test fun duplicateIdAndMissingAlt() {
        val r = HtmlLint.lint("<p id=\"a\"></p><p id=\"a\"></p><img src=\"x.png\">")
        assertTrue(r.any { it.message.contains("Duplicate id") })
        assertTrue(r.any { it.message.contains("alt") })
    }

    @Test fun unclosedQuote() {
        assertTrue(HtmlLint.lint("<a href=\"x>text</a>").any { it.message.contains("quote") })
    }

    @Test fun lineNumbersAreRight() {
        val r = HtmlLint.lint("<div>\n<span>\n</div>")
        assertEquals(2, r.first { it.message.contains("Unclosed <span>") }.line)
    }

    @Test fun blankTextGivesNothing() {
        assertEquals(0, HtmlLint.lint("   ").size)
    }
}
