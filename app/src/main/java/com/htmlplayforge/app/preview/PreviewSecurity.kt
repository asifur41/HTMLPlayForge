package com.htmlplayforge.app.preview

/** Pure rules for the preview. The WebView code in ui/ uses these. */
object PreviewSecurity {
    const val HOST = "appassets.androidplatform.net"

    /** Only same-origin files plus data: and blob: are allowed. Inline code is allowed so normal pages work. */
    const val CSP = "default-src 'self' data: blob:; " +
        "script-src 'self' 'unsafe-inline' 'unsafe-eval' blob:; " +
        "style-src 'self' 'unsafe-inline'; " +
        "img-src 'self' data: blob:; font-src 'self' data:; media-src 'self' data: blob:; " +
        "connect-src 'self' data: blob:; frame-src 'self' data: blob:; " +
        "object-src 'none'; base-uri 'self'; form-action 'self'"

    fun responseHeaders(): Map<String, String> = mapOf(
        "Content-Security-Policy" to CSP,
        "Cache-Control" to "no-store",
        "X-Content-Type-Options" to "nosniff"
    )

    /** True only for https://appassets.androidplatform.net/... */
    fun isProjectUrl(url: String?): Boolean =
        url != null && url.startsWith("https://$HOST/")

    fun isWebLink(url: String?): Boolean =
        url != null && (url.startsWith("http://") || url.startsWith("https://")) && !isProjectUrl(url)

    fun mimeFor(fileName: String): String {
        val n = fileName.lowercase()
        return when {
            n.endsWith(".html") || n.endsWith(".htm") -> "text/html"
            n.endsWith(".css") -> "text/css"
            n.endsWith(".js") || n.endsWith(".mjs") -> "text/javascript"
            n.endsWith(".json") -> "application/json"
            n.endsWith(".svg") -> "image/svg+xml"
            n.endsWith(".png") -> "image/png"
            n.endsWith(".jpg") || n.endsWith(".jpeg") -> "image/jpeg"
            n.endsWith(".gif") -> "image/gif"
            n.endsWith(".webp") -> "image/webp"
            n.endsWith(".ico") -> "image/x-icon"
            n.endsWith(".txt") || n.endsWith(".md") -> "text/plain"
            n.endsWith(".woff2") -> "font/woff2"
            n.endsWith(".woff") -> "font/woff"
            n.endsWith(".mp3") -> "audio/mpeg"
            n.endsWith(".mp4") -> "video/mp4"
            else -> "application/octet-stream"
        }
    }

    /** Makes a request path safe: no "..", no leading slash, no backslashes. Returns null if unsafe. */
    fun cleanPath(raw: String): String? {
        var p = raw.replace('\\', '/')
        while (p.startsWith("/")) p = p.substring(1)
        if (p.isEmpty()) return null
        val parts = p.split('/')
        if (parts.any { it == ".." }) return null
        return parts.filter { it.isNotEmpty() && it != "." }.joinToString("/").ifEmpty { null }
    }
}
