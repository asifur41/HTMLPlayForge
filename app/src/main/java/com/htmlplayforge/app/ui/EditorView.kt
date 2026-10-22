package com.htmlplayforge.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import com.htmlplayforge.app.R
import com.htmlplayforge.app.editor.CodeFormatter
import com.htmlplayforge.app.editor.CodeHints
import com.htmlplayforge.app.editor.EditState
import com.htmlplayforge.app.editor.EmmetLite
import com.htmlplayforge.app.editor.HtmlScanner
import com.htmlplayforge.app.editor.Lang
import com.htmlplayforge.app.editor.UndoRedoManager
import com.htmlplayforge.app.settings.AppTheme
import com.htmlplayforge.app.settings.FontManager
import com.htmlplayforge.app.settings.ThemeManager
import com.htmlplayforge.app.utils.Prefs
import com.htmlplayforge.app.utils.Ui
import com.htmlplayforge.app.utils.WarningDialog

/** Toolbar + code field + hint bar + symbol panel + status bar. */
class EditorView(private val activity: Activity) : LinearLayout(activity) {
    val editor = CodeEditText(activity)
    private val zoom = ZoomLayout(activity)
    private val toolbar = LinearLayout(activity)
    private val hintScroll = HorizontalScrollView(activity)
    private val hintRow = LinearLayout(activity)
    private val symbolScroll = HorizontalScrollView(activity)
    private val statusBar = LinearLayout(activity)
    private val statusPos = TextView(activity)
    private val statusSave = TextView(activity)
    private val undoManager = UndoRedoManager()
    private var theme: AppTheme = ThemeManager.current()
    private var undoBtn: ImageButton? = null
    private var redoBtn: ImageButton? = null
    private val toolButtons = ArrayList<ImageButton>()
    private val keyViews = ArrayList<TextView>()

    /** Called after every user change (typing, tools, undo/redo). */
    var onChange: () -> Unit = {}

    private val hintRunnable = Runnable { refreshHints() }
    private val emptyRunnable = Runnable { fillDoctypeIfEmpty() }

    val text: String get() = editor.text?.toString() ?: ""
    val lang: Lang get() = editor.lang

    init {
        orientation = VERTICAL
        buildToolbar()
        addView(toolbar, LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(activity, 44)))

        zoom.addView(editor, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        zoom.currentSize = { Prefs.fontSize }
        zoom.onSize = { editor.applySizeSp(it) }
        zoom.onEnd = { size -> Prefs.fontSize = size } // always a whole number
        addView(zoom, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        hintScroll.isHorizontalScrollBarEnabled = false
        hintRow.orientation = HORIZONTAL
        hintRow.gravity = Gravity.CENTER_VERTICAL
        hintScroll.addView(hintRow)
        addView(hintScroll, LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(activity, 36)))

        buildSymbols()
        addView(symbolScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        statusBar.orientation = HORIZONTAL
        statusBar.gravity = Gravity.CENTER_VERTICAL
        val p = Ui.dp(activity, 10)
        statusBar.setPadding(p, 0, p, 0)
        statusPos.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        statusSave.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        statusPos.text = "Ln 1, Col 1"
        statusBar.addView(statusPos, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        statusBar.addView(statusSave, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(statusBar, LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(activity, 26)))

        editor.onCursorLine = { line, col ->
            statusPos.text = "Ln $line, Col $col"
            scheduleHints()
        }
        editor.onUserChange = {
            if (!editor.skipUndoNext && (editor.text?.length ?: 0) <= 400_000) undoManager.record(EditState(text, editor.selectionStart), SystemClock.uptimeMillis())
            updateUndoButtons()
            scheduleHints()
            removeCallbacks(emptyRunnable)
            if (text.isEmpty()) postDelayed(emptyRunnable, 1500L)
            onChange()
        }
        applyTheme(theme)
        applyPrefs()
    }

    // ------------------------------------------------------ toolbar
    private fun buildToolbar() {
        toolbar.orientation = HORIZONTAL
        toolbar.gravity = Gravity.CENTER_VERTICAL
        fun add(icon: Int, desc: String, action: () -> Unit): ImageButton {
            val b = Ui.iconButton(activity, icon, theme.text, desc, action)
            toolbar.addView(b)
            toolButtons.add(b)
            return b
        }
        undoBtn = add(R.drawable.ic_undo, "Undo") { undo() }
        redoBtn = add(R.drawable.ic_redo, "Redo") { redo() }
        add(R.drawable.ic_format, "Format code") { format() }
        add(R.drawable.ic_search, "Find") { findReplace() }
        val spacer = View(activity)
        toolbar.addView(spacer, LayoutParams(0, 1, 1f))
        val more = add(R.drawable.ic_more, "More") { }
        more.setOnClickListener { showOverflow(more) }
    }

    private fun showOverflow(anchor: View) {
        val m = PopupMenu(activity, anchor)
        val items = listOf("Select all", "Copy all", "Paste", "Go to line", "Find / Replace", "Format", "Clear editor")
        items.forEachIndexed { i, s -> m.menu.add(0, i, i, s) }
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                0 -> selectAll()
                1 -> copyAll()
                2 -> paste()
                3 -> goToLineDialog()
                4 -> findReplace()
                5 -> format()
                6 -> clearEditor()
            }
            true
        }
        m.show()
    }

    private fun updateUndoButtons() {
        undoBtn?.imageAlpha = if (undoManager.canUndo()) 255 else 90
        redoBtn?.imageAlpha = if (undoManager.canRedo()) 255 else 90
    }

    // ---------------------------------------------------- symbols
    private fun buildSymbols() {
        val col = LinearLayout(activity)
        col.orientation = VERTICAL
        val rows = listOf(
            listOf("<", ">", "/", "\"", "=", "{", "}", "(", ")", ";", ":", "Tab"),
            listOf("#", ".", ",", "'", "[", "]", "-", "!", "&", "%", "*", "+", "|", "</>")
        )
        for (r in rows) {
            val row = LinearLayout(activity)
            row.orientation = HORIZONTAL
            for (sym in r) {
                val k = TextView(activity)
                k.text = sym
                k.gravity = Gravity.CENTER
                k.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                k.setOnClickListener { insertSymbol(sym) }
                val lp = LayoutParams(Ui.dp(activity, 44), Ui.dp(activity, 38))
                lp.setMargins(Ui.dp(activity, 2), Ui.dp(activity, 2), Ui.dp(activity, 2), Ui.dp(activity, 1))
                row.addView(k, lp)
                keyViews.add(k)
            }
            col.addView(row)
        }
        symbolScroll.isHorizontalScrollBarEnabled = false
        symbolScroll.addView(col)
    }

    fun insertSymbol(sym: String) {
        when (sym) {
            "Tab" -> handleTab()
            "</>" -> closeNearestTag()
            else -> {
                val s = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
                val e = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
                editor.text?.replace(s, e, sym)
                editor.setSelection(s + sym.length)
            }
        }
        editor.requestFocus()
    }

    private fun handleTab() {
        val cur = editor.selectionStart.coerceAtLeast(0)
        val t = text
        if (editor.selectionEnd == cur && editor.lang == Lang.HTML) {
            val abbr = EmmetLite.abbreviationBefore(t, cur)
            if (EmmetLite.looksLikeAbbreviation(abbr)) {
                val r = EmmetLite.expand(abbr)
                if (r != null) {
                    val ls = t.lastIndexOf('\n', cur - 1) + 1
                    val indent = t.substring(ls, cur).takeWhile { it == ' ' || it == '\t' }
                    val lines = r.text.split("\n")
                    val body = lines.mapIndexed { i, l -> if (i == 0) l else indent + l }.joinToString("\n")
                    val extra = indent.length * r.text.substring(0, r.cursor).count { it == '\n' }
                    editor.replaceRange(cur - abbr.length, cur, body, body.length - (r.cursor + extra))
                    return
                }
            }
        }
        val s = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val e = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        editor.replaceRange(s, e, "  ")
    }

    private fun closeNearestTag() {
        val cur = editor.selectionStart.coerceAtLeast(0)
        val before = text.substring(0, minOf(cur, text.length))
        val stack = ArrayList<String>()
        for (t in HtmlScanner.scan(before)) {
            if (t.badQuote) continue
            if (before.getOrNull(t.end - 1) != '>') continue // tag is still being typed
            if (!t.closing) {
                if (t.name !in HtmlScanner.VOID && !t.selfClosing) stack.add(t.name)
            } else {
                val i = stack.lastIndexOf(t.name)
                if (i >= 0) while (stack.size > i) stack.removeAt(stack.size - 1)
            }
        }
        if (stack.isEmpty()) { Ui.toast(activity, "No open tag to close"); return }
        editor.replaceRange(cur, cur, "</" + stack[stack.size - 1] + ">")
    }

    // ------------------------------------------------------- hints
    private fun scheduleHints() {
        removeCallbacks(hintRunnable)
        postDelayed(hintRunnable, 150L)
    }

    private fun refreshHints() {
        hintRow.removeAllViews()
        if (editor.selectionStart != editor.selectionEnd) return
        val cursor = editor.selectionStart
        val hints = CodeHints.suggest(text, cursor, editor.lang)
        for (h in hints) {
            val chip = TextView(activity)
            chip.text = h.label
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            chip.setTextColor(theme.text)
            val ph = Ui.dp(activity, 12)
            chip.setPadding(ph, Ui.dp(activity, 5), ph, Ui.dp(activity, 5))
            chip.background = Ui.roundBackground(Ui.withAlpha(theme.accent, 0x44), 14, activity)
            chip.setOnClickListener {
                val c = editor.selectionStart
                editor.replaceRange(c - h.replaceLength, c, h.insert, h.cursorBack)
            }
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.marginStart = Ui.dp(activity, 6)
            hintRow.addView(chip, lp)
        }
    }

    // -------------------------------------------------- operations
    /** Loads a file's text. This is not a user edit, and it resets undo. */
    fun setContent(content: String, fileName: String?, cursor: Int = 0) {
        editor.lang = Lang.fromFileName(fileName)
        editor.loadText(content, cursor)
        undoManager.reset(EditState(content, cursor))
        updateUndoButtons()
        refreshHints()
    }

    fun undo() {
        val s = undoManager.undo() ?: return
        editor.restoreState(s.text, s.cursor)
    }

    fun redo() {
        val s = undoManager.redo() ?: return
        editor.restoreState(s.text, s.cursor)
    }

    fun format() {
        val t = text
        val out = CodeFormatter.format(t, editor.lang)
        if (out == t) { Ui.toast(activity, "Nothing to format"); return }
        val c = editor.selectionStart
        editor.replaceRange(0, t.length, out)
        editor.setSelection(c.coerceIn(0, out.length))
    }

    fun findReplace() { FindReplaceSheet(activity, editor, theme).show() }

    fun selectAll() { editor.requestFocus(); editor.selectAll() }

    fun copyAll() {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("code", text))
        Ui.toast(activity, "Copied")
    }

    fun paste() {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) { Ui.toast(activity, "Clipboard is empty"); return }
        val item = clip.getItemAt(0)?.coerceToText(activity)?.toString() ?: return
        val s = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val e = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        editor.replaceRange(s, e, item)
    }

    fun goToLineDialog() {
        Ui.inputDialog(activity, "Go to line", "Line number", "", "Go",
            { v -> if (v.trim().toIntOrNull() == null) "Type a number" else null },
            { v -> jumpToLine(v.trim().toInt()) })
    }

    fun jumpToLine(line: Int) { editor.goToLine(line) }

    fun setErrorLines(lines: Set<Int>) { editor.errorLines = lines }

    fun clearEditor() {
        WarningDialog.confirm(activity, WarningDialog.CLEAR, "Clear") {
            editor.replaceRange(0, text.length, "")
            fillDoctypeIfEmpty()
        }
    }

    private fun fillDoctypeIfEmpty() {
        if (text.isEmpty() && editor.lang == Lang.HTML) editor.replaceRange(0, 0, "<!DOCTYPE html>\n")
    }

    fun setSaveStatus(s: String) { statusSave.text = s }

    // -------------------------------------------------- appearance
    fun applyPrefs() {
        editor.applyTypeface(FontManager.typeface(activity))
        editor.applySizeSp(Prefs.fontSize)
        editor.highlightingEnabled = Prefs.highlighting
        editor.showLineNumbers = Prefs.lineNumbers
        editor.autoCloseTags = Prefs.autoCloseTags
        editor.autoCloseBrackets = Prefs.autoCloseBrackets
        editor.setWrap(Prefs.wordWrap)
        symbolScroll.visibility = if (Prefs.symbolPanel) View.VISIBLE else View.GONE
        editor.recolor()
    }

    fun applyTheme(t: AppTheme) {
        theme = t
        setBackgroundColor(t.editorBg)
        toolbar.setBackgroundColor(t.bg)
        hintScroll.setBackgroundColor(t.bg)
        symbolScroll.setBackgroundColor(t.bg)
        statusBar.setBackgroundColor(t.bg)
        statusPos.setTextColor(t.gutter)
        statusSave.setTextColor(t.gutter)
        for (b in toolButtons) b.imageTintList = android.content.res.ColorStateList.valueOf(t.text)
        for (k in keyViews) {
            k.setTextColor(t.text)
            k.background = Ui.roundBackground(Ui.withAlpha(t.text, 0x1F), 8, activity)
        }
        editor.applyTheme(t)
        refreshHints()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hintRunnable)
        removeCallbacks(emptyRunnable)
        super.onDetachedFromWindow()
    }
}
