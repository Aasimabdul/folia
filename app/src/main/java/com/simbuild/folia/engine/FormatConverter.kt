package com.simbuild.folia.engine

import android.util.Base64
import org.w3c.dom.Node
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * High-performance, offline document-to-HTML converter.
 * Converts Word (.docx, .doc, .rtf, .odt), Markdown, CSV, and code files
 * into beautifully styled HTML so MuPDF can render them with crisp typography
 * and reflow layout.
 */
object FormatConverter {

    fun convertToHtmlFile(sourceFile: File, ext: String, destFile: File) {
        val htmlContent = try {
            when (ext.lowercase()) {
                "docx" -> docxToHtml(sourceFile)
                "doc" -> docToHtml(sourceFile)
                "rtf" -> rtfToHtml(sourceFile)
                "odt" -> odtToHtml(sourceFile)
                "md", "markdown" -> markdownToHtml(sourceFile.readText(Charsets.UTF_8))
                "csv" -> csvToHtml(sourceFile.readText(Charsets.UTF_8))
                "json", "xml", "kt", "java", "py", "c", "cpp", "js", "ts", "html", "htm" ->
                    codeToHtml(sourceFile.readText(Charsets.UTF_8), ext)
                else -> textToHtml(sourceFile.readText(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            fallbackErrorHtml(sourceFile.name, e)
        }
        destFile.writeText(htmlContent, Charsets.UTF_8)
    }

    // =========================================================================
    // Word Document (.docx) Parser
    // =========================================================================

    private fun docxToHtml(file: File): String {
        val sb = StringBuilder()
        val title = file.nameWithoutExtension
        sb.append(htmlHeader(title))

        ZipFile(file).use { zip ->
            // 1. Map embedded media (images) to base64
            val imageMap = mutableMapOf<String, String>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.startsWith("word/media/") && !entry.isDirectory) {
                    val ext = entry.name.substringAfterLast('.', "png").lowercase()
                    val mime = if (ext == "jpg" || ext == "jpeg") "image/jpeg" else "image/$ext"
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    val b64 = encodeBase64(bytes)
                    imageMap[entry.name.substringAfterLast('/')] = "data:$mime;base64,$b64"
                }
            }

            // 2. Parse relationships to connect r:id to media filename
            val relsMap = mutableMapOf<String, String>()
            val relsEntry = zip.getEntry("word/_rels/document.xml.rels")
            if (relsEntry != null) {
                zip.getInputStream(relsEntry).use { input ->
                    parseRels(input, relsMap)
                }
            }

            // 3. Parse main document body
            val docEntry = zip.getEntry("word/document.xml")
                ?: return textToHtml("Could not locate document content inside Word file.")

            zip.getInputStream(docEntry).use { input ->
                val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                val doc = dbf.newDocumentBuilder().parse(input)
                doc.documentElement.normalize()

                val bodyList = doc.getElementsByTagNameNS("*", "body")
                if (bodyList.length > 0) {
                    val body = bodyList.item(0)
                    val children = body.childNodes
                    for (i in 0 until children.length) {
                        val node = children.item(i)
                        when (node.localName) {
                            "p" -> parseDocxParagraph(node, sb, relsMap, imageMap)
                            "tbl" -> parseDocxTable(node, sb, relsMap, imageMap)
                        }
                    }
                }
            }
        }

        sb.append(htmlFooter())
        return sb.toString()
    }

    private fun parseRels(input: InputStream, relsMap: mutableMapOf<String, String>) {
        try {
            val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val doc = dbf.newDocumentBuilder().parse(input)
            val relList = doc.getElementsByTagNameNS("*", "Relationship")
            for (i in 0 until relList.length) {
                val rel = relList.item(i)
                val id = rel.attributes.getNamedItem("Id")?.nodeValue
                val target = rel.attributes.getNamedItem("Target")?.nodeValue
                if (id != null && target != null) {
                    relsMap[id] = target.substringAfterLast('/')
                }
            }
        } catch (_: Exception) {}
    }

    private fun parseDocxParagraph(
        pNode: Node,
        sb: StringBuilder,
        relsMap: Map<String, String>,
        imageMap: Map<String, String>
    ) {
        var tag = "p"
        var isListItem = false
        val pChildren = pNode.childNodes
        val pContent = StringBuilder()

        for (i in 0 until pChildren.length) {
            val child = pChildren.item(i)
            when (child.localName) {
                "pPr" -> {
                    val prChildren = child.childNodes
                    for (j in 0 until prChildren.length) {
                        val pr = prChildren.item(j)
                        when (pr.localName) {
                            "pStyle" -> {
                                val style = pr.attributes.getNamedItemNS("*", "val")?.nodeValue
                                    ?: pr.attributes.getNamedItem("w:val")?.nodeValue ?: ""
                                if (style.contains("Heading1", ignoreCase = true) || style.equals("Title", ignoreCase = true)) {
                                    tag = "h1"
                                } else if (style.contains("Heading2", ignoreCase = true) || style.equals("Subtitle", ignoreCase = true)) {
                                    tag = "h2"
                                } else if (style.contains("Heading3", ignoreCase = true)) {
                                    tag = "h3"
                                }
                            }
                            "numPr" -> isListItem = true
                        }
                    }
                }
                "r" -> parseDocxRun(child, pContent, relsMap, imageMap)
                "drawing" -> parseDocxDrawing(child, pContent, relsMap, imageMap)
            }
        }

        if (pContent.isNotEmpty()) {
            if (isListItem) {
                sb.append("<p class=\"list-item\">&bull;&nbsp;").append(pContent).append("</p>\n")
            } else {
                sb.append("<").append(tag).append(">").append(pContent).append("</").append(tag).append(">\n")
            }
        } else if (tag == "p") {
            sb.append("<p class=\"spacer\">&nbsp;</p>\n")
        }
    }

    private fun parseDocxRun(
        rNode: Node,
        sb: StringBuilder,
        relsMap: Map<String, String>,
        imageMap: Map<String, String>
    ) {
        var isBold = false
        var isItalic = false
        var isUnderline = false
        var isStrike = false
        val runText = StringBuilder()

        val children = rNode.childNodes
        for (i in 0 until children.length) {
            val c = children.item(i)
            when (c.localName) {
                "rPr" -> {
                    val rPr = c.childNodes
                    for (j in 0 until rPr.length) {
                        when (rPr.item(j).localName) {
                            "b" -> isBold = true
                            "i" -> isItalic = true
                            "u" -> isUnderline = true
                            "strike" -> isStrike = true
                        }
                    }
                }
                "t" -> runText.append(escapeHtml(c.textContent))
                "br" -> runText.append("<br/>")
                "drawing" -> parseDocxDrawing(c, runText, relsMap, imageMap)
            }
        }

        if (runText.isNotEmpty()) {
            if (isBold) sb.append("<strong>")
            if (isItalic) sb.append("<em>")
            if (isUnderline) sb.append("<u>")
            if (isStrike) sb.append("<del>")
            sb.append(runText)
            if (isStrike) sb.append("</del>")
            if (isUnderline) sb.append("</u>")
            if (isItalic) sb.append("</em>")
            if (isBold) sb.append("</strong>")
        }
    }

    private fun parseDocxDrawing(
        drawingNode: Node,
        sb: StringBuilder,
        relsMap: Map<String, String>,
        imageMap: Map<String, String>
    ) {
        try {
            val blips = (drawingNode as? org.w3c.dom.Element)?.getElementsByTagNameNS("*", "blip") ?: return
            if (blips.length > 0) {
                val blip = blips.item(0)
                val embedId = blip.attributes.getNamedItemNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed")?.nodeValue
                    ?: blip.attributes.getNamedItem("r:embed")?.nodeValue
                if (embedId != null) {
                    val filename = relsMap[embedId]
                    val base64Data = if (filename != null) imageMap[filename] else null
                    if (base64Data != null) {
                        sb.append("<div class=\"doc-img-wrap\"><img src=\"$base64Data\" class=\"doc-img\" /></div>\n")
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun parseDocxTable(
        tblNode: Node,
        sb: StringBuilder,
        relsMap: Map<String, String>,
        imageMap: Map<String, String>
    ) {
        sb.append("<table class=\"data-table\">\n")
        val rows = tblNode.childNodes
        for (i in 0 until rows.length) {
            val row = rows.item(i)
            if ("tr" == row.localName) {
                sb.append("<tr>")
                val cells = row.childNodes
                for (j in 0 until cells.length) {
                    val cell = cells.item(j)
                    if ("tc" == cell.localName) {
                        sb.append("<td>")
                        val cellChildren = cell.childNodes
                        for (k in 0 until cellChildren.length) {
                            val cc = cellChildren.item(k)
                            if ("p" == cc.localName) {
                                parseDocxParagraph(cc, sb, relsMap, imageMap)
                            }
                        }
                        sb.append("</td>")
                    }
                }
                sb.append("</tr>\n")
            }
        }
        sb.append("</table>\n")
    }

    // =========================================================================
    // OpenDocument Text (.odt) Parser
    // =========================================================================

    private fun odtToHtml(file: File): String {
        val sb = StringBuilder()
        sb.append(htmlHeader(file.nameWithoutExtension))

        ZipFile(file).use { zip ->
            val contentEntry = zip.getEntry("content.xml")
                ?: return textToHtml("Could not locate content.xml inside ODT document.")

            zip.getInputStream(contentEntry).use { input ->
                val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                val doc = dbf.newDocumentBuilder().parse(input)
                val paragraphs = doc.getElementsByTagNameNS("*", "p")
                for (i in 0 until paragraphs.length) {
                    val p = paragraphs.item(i)
                    val text = escapeHtml(p.textContent.trim())
                    if (text.isNotEmpty()) {
                        sb.append("<p>").append(text).append("</p>\n")
                    } else {
                        sb.append("<p class=\"spacer\">&nbsp;</p>\n")
                    }
                }
            }
        }

        sb.append(htmlFooter())
        return sb.toString()
    }

    // =========================================================================
    // Rich Text Format (.rtf) Parser
    // =========================================================================

    private fun rtfToHtml(file: File): String {
        val raw = file.readText(StandardCharsets.ISO_8859_1)
        val sb = StringBuilder()
        sb.append(htmlHeader(file.nameWithoutExtension))
        sb.append("<p>")

        var i = 0
        var isBold = false
        var isItalic = false
        val len = raw.length

        while (i < len) {
            val ch = raw[i]
            if (ch == '\\') {
                i++
                if (i >= len) break
                val nextCh = raw[i]
                if (nextCh == '\\' || nextCh == '{' || nextCh == '}') {
                    sb.append(escapeHtml(nextCh.toString()))
                    i++
                } else if (nextCh == '\'') {
                    // Hex character \'xx
                    i++
                    if (i + 2 <= len) {
                        try {
                            val hex = raw.substring(i, i + 2)
                            val byteVal = hex.toInt(16)
                            sb.append(byteVal.toChar())
                        } catch (_: Exception) {}
                        i += 2
                    }
                } else if (nextCh.isLetter()) {
                    val cmdStart = i
                    while (i < len && raw[i].isLetter()) i++
                    val cmd = raw.substring(cmdStart, i)
                    val paramStart = i
                    while (i < len && (raw[i].isDigit() || raw[i] == '-')) i++
                    val param = if (i > paramStart) raw.substring(paramStart, i) else ""
                    if (i < len && raw[i] == ' ') i++ // optional trailing space delimiter

                    when (cmd) {
                        "par" -> sb.append("</p>\n<p>")
                        "line" -> sb.append("<br/>")
                        "b" -> {
                            val turnOn = param.isEmpty() || param != "0"
                            if (turnOn && !isBold) { sb.append("<strong>"); isBold = true }
                            else if (!turnOn && isBold) { sb.append("</strong>"); isBold = false }
                        }
                        "i" -> {
                            val turnOn = param.isEmpty() || param != "0"
                            if (turnOn && !isItalic) { sb.append("<em>"); isItalic = true }
                            else if (!turnOn && isItalic) { sb.append("</em>"); isItalic = false }
                        }
                        "u" -> {
                            try {
                                val codePoint = param.toInt()
                                sb.append(codePoint.toChar())
                            } catch (_: Exception) {}
                        }
                    }
                }
            } else if (ch == '{' || ch == '}') {
                i++
            } else if (ch == '\r' || ch == '\n') {
                i++
            } else {
                sb.append(escapeHtml(ch.toString()))
                i++
            }
        }

        if (isItalic) sb.append("</em>")
        if (isBold) sb.append("</strong>")
        sb.append("</p>\n")
        sb.append(htmlFooter())
        return sb.toString()
    }

    // =========================================================================
    // Legacy Word 97-2003 (.doc) Binary Extractor
    // =========================================================================

    private fun docToHtml(file: File): String {
        val bytes = file.readBytes()
        val sb = StringBuilder()
        sb.append(htmlHeader(file.nameWithoutExtension))

        // Scan readable text sequences from Word binary file
        val textBuilder = StringBuilder()
        var idx = 0
        while (idx < bytes.size - 1) {
            // Check for UTF-16LE characters (common in Word binary streams)
            val b0 = bytes[idx].toInt() and 0xFF
            val b1 = bytes[idx + 1].toInt() and 0xFF
            val charCode = (b1 shl 8) or b0

            if (b1 == 0 && (b0 in 32..126 || b0 == 10 || b0 == 13)) {
                if (b0 == 13 || b0 == 10) {
                    textBuilder.append('\n')
                } else {
                    textBuilder.append(b0.toChar())
                }
                idx += 2
            } else if (b0 in 32..126) {
                textBuilder.append(b0.toChar())
                idx++
            } else {
                idx++
            }
        }

        val paragraphs = textBuilder.toString().lines()
        for (line in paragraphs) {
            val clean = line.trim()
            if (clean.length > 3) {
                sb.append("<p>").append(escapeHtml(clean)).append("</p>\n")
            }
        }

        sb.append(htmlFooter())
        return sb.toString()
    }

    // =========================================================================
    // Markdown, CSV, Code, and Text Parsers
    // =========================================================================

    private fun markdownToHtml(md: String): String {
        val lines = md.lines()
        val sb = StringBuilder()
        sb.append(htmlHeader("Document"))

        var inCodeBlock = false
        var inList = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    sb.append("</code></pre>\n")
                    inCodeBlock = false
                } else {
                    if (inList) { sb.append("</ul>\n"); inList = false }
                    sb.append("<pre><code>")
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                sb.append(escapeHtml(line)).append("\n")
                continue
            }

            if (trimmed.startsWith("# ")) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<h1>").append(formatInline(trimmed.substring(2))).append("</h1>\n")
            } else if (trimmed.startsWith("## ")) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<h2>").append(formatInline(trimmed.substring(3))).append("</h2>\n")
            } else if (trimmed.startsWith("### ")) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<h3>").append(formatInline(trimmed.substring(4))).append("</h3>\n")
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                if (!inList) { sb.append("<ul>\n"); inList = true }
                sb.append("<li>").append(formatInline(trimmed.substring(2))).append("</li>\n")
            } else if (trimmed.startsWith("> ")) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<blockquote>").append(formatInline(trimmed.substring(2))).append("</blockquote>\n")
            } else if (trimmed.isEmpty()) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<br/>\n")
            } else {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<p>").append(formatInline(trimmed)).append("</p>\n")
            }
        }

        if (inCodeBlock) sb.append("</code></pre>\n")
        if (inList) sb.append("</ul>\n")
        sb.append(htmlFooter())
        return sb.toString()
    }

    private fun csvToHtml(csv: String): String {
        val lines = csv.lines()
        val sb = StringBuilder()
        sb.append(htmlHeader("CSV Spreadsheet"))
        sb.append("<table class=\"data-table\">\n")

        for ((index, line) in lines.withIndex()) {
            if (line.isBlank()) continue
            val cols = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
            sb.append("<tr>")
            for (col in cols) {
                val cleaned = col.trim().removeSurrounding("\"").replace("\"\"", "\"")
                if (index == 0) {
                    sb.append("<th>").append(escapeHtml(cleaned)).append("</th>")
                } else {
                    sb.append("<td>").append(escapeHtml(cleaned)).append("</td>")
                }
            }
            sb.append("</tr>\n")
        }
        sb.append("</table>\n")
        sb.append(htmlFooter())
        return sb.toString()
    }

    private fun codeToHtml(code: String, ext: String): String {
        val sb = StringBuilder()
        sb.append(htmlHeader(ext.uppercase()))
        sb.append("<pre class=\"code-block\"><code>")
        sb.append(escapeHtml(code))
        sb.append("</code></pre>\n")
        sb.append(htmlFooter())
        return sb.toString()
    }

    private fun textToHtml(text: String): String {
        val sb = StringBuilder()
        sb.append(htmlHeader("Text Document"))
        for (line in text.lines()) {
            if (line.isBlank()) {
                sb.append("<p class=\"spacer\">&nbsp;</p>\n")
            } else {
                sb.append("<p>").append(escapeHtml(line)).append("</p>\n")
            }
        }
        sb.append(htmlFooter())
        return sb.toString()
    }

    private fun formatInline(text: String): String {
        var res = escapeHtml(text)
        res = res.replace(Regex("\\*\\*(.*?)\\*\\*"), "<strong>$1</strong>")
        res = res.replace(Regex("__(.*?)__"), "<strong>$1</strong>")
        res = res.replace(Regex("\\*(.*?)\\*"), "<em>$1</em>")
        res = res.replace(Regex("_(.*?)_"), "<em>$1</em>")
        res = res.replace(Regex("`(.*?)`"), "<code class=\"inline-code\">$1</code>")
        return res
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun fallbackErrorHtml(fileName: String, e: Exception): String = """
        <!DOCTYPE html>
        <html>
        <head><meta charset="utf-8"/><title>Error</title></head>
        <body style="font-family: sans-serif; padding: 24px; color: #b71c1c;">
            <h2>Failed to load document: $fileName</h2>
            <p>${escapeHtml(e.message ?: "An unexpected error occurred.")}</p>
        </body>
        </html>
    """.trimIndent()

    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun htmlHeader(title: String): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8"/>
            <title>$title</title>
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    font-size: 14pt;
                    line-height: 1.65;
                    color: #1a1a1a;
                    padding: 20px;
                    margin: 0;
                }
                h1 { font-size: 24pt; margin-top: 22px; margin-bottom: 10px; font-weight: 700; border-bottom: 1px solid #e0e0e0; padding-bottom: 8px; }
                h2 { font-size: 19pt; margin-top: 18px; margin-bottom: 8px; font-weight: 600; color: #2e7d32; }
                h3 { font-size: 15pt; margin-top: 14px; margin-bottom: 6px; font-weight: 600; }
                p { margin: 8px 0; text-align: justify; }
                p.spacer { margin: 6px 0; }
                p.list-item { margin: 4px 0; padding-left: 16px; }
                blockquote {
                    margin: 12px 0;
                    padding: 10px 18px;
                    background: #f7f9fa;
                    border-left: 4px solid #2e7d32;
                    font-style: italic;
                }
                ul { margin: 8px 0; padding-left: 24px; }
                li { margin-bottom: 4px; }
                .doc-img-wrap { text-align: center; margin: 16px 0; }
                .doc-img { max-width: 100%; height: auto; border-radius: 4px; }
                pre {
                    background: #f4f6f8;
                    border: 1px solid #d0d7de;
                    border-radius: 6px;
                    padding: 12px;
                    overflow-x: auto;
                    font-family: monospace;
                    font-size: 11pt;
                    line-height: 1.4;
                }
                code.inline-code {
                    background: #f0f0f0;
                    padding: 2px 5px;
                    border-radius: 3px;
                    font-family: monospace;
                    font-size: 12pt;
                }
                table.data-table {
                    border-collapse: collapse;
                    width: 100%;
                    margin: 18px 0;
                }
                table.data-table th, table.data-table td {
                    border: 1px solid #cccccc;
                    padding: 8px 12px;
                    text-align: left;
                    font-size: 12pt;
                }
                table.data-table th {
                    background-color: #f2f2f2;
                    font-weight: bold;
                }
            </style>
        </head>
        <body>
    """.trimIndent()

    private fun htmlFooter(): String = """
        </body>
        </html>
    """.trimIndent()
}
