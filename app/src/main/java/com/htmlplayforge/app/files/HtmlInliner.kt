package com.htmlplayforge.app.files

/** Makes one self-contained HTML text: local CSS and JS files are copied inside the page. */
object HtmlInliner {
    private val LINK = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val HREF = Regex("href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val SCRIPT = Regex(
        "<script\\b[^>]*?\\bsrc\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>\\s*</script>",
        RegexOption.IGNORE_CASE
    )

    private fun isLocal(p: String) = !p.contains("://") && !p.startsWith("//") && !p.startsWith("data:")

    /** [read] returns the text of a project file by relative path, or null if it does not exist. */
    fun inline(html: String, read: (String) -> String?): String {
        var out = LINK.replace(html) { m ->
            val tag = m.value
            if (!tag.contains("stylesheet", ignoreCase = true)) return@replace tag
            val href = HREF.find(tag)?.groupValues?.get(1) ?: return@replace tag
            if (!isLocal(href)) return@replace tag
            val css = read(href.substringBefore('?')) ?: return@replace tag
            "<style>\n" + css.replace("</style", "<\\/style") + "\n</style>"
        }
        out = SCRIPT.replace(out) { m ->
            val src = m.groupValues[1]
            if (!isLocal(src)) return@replace m.value
            val js = read(src.substringBefore('?')) ?: return@replace m.value
            "<script>\n" + js.replace("</script", "<\\/script") + "\n</script>"
        }
        return out
    }
}
