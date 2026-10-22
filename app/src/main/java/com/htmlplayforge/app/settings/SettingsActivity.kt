package com.htmlplayforge.app.settings

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.htmlplayforge.app.R
import com.htmlplayforge.app.ui.OnboardingActivity
import com.htmlplayforge.app.utils.BugReport
import com.htmlplayforge.app.utils.LinkOpener
import com.htmlplayforge.app.utils.Prefs
import com.htmlplayforge.app.utils.Ui

class SettingsActivity : AppCompatActivity() {
    private lateinit var adapter: SettingsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = ThemeManager.current()
        ThemeManager.styleWindow(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(t.bg)
        Ui.applyInsets(root)

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(t.editorBg)
        bar.addView(Ui.iconButton(this, R.drawable.ic_back, t.text, "Back") { finish() })
        val title = TextView(this)
        title.text = "Settings"
        title.textSize = 18f
        title.setTextColor(t.text)
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)))

        val list = RecyclerView(this)
        list.layoutManager = LinearLayoutManager(this)
        adapter = SettingsAdapter(buildRows(), t)
        list.adapter = adapter
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun refresh() { adapter.update(buildRows(), ThemeManager.current()) }

    private fun pick(title: String, labels: List<String>, current: Int, onPick: (Int) -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), current) { d, which -> onPick(which); d.dismiss() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildRows(): List<Row> {
        val rows = ArrayList<Row>()
        rows.add(Row.Header("Appearance"))
        rows.add(Row.Segmented("Layout mode", listOf("Split" to LayoutModeManager.SPLIT, "Flip" to LayoutModeManager.FLIP),
            { Prefs.layoutMode }, { LayoutModeManager.set(it) }))
        rows.add(Row.Choice("Theme", { ThemeManager.current().name }) {
            val all = ThemeManager.all
            pick("Theme", all.map { it.name }, all.indexOfFirst { it.id == Prefs.theme }) { i ->
                Prefs.theme = all[i].id
                ThemeManager.applyNightMode()
                recreate()
            }
        })
        rows.add(Row.Choice("Font family", { FontManager.currentLabel() }) {
            val all = FontManager.all
            pick("Font family", all.map { it.label }, all.indexOfFirst { it.id == Prefs.font }) { i ->
                Prefs.font = all[i].id
                refresh()
            }
        })
        rows.add(Row.SliderRow("Font size", 12, 28, { Prefs.fontSize }, { Prefs.fontSize = it }))

        rows.add(Row.Header("Editor"))
        rows.add(Row.Toggle("Syntax highlighting", "Colour tags, strings and comments", { Prefs.highlighting }, { Prefs.highlighting = it }))
        rows.add(Row.Toggle("Auto-format on Run", "Tidy the code before every Run", { Prefs.autoFormatOnRun }, { Prefs.autoFormatOnRun = it }))
        rows.add(Row.Toggle("Line numbers", "Show numbers next to the code", { Prefs.lineNumbers }, { Prefs.lineNumbers = it }))
        rows.add(Row.Toggle("Symbol panel", "Keys for < > { } and more", { Prefs.symbolPanel }, { Prefs.symbolPanel = it }))
        rows.add(Row.Toggle("Word wrap", "Long lines continue on the next line", { Prefs.wordWrap }, { Prefs.wordWrap = it }))
        rows.add(Row.Toggle("Auto-close tags", "Typing > adds the closing tag", { Prefs.autoCloseTags }, { Prefs.autoCloseTags = it }))
        rows.add(Row.Toggle("Auto-close brackets", "Add the closing bracket or quote", { Prefs.autoCloseBrackets }, { Prefs.autoCloseBrackets = it }))

        rows.add(Row.Header("Preview"))
        rows.add(Row.Toggle("Live preview", "Refresh shortly after you stop typing", { Prefs.livePreview }, { Prefs.livePreview = it }))
        rows.add(Row.Toggle("Run JavaScript", "Turn off to block scripts in the preview", { Prefs.runJavaScript }, { Prefs.runJavaScript = it }))
        rows.add(Row.Toggle("Show console", "Show messages and errors under the preview", { Prefs.showConsole }, { Prefs.showConsole = it }))
        rows.add(Row.Choice("Default device width", { if (Prefs.deviceWidth == 0) "Fit" else "${Prefs.deviceWidth} dp" }) {
            val values = listOf(0, 360, 412, 768, 1024)
            pick("Device width", values.map { if (it == 0) "Fit" else "$it dp" }, values.indexOf(Prefs.deviceWidth)) { i ->
                Prefs.deviceWidth = values[i]; refresh()
            }
        })
        rows.add(Row.Link("Clear preview data", "Delete saved data of your pages (localStorage, cookies)") { clearPreviewData() })
        rows.add(Row.Toggle("Ask before opening a link", "Ask first when a page link opens the browser", { Prefs.askBeforeLink }, { Prefs.askBeforeLink = it }))

        rows.add(Row.Header("Auto-save"))
        rows.add(Row.Toggle("Auto-save", "Save the open file while you type", { Prefs.autoSave }, { Prefs.autoSave = it }))
        rows.add(Row.Choice("Auto-save interval", { "${Prefs.autoSaveSeconds} seconds" }) {
            val values = listOf(3, 5, 10)
            pick("Interval", values.map { "$it seconds" }, values.indexOf(Prefs.autoSaveSeconds)) { i ->
                Prefs.autoSaveSeconds = values[i]; refresh()
            }
        })

        rows.add(Row.Header("Developer"))
        rows.add(Row.Info("Name", LinkOpener.DEVELOPER_NAME))
        rows.add(Row.Link("Email", LinkOpener.EMAIL) { LinkOpener.email(this, "HTMLPlayForge", "") })
        rows.add(Row.Info("Country", LinkOpener.COUNTRY))
        rows.add(Row.Link("Website", "Open the developer website") { LinkOpener.open(this, LinkOpener.WEBSITE) })

        rows.add(Row.Header("Support"))
        rows.add(Row.Link("Join Community", "Meet other users") { LinkOpener.open(this, LinkOpener.COMMUNITY) })
        rows.add(Row.Link("Download the latest APK", "Get the newest version") { LinkOpener.open(this, LinkOpener.APK_DOWNLOAD) })
        rows.add(Row.Link("Visit GitHub releases", "See all versions") { LinkOpener.open(this, LinkOpener.RELEASES) })
        rows.add(Row.Link("Leave a review", "Tell others what you think") { LinkOpener.open(this, LinkOpener.REVIEW) })
        rows.add(Row.Link("Report a Bug", "Send an email with details") { BugReport.send(this) })
        rows.add(Row.Link("Donate", "Support the work") { LinkOpener.open(this, LinkOpener.DONATE) })
        rows.add(Row.Link("Replay tutorial", "Show the intro screens again") {
            startActivity(android.content.Intent(this, OnboardingActivity::class.java))
        })

        rows.add(Row.Header("Version"))
        rows.add(Row.Info("HTMLPlayForge ${BugReport.appVersion(this)}", "${BugReport.androidVersion()}, WebView ${BugReport.webViewVersion()}"))
        return rows
    }

    private fun clearPreviewData() {
        try {
            android.webkit.WebStorage.getInstance().deleteAllData()
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
            Ui.toast(this, "Preview data cleared")
        } catch (t: Throwable) {
            Ui.toast(this, "Could not clear preview data")
        }
    }
}
