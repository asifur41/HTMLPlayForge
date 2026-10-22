package com.htmlplayforge.app.editor

/** Colours JavaScript and JSON. One pass, no regex, always moves forward. */
object JsHighlighter {
    private val KEYWORDS = setOf(
        "var", "let", "const", "function", "return", "if", "else", "for", "while", "do",
        "switch", "case", "break", "continue", "new", "this", "class", "extends", "import",
        "export", "from", "default", "try", "catch", "finally", "throw", "typeof",
        "instanceof", "in", "of", "async", "await", "yield", "null", "undefined", "true",
        "false", "void", "delete", "super", "static"
    )
    private val JSON_WORDS = setOf("true", "false", "null")

    /** Index just after the closing quote (or [limit] if the string never ends). */
    fun stringEnd(text: String, start: Int, limit: Int): Int {
        val q = text[start]
        var i = start + 1
        while (i < limit) {
            val c = text[i]
            if (c == '\\') { i += 2; continue }
            if (c == q) return i + 1
            if (c == '\n' && q != '`') return i
            i++
        }
        return limit
    }

    fun highlight(text: String, from: Int, to: Int, out: MutableList<Span>, json: Boolean = false) {
        var i = from
        while (i < to) {
            val c = text[i]
            if (c == '/' && i + 1 < to && text[i + 1] == '/' && !json) {
                var e = i
                while (e < to && text[e] != '\n') e++
                out.add(Span(i, e, Kind.COMMENT)); i = e
            } else if (c == '/' && i + 1 < to && text[i + 1] == '*' && !json) {
                val f = text.indexOf("*/", i + 2)
                val e = if (f < 0 || f + 2 > to) to else f + 2
                out.add(Span(i, e, Kind.COMMENT)); i = e
            } else if (c == '"' || c == '\'' || c == '`') {
                val e = stringEnd(text, i, to)
                var kind = Kind.STRING
                if (json && c == '"') {
                    var k = e
                    while (k < to && (text[k] == ' ' || text[k] == '\t')) k++
                    if (k < to && text[k] == ':') kind = Kind.ATTR
                }
                out.add(Span(i, maxOf(e, i + 1), kind)); i = maxOf(e, i + 1)
            } else if (c.isDigit()) {
                var j = i + 1
                while (j < to && (text[j].isLetterOrDigit() || text[j] == '.' || text[j] == '_')) j++
                out.add(Span(i, j, Kind.NUMBER)); i = j
            } else if (c.isLetter() || c == '_' || c == '$') {
                var j = i + 1
                while (j < to && (text[j].isLetterOrDigit() || text[j] == '_' || text[j] == '$')) j++
                val w = text.substring(i, j)
                if ((json && w in JSON_WORDS) || (!json && w in KEYWORDS)) out.add(Span(i, j, Kind.KEYWORD))
                i = j
            } else {
                i++
            }
        }
    }
}
