package com.htmlplayforge.app

import android.graphics.Typeface
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.htmlplayforge.app.editor.Lang
import com.htmlplayforge.app.files.FileBrowser
import com.htmlplayforge.app.files.FileManager
import com.htmlplayforge.app.files.FileNames
import com.htmlplayforge.app.files.ImportExportHandler
import com.htmlplayforge.app.files.SampleManager
import com.htmlplayforge.app.settings.FontManager
import com.htmlplayforge.app.settings.ThemeManager
import com.htmlplayforge.app.utils.Async
import com.htmlplayforge.app.utils.BugReport
import com.htmlplayforge.app.utils.LinkOpener
import com.htmlplayforge.app.utils.ShareHelper
import com.htmlplayforge.app.utils.Ui
import com.htmlplayforge.app.utils.WarningDialog
import java.io.File

/** The hamburger-menu actions: files, projects, samples, export, share, about. */
class ProjectActions(private val a: MainActivity) {
    private val fm: FileManager get() = a.fm

    private fun fail(e: Throwable) = Ui.toast(a, e.message ?: "Something went wrong")

    // ------------------------------------------------------------ new
    fun newFile() {
        a.guardUnsaved {
            Ui.inputDialog(a, "Input file name", "index.html", "", "Create",
                { v ->
                    val n = FileNames.forNewFile(v)
                    if (n == null) "Use letters, numbers, . _ - and space"
                    else if (File(a.projectDir ?: fm.root, n).exists() && a.projectDir != null) "'$n' exists"
                    else null
                },
                { v ->
                    val name = FileNames.forNewFile(v) ?: return@inputDialog
                    val dir = a.ensureProject()
                    val f = File(dir, name)
                    Async.run({ fm.writeText(f, FileManager.starterText(name)) }) { r ->
                        r.onSuccess { a.openFile(f, 0, force = true) }
                        r.onFailure { e -> fail(e) }
                    }
                })
        }
    }

    fun newProject() {
        a.guardUnsaved {
            Ui.inputDialog(a, "New project", "Project name", "", "Create",
                { v ->
                    val n = FileNames.forProject(v)
                    if (n == null) "Use letters, numbers, . _ - and space"
                    else if (File(fm.root, n).exists()) "'$n' exists"
                    else null
                },
                { v ->
                    Async.run({
                        val dir = fm.createProject(v)
                        val index = File(dir, "index.html")
                        fm.writeText(index, MainActivity.HELLO)
                        index
                    }) { r ->
                        r.onSuccess { a.openFile(it, 0, force = true) }
                        r.onFailure { e -> fail(e) }
                    }
                })
        }
    }

    // ----------------------------------------------------------- open
    fun openBrowser() {
        FileBrowser(
            a, fm, ThemeManager.current(), a.projectDir,
            onOpenFile = { f -> a.openFile(f) },
            onRenamed = { old, new ->
                if (a.currentFile?.canonicalPath == old.canonicalPath) { a.currentFile = new; a.refreshTitle() }
            },
            onDeleted = { f ->
                val cur = a.currentFile
                if (cur != null && cur.canonicalPath.startsWith(f.canonicalPath)) { a.currentFile = null; a.refreshTitle() }
            },
            onImportFile = { importFile(arrayOf("text/*", "application/json", "image/svg+xml", "application/octet-stream"), false) },
            onImportZip = { importZip() },
            onAddAsset = { importFile(arrayOf("image/*"), true) }
        ).show()
    }

    private fun importFile(types: Array<String>, asset: Boolean) {
        a.pendingOpen = { uri -> doImportFile(uri, asset) }
        a.openDoc.launch(types)
    }

    private fun doImportFile(uri: Uri, asset: Boolean) {
        val cr = a.contentResolver
        Async.run({
            val raw = ImportExportHandler.displayName(cr, uri)
            val name = FileNames.forNewFile(raw) ?: "imported.html"
            val finalName = if (!raw.contains('.')) FileNames.sanitizeSegment(raw).ifEmpty { "file" } else name
            val dir = a.projectDir ?: File(fm.root, "Imported").also { it.mkdirs() }
            val dest = File(dir, finalName)
            ImportExportHandler.copyToFile(cr, uri, dest)
            dest
        }) { r ->
            r.onSuccess { f ->
                if (a.projectDir == null) a.guardUnsaved { a.openFile(f, 0, force = true) }
                else if (!asset && FileNames.isTextFile(f.name)) a.guardUnsaved { a.openFile(f, 0, force = true) }
                else Ui.toast(a, "Added ${f.name}")
            }
            r.onFailure { e -> fail(e) }
        }
    }

    private fun importZip() {
        a.pendingOpen = { uri ->
            val cr = a.contentResolver
            Async.run({
                val name = ImportExportHandler.displayName(cr, uri)
                val dir = ImportExportHandler.importZip(cr, uri, fm, name)
                fm.firstHtml(dir) ?: dir.listFiles()?.firstOrNull { it.isFile }
            }) { r ->
                r.onSuccess { f ->
                    if (f != null) a.guardUnsaved { a.openFile(f, 0, force = true) } else Ui.toast(a, "ZIP imported")
                }
                r.onFailure { e ->
                    MaterialAlertDialogBuilder(a).setTitle("Could not import").setMessage(e.message ?: "Bad ZIP").setPositiveButton("OK", null).show()
                }
            }
        }
        a.openDoc.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
    }

    // -------------------------------------------------------- samples
    fun samples() {
        val list = SampleManager.list(a)
        if (list.isEmpty()) { Ui.toast(a, "No samples found"); return }
        MaterialAlertDialogBuilder(a)
            .setTitle("Sample Projects")
            .setItems(list.map { it.title }.toTypedArray()) { _, i ->
                val s = list[i]
                val dest = File(fm.root, s.folder)
                a.guardUnsaved {
                    if (dest.exists()) WarningDialog.confirm(a, WarningDialog.overwrite(s.folder), "Overwrite") { installSample(s) }
                    else installSample(s)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun installSample(s: SampleManager.Sample) {
        val dest = File(fm.root, s.folder)
        Async.run({
            SampleManager.install(a, s.folder, dest)
            File(dest, "index.html")
        }) { r ->
            r.onSuccess { a.openFile(it, 0, force = true) }
            r.onFailure { e -> fail(e) }
        }
    }

    // ---------------------------------------------------------- save
    fun saveAs() { askName("Save As", a.currentFile?.name ?: "index.html", "Save", false) }

    fun renameCurrent() {
        val cur = a.currentFile
        if (cur == null) { askName("Rename", "index.html", "Save", false); return }
        Ui.inputDialog(a, "Rename", "File name", cur.name, "Rename",
            { v ->
                val n = FileNames.forNewFile(v)
                if (n == null) "Use letters, numbers, . _ - and space"
                else if (File(cur.parentFile, n).exists() && n != cur.name) "'$n' exists"
                else null
            },
            { v ->
                WarningDialog.confirm(a, WarningDialog.rename(cur.name), "Rename") {
                    Async.run({ fm.rename(cur, v) }) { r ->
                        r.onSuccess { n ->
                            a.currentFile = n
                            a.editorView.editor.lang = Lang.fromFileName(n.name)
                            a.refreshTitle()
                        }
                        r.onFailure { e -> fail(e) }
                    }
                }
            })
    }

    private fun askName(title: String, initial: String, positive: String, unused: Boolean) {
        Ui.inputDialog(a, title, "File name", initial, positive,
            { v -> if (FileNames.forNewFile(v) == null) "Use letters, numbers, . _ - and space" else null },
            { v ->
                val name = FileNames.forNewFile(v) ?: return@inputDialog
                val dir = a.ensureProject()
                val target = File(dir, name)
                val write = {
                    val text = a.editorView.text
                    Async.run({ fm.writeText(target, text) }) { r ->
                        r.onSuccess {
                            a.currentFile = target
                            a.editorView.editor.lang = Lang.fromFileName(name)
                            a.markClean()
                            Ui.toast(a, "Saved $name")
                        }
                        r.onFailure { e -> fail(e) }
                    }
                }
                if (target.exists() && target.canonicalPath != a.currentFile?.canonicalPath)
                    WarningDialog.confirm(a, WarningDialog.overwrite(name), "Overwrite") { write() }
                else write()
            })
    }

    // -------------------------------------------------------- export
    fun export() {
        val labels = arrayOf("This file", "Project as ZIP", "Single HTML file (CSS and JS inside)")
        MaterialAlertDialogBuilder(a).setTitle("Export")
            .setItems(labels) { _, i ->
                when (i) {
                    0 -> exportFile()
                    1 -> exportZip()
                    2 -> exportSingleHtml()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun mimeFor(name: String) = com.htmlplayforge.app.preview.PreviewSecurity.mimeFor(name)

    private fun exportFile() {
        val name = a.currentFile?.name ?: "untitled.html"
        val text = a.editorView.text
        a.pendingCreate = { uri: Uri ->
            Async.run({ ImportExportHandler.writeText(a.contentResolver, uri, text) }) { r ->
                r.onSuccess { Ui.toast(a, "Exported") }
                r.onFailure { e -> fail(e) }
            }
        }
        a.createDoc.launch(Pair(mimeFor(name), name))
    }

    private fun exportZip() {
        val dir = a.projectDir
        if (dir == null) { Ui.toast(a, "Save your work in a project first"); return }
        a.guardUnsaved {
            a.pendingCreate = { uri: Uri ->
                Async.run({ ImportExportHandler.writeZip(a.contentResolver, uri, dir) }) { r ->
                    r.onSuccess { Ui.toast(a, "ZIP exported") }
                    r.onFailure { e -> fail(e) }
                }
            }
            a.createDoc.launch(Pair("application/zip", dir.name + ".zip"))
        }
    }

    private fun exportSingleHtml() {
        if (a.editorView.lang != Lang.HTML) { Ui.toast(a, "Open an HTML file first"); return }
        val text = a.editorView.text
        val proj = a.projectDir
        val rel = a.currentFile?.let { f -> proj?.let { fm.relativePath(it, f) } }
        val overrides = if (rel != null) mapOf(rel to text) else emptyMap()
        val name = (a.currentFile?.name ?: "page.html").substringBeforeLast('.') + "-single.html"
        a.pendingCreate = { uri: Uri ->
            Async.run({
                val html = ImportExportHandler.singleHtml(text, proj, fm, overrides)
                ImportExportHandler.writeText(a.contentResolver, uri, html)
            }) { r ->
                r.onSuccess { Ui.toast(a, "Exported") }
                r.onFailure { e -> fail(e) }
            }
        }
        a.createDoc.launch(Pair("text/html", name))
    }

    // --------------------------------------------------------- share
    fun share() {
        MaterialAlertDialogBuilder(a).setTitle("Share Code")
            .setItems(arrayOf("As text", "As image (PNG)")) { _, i ->
                val text = a.editorView.text
                if (i == 0) ShareHelper.shareText(a, text, a.currentFile?.name ?: "code")
                else {
                    val t = ThemeManager.current()
                    val tf: Typeface = FontManager.typeface(a)
                    Async.run({ ShareHelper.renderCodeImage(a, text, t.editorBg, t.text, tf) }) { r ->
                        r.onSuccess { ShareHelper.shareImage(a, it) }
                        r.onFailure { e -> fail(e) }
                    }
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    // --------------------------------------------------------- about
    fun about() {
        val msg = "HTMLPlayForge ${BugReport.appVersion(a)}\n\n" +
            "${BugReport.androidVersion()}\nWebView ${BugReport.webViewVersion()}\n\n" +
            "By ${LinkOpener.DEVELOPER_NAME}, ${LinkOpener.COUNTRY}\n${LinkOpener.EMAIL}\n\n" +
            "No permissions. No internet access. No trackers. Your files stay on your phone."
        val d: AlertDialog = MaterialAlertDialogBuilder(a)
            .setTitle("About")
            .setMessage(msg)
            .setPositiveButton("Close", null)
            .setNeutralButton("Website") { _, _ -> LinkOpener.open(a, LinkOpener.WEBSITE) }
            .create()
        d.show()
    }
}
