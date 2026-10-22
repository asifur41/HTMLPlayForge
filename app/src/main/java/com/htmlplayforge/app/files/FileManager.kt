package com.htmlplayforge.app.files

import java.io.File
import java.io.IOException

/** All file work for projects. No Android classes here. Call it from a background thread. */
class FileManager(val root: File) {
    init { root.mkdirs() }

    fun listProjects(): List<File> =
        (root.listFiles() ?: emptyArray()).filter { it.isDirectory }.sortedBy { it.name.lowercase() }

    fun createProject(rawName: String): File {
        val name = FileNames.forProject(rawName) ?: throw IOException("Invalid project name")
        val dir = File(root, name)
        if (dir.exists()) throw IOException("'$name' exists")
        if (!dir.mkdirs()) throw IOException("Could not create the folder")
        return dir
    }

    fun uniqueProjectName(base: String): String {
        val clean = FileNames.forProject(base) ?: "project"
        var name = clean
        var n = 2
        while (File(root, name).exists()) { name = "$clean $n"; n++ }
        return name
    }

    /** Joins a relative path to the project folder. Returns null if it would leave the folder. */
    fun resolve(project: File, rel: String): File? {
        val clean = FileNames.sanitizePath(rel) ?: return null
        val base = project.canonicalFile
        val f = File(base, clean).canonicalFile
        return if (f.path.startsWith(base.path + File.separator)) f else null
    }

    fun relativePath(project: File, f: File): String =
        f.canonicalFile.path.removePrefix(project.canonicalFile.path + File.separator).replace(File.separatorChar, '/')

    fun readText(f: File): String {
        if (f.length() > 5L * 1024 * 1024) throw IOException("File is too big to edit (over 5 MB)")
        return f.readText(Charsets.UTF_8)
    }

    fun writeText(f: File, text: String) {
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        try {
            java.nio.file.Files.move(
                tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        } catch (e: Exception) {
            // fallback: write straight into the file
            f.writeText(text, Charsets.UTF_8)
            tmp.delete()
        }
    }

    fun listDir(dir: File): List<File> =
        (dir.listFiles() ?: emptyArray())
            .filter { !it.name.startsWith(".") && !it.name.endsWith(".tmp") }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))

    fun delete(f: File) {
        if (f.isDirectory) f.listFiles()?.forEach { delete(it) }
        if (!f.delete() && f.exists()) throw IOException("Could not delete ${f.name}")
    }

    fun rename(f: File, rawName: String): File {
        val name = (if (f.isDirectory) FileNames.forProject(rawName) else FileNames.forNewFile(rawName))
            ?: throw IOException("Invalid name")
        val target = File(f.parentFile, name)
        if (target.exists() && target.canonicalPath != f.canonicalPath) throw IOException("'$name' exists")
        if (!f.renameTo(target)) throw IOException("Could not rename")
        return target
    }

    fun uniqueSibling(f: File): File {
        val base = f.name.substringBeforeLast('.', f.name)
        val ext = if (f.name.contains('.')) "." + f.name.substringAfterLast('.') else ""
        var n = 2
        var t = File(f.parentFile, "$base copy$ext")
        while (t.exists()) { t = File(f.parentFile, "$base copy $n$ext"); n++ }
        return t
    }

    fun duplicate(f: File): File {
        val t = uniqueSibling(f)
        if (f.isDirectory) copyDir(f, t) else f.copyTo(t)
        return t
    }

    fun copyDir(from: File, to: File) {
        to.mkdirs()
        from.listFiles()?.forEach { k ->
            val dest = File(to, k.name)
            if (k.isDirectory) copyDir(k, dest) else k.copyTo(dest, overwrite = true)
        }
    }

    /** Files whose name or text contains [query] (case-insensitive). Looks at text files up to 1 MB. */
    fun search(project: File, query: String, limit: Int = 100): List<File> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val found = ArrayList<File>()
        fun walk(d: File) {
            for (k in listDir(d)) {
                if (found.size >= limit) return
                if (k.isDirectory) { walk(k); continue }
                var hit = k.name.lowercase().contains(q)
                if (!hit && FileNames.isTextFile(k.name) && k.length() <= 1024 * 1024) {
                    hit = try { k.readText().lowercase().contains(q) } catch (e: Exception) { false }
                }
                if (hit) found.add(k)
            }
        }
        walk(project)
        return found
    }

    fun firstHtml(project: File): File? {
        val index = File(project, "index.html")
        if (index.isFile) return index
        fun walk(d: File): File? {
            for (k in listDir(d)) {
                if (k.isFile && (k.name.endsWith(".html", true) || k.name.endsWith(".htm", true))) return k
            }
            for (k in listDir(d)) if (k.isDirectory) walk(k)?.let { return it }
            return null
        }
        return walk(project)
    }

    companion object {
        fun starterText(fileName: String): String = when (FileNames.extension(fileName)) {
            "html", "htm" -> "<!DOCTYPE html>\n"
            "css" -> "/* Styles */\nbody {\n  \n}\n"
            "js" -> "// JavaScript\n"
            "json" -> "{\n  \n}\n"
            "svg" -> "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">\n  \n</svg>\n"
            else -> ""
        }
    }
}
