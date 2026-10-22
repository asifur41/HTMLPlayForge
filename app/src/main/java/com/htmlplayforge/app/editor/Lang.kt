package com.htmlplayforge.app.editor

/** The language of a file, decided by its extension. Pure Kotlin. */
enum class Lang {
    HTML, CSS, JS, JSON, PLAIN;

    companion object {
        fun fromFileName(name: String?): Lang {
            if (name == null) return HTML // untitled text behaves like a web page
            val n = name.lowercase()
            return when {
                n.endsWith(".html") || n.endsWith(".htm") || n.endsWith(".svg") || n.endsWith(".xml") -> HTML
                n.endsWith(".css") -> CSS
                n.endsWith(".js") || n.endsWith(".mjs") -> JS
                n.endsWith(".json") -> JSON
                else -> PLAIN
            }
        }
    }
}

/** What a coloured piece of code is. The theme decides the real colour. */
enum class Kind { KEYWORD, STRING, COMMENT, NUMBER, ATTR, SPECIAL }

/** A coloured range [start, end) of the text. */
data class Span(val start: Int, val end: Int, val kind: Kind)

/** Converts text offsets to 1-based line and column numbers. */
class LineIndex(text: String) {
    private val starts: IntArray

    init {
        var count = 1
        for (ch in text) if (ch == '\n') count++
        starts = IntArray(count)
        var k = 1
        for (i in text.indices) if (text[i] == '\n') starts[k++] = i + 1
    }

    val lineCount: Int get() = starts.size

    fun lineOf(offset: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo + 1
    }

    fun colOf(offset: Int): Int = offset - starts[lineOf(offset) - 1] + 1

    /** Offset where a 1-based line starts (clamped). */
    fun startOfLine(line: Int): Int = starts[(line - 1).coerceIn(0, starts.size - 1)]
}
