package com.htmlplayforge.app

import android.app.Application
import com.htmlplayforge.app.settings.ThemeManager
import com.htmlplayforge.app.utils.CrashLog
import com.htmlplayforge.app.utils.Prefs

class HtmlPlayForgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        CrashLog.install(this)
        ThemeManager.applyNightMode()
    }
}
