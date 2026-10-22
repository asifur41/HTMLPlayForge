package com.htmlplayforge.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File

/** Sharing through the system share sheet. Images go through FileProvider. */
object ShareHelper {
    fun shareText(context: Context, text: String, title: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        context.startActivity(Intent.createChooser(i, "Share code"))
    }

    /** Draws the first lines of the code on an image and shares it as PNG. Call from a background thread. */
    fun renderCodeImage(context: Context, code: String, bg: Int, fg: Int, typeface: Typeface): File {
        val maxLines = 80
        val lines = code.lines()
        val shown = lines.take(maxLines).joinToString("\n") + if (lines.size > maxLines) "\n..." else ""
        val width = 1080
        val pad = 40
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
        paint.typeface = typeface
        paint.textSize = 30f
        paint.color = fg
        val layout = StaticLayout.Builder.obtain(shown, 0, shown.length, paint, width - pad * 2)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        val bmp = Bitmap.createBitmap(width, layout.height + pad * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg)
        c.translate(pad.toFloat(), pad.toFloat())
        layout.draw(c)
        val dir = File(context.cacheDir, "share")
        dir.mkdirs()
        val f = File(dir, "code.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return f
    }

    fun shareImage(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(i, "Share image"))
    }
}
