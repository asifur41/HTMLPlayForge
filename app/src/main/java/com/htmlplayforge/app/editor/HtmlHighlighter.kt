package com.htmlplayforge.app.editor

/** Colours HTML. Script and style blocks are handed to the JS and CSS colourers. */
object HtmlHighlighter {
    private fun closeQuote(text: String, start: Int, n: Int): Int {
        val e = text.indexOf(text[start], start + 1)
        return if (e < 0 || e >= n) n else e + 1
    }

    fun highlight(text: String, out: MutableList<Span>) {
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            if (c == '&') {
                var j = i + 1
                while (j < n && j - i < 10 && (text[j].isLetterOrDigit() || text[j] == '#')) j++
                if (j < n && text[j] == ';' && j > i + 1) { out.add(Span(i, j + 1, Kind.SPECIAL)); i = j + 1 } else i++
                continue
            }
            if (c != '<') { i++; continue }
            if (text.startsWith("<!--", i)) {
                val e = text.indexOf("-->", i + 4)
                val end = if (e < 0) n else e + 3
                out.add(Span(i, end, Kind.COMMENT)); i = end
                continue
            }
            if (i + 1 < n && (text[i + 1] == '!' || text[i + 1] == '?')) {
                val e = text.indexOf('>', i)
                val end = if (e < 0) n else e + 1
                out.add(Span(i, end, Kind.SPECIAL)); i = end
                continue
            }
            if (i + 1 >= n) break
            val closing = text[i + 1] == '/'
            val ns = if (closing) i + 2 else i + 1
            if (ns >= n || !text[ns].isLetter()) { i++; continue }
            var j = ns
            while (j < n && (text[j].isLetterOrDigit() || text[j] == '-' || text[j] == ':' || text[j] == '_')) j++
            val name = text.substring(ns, j).lowercase()
            out.add(Span(i, j, Kind.KEYWORD))
            while (j < n && text[j] != '>') {
                val ch = text[j]
                if (ch.isWhitespace() || ch == '/') { j++; continue }
                if (ch == '"' || ch == '\'') {
                    val e = closeQuote(text, j, n)
                    out.add(Span(j, e, Kind.STRING)); j = e
                    continue
                }
                val s = j
                while (j < n && !text[j].isWhitespace() && text[j] != '=' && text[j] != '>' && text[j] != '/') j++
                if (j == s) { j++; continue }
                out.add(Span(s, j, Kind.ATTR))
                while (j < n && text[j].isWhitespace()) j++
                if (j < n && text[j] == '=') {
                    j++
                    while (j < n && text[j].isWhitespace()) j++
                    if (j < n && (text[j] == '"' || text[j] == '\'')) {
                        val e = closeQuote(text, j, n)
                        out.add(Span(j, e, Kind.STRING)); j = e
                    } else {
                        val s2 = j
                        while (j < n && !text[j].isWhitespace() && text[j] != '>') j++
                        if (j > s2) out.add(Span(s2, j, Kind.STRING))
                    }
                }
            }
            if (j < n) { out.add(Span(j, j + 1, Kind.KEYWORD)); j++ }
            i = j
            if (!closing && (name == "script" || name == "style")) {
                val f = text.indexOf("</$name", i, ignoreCase = true)
                val end = if (f < 0) n else f
                if (name == "script") JsHighlighter.highlight(text, i, end, out)
                else CssHighlighter.highlight(text, i, end, out)
                i = end
            }
        }
    }
}
