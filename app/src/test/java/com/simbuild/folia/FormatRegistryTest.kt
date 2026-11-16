package com.simbuild.folia

import com.simbuild.folia.engine.FormatCategory
import com.simbuild.folia.engine.FormatRegistry
import com.simbuild.folia.engine.SupportLevel
import org.junit.Assert.*
import org.junit.Test

class FormatRegistryTest {

    @Test
    fun testWordProcessingFormatsRegistered() {
        val wordFormats = listOf("docx", "doc", "odt", "rtf", "txt", "md")
        for (ext in wordFormats) {
            val info = FormatRegistry.findByExtension(ext)
            assertNotNull("Format .$ext must be registered", info)
            assertEquals("Format .$ext must be WORD_PROCESSING", FormatCategory.WORD_PROCESSING, info!!.category)
            assertTrue("Format .$ext must be offline capable", info.isOfflineAvailable)
        }
    }

    @Test
    fun testPdfAndFixedLayoutRegistered() {
        val pdfInfo = FormatRegistry.findByExtension("pdf")
        assertNotNull(pdfInfo)
        assertEquals(FormatCategory.PDF_FIXED, pdfInfo!!.category)
        assertEquals(SupportLevel.NATIVE_RENDERING, pdfInfo.supportLevel)

        val xpsInfo = FormatRegistry.findByExtension("xps")
        assertNotNull(xpsInfo)
        assertEquals(SupportLevel.NATIVE_RENDERING, xpsInfo!!.supportLevel)
    }

    @Test
    fun testEbooksAndComicsRegistered() {
        val epubInfo = FormatRegistry.findByExtension("epub")
        assertNotNull(epubInfo)
        assertEquals(FormatCategory.EBOOK_COMIC, epubInfo!!.category)
        assertEquals(SupportLevel.NATIVE_RENDERING, epubInfo.supportLevel)

        val cbzInfo = FormatRegistry.findByExtension("cbz")
        assertNotNull(cbzInfo)
        assertEquals(SupportLevel.NATIVE_RENDERING, cbzInfo!!.supportLevel)
    }

    @Test
    fun testSpreadsheetFormatsRegistered() {
        val xlsxInfo = FormatRegistry.findByExtension("xlsx")
        assertNotNull(xlsxInfo)
        assertEquals(FormatCategory.SPREADSHEET, xlsxInfo!!.category)
        assertEquals(SupportLevel.CONVERTED_PREVIEW, xlsxInfo.supportLevel)

        val csvInfo = FormatRegistry.findByExtension("csv")
        assertNotNull(csvInfo)
        assertEquals(FormatCategory.SPREADSHEET, csvInfo!!.category)

        val odsInfo = FormatRegistry.findByExtension("ods")
        assertNotNull(odsInfo)
        assertEquals(FormatCategory.SPREADSHEET, odsInfo!!.category)
    }

    @Test
    fun testPresentationFormatsRegistered() {
        val pptxInfo = FormatRegistry.findByExtension("pptx")
        assertNotNull(pptxInfo)
        assertEquals(FormatCategory.PRESENTATION, pptxInfo!!.category)
        assertEquals(SupportLevel.CONVERTED_PREVIEW, pptxInfo.supportLevel)

        val odpInfo = FormatRegistry.findByExtension("odp")
        assertNotNull(odpInfo)
        assertEquals(FormatCategory.PRESENTATION, odpInfo!!.category)
    }

    @Test
    fun testStructuredTextAndCodeRegistered() {
        val codeFormats = listOf("py", "java", "kt", "c", "cpp", "js", "ts", "json", "yaml", "xml", "sql", "sh", "html")
        for (ext in codeFormats) {
            val info = FormatRegistry.findByExtension(ext)
            assertNotNull("Code format .$ext must be registered", info)
            assertEquals(FormatCategory.STRUCTURED_TEXT_CODE, info!!.category)
            assertEquals(SupportLevel.CONVERTED_PREVIEW, info.supportLevel)
        }
    }

    @Test
    fun testArchiveFormatsRegistered() {
        val zipInfo = FormatRegistry.findByExtension("zip")
        assertNotNull(zipInfo)
        assertEquals(FormatCategory.ARCHIVE, zipInfo!!.category)

        val tarInfo = FormatRegistry.findByExtension("tar")
        assertNotNull(tarInfo)
        assertEquals(FormatCategory.ARCHIVE, tarInfo!!.category)
    }

    @Test
    fun testProprietaryFormatsExplicitlyReportUnsupportedOrPartial() {
        val dwgInfo = FormatRegistry.findByExtension("dwg")
        assertNotNull(dwgInfo)
        assertEquals(SupportLevel.UNSUPPORTED, dwgInfo!!.supportLevel)

        val inddInfo = FormatRegistry.findByExtension("indd")
        assertNotNull(inddInfo)
        assertEquals(SupportLevel.UNSUPPORTED, inddInfo!!.supportLevel)

        val pagesInfo = FormatRegistry.findByExtension("pages")
        assertNotNull(pagesInfo)
        assertEquals(SupportLevel.PARTIAL_SUPPORT, pagesInfo!!.supportLevel)
    }

    @Test
    fun testEveryRegisteredFormatHasMimeAndDescription() {
        val all = FormatRegistry.allFormats()
        assertTrue("Total registered formats should exceed 70", all.size >= 70)
        for (f in all) {
            assertTrue("MIME type must not be blank for ${f.extension}", f.mimeType.isNotBlank())
            assertTrue("Display name must not be blank for ${f.extension}", f.displayName.isNotBlank())
            assertNotNull("Category must be assigned for ${f.extension}", f.category)
            assertNotNull("Support level must be assigned for ${f.extension}", f.supportLevel)
        }
    }
}
