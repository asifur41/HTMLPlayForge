package com.htmlplayforge.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.webkit.WebViewAssetLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.htmlplayforge.app.R
import com.htmlplayforge.app.editor.LintIssue
import com.htmlplayforge.app.preview.ConsoleEntry
import com.htmlplayforge.app.preview.ConsoleParser
import com.htmlplayforge.app.preview.Level
import com.htmlplayforge.app.preview.PreviewSecurity
import com.htmlplayforge.app.preview.ProjectPathHandler
import com.htmlplayforge.app.settings.AppTheme
import com.htmlplayforge.app.settings.ThemeManager
import com.htmlplayforge.app.utils.LinkOpener
import com.htmlplayforge.app.utils.Prefs
import com.htmlplayforge.app.utils.Ui
import java.io.ByteArrayInputStream
import java.io.File

/**
 * The preview: a locked-down WebView that shows the project through WebViewAssetLoader,
 * a row of device widths, and the console drawer.
 */
class PreviewView(private val activity: Activity) : LinearLayout(activity) {
    private val pathHandler = ProjectPathHandler()
    private val loader = WebViewAssetLoader.Builder()
        .setDomain(PreviewSecurity.HOST)
        .addPathHandler("/", pathHandler)
        .build()

    private val presetRow = LinearLayout(activity)
    private val webHost = FrameLayout(activity)
    val console = ConsoleView(activity)
    private var webView: WebView? = null
    private var lastUrl: String? = null
    private var theme: AppTheme = ThemeManager.current()
    private val presetChips = ArrayList<TextView>()
    private val presets = listOf(0 to "Fit", 360 to "360", 412 to "412", 768 to "768", 1024 to "1024")

    /** True while a page is loading (Run button shows Reload when false). */
    var onLoadingChanged: (Boolean) -> Unit = {}
    /** The person tapped a console entry that has a file. */
    var onEntryClick: (ConsoleEntry) -> Unit = {}
    /** Every console message, so the editor can mark error lines. */
    var onConsoleEntry: (ConsoleEntry) -> Unit = {}
    /** Copy was tapped. */
    var onCopy: () -> Unit = {}

    var isConsoleVisible: Boolean
        get() = console.visibility == View.VISIBLE
        set(v) { console.visibility = if (v) View.VISIBLE else View.GONE }

    init {
        orientation = VERTICAL
        buildPresetRow()
        addView(presetRow, LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(activity, 40)))
        addView(webHost, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(console, LayoutParams(LayoutParams.MATCH_PARENT, 0, 0.8f))
        console.onEntryClick = { onEntryClick(it) }
        isConsoleVisible = Prefs.showConsole
        createWebView()
        webHost.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyWidth() }
        applyTheme(theme)
    }

    // ------------------------------------------------------ toolbar
    private fun buildPresetRow() {
        presetRow.orientation = HORIZONTAL
        presetRow.gravity = Gravity.CENTER_VERTICAL
        val scroll = HorizontalScrollView(activity)
        scroll.isHorizontalScrollBarEnabled = false
        val chipRow = LinearLayout(activity)
        chipRow.orientation = HORIZONTAL
        chipRow.gravity = Gravity.CENTER_VERTICAL
        for ((w, label) in presets) {
            val chip = TextView(activity)
            chip.text = label
            chip.textSize = 13f
            val ph = Ui.dp(activity, 12)
            chip.setPadding(ph, Ui.dp(activity, 6), ph, Ui.dp(activity, 6))
            chip.setOnClickListener { Prefs.deviceWidth = w; applyWidth(); styleChips() }
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.marginStart = Ui.dp(activity, 6)
            chipRow.addView(chip, lp)
            presetChips.add(chip)
        }
        scroll.addView(chipRow)
        presetRow.addView(scroll, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        presetRow.addView(Ui.iconButton(activity, R.drawable.ic_copy, theme.text, "Copy page code") { onCopy() })
    }

    private fun styleChips() {
        for ((i, chip) in presetChips.withIndex()) {
            val selected = presets[i].first == Prefs.deviceWidth
            chip.setTextColor(if (selected) 0xFFFFFFFF.toInt() else theme.text)
            chip.background = Ui.roundBackground(if (selected) theme.accent else Ui.withAlpha(theme.text, 0x22), 16, activity)
        }
    }

    fun applyTheme(t: AppTheme) {
        theme = t
        setBackgroundColor(t.bg)
        presetRow.setBackgroundColor(t.bg)
        webHost.setBackgroundColor(if (t.dark) 0xFF111111.toInt() else 0xFFE0E0E0.toInt())
        console.applyTheme(t)
        styleChips()
        // icon tint of the copy button
        val copy = presetRow.getChildAt(presetRow.childCount - 1)
        if (copy is android.widget.ImageButton) copy.imageTintList = android.content.res.ColorStateList.valueOf(t.text)
    }

    private fun applyWidth() {
        val wv = webView ?: return
        val w = Prefs.deviceWidth
        val hostW = webHost.width
        val want = if (w <= 0 || hostW <= 0 || Ui.dp(activity, w) >= hostW) ViewGroup.LayoutParams.MATCH_PARENT else Ui.dp(activity, w)
        val lp = wv.layoutParams as? FrameLayout.LayoutParams
        if (lp != null && lp.width != want) {
            lp.width = want
            lp.gravity = Gravity.CENTER_HORIZONTAL
            wv.layoutParams = lp
        }
    }

    // ----------------------------------------------------- web view
    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        val wv = WebView(activity)
        wv.setBackgroundColor(Color.WHITE)
        val s = wv.settings
        s.javaScriptEnabled = Prefs.runJavaScript
        s.domStorageEnabled = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        @Suppress("DEPRECATION")
        s.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        s.allowUniversalAccessFromFileURLs = false
        s.setGeolocationEnabled(false)
        s.setSupportMultipleWindows(false)
        s.javaScriptCanOpenWindowsAutomatically = false
        s.mediaPlaybackRequiresUserGesture = true
        s.cacheMode = WebSettings.LOAD_NO_CACHE
        s.setSupportZoom(true)
        s.builtInZoomControls = false
        WebView.setWebContentsDebuggingEnabled(false)

        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val r = loader.shouldInterceptRequest(request.url)
                if (r != null) return r
                // anything that is not our own address: empty 403
                return WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (PreviewSecurity.isProjectUrl(url)) return false
                if (PreviewSecurity.isWebLink(url)) askOpenInBrowser(url)
                return true // never leave the project
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { onLoadingChanged(true) }
            override fun onPageFinished(view: WebView, url: String?) { onLoadingChanged(false) }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                post { recreateWebView() }
                return true
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                val level = when (m.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> Level.ERROR
                    ConsoleMessage.MessageLevel.WARNING -> Level.WARN
                    ConsoleMessage.MessageLevel.TIP -> Level.INFO
                    else -> Level.LOG
                }
                val entry = ConsoleParser.parse(level, m.message() ?: "", m.sourceId(), m.lineNumber())
                console.add(entry)
                onConsoleEntry(entry)
                return true
            }

            override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                return showDialog(message, null, result, null, false)
            }

            override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                return showDialog(message, null, result, null, true)
            }

            override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean {
                return showDialog(message, defaultValue ?: "", result, result, true)
            }
        }

        webHost.removeAllViews()
        webHost.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL))
        webView = wv
        applyWidth()
    }

    /** Shows alert, confirm or prompt as a Material dialog. Always answers the page. */
    private fun showDialog(message: String?, promptDefault: String?, result: JsResult, prompt: JsPromptResult?, hasCancel: Boolean): Boolean {
        if (activity.isFinishing) { result.cancel(); return true }
        try {
            val b = MaterialAlertDialogBuilder(activity).setMessage(message ?: "")
            val input: EditText? = if (promptDefault != null && prompt != null) EditText(activity) else null
            if (input != null) {
                input.setText(promptDefault)
                val box = LinearLayout(activity)
                val p = Ui.dp(activity, 20)
                box.setPadding(p, 0, p, 0)
                box.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                b.setView(box)
            }
            b.setPositiveButton("OK") { _, _ ->
                if (prompt != null && input != null) prompt.confirm(input.text.toString()) else result.confirm()
            }
            if (hasCancel) b.setNegativeButton("Cancel") { _, _ -> result.cancel() }
            b.setOnCancelListener { result.cancel() }
            b.show()
        } catch (t: Throwable) {
            result.cancel()
        }
        return true
    }

    private fun askOpenInBrowser(url: String) {
        if (!Prefs.askBeforeLink) { LinkOpener.open(activity, url); return }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Open in your browser?")
            .setMessage(url)
            .setPositiveButton("Open") { _, _ -> LinkOpener.open(activity, url) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun recreateWebView() {
        val old = webView
        webView = null
        try { old?.destroy() } catch (t: Throwable) { }
        createWebView()
        lastUrl?.let { webView?.loadUrl(it) }
    }

    // -------------------------------------------------------- public
    fun setProjectRoot(root: File?) { pathHandler.projectRoot = root }
    fun setOverride(path: String, text: String) { pathHandler.overrides[path] = text }
    fun clearOverrides() { pathHandler.overrides.clear() }

    /** Loads a page of the project. [path] is relative to the project folder. */
    fun load(path: String) {
        val url = "https://${PreviewSecurity.HOST}/" + Uri.encode(path, "/")
        lastUrl = url
        console.clear()
        applySettings()
        webView?.loadUrl(url)
    }

    fun showMessage(html: String) {
        pathHandler.overrides["__message__.html"] = html
        load("__message__.html")
    }

    fun reload() {
        val u = lastUrl
        if (u != null) { console.clear(); applySettings(); webView?.loadUrl(u) }
    }

    fun applySettings() {
        webView?.settings?.javaScriptEnabled = Prefs.runJavaScript
    }

    fun showLint(issues: List<LintIssue>, file: String?) {
        console.addLint(issues, file)
    }

    fun clearPreviewData() {
        try {
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (t: Throwable) { }
    }

    fun onPause() { webView?.onPause(); webView?.pauseTimers() }
    fun onResume() { webView?.onResume(); webView?.resumeTimers() }

    fun destroy() {
        val old = webView
        webView = null
        try {
            webHost.removeAllViews()
            old?.stopLoading()
            old?.destroy()
        } catch (t: Throwable) { }
    }
}
