package com.htmlplayforge.app.files

import android.app.Activity
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.htmlplayforge.app.R
import com.htmlplayforge.app.settings.AppTheme
import com.htmlplayforge.app.utils.Async
import com.htmlplayforge.app.utils.Ui
import com.htmlplayforge.app.utils.WarningDialog
import java.io.File

/** Browse projects and files. Tap to open, long-press for rename / duplicate / delete. */
class FileBrowser(
    private val activity: Activity,
    private val fm: FileManager,
    private val theme: AppTheme,
    private val startDir: File?,
    private val onOpenFile: (File) -> Unit,
    private val onRenamed: (File, File) -> Unit,
    private val onDeleted: (File) -> Unit,
    private val onImportFile: () -> Unit,
    private val onImportZip: () -> Unit,
    private val onAddAsset: () -> Unit
) {
    private class Item(val file: File?, val label: String, val kind: Int) // kind: 0 up, 1 folder, 2 file

    private var dir: File = startDir?.takeIf { it.isDirectory } ?: fm.root
    private var query = ""
    private val items = ArrayList<Item>()
    private lateinit var dialog: AlertDialog
    private lateinit var pathView: TextView
    private lateinit var adapter: ArrayAdapter<Item>

    private fun projectOf(f: File): File {
        var p: File = f
        while (p.parentFile != null && p.parentFile!!.canonicalPath != fm.root.canonicalPath) p = p.parentFile!!
        return p
    }

    private fun relLabel(): String {
        val rel = dir.canonicalPath.removePrefix(fm.root.canonicalPath).trimStart('/')
        return if (rel.isEmpty()) "All projects" else rel
    }

    private fun rebuild(results: List<File>? = null) {
        items.clear()
        if (results != null) {
            for (f in results) items.add(Item(f, fm.relativePath(fm.root, f), if (f.isDirectory) 1 else 2))
        } else {
            if (dir.canonicalPath != fm.root.canonicalPath) items.add(Item(dir.parentFile, "..  (up)", 0))
            for (f in fm.listDir(dir)) items.add(Item(f, f.name, if (f.isDirectory) 1 else 2))
        }
        pathView.text = if (results != null) "Search results" else relLabel()
        adapter.notifyDataSetChanged()
    }

    private fun refresh() {
        if (query.isNotEmpty() && dir.canonicalPath != fm.root.canonicalPath) {
            val project = projectOf(dir)
            Async.run({ fm.search(project, query) }) { r -> r.onSuccess { rebuild(it) } }
        } else rebuild()
    }

    fun show() {
        val c = activity
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        val pad = Ui.dp(c, 16)
        box.setPadding(pad, Ui.dp(c, 8), pad, 0)

        pathView = TextView(c)
        pathView.setTextColor(theme.gutter)
        pathView.textSize = 13f
        box.addView(pathView)

        val search = EditText(c)
        search.hint = "Search by name or text"
        search.setSingleLine(true)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
            override fun afterTextChanged(s: Editable?) { query = s?.toString()?.trim() ?: ""; refresh() }
        })
        box.addView(search)

        val list = ListView(c)
        adapter = object : ArrayAdapter<Item>(c, 0, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val it = items[position]
                val tv = (convertView as? TextView) ?: TextView(c)
                tv.text = if (it.kind == 1) it.label + "/" else it.label
                tv.textSize = 16f
                tv.setTextColor(theme.text)
                tv.gravity = Gravity.CENTER_VERTICAL
                tv.setPadding(Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12))
                tv.compoundDrawablePadding = Ui.dp(c, 12)
                val icon = if (it.kind == 2) R.drawable.ic_file else R.drawable.ic_folder
                val d = ContextCompat.getDrawable(c, icon)?.mutate()
                if (d != null) {
                    d.setTint(if (it.kind == 0) theme.gutter else theme.accent)
                    val s = Ui.dp(c, 22)
                    d.setBounds(0, 0, s, s)
                    tv.setCompoundDrawablesRelative(d, null, null, null)
                }
                return tv
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val it = items[pos]
            val f = it.file ?: return@setOnItemClickListener
            when (it.kind) {
                0, 1 -> { dir = f; query = ""; search.setText(""); rebuild() }
                else -> {
                    if (FileNames.isTextFile(f.name)) { dialog.dismiss(); onOpenFile(f) }
                    else Ui.toast(c, "Images are used inside your pages. They cannot be edited here.")
                }
            }
        }
        list.setOnItemLongClickListener { _, view, pos, _ ->
            val it = items[pos]
            if (it.kind != 0 && it.file != null) showItemMenu(view, it.file)
            true
        }
        box.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 280)))

        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        for ((label, action) in listOf<Pair<String, () -> Unit>>(
            "Import file" to { dialog.dismiss(); onImportFile() },
            "Import ZIP" to { dialog.dismiss(); onImportZip() },
            "Add image" to { dialog.dismiss(); onAddAsset() }
        )) {
            val b = TextView(c)
            b.text = label
            b.textSize = 14f
            b.gravity = Gravity.CENTER
            b.setTextColor(theme.accent)
            b.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 12))
            b.setOnClickListener { action() }
            row.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        box.addView(row)

        dialog = MaterialAlertDialogBuilder(c)
            .setTitle("Open")
            .setView(box)
            .setNegativeButton("Close", null)
            .create()
        rebuild()
        dialog.show()
    }

    private fun showItemMenu(anchor: View, f: File) {
        val m = PopupMenu(activity, anchor)
        m.menu.add(0, 0, 0, "Rename")
        m.menu.add(0, 1, 1, "Duplicate")
        m.menu.add(0, 2, 2, "Delete")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                0 -> Ui.inputDialog(activity, "Rename", "New name", f.name, "Rename",
                    { v -> validateName(f, v) },
                    { v ->
                        Async.run({ fm.rename(f, v) }) { r ->
                            r.onSuccess { n -> onRenamed(f, n); refresh() }
                            r.onFailure { e -> Ui.toast(activity, e.message ?: "Could not rename") }
                        }
                    })
                1 -> Async.run({ fm.duplicate(f) }) { r ->
                    r.onSuccess { refresh() }
                    r.onFailure { e -> Ui.toast(activity, e.message ?: "Could not duplicate") }
                }
                2 -> WarningDialog.confirm(activity, WarningDialog.delete(f.name), "Delete") {
                    Async.run({ fm.delete(f) }) { r ->
                        r.onSuccess { onDeleted(f); refresh() }
                        r.onFailure { e -> Ui.toast(activity, e.message ?: "Could not delete") }
                    }
                }
            }
            true
        }
        m.show()
    }

    private fun validateName(f: File, v: String): String? {
        val clean = (if (f.isDirectory) FileNames.forProject(v) else FileNames.forNewFile(v)) ?: return "Use letters, numbers, . _ - and space"
        val target = File(f.parentFile, clean)
        if (target.exists() && target.canonicalPath != f.canonicalPath) return "'$clean' exists"
        return null
    }
}
