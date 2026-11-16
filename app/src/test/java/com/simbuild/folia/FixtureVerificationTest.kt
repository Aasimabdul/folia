package com.simbuild.folia

import com.simbuild.folia.engine.FormatConverter
import com.simbuild.folia.engine.FormatRegistry
import com.simbuild.folia.engine.PresentationConverter
import com.simbuild.folia.engine.SpreadsheetConverter
import com.simbuild.folia.engine.StructuredTextConverter
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FixtureVerificationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fixturesDir = File("src/test/resources/fixtures").let {
        if (it.exists()) it else File("app/src/test/resources/fixtures")
    }

    @Test
    fun testDocxFixtureParsesAndRendersHtml() {
        val file = File(fixturesDir, "sample.docx")
        assertTrue("Fixture sample.docx must exist", file.exists())

        val destHtml = tempFolder.newFile("docx_output.html")
        FormatConverter.convertToHtmlFile(file, "docx", destHtml)

        val html = destHtml.readText()
        assertTrue("Must contain document title", html.contains("Word Test Document"))
        assertTrue("Must contain bold tag", html.contains("<strong>bold</strong>"))
        assertTrue("Must contain italic tag", html.contains("<em>italic</em>"))
    }

    @Test
    fun testXlsxFixtureParsesWorksheetsAndSharedStrings() {
        val file = File(fixturesDir, "sample.xlsx")
        assertTrue("Fixture sample.xlsx must exist", file.exists())

        val destHtml = tempFolder.newFile("xlsx_output.html")
        SpreadsheetConverter.convertToHtmlFile(file, "xlsx", destHtml)

        val html = destHtml.readText()
        assertTrue("Must contain sheet title", html.contains("Summary"))
        assertTrue("Must resolve shared string Q1 Revenue", html.contains("Q1 Revenue"))
        assertTrue("Must resolve shared string Q2 Revenue", html.contains("Q2 Revenue"))
        assertTrue("Must contain cell value 15000", html.contains("15000"))
    }

    @Test
    fun testPptxFixtureParsesSlideDecks() {
        val file = File(fixturesDir, "sample.pptx")
        assertTrue("Fixture sample.pptx must exist", file.exists())

        val destHtml = tempFolder.newFile("pptx_output.html")
        PresentationConverter.convertToHtmlFile(file, "pptx", destHtml)

        val html = destHtml.readText()
        assertTrue("Must contain slide badge", html.contains("Slide 1 of"))
        assertTrue("Must contain slide title", html.contains("Folia Universal Deck"))
        assertTrue("Must contain body text", html.contains("High fidelity presentation slide viewer"))
    }

    @Test
    fun testCsvAndTsvFixtures() {
        val csvFile = File(fixturesDir, "sample.csv")
        assertTrue(csvFile.exists())
        val csvHtml = tempFolder.newFile("csv_out.html")
        SpreadsheetConverter.convertToHtmlFile(csvFile, "csv", csvHtml)
        val csvOut = csvHtml.readText()
        assertTrue("Header row uses <th>", csvOut.contains("<th>Name</th>"))
        assertTrue("Data rows use <td>", csvOut.contains("<td>PDF Engine</td>"))

        val tsvFile = File(fixturesDir, "sample.tsv")
        assertTrue(tsvFile.exists())
        val tsvHtml = tempFolder.newFile("tsv_out.html")
        SpreadsheetConverter.convertToHtmlFile(tsvFile, "tsv", tsvHtml)
        assertTrue(tsvHtml.readText().contains("Keyboard"))
    }

    @Test
    fun testCodeAndMarkupFixtures() {
        val ktFile = File(fixturesDir, "sample.kt")
        assertTrue(ktFile.exists())
        val ktHtml = tempFolder.newFile("kt_out.html")
        StructuredTextConverter.convertToHtmlFile(ktFile, "kt", ktHtml)
        assertTrue(ktHtml.readText().contains("<span class=\"kw\">fun</span>"))

        val jsonFile = File(fixturesDir, "sample.json")
        assertTrue(jsonFile.exists())
        val jsonHtml = tempFolder.newFile("json_out.html")
        StructuredTextConverter.convertToHtmlFile(jsonFile, "json", jsonHtml)
        // Quotes are HTML-escaped on purpose (shown as text, never parsed as markup)
        assertTrue(jsonHtml.readText().contains("&quot;app&quot;: &quot;Folia&quot;"))
    }

    @Test
    fun testVcfAndIcsFixtures() {
        val vcfFile = File(fixturesDir, "sample.vcf")
        assertTrue(vcfFile.exists())
        val vcfHtml = tempFolder.newFile("vcf_out.html")
        StructuredTextConverter.convertToHtmlFile(vcfFile, "vcf", vcfHtml)
        assertTrue(vcfHtml.readText().contains("Folia Support"))

        val icsFile = File(fixturesDir, "sample.ics")
        assertTrue(icsFile.exists())
        val icsHtml = tempFolder.newFile("ics_out.html")
        StructuredTextConverter.convertToHtmlFile(icsFile, "ics", icsHtml)
        assertTrue(icsHtml.readText().contains("Folia Release"))
    }
}
