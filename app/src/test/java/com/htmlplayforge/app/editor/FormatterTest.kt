package com.htmlplayforge.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatterTest {
    @Test fun htmlNestedTags() {
        assertEquals("<div>\n  <p>Hi</p>\n</div>\n", CodeFormatter.format("<div><p>Hi</p></div>", Lang.HTML))
    }

    @Test fun htmlVoidTagsDoNotIndent() {
        assertEquals("<div>\n  <br>\n</div>\n", CodeFormatter.format("<div><br></div>", Lang.HTML))
    }

    @Test fun htmlScriptIsIndented() {
        assertEquals("<script>\n  var a=1;\n</script>\n", CodeFormatter.format("<script>var a=1;</script>", Lang.HTML))
    }

    @Test fun htmlPreIsKept() {
        val out = CodeFormatter.format("<div><pre>a\n  b</pre></div>", Lang.HTML)
        assertTrue(out.contains("<pre>a\n  b</pre>"))
    }

    @Test fun jsBraces() {
        assertTrue(CodeFormatter.format("if(a){b();}", Lang.JS).contains("{\n  b();\n}"))
    }

    @Test fun cssBraces() {
        val out = CodeFormatter.format("a{color:red;margin:0}", Lang.CSS)
        assertTrue(out.contains("a {\n  color:red;\n  margin:0\n}"))
    }

    @Test fun jsonKeepsStrings() {
        val out = CodeFormatter.format("{\"a\":\"x,y{z}\"}", Lang.JSON)
        assertTrue(out.contains("\"x,y{z}\""))
    }

    @Test fun plainAndBlankAreUnchanged() {
        assertEquals("hello", CodeFormatter.format("hello", Lang.PLAIN))
        assertEquals("  ", CodeFormatter.format("  ", Lang.HTML))
    }

    @Test fun strangeTextNeverThrows() {
        for (s in listOf("<", "{", "}}}", "<div", "\"", "<pre>", "<script>")) for (l in Lang.values()) CodeFormatter.format(s, l)
    }
}
