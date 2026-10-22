package com.htmlplayforge.app.editor

/** The result of an expansion: new text and where the cursor goes inside it. */
data class EmmetResult(val text: String, val cursor: Int)

/**
 * A small Emmet: div.card>h2+p, ul>li*3, #id, .class, a[href=#]{text}, "!" for a page skeleton.
 * No groups and no climb-up. Pure Kotlin.
 */
object EmmetLite {
    private class Node(
        var tag: String = "",
        var id: String? = null,
        val classes: MutableList<String> = ArrayList(),
        val attrs: MutableList<String> = ArrayList(),
        var text: String? = null,
        var count: Int = 1,
        val children: MutableList<Node> = ArrayList()
    )

    private const val SKELETON = "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n" +
        "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n  <title></title>\n</head>\n<body>\n  \n</body>\n</html>"

    fun expand(abbr: String): EmmetResult? {
        val a = abbr.trim()
        if (a.isEmpty()) return null
        if (a == "!") return EmmetResult(SKELETON, SKELETON.indexOf("<title>") + 7)
        return try { build(a) } catch (t: Throwable) { null }
    }

    private val KNOWN = setOf(
        "div", "span", "p", "a", "ul", "ol", "li", "table", "tr", "td", "th", "form", "input", "button",
        "img", "h1", "h2", "h3", "h4", "section", "header", "footer", "nav", "main", "article", "label",
        "select", "textarea", "canvas", "pre", "code", "br", "hr", "script", "style", "link", "meta"
    )

    /** True if the text looks like an abbreviation and not like an ordinary word. */
    fun looksLikeAbbreviation(a: String): Boolean {
        if (a == "!") return true
        if (a.isEmpty() || a.length > 200) return false
        if (a.any { it == '.' || it == '#' || it == '>' || it == '+' || it == '*' || it == '[' || it == '{' }) return true
        return a.lowercase() in KNOWN
    }

    /** Takes the abbreviation that ends at [cursor]: the characters back to the last space or line break. */
    fun abbreviationBefore(text: String, cursor: Int): String {
        var s = cursor
        while (s > 0 && !text[s - 1].isWhitespace()) s--
        return text.substring(s, cursor)
    }

    private fun build(a: String): EmmetResult? {
        // split into items at '>' and '+' outside [] and {}
        val items = ArrayList<Pair<Char, String>>() // operator before the item
        var op = ' '
        val cur = StringBuilder()
        var depthB = 0
        var depthC = 0
        for (ch in a) {
            when {
                ch == '[' -> { depthB++; cur.append(ch) }
                ch == ']' -> { depthB--; cur.append(ch) }
                ch == '{' -> { depthC++; cur.append(ch) }
                ch == '}' -> { depthC--; cur.append(ch) }
                (ch == '>' || ch == '+') && depthB == 0 && depthC == 0 -> {
                    items.add(Pair(op, cur.toString())); cur.setLength(0); op = ch
                }
                else -> cur.append(ch)
            }
        }
        if (depthB != 0 || depthC != 0) return null
        items.add(Pair(op, cur.toString()))

        val root = Node()
        var parent = root
        var last: Node? = null
        for ((o, item) in items) {
            val node = parseItem(item) ?: return null
            if (o == '>') {
                val l = last ?: return null
                parent = l
            }
            if (node.tag.isEmpty()) node.tag = defaultTag(parent.tag)
            parent.children.add(node)
            last = node
        }
        val sb = StringBuilder()
        var cursor = -1
        for (c in root.children) cursor = render(c, 0, sb, cursor)
        val text = sb.toString().trimEnd('\n')
        return EmmetResult(text, if (cursor < 0 || cursor > text.length) text.length else cursor)
    }

    private fun defaultTag(parent: String): String = when (parent) {
        "ul", "ol" -> "li"
        "table", "tbody", "thead" -> "tr"
        "tr" -> "td"
        "select" -> "option"
        else -> "div"
    }

    private fun parseItem(s: String): Node? {
        val n = Node()
        var i = 0
        val len = s.length
        // tag name
        val ts = i
        while (i < len && (s[i].isLetterOrDigit() || s[i] == '-' || s[i] == ':')) i++
        n.tag = s.substring(ts, i).lowercase()
        if (n.tag.isNotEmpty() && !n.tag[0].isLetter()) return null
        while (i < len) {
            val c = s[i]
            when (c) {
                '#', '.' -> {
                    var j = i + 1
                    while (j < len && (s[j].isLetterOrDigit() || s[j] == '-' || s[j] == '_')) j++
                    val v = s.substring(i + 1, j)
                    if (v.isEmpty()) return null
                    if (c == '#') n.id = v else n.classes.add(v)
                    i = j
                }
                '[' -> {
                    val e = s.indexOf(']', i)
                    if (e < 0) return null
                    n.attrs.addAll(splitAttrs(s.substring(i + 1, e)))
                    i = e + 1
                }
                '{' -> {
                    val e = s.indexOf('}', i)
                    if (e < 0) return null
                    n.text = s.substring(i + 1, e)
                    i = e + 1
                }
                '*' -> {
                    var j = i + 1
                    while (j < len && s[j].isDigit()) j++
                    n.count = s.substring(i + 1, j).toIntOrNull()?.coerceIn(1, 50) ?: return null
                    i = j
                }
                else -> return null
            }
        }
        if (n.tag.isEmpty() && n.id == null && n.classes.isEmpty() && n.attrs.isEmpty() && n.text == null) return null
        return n
    }

    private fun splitAttrs(s: String): List<String> {
        val out = ArrayList<String>()
        for (part in s.trim().split(Regex("\\s+"))) {
            if (part.isEmpty()) continue
            val eq = part.indexOf('=')
            if (eq < 0) out.add(part) else {
                var v = part.substring(eq + 1)
                if (v.length >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'")))
                    v = v.substring(1, v.length - 1)
                out.add(part.substring(0, eq) + "=\"" + v + "\"")
            }
        }
        return out
    }

    /** Writes a node and returns the cursor offset (first empty spot) or the one passed in. */
    private fun render(n: Node, depth: Int, sb: StringBuilder, cursorIn: Int): Int {
        var cursor = cursorIn
        val pad = "  ".repeat(depth)
        for (k in 1..n.count) {
            val opening = StringBuilder("<").append(n.tag)
            if (n.id != null) opening.append(" id=\"").append(n.id).append('"')
            if (n.classes.isNotEmpty()) opening.append(" class=\"").append(n.classes.joinToString(" ")).append('"')
            for (a in n.attrs) opening.append(' ').append(a)
            opening.append('>')
            sb.append(pad).append(opening)
            if (n.tag in HtmlScanner.VOID) { sb.append('\n'); continue }
            if (n.children.isEmpty()) {
                if (n.text != null) sb.append(n.text)
                else if (cursor < 0) cursor = sb.length
                sb.append("</").append(n.tag).append(">\n")
            } else {
                sb.append('\n')
                for (c in n.children) cursor = render(c, depth + 1, sb, cursor)
                sb.append(pad).append("</").append(n.tag).append(">\n")
            }
        }
        return cursor
    }
}
