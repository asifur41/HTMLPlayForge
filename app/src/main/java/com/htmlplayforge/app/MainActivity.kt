package com.htmlplayforge.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.htmlplayforge.app.editor.CodeFormatter
import com.htmlplayforge.app.editor.HtmlLint
import com.htmlplayforge.app.editor.Lang
import com.htmlplayforge.app.files.FileManager
import com.htmlplayforge.app.files.StorageHelper
import com.htmlplayforge.app.preview.ConsoleEntry
import com.htmlplayforge.app.preview.Level
import com.htmlplayforge.app.settings.AppTheme
import com.htmlplayforge.app.settings.LayoutModeManager
import com.htmlplayforge.app.settings.SettingsActivity
import com.htmlplayforge.app.settings.ThemeManager
import com.htmlplayforge.app.ui.CreateDocContract
import com.htmlplayforge.app.ui.EditorView
import com.htmlplayforge.app.ui.FlipLayoutView
import com.htmlplayforge.app.ui.OnboardingActivity
import com.htmlplayforge.app.ui.PaneLayout
import com.htmlplayforge.app.ui.PreviewView
import com.htmlplayforge.app.utils.Async
import com.htmlplayforge.app.utils.Prefs
import com.htmlplayforge.app.utils.Ui
import com.htmlplayforge.app.utils.WarningDialog
import java.io.File

/** The one main screen: top bar, editor and preview. File actions live in ProjectActions. */
class MainActivity : AppCompatActivity() {
    lateinit var fm: FileManager
    lateinit var editorView: EditorView
    lateinit var preview: PreviewView
    lateinit var appTheme: AppTheme
    private lateinit var pane: PaneLayout
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var topBar: LinearLayout
    private lateinit var fileNameView: TextView
    private lateinit var dirtyDot: View
    private lateinit var runBtn: TextView
    private lateinit var actions: ProjectActions
    private var shownMode = ""

    var projectDir: File? = null
    var currentFile: File? = null
    var dirty = false
    private var hasRun = false
    private var lintLines: Set<Int> = emptySet()
    private val runtimeLines = HashSet<Int>()

    // result launchers (must be created before the activity starts)
    var pendingCreate: ((Uri) -> Unit)? = null
    var pendingOpen: ((Uri) -> Unit)? = null
    val createDoc = registerForActivityResult(CreateDocContract()) { uri ->
        val a = pendingCreate; pendingCreate = null
        if (uri != null && a != null) a(uri)
    }
    val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val a = pendingOpen; pendingOpen = null
        if (uri != null && a != null) a(uri)
    }

    private val liveRunnable = Runnable { runPreview(fromRun = false) }
    private val autosaveRunnable = object : Runnable {
        override fun run() {
            if (dirty && Prefs.autoSave) saveNow(silent = true)
            Ui.post(Prefs.autoSaveSeconds * 1000L, this)
        }
    }

    // ------------------------------------------------------------ create
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fm = StorageHelper.fileManager(this)
        appTheme = ThemeManager.current()
        ThemeManager.styleWindow(this)
        actions = ProjectActions(this)

        editorView = EditorView(this)
        preview = PreviewView(this)
        buildScreen()
        editorView.onChange = { onEditorChanged() }
        preview.onEntryClick = { jumpTo(it) }
        preview.onConsoleEntry = { onConsole(it) }
        preview.onCopy = { editorView.copyAll() }
        preview.onLoadingChanged = { loading -> runBtn.text = if (loading) "Reload" else "Run" }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBack() }
        })

        if (!Prefs.onboardingDone) startActivity(Intent(this, OnboardingActivity::class.java))
        loadInitial(savedInstanceState)
    }

    private fun buildScreen() {
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        Ui.applyInsets(root)

        topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.HORIZONTAL
        topBar.gravity = Gravity.CENTER_VERTICAL
        val menu = Ui.iconButton(this, R.drawable.ic_menu, appTheme.text, "Menu") { }
        menu.setOnClickListener { showMenu(menu) }
        topBar.addView(menu)
        dirtyDot = Ui.dot(this, appTheme.accent)
        dirtyDot.visibility = View.GONE
        topBar.addView(dirtyDot)
        fileNameView = TextView(this)
        fileNameView.textSize = 16f
        fileNameView.maxLines = 1
        fileNameView.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        topBar.addView(fileNameView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        runBtn = TextView(this)
        runBtn.text = "Run"
        runBtn.textSize = 15f
        runBtn.gravity = Gravity.CENTER
        runBtn.setTextColor(0xFFFFFFFF.toInt())
        val play = ContextCompat.getDrawable(this, R.drawable.ic_play)?.mutate()
        if (play != null) {
            play.setTint(0xFFFFFFFF.toInt())
            val s = Ui.dp(this, 20)
            play.setBounds(0, 0, s, s)
            runBtn.setCompoundDrawablesRelative(play, null, null, null)
        }
        runBtn.compoundDrawablePadding = Ui.dp(this, 4)
        runBtn.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 16), 0)
        runBtn.setOnClickListener { runPreview(fromRun = true) }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 36))
        lp.marginEnd = Ui.dp(this, 10)
        topBar.addView(runBtn, lp)
        root.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)))

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        attachLayout()
        applyThemeColors()
    }

    private fun attachLayout() {
        if (::pane.isInitialized) { pane.detach(); content.removeView(pane.root) }
        pane = if (LayoutModeManager.isFlip) FlipLayoutView(this) else com.htmlplayforge.app.ui.SplitLayoutView(this)
        shownMode = Prefs.layoutMode
        pane.setContent(editorView, preview)
        pane.onReload = { runPreview(fromRun = false) }
        pane.onConsoleToggle = {
            preview.isConsoleVisible = !preview.isConsoleVisible
            Prefs.showConsole = preview.isConsoleVisible
        }
        pane.onBackToEditor = { pane.showEditor() }
        content.addView(pane.root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pane.applyTheme(appTheme)
    }

    private fun applyThemeColors() {
        root.setBackgroundColor(appTheme.bg)
        topBar.setBackgroundColor(appTheme.bg)
        fileNameView.setTextColor(appTheme.text)
        runBtn.background = Ui.roundBackground(appTheme.accent, 18, this)
        for (i in 0 until topBar.childCount) {
            val v = topBar.getChildAt(i)
            if (v is android.widget.ImageButton) v.imageTintList = android.content.res.ColorStateList.valueOf(appTheme.text)
        }
    }

    private fun applyAll() {
        appTheme = ThemeManager.current()
        ThemeManager.styleWindow(this)
        applyThemeColors()
        editorView.applyTheme(appTheme)
        editorView.applyPrefs()
        preview.applyTheme(appTheme)
        preview.applySettings()
        if (shownMode != Prefs.layoutMode) attachLayout() else pane.applyTheme(appTheme)
    }

    // ------------------------------------------------------- loading
    private fun loadInitial(saved: Bundle?) {
        val savedText = saved?.getString("text")
        val savedFile = saved?.getString("file")
        if (saved != null && savedText != null) {
            val f = if (savedFile != null) File(savedFile) else null
            projectDir = f?.let { projectOf(it) }
            currentFile = f?.takeIf { it.isFile }
            editorView.setContent(savedText, currentFile?.name)
            dirty = saved.getBoolean("dirty", false)
            refreshTitle()
            return
        }
        val last = Prefs.lastFile
        val lastProject = Prefs.lastProject
        if (last.isNotEmpty() && lastProject.isNotEmpty()) {
            val proj = File(fm.root, lastProject)
            val f = fm.resolve(proj, last)
            if (f != null && f.isFile) { openFile(f, 0, force = true); return }
        }
        val draft = StorageHelper.draftFile(this)
        if (draft.isFile) {
            Async.run({ draft.readText() }) { r ->
                setUntitled(r.getOrNull() ?: HELLO)
            }
            return
        }
        setUntitled(HELLO)
        Prefs.firstLaunchDone = true
    }

    fun setUntitled(text: String) {
        currentFile = null
        projectDir = null
        preview.setProjectRoot(null)
        editorView.setContent(text, null, if (text == "<!DOCTYPE html>\n") text.length else 0)
        dirty = false
        refreshTitle()
    }

    fun projectOf(f: File): File? {
        val rootPath = fm.root.canonicalPath
        var p: File? = f.canonicalFile
        while (p != null && p.parentFile != null && p.parentFile!!.canonicalPath != rootPath) p = p.parentFile
        return if (p != null && p.parentFile != null) p else null
    }

    /** Opens a file in the editor. [line] > 0 jumps to that line. */
    fun openFile(file: File, line: Int = 0, force: Boolean = false) {
        val doOpen = {
            Async.run({ fm.readText(file) }) { r ->
                r.onSuccess { text ->
                    currentFile = file
                    projectDir = projectOf(file)
                    Prefs.lastProject = projectDir?.name ?: ""
                    Prefs.lastFile = projectDir?.let { fm.relativePath(it, file) } ?: ""
                    preview.setProjectRoot(projectDir)
                    preview.clearOverrides()
                    editorView.setContent(text, file.name, if (text == "<!DOCTYPE html>\n") text.length else 0)
                    dirty = false
                    lintLines = emptySet(); runtimeLines.clear(); editorView.setErrorLines(emptySet())
                    refreshTitle()
                    if (line > 0) editorView.jumpToLine(line)
                    pane.showEditor()
                    if (pane !is FlipLayoutView && Prefs.livePreview && !hasRun) runPreview(fromRun = false)
                }
                r.onFailure { e -> Ui.toast(this, e.message ?: "Could not open the file") }
            }
        }
        if (force) doOpen() else guardUnsaved { doOpen() }
    }

    fun ensureProject(): File {
        projectDir?.let { if (it.isDirectory) return it }
        val dir = File(fm.root, fm.uniqueProjectName("My project"))
        dir.mkdirs()
        projectDir = dir
        return dir
    }

    fun refreshTitle() {
        val f = currentFile
        val p = projectDir
        fileNameView.text = if (f != null && p != null) fm.relativePath(p, f) else "Untitled"
        dirtyDot.visibility = if (dirty) View.VISIBLE else View.GONE
        editorView.setSaveStatus(if (dirty) "Unsaved" else "Saved")
    }

    // --------------------------------------------------------- saving
    fun markClean() { dirty = false; refreshTitle() }

    /** Runs [next] after unsaved work is dealt with (saved, or the person agreed to lose it). */
    fun guardUnsaved(next: () -> Unit) {
        if (!dirty) { next(); return }
        if (Prefs.autoSave && currentFile != null) { saveNow(silent = true) { next() }; return }
        WarningDialog.confirm(this, WarningDialog.DISCARD) { next() }
    }

    fun saveNow(silent: Boolean = false, then: (() -> Unit)? = null) {
        val f = currentFile
        if (f == null) {
            writeDraft()
            if (!silent) actions.saveAs()
            then?.invoke()
            return
        }
        val snapshot = editorView.text
        editorView.setSaveStatus("Saving...")
        Async.run({ fm.writeText(f, snapshot) }) { r ->
            r.onSuccess {
                if (editorView.text == snapshot) { dirty = false }
                refreshTitle()
                if (!silent) Ui.toast(this, "Saved")
                then?.invoke()
            }
            r.onFailure { e ->
                editorView.setSaveStatus("Not saved")
                Ui.toast(this, "Could not save: " + (e.message ?: ""))
            }
        }
    }

    private fun saveSync() {
        try {
            val f = currentFile
            if (f != null) { if (dirty && Prefs.autoSave) { fm.writeText(f, editorView.text); dirty = false } }
            else writeDraft()
        } catch (t: Throwable) { /* best effort while pausing */ }
    }

    private fun writeDraft() {
        try { StorageHelper.draftFile(this).writeText(editorView.text) } catch (t: Throwable) { }
    }

    // -------------------------------------------------------- editing
    private fun onEditorChanged() {
        if (!dirty) { dirty = true; refreshTitle() } else editorView.setSaveStatus("Unsaved")
        if (Prefs.livePreview && pane !is FlipLayoutView && hasRun) {
            Ui.cancel(liveRunnable)
            Ui.post(700L, liveRunnable)
        }
    }

    private fun onConsole(e: ConsoleEntry) {
        if (e.level == Level.ERROR && e.line > 0 && e.file != null && e.file == currentRel()) {
            runtimeLines.add(e.line)
            editorView.setErrorLines(lintLines + runtimeLines)
        }
    }

    private fun currentRel(): String? {
        val f = currentFile ?: return "__untitled__.html"
        val p = projectDir ?: return null
        return fm.relativePath(p, f)
    }

    private fun jumpTo(e: ConsoleEntry) {
        val file = e.file ?: return
        if (file == currentRel()) { pane.showEditor(); editorView.jumpToLine(e.line); return }
        val p = projectDir ?: return
        val target = fm.resolve(p, file)
        if (target != null && target.isFile) openFile(target, e.line)
    }

    // ------------------------------------------------------------ run
    private fun runPreview(fromRun: Boolean) {
        if (fromRun && Prefs.autoFormatOnRun && editorView.lang != Lang.PLAIN) {
            val t = editorView.text
            val out = CodeFormatter.format(t, editorView.lang)
            if (out != t) editorView.editor.replaceRange(0, t.length, out)
        }
        if (fromRun && dirty && Prefs.autoSave) saveNow(silent = true)
        val text = editorView.text
        val lang = editorView.lang
        val file = currentFile
        val proj = projectDir
        var entry: String? = null
        val label: String
        runtimeLines.clear()
        if (file != null && proj != null) {
            preview.setProjectRoot(proj)
            preview.clearOverrides()
            val rel = fm.relativePath(proj, file)
            preview.setOverride(rel, text)
            label = rel
            entry = if (lang == Lang.HTML) rel
            else if (File(proj, "index.html").isFile) "index.html"
            else fm.firstHtml(proj)?.let { fm.relativePath(proj, it) }
        } else {
            preview.setProjectRoot(null)
            preview.clearOverrides()
            preview.setOverride("__untitled__.html", text)
            label = "__untitled__.html"
            entry = "__untitled__.html"
        }
        hasRun = true
        if (fromRun) { hideKeyboard(); pane.showPreview() }
        if (entry == null) preview.showMessage(NO_PAGE) else preview.load(entry)
        if (fromRun && lang == Lang.HTML && entry == label) {
            val issues = HtmlLint.lint(text)
            preview.showLint(issues, label)
            lintLines = issues.map { it.line }.toSet()
        } else lintLines = emptySet()
        editorView.setErrorLines(lintLines)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editorView.editor.windowToken, 0)
    }

    // ----------------------------------------------------------- menu
    private fun showMenu(anchor: View) {
        val m = PopupMenu(this, anchor)
        val names = listOf(
            "New File", "New Project", "Open File / Project", "Sample Projects", "Save", "Save As",
            "Rename Current File", "Export", "Share Code...", "Settings", "Report Bug", "Donate", "About"
        )
        names.forEachIndexed { i, s -> m.menu.add(0, i, i, s) }
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                0 -> actions.newFile()
                1 -> actions.newProject()
                2 -> actions.openBrowser()
                3 -> actions.samples()
                4 -> saveNow()
                5 -> actions.saveAs()
                6 -> actions.renameCurrent()
                7 -> actions.export()
                8 -> actions.share()
                9 -> startActivity(Intent(this, SettingsActivity::class.java))
                10 -> com.htmlplayforge.app.utils.BugReport.send(this)
                11 -> com.htmlplayforge.app.utils.LinkOpener.open(this, com.htmlplayforge.app.utils.LinkOpener.DONATE)
                12 -> actions.about()
            }
            true
        }
        m.show()
    }

    // ------------------------------------------------------ lifecycle
    private fun handleBack() {
        val p = pane
        if (p is FlipLayoutView && p.isPreviewShown) { p.showEditor(); return }
        val needsWarning = dirty && (currentFile == null || !Prefs.autoSave)
        if (needsWarning) {
            WarningDialog.confirm(this, WarningDialog.EXIT, "Exit") { writeDraft(); finish() }
        } else { saveSync(); finish() }
    }

    override fun onResume() {
        super.onResume()
        preview.onResume()
        applyAll()
        Ui.cancel(autosaveRunnable)
        Ui.post(Prefs.autoSaveSeconds * 1000L, autosaveRunnable)
    }

    override fun onPause() {
        preview.onPause()
        Ui.cancel(autosaveRunnable)
        Ui.cancel(liveRunnable)
        saveSync()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val t = editorView.text
        if (t.length <= 150_000) { // never put more than 150,000 characters in the saved state
            outState.putString("text", t)
            outState.putString("file", currentFile?.absolutePath)
            outState.putBoolean("dirty", dirty)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyAll()
    }

    override fun onDestroy() {
        Ui.cancel(autosaveRunnable)
        Ui.cancel(liveRunnable)
        preview.destroy()
        super.onDestroy()
    }

    companion object {
        const val HELLO = "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n" +
            "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n  <title>Hello</title>\n" +
            "</head>\n<body>\n  <h1>Hello, World!</h1>\n  <p>Edit this page, then tap Run.</p>\n</body>\n</html>\n"

        const val NO_PAGE = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" " +
            "content=\"width=device-width, initial-scale=1\"><title>No page</title></head>" +
            "<body style=\"font-family:sans-serif;padding:24px;color:#333\"><h2>No HTML page yet</h2>" +
            "<p>Create a file named index.html with New File, then tap Run.</p></body></html>"
    }
}
