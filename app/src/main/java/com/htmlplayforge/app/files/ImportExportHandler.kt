package com.htmlplayforge.app.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

/** Reads and writes through the system file picker. Call from a background thread. */
object ImportExportHandler {
    const val MAX_ASSET_BYTES = 10L * 1024 * 1024

    fun displayName(cr: ContentResolver, uri: Uri): String {
        try {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) c.getString(i)?.let { return it }
                }
            }
        } catch (e: Exception) { /* fall back below */ }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    fun writeText(cr: ContentResolver, uri: Uri, text: String) {
        val os = cr.openOutputStream(uri, "wt") ?: throw IOException("Could not write the file")
        os.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    fun writeZip(cr: ContentResolver, uri: Uri, dir: File) {
        val os = cr.openOutputStream(uri, "wt") ?: throw IOException("Could not write the file")
        ZipTools.zipDirectory(dir, os)
    }

    /** Copies a picked file into [dest]. Fails for files bigger than [maxBytes]. */
    fun copyToFile(cr: ContentResolver, uri: Uri, dest: File, maxBytes: Long = MAX_ASSET_BYTES) {
        val input = cr.openInputStream(uri) ?: throw IOException("Could not read the file")
        dest.parentFile?.mkdirs()
        var total = 0L
        try {
            input.use { ins ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val r = ins.read(buf)
                        if (r < 0) break
                        total += r
                        if (total > maxBytes) throw IOException("The file is bigger than ${maxBytes / (1024 * 1024)} MB")
                        out.write(buf, 0, r)
                    }
                }
            }
        } catch (e: IOException) {
            dest.delete()
            throw e
        }
    }

    /** Unpacks a ZIP into a new project folder. Returns the folder. */
    fun importZip(cr: ContentResolver, uri: Uri, fm: FileManager, zipName: String): File {
        val base = zipName.substringBeforeLast('.', zipName)
        val dir = File(fm.root, fm.uniqueProjectName(base))
        val input = cr.openInputStream(uri) ?: throw IOException("Could not read the file")
        try {
            ZipTools.unzipSafely(input, dir)
        } catch (e: Exception) {
            fm.delete(dir)
            throw e
        }
        return dir
    }

    /** One HTML text with local CSS and JS files copied inside. [overrides] holds unsaved text by path. */
    fun singleHtml(html: String, project: File?, fm: FileManager, overrides: Map<String, String>): String =
        HtmlInliner.inline(html) { path ->
            overrides[path] ?: run {
                val f = if (project != null) fm.resolve(project, path) else null
                if (f != null && f.isFile) try { fm.readText(f) } catch (e: Exception) { null } else null
            }
        }
}
