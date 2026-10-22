package com.htmlplayforge.app.files

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipProblem(message: String) : IOException(message)

/** ZIP export and a safe ZIP import (no zip-slip, no zip bombs). Pure Kotlin/Java. */
object ZipTools {
    const val MAX_ENTRIES = 2000
    const val MAX_TOTAL_BYTES = 50L * 1024 * 1024

    /** Writes every file under [dir] into a ZIP. Paths inside the ZIP are relative. Closes [out]. */
    fun zipDirectory(dir: File, out: OutputStream) {
        ZipOutputStream(out).use { zos ->
            fun walk(f: File, prefix: String) {
                val kids = f.listFiles() ?: return
                kids.sortedBy { it.name }.forEach { k ->
                    val name = prefix + k.name
                    if (k.isDirectory) walk(k, "$name/")
                    else {
                        zos.putNextEntry(ZipEntry(name))
                        k.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            walk(dir, "")
        }
    }

    /**
     * Unpacks into [dest]. Throws [ZipProblem] for unsafe or oversized ZIPs.
     * Returns the number of files written.
     */
    fun unzipSafely(
        input: InputStream,
        dest: File,
        maxEntries: Int = MAX_ENTRIES,
        maxTotal: Long = MAX_TOTAL_BYTES
    ): Int {
        dest.mkdirs()
        val root = dest.canonicalFile
        var entries = 0
        var written = 0
        var total = 0L
        ZipInputStream(input).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                entries++
                if (entries > maxEntries) throw ZipProblem("The ZIP has too many files.")
                val raw = e.name.replace('\\', '/')
                if (raw.startsWith("/") || raw.contains(':')) throw ZipProblem("The ZIP has an unsafe path: $raw")
                val segs = raw.split('/').filter { it.isNotEmpty() }
                if (segs.any { it == ".." }) throw ZipProblem("The ZIP has an unsafe path: $raw")
                // skip hidden and system entries quietly
                if (segs.isEmpty() || segs.any { it.startsWith(".") || it == "__MACOSX" }) continue
                val clean = FileNames.sanitizePath(raw) ?: continue
                val target = File(root, clean).canonicalFile
                if (!target.path.startsWith(root.path + File.separator)) throw ZipProblem("The ZIP has an unsafe path: $raw")
                if (e.isDirectory) { target.mkdirs(); continue }
                target.parentFile?.mkdirs()
                target.outputStream().use { os ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val r = zis.read(buf)
                        if (r < 0) break
                        total += r
                        if (total > maxTotal) throw ZipProblem("The ZIP is too big when unpacked.")
                        os.write(buf, 0, r)
                    }
                }
                written++
            }
        }
        return written
    }
}
