package com.htmlplayforge.app.utils

import android.content.Context
import android.content.SharedPreferences

/** All settings in one place. Call Prefs.init(context) once (done in HtmlPlayForgeApp). */
object Prefs {
    private var sp: SharedPreferences? = null

    fun init(context: Context) {
        if (sp == null) sp = context.applicationContext.getSharedPreferences("htmlplayforge_prefs", Context.MODE_PRIVATE)
    }

    private fun p(): SharedPreferences = sp ?: throw IllegalStateException("Prefs.init was not called")

    fun bool(key: String, def: Boolean): Boolean = p().getBoolean(key, def)
    fun setBool(key: String, v: Boolean) { p().edit().putBoolean(key, v).apply() }
    fun int(key: String, def: Int): Int = try { p().getInt(key, def) } catch (e: ClassCastException) { def }
    fun setInt(key: String, v: Int) { p().edit().putInt(key, v).apply() }
    fun float(key: String, def: Float): Float = try { p().getFloat(key, def) } catch (e: ClassCastException) { def }
    fun setFloat(key: String, v: Float) { p().edit().putFloat(key, v).apply() }
    fun string(key: String, def: String): String = p().getString(key, def) ?: def
    fun setString(key: String, v: String) { p().edit().putString(key, v).apply() }

    // Appearance
    var layoutMode: String
        get() = string("layout_mode", "split")
        set(v) = setString("layout_mode", v)
    var theme: String
        get() = string("theme", "night")
        set(v) = setString("theme", v)
    var font: String
        get() = string("font", "roboto_mono")
        set(v) = setString("font", v)
    /** Always a whole number between 12 and 28 (lesson: never save fractions). */
    var fontSize: Int
        get() = int("font_size", 16).coerceIn(12, 28)
        set(v) = setInt("font_size", v.coerceIn(12, 28))

    // Editor
    var highlighting: Boolean
        get() = bool("highlighting", true)
        set(v) = setBool("highlighting", v)
    var autoFormatOnRun: Boolean
        get() = bool("auto_format_run", false)
        set(v) = setBool("auto_format_run", v)
    var lineNumbers: Boolean
        get() = bool("line_numbers", true)
        set(v) = setBool("line_numbers", v)
    var symbolPanel: Boolean
        get() = bool("symbol_panel", true)
        set(v) = setBool("symbol_panel", v)
    var wordWrap: Boolean
        get() = bool("word_wrap", true)
        set(v) = setBool("word_wrap", v)
    var autoCloseTags: Boolean
        get() = bool("auto_close_tags", true)
        set(v) = setBool("auto_close_tags", v)
    var autoCloseBrackets: Boolean
        get() = bool("auto_close_brackets", true)
        set(v) = setBool("auto_close_brackets", v)

    // Preview
    var livePreview: Boolean
        get() = bool("live_preview", true)
        set(v) = setBool("live_preview", v)
    var runJavaScript: Boolean
        get() = bool("run_js", true)
        set(v) = setBool("run_js", v)
    var showConsole: Boolean
        get() = bool("show_console", true)
        set(v) = setBool("show_console", v)
    /** 0 means "Fit". */
    var deviceWidth: Int
        get() = int("device_width", 0)
        set(v) = setInt("device_width", v)
    var askBeforeLink: Boolean
        get() = bool("ask_link", true)
        set(v) = setBool("ask_link", v)

    // Auto-save
    var autoSave: Boolean
        get() = bool("auto_save", true)
        set(v) = setBool("auto_save", v)
    var autoSaveSeconds: Int
        get() = int("auto_save_sec", 5)
        set(v) = setInt("auto_save_sec", v)

    // State
    var splitRatio: Float
        get() = float("split_ratio", 0.5f).coerceIn(0.15f, 0.85f)
        set(v) = setFloat("split_ratio", v.coerceIn(0.15f, 0.85f))
    var onboardingDone: Boolean
        get() = bool("onboarding_done", false)
        set(v) = setBool("onboarding_done", v)
    var firstLaunchDone: Boolean
        get() = bool("first_launch_done", false)
        set(v) = setBool("first_launch_done", v)
    var lastProject: String
        get() = string("last_project", "")
        set(v) = setString("last_project", v)
    var lastFile: String
        get() = string("last_file", "")
        set(v) = setString("last_file", v)
}
