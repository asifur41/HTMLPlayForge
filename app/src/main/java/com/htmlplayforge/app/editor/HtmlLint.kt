package com.htmlplayforge.app.editor

data class LintIssue(val line: Int, val column: Int, val message: String)

/** Simple HTML checker. Every message is short and says what to do. */
object HtmlLint {
    private val OPTIONAL_END = setOf(
        "p", "li", "dt", "dd", "tr", "td", "th", "thead", "tbody", "tfoot",
        "option", "optgroup", "colgroup", "rp", "rt"
    )

    fun lint(text: String): List<LintIssue> {
        val issues = ArrayList<LintIssue>()
        if (text.isBlank()) return issues
        return try {
            run(text, issues)
            issues.sortedBy { it.line * 10000 + it.column }
        } catch (t: Throwable) {
            issues
        }
    }

    private fun run(text: String, issues: MutableList<LintIssue>) {
        val idx = LineIndex(text)
        val tags = HtmlScanner.scan(text)
        fun add(offset: Int, msg: String) = issues.add(LintIssue(idx.lineOf(offset), idx.colOf(offset), msg))

        val hasHtml = tags.any { it.name == "html" && !it.closing }
        if (hasHtml) {
            if (text.indexOf("<!doctype", ignoreCase = true) < 0)
                issues.add(LintIssue(1, 1, "Missing <!DOCTYPE html>. Add it as the very first line of the page."))
            if (tags.none { it.name == "title" && !it.closing })
                issues.add(LintIssue(1, 1, "Missing <title>. Add <title>My page</title> inside <head>."))
        }

        val stack = ArrayList<HtmlTag>()
        val ids = HashMap<String, Int>()
        for (t in tags) {
            if (t.badQuote) add(t.start, "Unclosed quote in <${t.name}>. Add the missing quote character.")
            if (!t.closing) {
                val id = t.attrs.firstOrNull { it.first == "id" }?.second
                if (id != null && id.isNotEmpty()) {
                    val first = ids[id]
                    if (first != null) add(t.start, "Duplicate id \"$id\" (first used on line $first). Each id must be unique.")
                    else ids[id] = idx.lineOf(t.start)
                }
                if (t.name == "img" && t.attrs.none { it.first == "alt" })
                    add(t.start, "<img> has no alt text. Add alt=\"short description\".")
                if (t.name in HtmlScanner.VOID || t.selfClosing) continue
                stack.add(t)
            } else {
                if (t.name in HtmlScanner.VOID) continue
                val pos = stack.indexOfLast { it.name == t.name }
                if (pos < 0) {
                    add(t.start, "Unexpected </${t.name}> with no matching opening tag. Remove it or add <${t.name}>.")
                } else {
                    for (k in stack.size - 1 downTo pos + 1) {
                        val u = stack[k]
                        if (u.name !in OPTIONAL_END)
                            add(u.start, "Unclosed <${u.name}>. Add </${u.name}> before </${t.name}> on line ${idx.lineOf(t.start)}.")
                    }
                    while (stack.size > pos) stack.removeAt(stack.size - 1)
                }
            }
        }
        for (u in stack) {
            if (u.name !in OPTIONAL_END) add(u.start, "Unclosed <${u.name}>. Add </${u.name}> where it should end.")
        }
    }
}
