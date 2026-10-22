package com.htmlplayforge.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmmetTest {
    @Test fun listWithRepeat() {
        val r = EmmetLite.expand("ul>li*3")!!
        assertEquals("<ul>\n  <li></li>\n  <li></li>\n  <li></li>\n</ul>", r.text)
        assertEquals(11, r.cursor)
    }

    @Test fun classChildAndSibling() {
        assertEquals("<div class=\"card\">\n  <h2></h2>\n  <p></p>\n</div>", EmmetLite.expand("div.card>h2+p")!!.text)
    }

    @Test fun idOnly() {
        assertEquals("<div id=\"main\"></div>", EmmetLite.expand("#main")!!.text)
    }

    @Test fun attributeAndText() {
        assertEquals("<a href=\"#\">text</a>", EmmetLite.expand("a[href=#]{text}")!!.text)
    }

    @Test fun bangMakesAPage() {
        assertTrue(EmmetLite.expand("!")!!.text.startsWith("<!DOCTYPE html>"))
    }

    @Test fun badInputGivesNull() {
        assertNull(EmmetLite.expand(""))
        assertNull(EmmetLite.expand("<<"))
        assertNull(EmmetLite.expand("a[href"))
    }

    @Test fun abbreviationBeforeCursor() {
        assertEquals("ul>li*2", EmmetLite.abbreviationBefore("x\n  ul>li*2", 11))
        assertTrue(EmmetLite.looksLikeAbbreviation("div"))
        assertTrue(EmmetLite.looksLikeAbbreviation("p.a"))
        assertTrue(!EmmetLite.looksLikeAbbreviation("hello"))
        assertNotNull(EmmetLite.expand("table>tr>td"))
    }
}
