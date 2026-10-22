package com.htmlplayforge.app.editor

/** Search and replace helpers. Plain text or regex. Pure Kotlin. */
object FindEngine {
    private fun regexFor(query: String, matchCase: Boolean, regex: Boolean): Regex? {
        if (query.isEmpty()) return null
        return try {
            val opts = if (matchCase) emptySet<RegexOption>() else setOf(RegexOption.IGNORE_CASE)
            if (regex) Regex(query, opts) else Regex(Regex.escape(query), opts)
        } catch (e: Exception) {
            null
        }
    }

    /** Finds the next match at or after [from]; wraps around. Returns start..endExclusive. */
    fun findNext(text: String, query: String, matchCase: Boolean, regex: Boolean, from: Int): IntRange? {
        val r = regexFor(query, matchCase, regex) ?: return null
        return try {
            var m = r.find(text, from.coerceIn(0, text.length))
            if (m == null || m.value.isEmpty()) m = r.find(text, 0)
            if (m == null || m.value.isEmpty()) null else m.range.first until (m.range.last + 1)
        } catch (t: Throwable) {
            null
        }
    }

    /** Finds the last match that ends before [before]; wraps around. */
    fun findPrev(text: String, query: String, matchCase: Boolean, regex: Boolean, before: Int): IntRange? {
        val r = regexFor(query, matchCase, regex) ?: return null
        return try {
            var best: IntRange? = null
            var last: IntRange? = null
            for (m in r.findAll(text)) {
                if (m.value.isEmpty()) continue
                val range = m.range.first until (m.range.last + 1)
                last = range
                if (range.last + 1 <= before) best = range
            }
            best ?: last
        } catch (t: Throwable) {
            null
        }
    }

    fun count(text: String, query: String, matchCase: Boolean, regex: Boolean): Int {
        val r = regexFor(query, matchCase, regex) ?: return 0
        return try { r.findAll(text).count { it.value.isNotEmpty() } } catch (t: Throwable) { 0 }
    }

    fun replaceAll(text: String, query: String, replacement: String, matchCase: Boolean, regex: Boolean): String {
        val r = regexFor(query, matchCase, regex) ?: return text
        return try {
            if (regex) r.replace(text, replacement)
            else r.replace(text, Regex.escapeReplacement(replacement))
        } catch (t: Throwable) {
            text
        }
    }

    fun isValidRegex(q: String): Boolean = try { Regex(q); true } catch (e: Exception) { false }
}
