package com.htmlplayforge.app.files

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FilesLogicTest {
    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((n, c) in entries) { z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() }
        }
        return bos.toByteArray()
    }

    private fun tmp(): File = Files.createTempDirectory("hpf").toFile()

    @Test fun namesAreSanitized() {
        assertFalse(FileNames.sanitizeSegment("../a b.html").contains("/"))
        assertFalse(FileNames.sanitizeSegment("..hidden").startsWith("."))
        assertEquals("css/style.css", FileNames.sanitizePath("css/style.css"))
        assertNull(FileNames.sanitizePath("../x"))
        assertEquals("page.html", FileNames.forNewFile("page"))
        assertEquals("a_b.css", FileNames.forNewFile("a/b.css"))
        assertNull(FileNames.forNewFile("   "))
        assertEquals("a_b", FileNames.sanitizeSegment("a?b"))
        assertTrue(FileNames.isImageFile("x.PNG"))
        assertTrue(FileNames.isTextFile("x.md"))
    }

    @Test fun zipRoundTrip() {
        val src = tmp(); File(src, "sub").mkdirs()
        File(src, "index.html").writeText("<p>hi</p>")
        File(src, "sub/a.css").writeText("p{}")
        val bos = ByteArrayOutputStream()
        ZipTools.zipDirectory(src, bos)
        val dst = tmp()
        assertEquals(2, ZipTools.unzipSafely(ByteArrayInputStream(bos.toByteArray()), dst))
        assertEquals("p{}", File(dst, "sub/a.css").readText())
    }

    @Test fun zipSlipIsRejected() {
        for (bad in listOf("../evil.txt", "a/../../evil.txt", "/abs.txt", "C:/x.txt")) {
            try {
                ZipTools.unzipSafely(ByteArrayInputStream(zipOf(Pair(bad, "x"))), tmp())
                fail("should reject $bad")
            } catch (e: ZipProblem) { /* good */ }
        }
    }

    @Test fun zipLimitsAreEnforced() {
        try {
            ZipTools.unzipSafely(ByteArrayInputStream(zipOf(Pair("a.txt", "x"), Pair("b.txt", "y"))), tmp(), maxEntries = 1)
            fail("too many entries")
        } catch (e: ZipProblem) { }
        try {
            ZipTools.unzipSafely(ByteArrayInputStream(zipOf(Pair("a.txt", "x".repeat(100)))), tmp(), maxTotal = 10)
            fail("too big")
        } catch (e: ZipProblem) { }
    }

    @Test fun hiddenEntriesAreSkipped() {
        val dst = tmp()
        val n = ZipTools.unzipSafely(ByteArrayInputStream(zipOf(Pair(".git/x", "1"), Pair("__MACOSX/y", "2"), Pair("ok.txt", "3"))), dst)
        assertEquals(1, n)
        assertTrue(File(dst, "ok.txt").isFile)
    }

    @Test fun inlinerCopiesLocalFilesOnly() {
        val html = "<link rel=\"stylesheet\" href=\"s.css\"><link rel=\"stylesheet\" href=\"https://x.com/a.css\">" +
            "<script src=\"a.js\"></script><script src=\"https://x.com/b.js\"></script>"
        val out = HtmlInliner.inline(html) { p -> mapOf("s.css" to "p{}", "a.js" to "var a=1;")[p] }
        assertTrue(out.contains("<style>\np{}\n</style>"))
        assertTrue(out.contains("<script>\nvar a=1;\n</script>"))
        assertTrue(out.contains("https://x.com/a.css"))
        assertTrue(out.contains("https://x.com/b.js"))
    }

    @Test fun fileManagerBasics() {
        val fm = FileManager(tmp())
        val p = fm.createProject("My site")
        val f = File(p, "index.html")
        fm.writeText(f, "<h1>Hello</h1>")
        assertEquals("<h1>Hello</h1>", fm.readText(f))
        assertNull(fm.resolve(p, "../outside.txt"))
        assertEquals("index.html", fm.relativePath(p, f))
        assertEquals(f.canonicalPath, fm.firstHtml(p)!!.canonicalPath)
        assertEquals(1, fm.search(p, "hello").size)
        val copy = fm.duplicate(f)
        assertTrue(copy.name.contains("copy"))
        val r = fm.rename(copy, "other")
        assertEquals("other.html", r.name)
        fm.delete(r)
        assertFalse(r.exists())
        try { fm.createProject("My site"); fail("exists") } catch (e: java.io.IOException) { }
        assertEquals("<!DOCTYPE html>\n", FileManager.starterText("a.html"))
    }
}
