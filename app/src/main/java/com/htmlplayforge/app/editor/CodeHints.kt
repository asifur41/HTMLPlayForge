package com.htmlplayforge.app.editor

/**
 * One suggestion. Tapping it replaces the word being typed ([replaceLength] characters
 * before the cursor) with [insert] and moves the cursor back by [cursorBack].
 */
data class Hint(val label: String, val insert: String, val cursorBack: Int, val replaceLength: Int)

object CodeHints {
    private val TAGS = listOf(
        "div", "span", "p", "a", "img", "ul", "ol", "li", "h1", "h2", "h3", "h4", "button", "input",
        "form", "label", "select", "option", "textarea", "table", "tr", "td", "th", "section", "header",
        "footer", "nav", "main", "article", "aside", "canvas", "script", "style", "link", "meta", "title",
        "body", "head", "br", "hr", "video", "audio", "svg", "pre", "code", "strong", "em"
    )
    private val ATTRS = listOf(
        "class", "id", "style", "href", "src", "alt", "type", "value", "name", "placeholder", "title",
        "width", "height", "rel", "for", "onclick", "disabled", "checked", "required", "target",
        "lang", "charset", "content", "action", "method", "data-"
    )
    private val CSS_PROPS = listOf(
        "color", "background", "background-color", "border", "border-radius", "margin", "padding",
        "width", "height", "max-width", "min-height", "display", "flex", "flex-direction", "justify-content",
        "align-items", "gap", "grid-template-columns", "font-size", "font-family", "font-weight",
        "text-align", "position", "top", "left", "right", "bottom", "z-index", "opacity", "overflow",
        "box-shadow", "transition", "transform", "animation", "cursor", "line-height"
    )
    private val CSS_VALUES = listOf(
        "none", "block", "inline-block", "flex", "grid", "center", "space-between", "auto", "relative",
        "absolute", "fixed", "bold", "solid", "1px solid", "100%", "0", "red", "white", "black", "transparent", "pointer"
    )
    private val JS_WORDS = listOf(
        "const", "let", "var", "function", "return", "if", "else", "for", "while", "switch", "case", "break",
        "class", "new", "this", "async", "await", "try", "catch", "true", "false", "null", "undefined"
    )
    private val JS_CALLS = listOf(
        "document.getElementById('')" to 2,
        "document.querySelector('')" to 2,
        "document.querySelectorAll('')" to 2,
        "document.createElement('')" to 2,
        "addEventListener('click', () => {\n  \n})" to 4,
        "console.log()" to 1,
        "setTimeout(() => {\n  \n}, 1000)" to 8,
        "setInterval(() => {\n  \n}, 1000)" to 8,
        "JSON.stringify()" to 1,
        "JSON.parse()" to 1,
        "Math.random()" to 0,
        "localStorage.getItem('')" to 2,
        "localStorage.setItem('', '')" to 5,
        "classList.toggle('')" to 2,
        "innerHTML" to 0,
        "textContent" to 0
    )

    fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '-' || c == '_' || c == '.'

    fun suggest(text: String, cursor: Int, lang: Lang): List<Hint> {
        if (cursor < 0 || cursor > text.length) return emptyList()
        return try {
            val word = wordBefore(text, cursor)
            when (lang) {
                Lang.HTML -> htmlHints(text, cursor, word)
                Lang.CSS -> cssHints(text, cursor, word)
                Lang.JS -> jsHints(word)
                else -> emptyList()
            }.take(14)
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun wordBefore(text: String, cursor: Int): String {
        var s = cursor
        while (s > 0 && isWordChar(text[s - 1])) s--
        return text.substring(s, cursor)
    }

    private fun htmlHints(text: String, cursor: Int, word: String): List<Hint> {
        val lt = text.lastIndexOf('<', cursor - 1)
        val gt = text.lastIndexOf('>', cursor - 1)
        if (lt > gt) {
            val inside = text.substring(lt + 1, cursor)
            if (inside.none { it.isWhitespace() }) {
                val w = inside.trimStart('/')
                if (inside.startsWith("/")) return emptyList()
                return TAGS.filter { it.startsWith(w, true) && it != w }.map {
                    if (it in HtmlScanner.VOID) Hint(it, "$it ", 0, w.length)
                    else Hint(it, "$it></$it>", it.length + 3, w.length)
                }
            }
            return ATTRS.filter { it.startsWith(word, true) && it != word }.map {
                if (it.endsWith("-")) Hint(it, it, 0, word.length) else Hint(it, "$it=\"\"", 1, word.length)
            }
        }
        // inside <style> or <script>?
        val before = text.substring(0, cursor)
        val ls = before.lastIndexOf("<style", ignoreCase = true)
        val le = before.lastIndexOf("</style", ignoreCase = true)
        if (ls >= 0 && ls > le && before.indexOf('>', ls) >= 0) return cssHints(text, cursor, word)
        val js = before.lastIndexOf("<script", ignoreCase = true)
        val je = before.lastIndexOf("</script", ignoreCase = true)
        if (js >= 0 && js > je && before.indexOf('>', js) >= 0) return jsHints(word)
        return emptyList()
    }

    private fun cssHints(text: String, cursor: Int, word: String): List<Hint> {
        var k = cursor - 1
        var inValue = false
        while (k >= 0) {
            val c = text[k]
            if (c == ';' || c == '{' || c == '}') break
            if (c == ':') { inValue = true; break }
            k--
        }
        return if (inValue) CSS_VALUES.filter { it.startsWith(word, true) && it != word }.map { Hint(it, it, 0, word.length) }
        else CSS_PROPS.filter { it.startsWith(word, true) && it != word }.map { Hint(it, "$it: ;", 1, word.length) }
    }

    private fun jsHints(word: String): List<Hint> {
        if (word.isEmpty()) return emptyList()
        val out = ArrayList<Hint>()
        for (k in JS_WORDS) if (k.startsWith(word, true) && k != word) out.add(Hint(k, "$k ", 0, word.length))
        for ((c, back) in JS_CALLS) if (c.startsWith(word, true) && c != word) out.add(Hint(c.substringBefore('('), c, back, word.length))
        return out
    }
}
