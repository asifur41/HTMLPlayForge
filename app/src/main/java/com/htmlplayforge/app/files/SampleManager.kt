package com.htmlplayforge.app.files

import android.content.Context
import java.io.File

/** The sample projects shipped inside the app (assets/samples). */
object SampleManager {
    data class Sample(val folder: String, val title: String)

    fun list(context: Context): List<Sample> {
        return try {
            context.assets.open("samples/index.txt").bufferedReader().readLines()
                .filter { it.contains('|') }
                .map { Sample(it.substringBefore('|').trim(), it.substringAfter('|').trim()) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Copies the sample into [dest] (replacing files with the same names). */
    fun install(context: Context, folder: String, dest: File) {
        copyAssetDir(context, "samples/$folder", dest)
    }

    private fun copyAssetDir(context: Context, path: String, dest: File) {
        val names = context.assets.list(path) ?: emptyArray()
        if (names.isEmpty()) {
            dest.parentFile?.mkdirs()
            context.assets.open(path).use { ins -> dest.outputStream().use { ins.copyTo(it) } }
            return
        }
        dest.mkdirs()
        for (n in names) copyAssetDir(context, "$path/$n", File(dest, n))
    }
}
