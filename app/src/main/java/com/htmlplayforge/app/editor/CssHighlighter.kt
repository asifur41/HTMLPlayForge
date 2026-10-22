package com.htmlplayforge.app.editor

/** Colours CSS: selectors, properties, values, units, at-rules, comments. */
object CssHighlighter {
    private fun delimIndex(text: String, from: Int, to: Int): Int {
        var j = from
        while (j < to) {
            val c = text[j]
            if (c == ';' || c == '{' || c == '}') return j
            j++
        }
        return to
    }

    private fun trimEnd(text: String, start: Int, end: Int): Int {
        var e = end
        while (e > start && text[e - 1].isWhitespace()) e--
        return e
    }

    fun highlight(text: String, from: Int, to: Int, out: MutableList<Span>) {
        var i = from
        var inValue = false
        while (i < to) {
            val c = text[i]
            if (c == '/' && i + 1 < to && text[i + 1] == '*') {
                val f = text.indexOf("*/", i + 2)
                val e = if (f < 0 || f + 2 > to) to else f + 2
                out.add(Span(i, e, Kind.COMMENT)); i = e
                continue
            }
            if (c == '"' || c == '\'') {
                val e = maxOf(JsHighlighter.stringEnd(text, i, to), i + 1)
                out.add(Span(i, e, Kind.STRING)); i = e
                continue
            }
            if (inValue) {
                if (c == ';' || c == '}' || c == '{') {
                    inValue = false; i++
                } else if (c == '#') {
                    var j = i + 1
                    while (j < to && text[j].isLetterOrDigit()) j++
                    out.add(Span(i, j, Kind.NUMBER)); i = j
                } else if (c.isDigit() || (c == '.' && i + 1 < to && text[i + 1].isDigit()) ||
                    ((c == '-' || c == '+') && i + 1 < to && text[i + 1].isDigit())
                ) {
                    var j = i + 1
                    while (j < to && (text[j].isLetterOrDigit() || text[j] == '.' || text[j] == '%')) j++
                    out.add(Span(i, j, Kind.NUMBER)); i = j
                } else if (c == '!') {
                    var j = i + 1
                    while (j < to && text[j].isLetter()) j++
                    out.add(Span(i, j, Kind.SPECIAL)); i = j
                } else {
                    i++
                }
                continue
            }
            if (c.isWhitespace() || c == ';' || c == '{' || c == '}') { i++; continue }
            // A new statement starts here: selector, at-rule or property.
            val d = delimIndex(text, i, to)
            if (c == '@') {
                val e = trimEnd(text, i, d)
                out.add(Span(i, maxOf(e, i + 1), Kind.KEYWORD)); i = maxOf(d, i + 1)
            } else if (d < to && text[d] == '{') {
                val e = trimEnd(text, i, d)
                if (e > i) out.add(Span(i, e, Kind.KEYWORD))
                i = d
            } else {
                var colon = -1
                var k = i
                while (k < d) { if (text[k] == ':') { colon = k; break }; k++ }
                if (colon >= 0) {
                    val e = trimEnd(text, i, colon)
                    if (e > i) out.add(Span(i, e, Kind.ATTR))
                    i = colon + 1
                    inValue = true
                } else {
                    i = maxOf(d, i + 1)
                }
            }
        }
    }
}
