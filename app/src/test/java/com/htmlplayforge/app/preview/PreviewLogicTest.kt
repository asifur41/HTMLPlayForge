package com.htmlplayforge.app.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewLogicTest {
    @Test fun hintsMatchInOrder() {
        assertTrue(ErrorHints.hintFor("Uncaught ReferenceError: x is not defined")!!.contains("misspelled"))
        assertTrue(ErrorHints.hintFor("Cannot read properties of null (reading 'x')")!!.contains("id"))
        assertTrue(ErrorHints.hintFor("Uncaught SyntaxError: Unexpected token '}'")!!.contains("bracket"))
        assertTrue(ErrorHints.hintFor("Unexpected end of input")!!.contains("bracket"))
        assertNotNull(ErrorHints.hintFor("x.foo is not a function"))
        assertNotNull(ErrorHints.hintFor("Identifier 'a' has already been declared"))
        assertNotNull(ErrorHints.hintFor("Failed to load resource: net::ERR_FILE_NOT_FOUND"))
        assertNull(ErrorHints.hintFor("something else"))
    }

    @Test fun consoleSourceBecomesProjectFile() {
        assertEquals("css/style.css", ConsoleParser.fileFromSource("https://appassets.androidplatform.net/css/style.css?x=1"))
        assertEquals("a b.js", ConsoleParser.fileFromSource("https://appassets.androidplatform.net/a%20b.js"))
        assertNull(ConsoleParser.fileFromSource("https://example.com/a.js"))
        assertNull(ConsoleParser.fileFromSource(null))
        val e = ConsoleParser.parse(Level.ERROR, "x is not defined", "https://appassets.androidplatform.net/index.html", 7)
        assertEquals("index.html", e.file)
        assertEquals(7, e.line)
        assertNotNull(e.hint)
    }

    @Test fun pathCleaning() {
        assertNull(PreviewSecurity.cleanPath("../a"))
        assertNull(PreviewSecurity.cleanPath("a/../../b"))
        assertNull(PreviewSecurity.cleanPath(""))
        assertEquals("a/b", PreviewSecurity.cleanPath("/a//b"))
    }

    @Test fun urlRules() {
        assertTrue(PreviewSecurity.isProjectUrl("https://appassets.androidplatform.net/index.html"))
        assertFalse(PreviewSecurity.isProjectUrl("https://example.com/"))
        assertTrue(PreviewSecurity.isWebLink("https://example.com/"))
        assertFalse(PreviewSecurity.isWebLink("mailto:a@b.c"))
        assertEquals("text/html", PreviewSecurity.mimeFor("A.HTML"))
        assertEquals("image/svg+xml", PreviewSecurity.mimeFor("x.svg"))
        assertTrue(PreviewSecurity.CSP.contains("default-src 'self'"))
        assertTrue(PreviewSecurity.responseHeaders().containsKey("Content-Security-Policy"))
    }
}
