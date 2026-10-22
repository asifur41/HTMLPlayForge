package com.htmlplayforge.app.editor

/** Re-indents code with 2 spaces. Pure Kotlin; returns the input unchanged if anything goes wrong. */
object CodeFormatter {
    private const val INDENT = "  "

    fun format(text: String, lang: Lang): String {
        if (text.isBlank()) return text
        return try {
            when (lang) {
                Lang.HTML -> formatHtml(text)
                Lang.CSS, Lang.JS -> formatBraces(text, false)
                Lang.JSON -> formatBraces(text, true)
                Lang.PLAIN -> text
            }
        } catch (t: Throwable) {
            text
        }
    }

    private fun collapse(s: String): String = s.trim().replace(Regex("\\s+"), " ")

    // ---------------------------------------------------------------- HTML
    private fun formatHtml(text: String): String {
        val tags = HtmlScanner.scan(text)
        val out = StringBuilder()
        var depth = 0
        fun line(s: String) {
            if (s.isEmpty()) return
            for (x in 0 until depth) out.append(INDENT)
            out.append(s).append('\n')
        }
        fun chunk(c: String) {
            val t = c.trim()
            if (t.isEmpty()) return
            if (t.contains("<!--")) {
                for (l in t.lines()) { val tl = l.trim(); if (tl.isNotEmpty()) line(tl) }
            } else line(collapse(t))
        }
        var pos = 0
        var k = 0
        while (k < tags.size) {
            val t = tags[k]
            chunk(text.substring(pos, t.start))
            val raw = text.substring(t.start, t.end)
            if (t.closing) {
                depth = maxOf(0, depth - 1)
                line(raw); pos = t.end; k++
                continue
            }
            val isBlock = t.name == "script" || t.name == "style" || t.name == "textarea" || t.name == "pre"
            if (isBlock && !t.selfClosing) {
                var m = k + 1
                while (m < tags.size && !(tags[m].closing && tags[m].name == t.name)) m++
                if (m >= tags.size) { // never closed: keep the rest as it is
                    line(raw + text.substring(t.end).trimEnd())
                    pos = text.length; k = tags.size
                    continue
                }
                val close = tags[m]
                val inner = text.substring(t.end, close.start)
                val closeRaw = text.substring(close.start, close.end)
                if (t.name == "script" || t.name == "style") {
                    if (inner.isBlank()) line(raw + closeRaw) else {
                        val f = formatBraces(inner.trim(), false)
                        line(raw); depth++
                        for (l in f.lines()) line(l)
                        depth--; line(closeRaw)
                    }
                } else {
                    for (x in 0 until depth) out.append(INDENT)
                    out.append(raw).append(inner).append(closeRaw).append('\n')
                }
                pos = close.end; k = m + 1
                continue
            }
            val isVoid = t.name in HtmlScanner.VOID || t.selfClosing
            if (!isVoid && k + 1 < tags.size && tags[k + 1].closing && tags[k + 1].name == t.name) {
                val inner = text.substring(t.end, tags[k + 1].start)
                val ci = collapse(inner)
                if (!inner.contains('<') && ci.length <= 80) {
                    line(raw + ci + text.substring(tags[k + 1].start, tags[k + 1].end))
                    pos = tags[k + 1].end; k += 2
                    continue
                }
            }
            line(raw)
            if (!isVoid) depth++
            pos = t.end; k++
        }
        chunk(text.substring(pos))
        return out.toString().trimEnd() + "\n"
    }

    // ------------------------------------------------- CSS / JS / JSON
    private fun formatBraces(text: String, json: Boolean): String {
        val sb = StringBuilder()
        var indent = 0
        var atStart = true
        var paren = 0
        val n = text.length

        fun nl() {
            while (sb.isNotEmpty() && sb[sb.length - 1] == ' ') sb.setLength(sb.length - 1)
            if (sb.isNotEmpty() && sb[sb.length - 1] != '\n') sb.append('\n')
            atStart = true
        }
        fun put(s: String) {
            if (atStart) { for (x in 0 until indent) sb.append(INDENT); atStart = false }
            sb.append(s)
        }
        // Looks ahead over spaces and line breaks. Returns index of next real character.
        fun skipWs(from: Int): Int {
            var j = from
            while (j < n && text[j].isWhitespace()) j++
            return j
        }

        var i = 0
        while (i < n) {
            val c = text[i]
            if (c == '"' || c == '\'' || c == '`') {
                val e = maxOf(JsHighlighter.stringEnd(text, i, n), i + 1)
                put(text.substring(i, e)); i = e
            } else if (c == '/' && i + 1 < n && text[i + 1] == '/' && !json) {
                var e = i
                while (e < n && text[e] != '\n') e++
                put(text.substring(i, e)); i = e
            } else if (c == '/' && i + 1 < n && text[i + 1] == '*' && !json) {
                val f = text.indexOf("*/", i + 2)
                val e = if (f < 0) n else f + 2
                put(text.substring(i, e)); i = e
            } else if (c == '{' || (json && c == '[')) {
                if (!json && !atStart && sb.isNotEmpty() && sb[sb.length - 1] != ' ') sb.append(' ')
                put(c.toString()); indent++; nl(); i++
            } else if (c == '}' || (json && c == ']')) {
                indent = maxOf(0, indent - 1)
                nl(); put(c.toString()); i++
                val j = skipWs(i)
                val keepSame = j < n && (text.startsWith("else", j) || text.startsWith("catch", j) ||
                    text.startsWith("finally", j) || text[j] == ')' || text[j] == ']' ||
                    text[j] == ';' || text[j] == ',' || text[j] == '.')
                if (keepSame) {
                    if (text[j].isLetter()) sb.append(' ')
                    i = j
                } else {
                    var breaks = 0
                    for (x in i until j) if (text[x] == '\n') breaks++
                    nl()
                    if (breaks >= 2 && j < n) sb.append('\n')
                    i = j
                }
            } else if (c == ';' && paren == 0) {
                put(";"); nl(); i++
            } else if (c == ',' && json) {
                put(","); nl(); i++
            } else if (c == '(') {
                paren++; put("("); i++
            } else if (c == ')') {
                paren = maxOf(0, paren - 1); put(")"); i++
            } else if (c.isWhitespace()) {
                var j = i
                var breaks = 0
                while (j < n && text[j].isWhitespace()) { if (text[j] == '\n') breaks++; j++ }
                if (atStart) {
                    if (breaks >= 2 && sb.isNotEmpty() && !(sb.length >= 2 && sb[sb.length - 2] == '\n')) sb.append('\n')
                } else if (breaks >= 1) {
                    nl()
                    if (breaks >= 2) sb.append('\n')
                } else if (sb.isNotEmpty() && sb[sb.length - 1] != ' ') {
                    sb.append(' ')
                }
                i = j
            } else {
                put(c.toString()); i++
            }
        }
        return sb.toString().trimEnd() + "\n"
    }
}
