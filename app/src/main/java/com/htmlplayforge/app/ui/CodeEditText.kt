package com.htmlplayforge.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextPaint
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.AppCompatEditText
import com.htmlplayforge.app.editor.HtmlScanner
import com.htmlplayforge.app.editor.Lang
import com.htmlplayforge.app.editor.LineIndex
import com.htmlplayforge.app.editor.SyntaxHighlighter
import com.htmlplayforge.app.settings.AppTheme
import com.htmlplayforge.app.settings.ThemeManager
import com.htmlplayforge.app.utils.Ui

/**
 * The code field. It draws its own line numbers (so they scroll with the text and line up
 * with wrapped lines), colours the code, auto-indents and auto-closes tags and brackets.
 */
class CodeEditText(context: Context) : AppCompatEditText(context) {
    private class SyntaxSpan(color: Int) : ForegroundColorSpan(color)

    var lang: Lang = Lang.HTML
        set(v) { field = v; scheduleHighlight(0L) }
    var theme: AppTheme = ThemeManager.current()
    var highlightingEnabled = true
    var showLineNumbers = true
        set(v) { field = v; updateGutter(true) }
    var autoCloseTags = true
    var autoCloseBrackets = true
    var errorLines: Set<Int> = emptySet()
        set(v) { field = v; invalidate() }

    /** Called after every change made by the user or by an editor tool (not by loading text). */
    var onUserChange: (() -> Unit)? = null
    /** Called with 1-based line and column when the cursor moves. */
    var onCursorLine: ((Int, Int) -> Unit)? = null

    private var ready = false
    private var selfEdit = false
    private var loading = false
    private var lastStart = 0
    private var lastBefore = 0
    private var lastCount = 0
    private var gutterWidth = 0
    private var gutterDigits = 0

    private val gutterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint()
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightRunnable = Runnable { applyHighlight() }

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (!selfEdit) { lastStart = start; lastBefore = before; lastCount = count }
        }
        override fun afterTextChanged(e: Editable?) {
            if (selfEdit || loading || e == null) return
            try { handleTyping(e) } catch (t: Throwable) { selfEdit = false }
            afterChange()
        }
    }

    init {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO // required for this input type
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        gravity = Gravity.TOP or Gravity.START
        background = null
        isSaveEnabled = false // never put the whole text into the saved state
        isVerticalScrollBarEnabled = true
        setHorizontallyScrolling(false)
        gutterPaint.textAlign = Paint.Align.RIGHT
        addTextChangedListener(watcher)
        ready = true
    }

    // ------------------------------------------------------------ look
    fun applyTypeface(tf: Typeface) {
        typeface = tf
        gutterPaint.typeface = tf
        updateGutter(true)
    }

    fun applySizeSp(sp: Int) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp.toFloat())
        gutterPaint.textSize = textSize * 0.8f
        updateGutter(true)
    }

    fun applyTheme(t: AppTheme) {
        theme = t
        setTextColor(t.text)
        setHintTextColor(t.gutter)
        highlightColor = Ui.withAlpha(t.accent, 0x55)
        gutterPaint.color = t.gutter
        scheduleHighlight(0L)
        invalidate()
    }

    fun recolor() { scheduleHighlight(0L) }

    fun setWrap(wrap: Boolean) {
        setHorizontallyScrolling(!wrap)
        scrollTo(0, scrollY)
    }

    private fun updateGutter(force: Boolean) {
        val lines = logicalLines()
        val digits = maxOf(2, lines.toString().length)
        if (!force && digits == gutterDigits) return
        gutterDigits = digits
        gutterPaint.textSize = textSize * 0.8f
        gutterWidth = if (showLineNumbers) (digits * gutterPaint.measureText("0") + Ui.dp(context, 14)).toInt() else 0
        val left = gutterWidth + Ui.dp(context, 8)
        if (paddingLeft != left) setPadding(left, Ui.dp(context, 6), Ui.dp(context, 8), Ui.dp(context, 6))
    }

    fun logicalLines(): Int {
        val t = text ?: return 1
        var n = 1
        for (i in 0 until t.length) if (t[i] == '\n') n++
        return n
    }

    // ------------------------------------------------------- loading
    /** Replaces all text without counting it as a user edit. */
    fun loadText(content: String, cursor: Int = 0) {
        loading = true
        setText(content)
        setSelection(cursor.coerceIn(0, content.length))
        loading = false
        updateGutter(true)
        scheduleHighlight(0L)
        scrollTo(0, 0)
    }

    /** Used by undo and redo: sets text and cursor, then reports a change. */
    fun restoreState(content: String, cursor: Int) {
        loading = true
        setText(content)
        setSelection(cursor.coerceIn(0, content.length))
        loading = false
        afterChange(skipUndo = true)
    }

    /** Edits made by tools (formatter, hints, find/replace). Reported like typing, without auto-close. */
    fun replaceRange(start: Int, end: Int, with: String, cursorBack: Int = 0) {
        val e = text ?: return
        val s = start.coerceIn(0, e.length)
        val en = end.coerceIn(s, e.length)
        selfEdit = true
        try {
            e.replace(s, en, with)
            setSelection((s + with.length - cursorBack).coerceIn(0, e.length))
        } finally {
            selfEdit = false
        }
        afterChange()
    }

    // --------------------------------------------------- after change
    var skipUndoNext = false

    private fun afterChange(skipUndo: Boolean = false) {
        skipUndoNext = skipUndo
        updateGutter(false)
        scheduleHighlight(if ((text?.length ?: 0) > 20_000) 450L else 120L)
        onUserChange?.invoke()
        skipUndoNext = false
    }

    private fun scheduleHighlight(delay: Long) {
        removeCallbacks(highlightRunnable)
        postDelayed(highlightRunnable, delay)
    }

    private fun applyHighlight() {
        val e = text ?: return
        try {
            for (s in e.getSpans(0, e.length, SyntaxSpan::class.java)) e.removeSpan(s)
            if (!highlightingEnabled) return
            val spans = SyntaxHighlighter.highlight(e.toString(), lang)
            for (sp in spans) {
                if (sp.start >= 0 && sp.end <= e.length && sp.start < sp.end)
                    e.setSpan(SyntaxSpan(ThemeManager.colorFor(sp.kind, theme)), sp.start, sp.end, Editable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } catch (t: Throwable) {
            // colouring must never crash the editor
        }
    }

    // ------------------------------------------------------- typing
    private fun handleTyping(e: Editable) {
        if (lastCount != 1 || lastBefore != 0) return
        val pos = lastStart
        if (pos < 0 || pos >= e.length) return
        val c = e[pos]
        if (c == '\n') { autoIndent(e, pos); return }
        if (c == '>' && autoCloseTags && lang == Lang.HTML) { autoCloseTag(e, pos); return }
        if (autoCloseBrackets && lang != Lang.PLAIN) autoBracket(e, pos, c)
    }

    private fun lineStart(e: CharSequence, pos: Int): Int {
        var i = pos
        while (i > 0 && e[i - 1] != '\n') i--
        return i
    }

    private fun autoIndent(e: Editable, pos: Int) {
        val ls = lineStart(e, pos)
        val prev = e.subSequence(ls, pos).toString()
        val indent = prev.takeWhile { it == ' ' || it == '\t' }
        val trimmed = prev.trimEnd()
        var extra = ""
        if (trimmed.endsWith("{") || trimmed.endsWith("[") || trimmed.endsWith("(")) extra = "  "
        else if (lang == Lang.HTML && endsWithOpenTag(trimmed)) extra = "  "
        var ins = indent + extra
        var cursorAt = pos + 1 + ins.length
        if (extra.isNotEmpty() && pos + 1 < e.length) {
            val next = e[pos + 1]
            val nextTwo = e.subSequence(pos + 1, minOf(e.length, pos + 3)).toString()
            if (next == '}' || next == ']' || next == ')' || nextTwo == "</") ins = ins + "\n" + indent
        }
        if (ins.isEmpty()) return
        selfEdit = true
        try {
            e.insert(pos + 1, ins)
            setSelection(cursorAt.coerceIn(0, e.length))
        } finally { selfEdit = false }
    }

    private fun endsWithOpenTag(line: String): Boolean {
        if (!line.endsWith(">") || line.endsWith("/>")) return false
        val lt = line.lastIndexOf('<')
        if (lt < 0) return false
        val tag = line.substring(lt)
        if (tag.startsWith("</") || tag.startsWith("<!")) return false
        val name = tag.removePrefix("<").takeWhile { it.isLetterOrDigit() || it == '-' }.lowercase()
        return name.isNotEmpty() && name !in HtmlScanner.VOID
    }

    private fun autoCloseTag(e: Editable, pos: Int) {
        val lt = run {
            var i = pos
            while (i >= 0 && e[i] != '<') { if (pos - i > 400) return; i-- }
            i
        }
        if (lt < 0) return
        val between = e.subSequence(lt, pos + 1).toString()
        if (between.endsWith("/>") || between.indexOf('<', 1) >= 0) return
        val m = Regex("^<([A-Za-z][A-Za-z0-9-]*)(\\s[^<>]*)?>$").find(between) ?: return
        val name = m.groupValues[1]
        if (name.lowercase() in HtmlScanner.VOID) return
        val closing = "</$name>"
        if (e.length >= pos + 1 + closing.length && e.subSequence(pos + 1, pos + 1 + closing.length).toString() == closing) return
        selfEdit = true
        try {
            e.insert(pos + 1, closing)
            setSelection(pos + 1)
        } finally { selfEdit = false }
    }

    private fun autoBracket(e: Editable, pos: Int, c: Char) {
        val next = if (pos + 1 < e.length) e[pos + 1] else '\u0000'
        val prev = if (pos > 0) e[pos - 1] else '\u0000'
        // typing a closing character right before the same one: just step over it
        if ((c == ')' || c == ']' || c == '}' || c == '"' || c == '\'' || c == '`') && next == c) {
            selfEdit = true
            try { e.delete(pos, pos + 1); setSelection(pos + 1) } finally { selfEdit = false }
            return
        }
        val close = when (c) {
            '(' -> ')'
            '[' -> ']'
            '{' -> '}'
            '"', '\'', '`' -> c
            else -> return
        }
        if (c == '"' || c == '\'' || c == '`') {
            if (prev.isLetterOrDigit()) return
        }
        if (lang == Lang.HTML) {
            val s = e.toString()
            val inTag = s.lastIndexOf('<', pos) > s.lastIndexOf('>', pos)
            val lower = s.substring(0, pos).lowercase()
            val inCode = (lower.lastIndexOf("<script") > lower.lastIndexOf("</script")) ||
                (lower.lastIndexOf("<style") > lower.lastIndexOf("</style"))
            val quote = c == '"' || c == '\'' || c == '`'
            if (quote && !inTag && !inCode) return
            if (!quote && !inCode) return
        }
        selfEdit = true
        try { e.insert(pos + 1, close.toString()); setSelection(pos + 1) } finally { selfEdit = false }
    }

    // ---------------------------------------------------- selection
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!ready) return
        val t = text ?: return
        if (t.length > 300_000) return // very big file: skip the status line, keep typing fast
        try {
            val idx = LineIndex(t.toString())
            val s = selStart.coerceIn(0, t.length)
            onCursorLine?.invoke(idx.lineOf(s), idx.colOf(s))
        } catch (e: Throwable) {
            // status bar only
        }
    }

    /** Moves the cursor to the start of a line and scrolls so the line is visible. */
    fun goToLine(line: Int) {
        val t = text ?: return
        val idx = LineIndex(t.toString())
        val l = line.coerceIn(1, idx.lineCount)
        val off = idx.startOfLine(l)
        requestFocus()
        setSelection(off)
        post {
            val lay = layout ?: return@post
            val y = lay.getLineTop(lay.getLineForOffset(off)) + extendedPaddingTop
            val maxScroll = maxOf(0, lay.height + extendedPaddingTop + extendedPaddingBottom - height)
            scrollTo(0, (y - height / 3).coerceIn(0, maxScroll))
        }
    }

    // ------------------------------------------------------- drawing
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showLineNumbers) return
        try { drawGutter(canvas) } catch (t: Throwable) { /* never crash while drawing */ }
    }

    private fun drawGutter(canvas: Canvas) {
        val l = layout ?: return
        val t = text ?: return
        val clip = canvas.clipBounds
        val top = extendedPaddingTop
        val x0 = scrollX.toFloat()
        fillPaint.color = theme.editorBg
        canvas.drawRect(x0, clip.top.toFloat(), x0 + gutterWidth, clip.bottom.toFloat(), fillPaint)
        fillPaint.color = theme.gutter and 0x00FFFFFF or 0x33000000
        canvas.drawRect(x0 + gutterWidth - 1f, clip.top.toFloat(), x0 + gutterWidth, clip.bottom.toFloat(), fillPaint)
        if (l.lineCount == 0) return
        val first = l.getLineForVertical(maxOf(0, clip.top - top)).coerceIn(0, l.lineCount - 1)
        val last = l.getLineForVertical(maxOf(0, clip.bottom - top)).coerceIn(first, l.lineCount - 1)
        var current = 1
        val start0 = minOf(l.getLineStart(first), t.length)
        for (i in 0 until start0) if (t[i] == '\n') current++
        val right = x0 + gutterWidth - Ui.dp(context, 6)
        for (v in first..last) {
            val s = l.getLineStart(v)
            val isStart = s == 0 || (s - 1 < t.length && t[s - 1] == '\n')
            if (isStart && v != first) current++
            if (!isStart) continue
            val baseline = (top + l.getLineBaseline(v)).toFloat()
            if (current in errorLines) {
                badgePaint.color = 0xFFD32F2F.toInt()
                val r = RectF(x0 + 2f, (top + l.getLineTop(v)).toFloat(), x0 + gutterWidth - 2f, (top + l.getLineBottom(v)).toFloat())
                canvas.drawRoundRect(r, 6f, 6f, badgePaint)
                val old = gutterPaint.color
                gutterPaint.color = 0xFFFFFFFF.toInt()
                canvas.drawText(current.toString(), right, baseline, gutterPaint)
                gutterPaint.color = old
            } else {
                canvas.drawText(current.toString(), right, baseline, gutterPaint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(highlightRunnable)
        super.onDetachedFromWindow()
    }
}
