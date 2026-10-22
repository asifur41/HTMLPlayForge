package com.htmlplayforge.app.settings

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.htmlplayforge.app.utils.Prefs

data class FontChoice(val id: String, val label: String)

/** Fonts are bundled in res/font by the build. If one is missing, plain monospace is used. */
object FontManager {
    val all = listOf(
        FontChoice("roboto_mono", "Roboto Mono"),
        FontChoice("fira_code", "Fira Code"),
        FontChoice("jetbrains_mono", "JetBrains Mono"),
        FontChoice("courier_prime", "Courier Prime")
    )

    fun currentLabel(): String = all.firstOrNull { it.id == Prefs.font }?.label ?: all[0].label

    fun typeface(context: Context): Typeface {
        try {
            val id = context.resources.getIdentifier(Prefs.font, "font", context.packageName)
            if (id != 0) {
                val tf = ResourcesCompat.getFont(context, id)
                if (tf != null) return tf
            }
        } catch (t: Throwable) {
            // fall through to the system font
        }
        return Typeface.MONOSPACE
    }
}
