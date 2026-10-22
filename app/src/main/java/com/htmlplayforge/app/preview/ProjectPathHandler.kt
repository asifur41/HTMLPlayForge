package com.htmlplayforge.app.preview

import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Serves the project's files to the preview. Text in [overrides] (the editor's unsaved text)
 * wins over the file on disk. Files outside the project folder are never served.
 */
class ProjectPathHandler : WebViewAssetLoader.PathHandler {
    @Volatile
    var projectRoot: File? = null
    val overrides = ConcurrentHashMap<String, String>()

    override fun handle(path: String): WebResourceResponse? {
        val clean = PreviewSecurity.cleanPath(path) ?: return notFound()
        val text = overrides[clean]
        if (text == null && clean == "favicon.ico" && !hasFile(clean)) return noContent()
        if (text != null) return respond(clean, ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        val root = projectRoot ?: return notFound()
        return try {
            val base = root.canonicalFile
            val f = File(base, clean).canonicalFile
            if (!f.path.startsWith(base.path + File.separator) || !f.isFile) notFound()
            else respond(clean, FileInputStream(f))
        } catch (e: Exception) {
            notFound()
        }
    }

    private fun hasFile(clean: String): Boolean {
        val root = projectRoot ?: return false
        return try { File(root.canonicalFile, clean).isFile } catch (e: Exception) { false }
    }

    /** The browser asks for /favicon.ico by itself. Answer "nothing here" without an error. */
    private fun noContent(): WebResourceResponse =
        WebResourceResponse("image/x-icon", null, 204, "No Content", PreviewSecurity.responseHeaders(), ByteArrayInputStream(ByteArray(0)))

    private fun respond(name: String, stream: java.io.InputStream): WebResourceResponse {
        val mime = PreviewSecurity.mimeFor(name)
        val enc = if (mime.startsWith("text/") || mime == "application/json" || mime == "image/svg+xml") "utf-8" else null
        return WebResourceResponse(mime, enc, 200, "OK", PreviewSecurity.responseHeaders(), stream)
    }

    private fun notFound(): WebResourceResponse =
        WebResourceResponse(
            "text/plain", "utf-8", 404, "Not Found",
            PreviewSecurity.responseHeaders(), ByteArrayInputStream("Not found".toByteArray())
        )
}
