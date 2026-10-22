package com.htmlplayforge.app.editor

/** One door for all colouring. Never throws; very long text is not coloured. */
object SyntaxHighlighter {
    const val MAX_LENGTH = 60_000

    fun highlight(text: String, lang: Lang): List<Span> {
        if (text.length > MAX_LENGTH) return emptyList()
        val out = ArrayList<Span>()
        try {
            when (lang) {
                Lang.HTML -> HtmlHighlighter.highlight(text, out)
                Lang.CSS -> CssHighlighter.highlight(text, 0, text.length, out)
                Lang.JS -> JsHighlighter.highlight(text, 0, text.length, out)
                Lang.JSON -> JsHighlighter.highlight(text, 0, text.length, out, json = true)
                Lang.PLAIN -> Unit
            }
        } catch (t: Throwable) {
            return emptyList()
        }
        return out
    }
}
