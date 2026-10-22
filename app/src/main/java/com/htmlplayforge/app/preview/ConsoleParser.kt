package com.htmlplayforge.app.preview

enum class Level { LOG, WARN, ERROR, INFO, LINT }

/** One line in the console. [file] is relative to the project, [line] is 1-based (0 = unknown). */
data class ConsoleEntry(
    val level: Level,
    val message: String,
    val file: String?,
    val line: Int,
    val hint: String?
)

object ConsoleParser {
    const val ORIGIN = "https://appassets.androidplatform.net/"

    /** Turns a source URL from the WebView into a project file name. */
    fun fileFromSource(sourceId: String?): String? {
        if (sourceId.isNullOrEmpty()) return null
        if (!sourceId.startsWith(ORIGIN)) return null
        var p = sourceId.substring(ORIGIN.length)
        val q = p.indexOfFirst { it == '?' || it == '#' }
        if (q >= 0) p = p.substring(0, q)
        p = try { java.net.URLDecoder.decode(p.replace("+", "%2B"), "UTF-8") } catch (e: Exception) { p }
        return if (p.isEmpty()) null else p
    }

    fun parse(level: Level, message: String, sourceId: String?, line: Int): ConsoleEntry {
        val hint = if (level == Level.ERROR || level == Level.WARN) ErrorHints.hintFor(message) else null
        return ConsoleEntry(level, message, fileFromSource(sourceId), if (line > 0) line else 0, hint)
    }
}
