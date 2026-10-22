package com.htmlplayforge.app.files

import android.content.Context
import java.io.File

/** Where the app keeps its own files. No storage permission is needed for these folders. */
object StorageHelper {
    fun projectsDir(context: Context): File {
        val base = try { context.getExternalFilesDir(null) } catch (e: Exception) { null } ?: context.filesDir
        val dir = File(base, "projects")
        if (!dir.exists() && !dir.mkdirs()) return File(context.filesDir, "projects").also { it.mkdirs() }
        return dir
    }

    fun draftFile(context: Context): File = File(context.filesDir, "draft.txt")

    fun fileManager(context: Context): FileManager = FileManager(projectsDir(context))
}
