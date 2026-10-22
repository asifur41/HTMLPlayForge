package com.htmlplayforge.app.editor

/** One tag found in an HTML text. [end] is exclusive. */
data class HtmlTag(
    val name: String,
    val closing: Boolean,
    val selfClosing: Boolean,
    val start: Int,
    val end: Int,
    val attrs: List<Pair<String, String?>>,
    val badQuote: Boolean
)

/** Finds tags in HTML. Skips comments, doctype and the insides of script/style/textarea/title. */
object HtmlScanner {
    val VOID = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta",
        "param", "source", "track", "wbr"
    )
    private val RAW = setOf("script", "style", "textarea", "title")

    fun scan(text: String): List<HtmlTag> {
        val tags = ArrayList<HtmlTag>()
        val n = text.length
        var i = 0
        while (i < n) {
            if (text[i] != '<') { i++; continue }
            if (text.startsWith("<!--", i)) {
                val e = text.indexOf("-->", i + 4)
                i = if (e < 0) n else e + 3
                continue
            }
            if (i + 1 < n && (text[i + 1] == '!' || text[i + 1] == '?')) {
                val e = text.indexOf('>', i)
                i = if (e < 0) n else e + 1
                continue
            }
            if (i + 1 >= n) break
            val closing = text[i + 1] == '/'
            val ns = if (closing) i + 2 else i + 1
            if (ns >= n || !text[ns].isLetter()) { i++; continue }
            var j = ns
            while (j < n && (text[j].isLetterOrDigit() || text[j] == '-' || text[j] == ':' || text[j] == '_')) j++
            val name = text.substring(ns, j).lowercase()
            val attrs = ArrayList<Pair<String, String?>>()
            var bad = false
            var selfClosing = false
            while (j < n && text[j] != '>') {
                val ch = text[j]
                if (ch == '/') { selfClosing = true; j++; continue }
                if (ch.isWhitespace()) { j++; continue }
                selfClosing = false
                if (ch == '"' || ch == '\'') {
                    val e = text.indexOf(ch, j + 1)
                    if (e < 0) { bad = true; j = n } else j = e + 1
                    continue
                }
                val s = j
                while (j < n && !text[j].isWhitespace() && text[j] != '=' && text[j] != '>' && text[j] != '/') j++
                if (j == s) { j++; continue }
                val an = text.substring(s, j).lowercase()
                var k = j
                while (k < n && text[k].isWhitespace()) k++
                var value: String? = null
                if (k < n && text[k] == '=') {
                    k++
                    while (k < n && text[k].isWhitespace()) k++
                    if (k < n && (text[k] == '"' || text[k] == '\'')) {
                        val q = text[k]
                        val e = text.indexOf(q, k + 1)
                        if (e < 0) { bad = true; value = text.substring(k + 1); k = n }
                        else { value = text.substring(k + 1, e); k = e + 1 }
                    } else {
                        val vs = k
                        while (k < n && !text[k].isWhitespace() && text[k] != '>') k++
                        value = text.substring(vs, k)
                    }
                    j = k
                }
                attrs.add(Pair(an, value))
            }
            val end = if (j < n) j + 1 else n
            tags.add(HtmlTag(name, closing, selfClosing && !closing, i, end, attrs, bad))
            i = end
            if (!closing && !selfClosing && name in RAW) {
                val f = text.indexOf("</$name", i, ignoreCase = true)
                i = if (f < 0) n else f
            }
        }
        return tags
    }
}
