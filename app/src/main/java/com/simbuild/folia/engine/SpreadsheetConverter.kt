package com.simbuild.folia.engine

import org.w3c.dom.Node
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Robust offline converter for spreadsheets:
 * - Excel OpenXML (.xlsx, .xlsm, .xltx, .xltm) with multiple worksheets & shared strings
 * - OpenDocument Spreadsheet (.ods, .ots)
 * - Delimited text (.csv, .tsv) with delimiter detection
 * - Legacy Excel (.xls, .xlsb) with text table extraction
 */
object SpreadsheetConverter {

    fun convertToHtmlFile(sourceFile: File, ext: String, destFile: File) {
        val html = when (ext.lowercase()) {
            "xlsx", "xlsm", "xltx", "xltm" -> xlsxToHtml(sourceFile)
            "ods", "ots" -> odsToHtml(sourceFile)
            "csv", "tsv" -> delimitedToHtml(sourceFile, ext)
            "xls", "xlsb" -> xlsBinaryToHtml(sourceFile)
            else -> delimitedToHtml(sourceFile, "csv")
        }
        destFile.writeText(html, Charsets.UTF_8)
    }

    // =========================================================================
    // Excel OpenXML (.xlsx, .xlsm) Parser
    // =========================================================================

    private fun xlsxToHtml(file: File): String {
        val sb = StringBuilder()
        val title = file.nameWithoutExtension
        sb.append(htmlHeader(title))

        try {
            ZipFile(file).use { zip ->
                // 1. Read shared strings pool (xl/sharedStrings.xml)
                val sharedStrings = mutableListOf<String>()
                val sstEntry = zip.getEntry("xl/sharedStrings.xml")
                if (sstEntry != null) {
                    zip.getInputStream(sstEntry).use { input ->
                        parseSharedStrings(input, sharedStrings)
                    }
                }

                // 2. Discover sheets from xl/workbook.xml
                val sheets = mutableListOf<Pair<String, String>>() // (sheetName, rId)
                val wbEntry = zip.getEntry("xl/workbook.xml")
                if (wbEntry != null) {
                    zip.getInputStream(wbEntry).use { input ->
                        parseWorkbookSheets(input, sheets)
                    }
                }

                // If no workbook.xml found, fallback to finding sheet1.xml directly
                if (sheets.isEmpty()) {
                    sheets.add("Sheet 1" to "sheet1.xml")
                }

                // 3. Render sheets
                sb.append("<div class=\"spreadsheet-container\">\n")
                sb.append("<h1>").append(escapeHtml(title)).append("</h1>\n")

                var sheetIndex = 1
                for ((sheetName, _) in sheets) {
                    val entryName = "xl/worksheets/sheet$sheetIndex.xml"
                    val sheetEntry = zip.getEntry(entryName) ?: zip.getEntry("xl/worksheets/sheet1.xml")
                    if (sheetEntry != null) {
                        sb.append("<div class=\"sheet-card\">\n")
                        sb.append("<h2 class=\"sheet-title\">📊 ").append(escapeHtml(sheetName)).append("</h2>\n")
                        zip.getInputStream(sheetEntry).use { input ->
                            renderWorksheetTable(input, sharedStrings, sb)
                        }
                        sb.append("</div>\n")
                    }
                    sheetIndex++
                }
                sb.append("</div>\n")
            }
        } catch (e: Exception) {
            sb.append("<div class=\"error-banner\">Failed to read spreadsheet: ")
                .append(escapeHtml(e.message ?: "Unknown error"))
                .append("</div>\n")
        }

        sb.append(htmlFooter())
        return sb.toString()
    }

    private fun parseSharedStrings(input: InputStream, list: MutableList<String>) {
        try {
            val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val doc = dbf.newDocumentBuilder().parse(input)
            val siNodes = doc.getElementsByTagNameNS("*", "si")
            for (i in 0 until siNodes.length) {
                val si = siNodes.item(i)
                val tNodes = (si as? org.w3c.dom.Element)?.getElementsByTagNameNS("*", "t")
                val textBuilder = StringBuilder()
                if (tNodes != null) {
                    for (j in 0 until tNodes.length) {
                        textBuilder.append(tNodes.item(j).textContent)
                    }
                }
                list.add(textBuilder.toString())
            }
        } catch (_: Exception) {}
    }

    private fun parseWorkbookSheets(input: InputStream, sheets: MutableList<Pair<String, String>>) {
        try {
            val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val doc = dbf.newDocumentBuilder().parse(input)
            val sheetNodes = doc.getElementsByTagNameNS("*", "sheet")
            for (i in 0 until sheetNodes.length) {
                val node = sheetNodes.item(i)
                val name = node.attributes.getNamedItem("name")?.nodeValue ?: "Sheet ${i + 1}"
                val rId = node.attributes.getNamedItemNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")?.nodeValue
                    ?: "rId${i + 1}"
                sheets.add(name to rId)
            }
        } catch (_: Exception) {}
    }

    private fun renderWorksheetTable(
        input: InputStream,
        sharedStrings: List<String>,
        sb: StringBuilder
    ) {
        sb.append("<div class=\"table-scroll-wrap\"><table class=\"data-table\">\n")
        try {
            val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val doc = dbf.newDocumentBuilder().parse(input)
            val rowNodes = doc.getElementsByTagNameNS("*", "row")

            for (i in 0 until rowNodes.length) {
                val row = rowNodes.item(i)
                val rowNum = row.attributes?.getNamedItem("r")?.nodeValue ?: "${i + 1}"
                sb.append("<tr>")
                sb.append("<th class=\"row-header\">").append(rowNum).append("</th>")

                val cNodes = row.childNodes
                for (j in 0 until cNodes.length) {
                    val cell = cNodes.item(j)
                    if ("c" == cell.localName) {
                        val type = cell.attributes?.getNamedItem("t")?.nodeValue ?: ""
                        var cellValue = ""

                        val cellChildren = cell.childNodes
                        for (k in 0 until cellChildren.length) {
                            val child = cellChildren.item(k)
                            when (child.localName) {
                                "v" -> {
                                    val rawVal = child.textContent.trim()
                                    cellValue = if (type == "s") {
                                        val strIndex = rawVal.toIntOrNull()
                                        if (strIndex != null && strIndex in sharedStrings.indices) {
                                            sharedStrings[strIndex]
                                        } else rawVal
                                    } else rawVal
                                }
                                "is" -> {
                                    cellValue = child.textContent.trim()
                                }
                            }
                        }

                        if (i == 0) {
                            sb.append("<th class=\"cell-th\">").append(escapeHtml(cellValue)).append("</th>")
                        } else {
                            sb.append("<td>").append(escapeHtml(cellValue)).append("</td>")
                        }
                    }
                }
                sb.append("</tr>\n")
            }
        } catch (e: Exception) {
            sb.append("<tr><td>Error rendering sheet content: ").append(escapeHtml(e.message ?: "")).append("</td></tr>")
        }
        sb.append("</table></div>\n")
    }

    // =========================================================================
    // OpenDocument Spreadsheet (.ods) Parser
    // =========================================================================

    private fun odsToHtml(file: File): String {
        val sb = StringBuilder()
        sb.append(htmlHeader(file.nameWithoutExtension))
        sb.append("<h1>").append(escapeHtml(file.nameWithoutExtension)).append("</h1>\n")

        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("content.xml")
                    ?: return delimitedToHtml(file, "csv")

                zip.getInputStream(entry).use { input ->
                    val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                    val doc = dbf.newDocumentBuilder().parse(input)
                    val tableNodes = doc.getElementsByTagNameNS("*", "table")

                    for (t in 0 until tableNodes.length) {
                        val table = tableNodes.item(t)
                        val tableName = table.attributes?.getNamedItemNS("*", "name")?.nodeValue
                            ?: "Table ${t + 1}"
                        sb.append("<div class=\"sheet-card\">\n")
                        sb.append("<h2 class=\"sheet-title\">📊 ").append(escapeHtml(tableName)).append("</h2>\n")
                        sb.append("<div class=\"table-scroll-wrap\"><table class=\"data-table\">\n")

                        val rowNodes = (table as? org.w3c.dom.Element)?.getElementsByTagNameNS("*", "table-row")
                        if (rowNodes != null) {
                            for (r in 0 until rowNodes.length) {
                                val row = rowNodes.item(r)
                                sb.append("<tr>")
                                val cellNodes = (row as? org.w3c.dom.Element)?.getElementsByTagNameNS("*", "table-cell")
                                if (cellNodes != null) {
                                    for (c in 0 until cellNodes.length) {
                                        val cell = cellNodes.item(c)
                                        val text = cell.textContent.trim()
                                        if (r == 0) {
                                            sb.append("<th>").append(escapeHtml(text)).append("</th>")
                                        } else {
                                            sb.append("<td>").append(escapeHtml(text)).append("</td>")
                                        }
                                    }
                                }
                                sb.append("</tr>\n")
                            }
                        }
                        sb.append("</table></div></div>\n")
                    }
                }
            }
        } catch (e: Exception) {
            sb.append("<p>Error parsing ODS: ").append(escapeHtml(e.message ?: "")).append("</p>")
        }

        sb.append(htmlFooter())
        return sb.toString()
    }

    // =========================================================================
    // Delimited Text (.csv, .tsv) Parser
    // =========================================================================

    private fun delimitedToHtml(file: File, ext: String): String {
        val lines = file.readLines(Charsets.UTF_8)
        val sb = StringBuilder()
        val title = file.nameWithoutExtension
        sb.append(htmlHeader(title))
        sb.append("<h1>").append(escapeHtml(title)).append("</h1>\n")

        val delimiter = if (ext.equals("tsv", ignoreCase = true)) "\t" else detectDelimiter(lines)

        sb.append("<div class=\"table-scroll-wrap\"><table class=\"data-table\">\n")
        for ((idx, line) in lines.withIndex()) {
            if (line.isBlank()) continue
            val tokens = splitCsvLine(line, delimiter)
            sb.append("<tr>")
            for (token in tokens) {
                if (idx == 0) {
                    sb.append("<th>").append(escapeHtml(token)).append("</th>")
                } else {
                    sb.append("<td>").append(escapeHtml(token)).append("</td>")
                }
            }
            sb.append("</tr>\n")
        }
        sb.append("</table></div>\n")
        sb.append(htmlFooter())
        return sb.toString()
    }

    private fun detectDelimiter(lines: List<String>): String {
        val sample = lines.take(5).joinToString("\n")
        val commas = sample.count { it == ',' }
        val semicolons = sample.count { it == ';' }
        val tabs = sample.count { it == '\t' }
        val pipes = sample.count { it == '|' }

        return when {
            tabs > commas && tabs > semicolons -> "\t"
            semicolons > commas -> ";"
            pipes > commas && pipes > semicolons -> "|"
            else -> ","
        }
    }

    private fun splitCsvLine(line: String, delimiter: String): List<String> {
        val pattern = if (delimiter == "\t") "\t" else "$delimiter(?=([^\"]*\"[^\"]*\")*[^\"]*$)"
        return line.split(pattern.toRegex()).map { col ->
            col.trim().removeSurrounding("\"").replace("\"\"", "\"")
        }
    }

    // =========================================================================
    // Legacy Excel (.xls, .xlsb) Text Extractor
    // =========================================================================

    private fun xlsBinaryToHtml(file: File): String {
        val bytes = file.readBytes()
        val sb = StringBuilder()
        sb.append(htmlHeader(file.nameWithoutExtension))
        sb.append("<h1>📊 ").append(escapeHtml(file.nameWithoutExtension)).append("</h1>\n")
        sb.append("<p class=\"notice\">Showing extracted cell data from legacy Excel workbook:</p>\n")
        sb.append("<div class=\"table-scroll-wrap\"><table class=\"data-table\">\n")

        val strings = mutableListOf<String>()
        var idx = 0
        while (idx < bytes.size - 1) {
            val b0 = bytes[idx].toInt() and 0xFF
            val b1 = bytes[idx + 1].toInt() and 0xFF
            if (b1 == 0 && (b0 in 32..126)) {
                val start = idx
                val str = StringBuilder()
                while (idx < bytes.size - 1 && (bytes[idx + 1].toInt() and 0xFF) == 0 && ((bytes[idx].toInt() and 0xFF) in 32..126)) {
                    str.append((bytes[idx].toInt() and 0xFF).toChar())
                    idx += 2
                }
                if (str.length >= 2) strings.add(str.toString())
            } else {
                idx++
            }
        }

        // Output extracted cells in rows of 5 columns
        val cols = 5
        for (i in strings.indices step cols) {
            sb.append("<tr>")
            for (j in 0 until cols) {
                val cell = if (i + j < strings.size) strings[i + j] else ""
                sb.append("<td>").append(escapeHtml(cell)).append("</td>")
            }
            sb.append("</tr>\n")
        }

        sb.append("</table></div>\n")
        sb.append(htmlFooter())
        return sb.toString()
    }

    private fun escapeHtml(text: String): String = text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun htmlHeader(title: String): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8"/>
            <title>$title</title>
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif;
                    font-size: 13pt;
                    color: #212121;
                    padding: 16px;
                    margin: 0;
                    background-color: #fafafa;
                }
                h1 { font-size: 20pt; margin-bottom: 16px; color: #1b5e20; }
                .sheet-card {
                    background: #ffffff;
                    border: 1px solid #e0e0e0;
                    border-radius: 8px;
                    padding: 16px;
                    margin-bottom: 24px;
                    box-shadow: 0 1px 3px rgba(0,0,0,0.06);
                }
                .sheet-title {
                    font-size: 16pt;
                    margin-top: 0;
                    margin-bottom: 12px;
                    color: #2e7d32;
                    border-bottom: 2px solid #81c784;
                    padding-bottom: 6px;
                }
                .table-scroll-wrap {
                    overflow-x: auto;
                    width: 100%;
                }
                table.data-table {
                    border-collapse: collapse;
                    width: 100%;
                    white-space: nowrap;
                    font-size: 11pt;
                }
                table.data-table th, table.data-table td {
                    border: 1px solid #dcdcdc;
                    padding: 7px 12px;
                    text-align: left;
                }
                table.data-table th {
                    background-color: #f1f8e9;
                    color: #1b5e20;
                    font-weight: 600;
                }
                table.data-table th.row-header {
                    background-color: #f5f5f5;
                    color: #757575;
                    width: 40px;
                    text-align: center;
                    font-size: 10pt;
                }
                table.data-table tr:nth-child(even) td {
                    background-color: #fcfcfc;
                }
                table.data-table tr:hover td {
                    background-color: #f0f4c3;
                }
                .notice { font-size: 11pt; color: #616161; margin-bottom: 10px; font-style: italic; }
                .error-banner { background: #ffebee; color: #c62828; padding: 12px; border-radius: 6px; margin: 12px 0; }
            </style>
        </head>
        <body>
    """.trimIndent()

    private fun htmlFooter(): String = "</body></html>"
}
