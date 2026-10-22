package com.htmlplayforge.app.ui

import android.app.Activity
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.htmlplayforge.app.R
import com.htmlplayforge.app.editor.FindEngine
import com.htmlplayforge.app.settings.AppTheme
import com.htmlplayforge.app.utils.Ui

/** Find and replace in a bottom sheet. Plain text or regex, match case optional. */
class FindReplaceSheet(private val activity: Activity, private val editor: CodeEditText, private val theme: AppTheme) {
    private val dialog = BottomSheetDialog(activity)
    private val find = EditText(activity)
    private val replace = EditText(activity)
    private val matchCase = CheckBox(activity)
    private val regex = CheckBox(activity)
    private val info = TextView(activity)

    private fun text(): String = editor.text?.toString() ?: ""

    private fun updateInfo() {
        val q = find.text.toString()
        if (regex.isChecked && q.isNotEmpty() && !FindEngine.isValidRegex(q)) { info.text = "Not a valid pattern"; return }
        val n = FindEngine.count(text(), q, matchCase.isChecked, regex.isChecked)
        info.text = if (q.isEmpty()) "" else "$n found"
    }

    private fun select(r: IntRange?) {
        if (r == null) { info.text = "Not found"; return }
        editor.requestFocus()
        editor.setSelection(r.first, r.last + 1)
        updateInfo()
    }

    private fun next() {
        val from = maxOf(editor.selectionEnd, 0)
        select(FindEngine.findNext(text(), find.text.toString(), matchCase.isChecked, regex.isChecked, from))
    }

    private fun prev() {
        val before = maxOf(editor.selectionStart, 0)
        select(FindEngine.findPrev(text(), find.text.toString(), matchCase.isChecked, regex.isChecked, before))
    }

    private fun replaceOne() {
        val s = editor.selectionStart
        val e = editor.selectionEnd
        val q = find.text.toString()
        if (s >= 0 && e > s) {
            val selected = text().substring(s, e)
            val ok = FindEngine.findNext(selected, q, matchCase.isChecked, regex.isChecked, 0)
            if (ok != null && ok.first == 0 && ok.last + 1 == selected.length) {
                val rep = if (regex.isChecked) FindEngine.replaceAll(selected, q, replace.text.toString(), matchCase.isChecked, true)
                else replace.text.toString()
                editor.replaceRange(s, e, rep)
            }
        }
        next()
    }

    private fun replaceAll() {
        val q = find.text.toString()
        if (q.isEmpty()) return
        val before = text()
        val after = FindEngine.replaceAll(before, q, replace.text.toString(), matchCase.isChecked, regex.isChecked)
        if (after != before) editor.replaceRange(0, before.length, after)
        updateInfo()
    }

    fun show() {
        val pad = Ui.dp(activity, 16)
        val root = LinearLayout(activity)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)
        root.setBackgroundColor(theme.bg)

        for ((box, hint) in listOf(find to "Find", replace to "Replace with")) {
            box.hint = hint
            box.setSingleLine(true)
            box.setTextColor(theme.text)
            box.setHintTextColor(theme.gutter)
            root.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val opts = LinearLayout(activity)
        opts.orientation = LinearLayout.HORIZONTAL
        matchCase.text = "Match case"
        regex.text = "Regex"
        for (c in listOf(matchCase, regex)) {
            c.setTextColor(theme.text)
            c.setOnCheckedChangeListener { _, _ -> updateInfo() }
            opts.addView(c, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(opts)

        val row = LinearLayout(activity)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(Ui.iconButton(activity, R.drawable.ic_prev, theme.text, "Previous match") { prev() })
        row.addView(Ui.iconButton(activity, R.drawable.ic_next, theme.text, "Next match") { next() })
        info.setTextColor(theme.gutter)
        row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)

        val row2 = LinearLayout(activity)
        row2.orientation = LinearLayout.HORIZONTAL
        val b1 = Button(activity)
        b1.text = "Replace"
        b1.setOnClickListener { replaceOne() }
        val b2 = Button(activity)
        b2.text = "Replace all"
        b2.setOnClickListener { replaceAll() }
        row2.addView(b1, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row2.addView(b2, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row2)

        // pre-fill with the selected text
        val s = editor.selectionStart
        val e = editor.selectionEnd
        if (s >= 0 && e > s && e - s < 100 && !text().substring(s, e).contains('\n')) find.setText(text().substring(s, e))
        dialog.setContentView(root)
        dialog.setOnShowListener { find.requestFocus() }
        dialog.show()
        updateInfo()
    }
}
