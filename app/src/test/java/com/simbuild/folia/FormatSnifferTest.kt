package com.simbuild.folia

import com.simbuild.folia.engine.FormatRegistry
import com.simbuild.folia.engine.SupportLevel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FormatSnifferTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testPdfMagicBytesSniffedCorrectly() {
        val file = tempFolder.newFile("document_without_extension")
        FileOutputStream(file).use {
            it.write("%PDF-1.7\n%Fake PDF content for test".toByteArray())
        }
        val info = FormatRegistry.sniffFormat(file, null, file.name)
        assertEquals("pdf", info.extension)
        assertEquals(SupportLevel.NATIVE_RENDERING, info.supportLevel)
    }

    @Test
    fun testRtfMagicBytesSniffedCorrectly() {
        val file = tempFolder.newFile("test_rtf")
        FileOutputStream(file).use {
            it.write("{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0\\fnil\\fcharset0 Arial;}}Hello}".toByteArray())
        }
        val info = FormatRegistry.sniffFormat(file, null, file.name)
        assertEquals("rtf", info.extension)
    }

    @Test
    fun testPngMagicBytesSniffedCorrectly() {
        val file = tempFolder.newFile("test_png")
        FileOutputStream(file).use {
            // PNG header: \x89PNG\r\n\x1a\n
            it.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        }
        val info = FormatRegistry.sniffFormat(file, null, file.name)
        assertEquals("png", info.extension)
    }

    @Test
    fun testJpegMagicBytesSniffedCorrectly() {
        val file = tempFolder.newFile("test_jpeg")
        FileOutputStream(file).use {
            // JPEG header: \xFF\xD8\xFF
            it.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
        }
        val info = FormatRegistry.sniffFormat(file, null, file.name)
        assertEquals("jpg", info.extension)
    }

    @Test
    fun testDocxZipContainerSniffedCorrectly() {
        val file = tempFolder.newFile("test_docx.bin")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write("<w:document><w:body><w:p><w:r><w:t>Hello</w:t></w:r></w:p></w:body></w:document>".toByteArray())
            zos.closeEntry()
        }
        val info = FormatRegistry.sniffFormat(file, null, file.name)
        assertEquals("docx", info.extension)
    }

    @Test
    fun testXlsxZipContainerSniffedCorrectly() {
        val file = tempFolder.newFile("test_xlsx.bin")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            zos.write("<workbook/>".toByteArray())
            zos.closeEntry()
        }
        val info = FormatRegistry.sniffFormat(file, null, file.name)
        assertEquals("xlsx", info.extension)
    }

    @Test
    fun testPptxZipContainerSniffedCorrectly() {
        val file = tempFolder.newFile("test_pptx.bin")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("ppt/presentation.xml"))
            zos.write("<presentation/>".toByteArray())
            zos.closeEntry()
        }
        val info = FormatRegistry.sniffFormat(file, null, file.name)
        assertEquals("pptx", info.extension)
    }

    @Test
    fun testOle2WordDocumentSniffed() {
        val file = tempFolder.newFile("legacy.doc")
        FileOutputStream(file).use {
            it.write(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte()))
        }
        val info = FormatRegistry.sniffFormat(file, null, file.name)
        assertEquals("doc", info.extension)
    }

    @Test
    fun testOle2ExcelDocumentSniffed() {
        val file = tempFolder.newFile("legacy.xls")
        FileOutputStream(file).use {
            it.write(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte()))
        }
        val info = FormatRegistry.sniffFormat(file, null, file.name)
        assertEquals("xls", info.extension)
    }
}
