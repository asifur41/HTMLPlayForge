package com.htmlplayforge.app.files

/** Rules for file names. Pure Kotlin. Allowed characters: A-Z a-z 0-9 . _ - and space. */
object FileNames {
    const val MAX_LENGTH = 80
    private val BAD = Regex("[^A-Za-z0-9._ -]")

    val TEXT_EXT = setOf("html", "htm", "css", "js", "json", "svg", "txt", "md")
    val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "webp", "ico")

    fun extension(name: String): String {
        val i = name.lastIndexOf('.')
        return if (i < 0 || i == name.length - 1) "" else name.substring(i + 1).lowercase()
    }

    fun isTextFile(name: String) = extension(name) in TEXT_EXT
    fun isImageFile(name: String) = extension(name) in IMAGE_EXT

    /** One path part: bad characters become "_", leading dots are removed. Can return "". */
    fun sanitizeSegment(raw: String): String {
        var s = BAD.replace(raw.trim(), "_").trim()
        s = s.trimStart('.')
        if (s.length > MAX_LENGTH) s = s.substring(0, MAX_LENGTH)
        return s.trim()
    }

    /**
     * Cleans a relative path such as "css/style.css". Returns null if it is empty,
     * absolute, or tries to leave the folder with "..".
     */
    fun sanitizePath(raw: String): String? {
        val parts = raw.replace('\\', '/').split('/')
        val clean = ArrayList<String>()
        for (p in parts) {
            if (p.isEmpty() || p == ".") continue
            if (p == "..") return null
            val s = sanitizeSegment(p)
            if (s.isEmpty()) return null
            clean.add(s)
        }
        return if (clean.isEmpty()) null else clean.joinToString("/")
    }

    /** A single file name (no folders). Adds ".html" when there is no extension. */
    fun forNewFile(raw: String): String? {
        val s = sanitizeSegment(raw.replace('/', '_').replace('\\', '_'))
        if (s.isEmpty()) return null
        return if (s.contains('.')) s else "$s.html"
    }

    fun forProject(raw: String): String? {
        val s = sanitizeSegment(raw.replace('/', '_').replace('\\', '_'))
        return if (s.isEmpty()) null else s
    }
}
