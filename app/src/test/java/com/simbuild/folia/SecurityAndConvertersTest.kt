package com.simbuild.folia

import com.simbuild.folia.engine.ArchiveConverter
import com.simbuild.folia.engine.FormatConverter
import com.simbuild.folia.engine.FormatRegistry
import com.simbuild.folia.engine.SpreadsheetConverter
import com.simbuild.folia.engine.StructuredTextConverter
import com.simbuild.folia.engine.UnsupportedDocEngine
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SecurityAndConvertersTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testZipSlipPathTraversalBlocked() {
        val zipFile = tempFolder.newFile("malicious.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // Attempting Zip-slip path traversal entry
            zos.putNextEntry(ZipEntry("../../../etc/passwd"))
            zos.write("root:x:0:0:root".toByteArray())
            zos.closeEntry()

            // Normal safe entry
            zos.putNextEntry(ZipEntry("safe/readme.txt"))
            zos.write("This is safe content".toByteArray())
            zos.closeEntry()
        }

        val destHtml = tempFolder.newFile("archive_view.html")
        ArchiveConverter.convertToHtmlFile(zipFile, "zip", destHtml)
        val html = destHtml.readText()

        // Verify the safe entry is present
        assertTrue("Safe file should be listed", html.contains("safe/readme.txt"))
        // Verify the path traversal was neutralized and not extracted outside
        assertFalse("Zip slip path traversal should be rejected", html.contains("../../../etc/passwd"))
    }

    @Test
    fun testHtmlSanitizationBlocksScriptsAndActiveContent() {
        val unsafeHtml = """
            <html>
            <head><title>Test</title><script>alert('XSS');</script></head>
            <body onload="doBadThings();">
                <h1>Document Title</h1>
                <p onclick="stealCookies();">Paragraph with <a href="javascript:alert(1)">dangerous link</a></p>
                <iframe src="http://evil.com"></iframe>
            </body>
            </html>
        """.trimIndent()

        val sourceFile = tempFolder.newFile("unsafe.html").apply { writeText(unsafeHtml) }
        val destFile = tempFolder.newFile("sanitized.html")

        StructuredTextConverter.convertToHtmlFile(sourceFile, "html", destFile)
        val result = destFile.readText()

        assertFalse("Script tags must be removed", result.contains("<script"))
        assertFalse("alert('XSS') must be stripped", result.contains("alert('XSS')"))
        assertFalse("iframe must be stripped", result.contains("<iframe"))
        assertFalse("onload handler must be stripped", result.contains("onload="))
        assertFalse("onclick handler must be stripped", result.contains("onclick="))
        assertFalse("javascript: protocol must be stripped", result.contains("javascript:"))
        assertTrue("Legitimate title should be preserved", result.contains("Document Title"))
    }

    @Test
    fun testCsvDelimiterAutoDetectionAndTableRendering() {
        val csvContent = """
            Product,Price,Quantity
            "Mechanical Keyboard, RGB",129.99,5
            "UltraWide Monitor, 34""${'"'},499.50,2
            USB-C Cable,14.99,10
        """.trimIndent()

        val sourceFile = tempFolder.newFile("data.csv").apply { writeText(csvContent) }
        val destFile = tempFolder.newFile("table.html")

        SpreadsheetConverter.convertToHtmlFile(sourceFile, "csv", destFile)
        val result = destFile.readText()

        assertTrue("Table header should exist", result.contains("<th>Product</th>"))
        assertTrue("Quoted comma in field should be handled", result.contains("Mechanical Keyboard, RGB"))
        // CSV "" becomes a single quote character, which is then HTML-escaped for display
        assertTrue("Escaped quotes should be cleaned", result.contains("UltraWide Monitor, 34&quot;"))
        assertTrue("Row element should be rendered", result.contains("<td>129.99</td>"))
    }

    @Test
    fun testMarkdownConversionToHtml() {
        val mdContent = """
            # Project Overview
            This is a **bold statement** and an *italic note*.
            
            - Feature A
            - Feature B
            
            ```kotlin
            fun main() = println("Hello")
            ```
        """.trimIndent()

        val sourceFile = tempFolder.newFile("readme.md").apply { writeText(mdContent) }
        val destFile = tempFolder.newFile("doc.html")

        FormatConverter.convertToHtmlFile(sourceFile, "md", destFile)
        val html = destFile.readText()

        assertTrue("H1 should be generated", html.contains("<h1>Project Overview</h1>"))
        assertTrue("Bold tag should be generated", html.contains("<strong>bold statement</strong>"))
        assertTrue("Italic tag should be generated", html.contains("<em>italic note</em>"))
        assertTrue("List tag should be generated", html.contains("<li>Feature A</li>"))
        assertTrue("Code block should be generated", html.contains("<pre><code>"))
    }

    @Test
    fun testCodeWithLineNumbersAndSyntaxHighlighting() {
        val codeContent = """
            package main
            
            import "fmt"
            
            fun calculate(x: Int): Int {
                if (x > 0) return x * 2
                return 0
            }
        """.trimIndent()

        val sourceFile = tempFolder.newFile("test.kt").apply { writeText(codeContent) }
        val destFile = tempFolder.newFile("code.html")

        StructuredTextConverter.convertToHtmlFile(sourceFile, "kt", destFile)
        val html = destFile.readText()

        assertTrue("Line numbers should be present", html.contains("<td class=\"line-num\">1</td>"))
        assertTrue("Line numbers should be present", html.contains("<td class=\"line-num\">5</td>"))
        assertTrue("Keywords should be highlighted", html.contains("<span class=\"kw\">fun</span>"))
        assertTrue("Keywords should be highlighted", html.contains("<span class=\"kw\">return</span>"))
    }

    @Test
    fun testEmailParsingExtractsHeadersAndBody() {
        val emlContent = """
            Subject: Quarterly Financial Report
            From: reports@company.org
            To: team@company.org
            Date: Mon, 16 Nov 2026 10:00:00 +0000
            
            Hello Team,
            Here is the summary of the quarterly financial metrics.
            Revenue grew by 24% year over year.
        """.trimIndent()

        val sourceFile = tempFolder.newFile("message.eml").apply { writeText(emlContent) }
        val destFile = tempFolder.newFile("email.html")

        StructuredTextConverter.convertToHtmlFile(sourceFile, "eml", destFile)
        val html = destFile.readText()

        assertTrue("Subject should be displayed", html.contains("Quarterly Financial Report"))
        assertTrue("Sender should be displayed", html.contains("reports@company.org"))
        assertTrue("Recipient should be displayed", html.contains("team@company.org"))
        assertTrue("Body text should be displayed", html.contains("Revenue grew by 24%"))
    }

    @Test
    fun testUnsupportedFormatDisclosesLimitationHonestly() {
        val file = tempFolder.newFile("blueprint.dwg")
        FileOutputStream(file).use {
            it.write("AC1027AutoCAD Drawing Header Binary Test Data String".toByteArray())
        }

        val destFile = tempFolder.newFile("info.html")
        val info = FormatRegistry.findByExtension("dwg")!!
        UnsupportedDocEngine.convertToHtmlFile(file, "dwg", info, destFile)
        val html = destFile.readText()

        assertTrue("Format name should appear", html.contains("AutoCAD Drawing"))
        assertTrue("Status must state unsupported", html.contains("Unsupported"))
        assertTrue("Recommendation must suggest PDF export", html.contains("Export as PDF"))
    }
}
