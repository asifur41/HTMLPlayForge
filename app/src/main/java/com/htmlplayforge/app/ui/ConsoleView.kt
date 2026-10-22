package com.htmlplayforge.app.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.htmlplayforge.app.R
import com.htmlplayforge.app.editor.LintIssue
import com.htmlplayforge.app.preview.ConsoleEntry
import com.htmlplayforge.app.preview.Level
import com.htmlplayforge.app.settings.AppTheme
import com.htmlplayforge.app.settings.ThemeManager
import com.htmlplayforge.app.utils.Ui

/** The console drawer under the preview. Tap an entry that has a file and line to jump there. */
class ConsoleView(context: Context) : LinearLayout(context) {
    private val title = TextView(context)
    private val list = LinearLayout(context)
    private val scroll = ScrollView(context)
    private var theme: AppTheme = ThemeManager.current()
    private var count = 0
    private val maxRows = 200

    /** Called when the person taps an entry that has a file. */
    var onEntryClick: (ConsoleEntry) -> Unit = {}

    init {
        orientation = VERTICAL
        val header = LinearLayout(context)
        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(Ui.dp(context, 12), 0, 0, 0)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        title.typeface = Typeface.DEFAULT_BOLD
        header.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Ui.iconButton(context, R.drawable.ic_close, theme.text, "Clear console") { clear() })
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 40)))
        list.orientation = VERTICAL
        scroll.addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        updateTitle()
        applyTheme(theme)
    }

    fun applyTheme(t: AppTheme) {
        theme = t
        setBackgroundColor(t.editorBg)
        title.setTextColor(t.text)
        for (i in 0 until list.childCount) styleRow(list.getChildAt(i) as TextView, list.getChildAt(i).tag as? Level ?: Level.LOG)
    }

    private fun updateTitle() { title.text = "Console ($count)" }

    fun clear() {
        list.removeAllViews()
        count = 0
        updateTitle()
    }

    private fun colorFor(level: Level): Int = when (level) {
        Level.ERROR -> 0xFFEF5350.toInt()
        Level.WARN, Level.LINT -> 0xFFFFB300.toInt()
        Level.INFO -> theme.keyword
        Level.LOG -> theme.text
    }

    private fun styleRow(row: TextView, level: Level) {
        row.setTextColor(colorFor(level))
    }

    fun add(entry: ConsoleEntry) {
        val sb = StringBuilder()
        sb.append(when (entry.level) {
            Level.ERROR -> "error: "
            Level.WARN -> "warning: "
            Level.LINT -> "check: "
            else -> ""
        })
        sb.append(entry.message)
        if (entry.file != null) {
            sb.append("  (").append(entry.file)
            if (entry.line > 0) sb.append(':').append(entry.line)
            sb.append(')')
        }
        if (entry.hint != null) sb.append("\n").append(entry.hint)
        val row = TextView(context)
        row.text = sb.toString()
        row.tag = entry.level
        row.typeface = Typeface.MONOSPACE
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        val p = Ui.dp(context, 10)
        row.setPadding(p, p / 2, p, p / 2)
        styleRow(row, entry.level)
        if (entry.file != null) {
            row.setOnClickListener { onEntryClick(entry) }
            val out = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
            row.setBackgroundResource(out.resourceId)
        }
        list.addView(row)
        if (list.childCount > maxRows) list.removeViewAt(0)
        count++
        updateTitle()
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    fun addLint(issues: List<LintIssue>, file: String?) {
        for (i in issues) add(ConsoleEntry(Level.LINT, "${i.message}", file, i.line, null))
    }
}
