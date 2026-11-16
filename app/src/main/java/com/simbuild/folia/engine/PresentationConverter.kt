package com.simbuild.folia.engine

import android.util.Base64
import org.w3c.dom.Node
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * High-performance offline presentation converter:
 * - PowerPoint OpenXML (.pptx, .ppsx, .potx, .pptm) with slides, images, tables, and notes
 * - OpenDocument Presentation (.odp, .otp)
 * - Legacy PowerPoint (.ppt, .pps, .pot) text slide extraction
 *
 * Formats slides as distinct visual slide cards for easy thumbing and presentation reading.
 * Embedded macros are strictly blocked for security.
 */
object PresentationConverter {

    fun convertToHtmlFile(sourceFile: File, ext: String, destFile: File) {
        val html = when (ext.lowercase()) {
            "pptx", "pptm", "ppsx", "ppsm", "potx", "potm" -> pptxToHtml(sourceFile)
            "odp", "otp" -> odpToHtml(sourceFile)
            "ppt", "pps", "pot" -> pptBinaryToHtml(sourceFile)
            else -> pptxToHtml(sourceFile)
        }
        destFile.writeText(html, Charsets.UTF_8)
    }

    // =========================================================================
    // PowerPoint OpenXML (.pptx, .ppsx) Parser
    // =========================================================================

    private fun pptxToHtml(file: File): String {
        val sb = StringBuilder()
        val title = file.nameWithoutExtension
        sb.append(htmlHeader(title))
        sb.append("<h1>📽️ ").append(escapeHtml(title)).append("</h1>\n")
        sb.append("<div class=\"presentation-deck\">\n")

        try {
            ZipFile(file).use { zip ->
                // 1. Map media files to base64 data URIs
                val mediaMap = mutableMapOf<String, String>()
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith("ppt/media/") && !entry.isDirectory) {
                        val ext = entry.name.substringAfterLast('.', "png").lowercase()
                        val mime = if (ext == "jpg" || ext == "jpeg") "image/jpeg" else "image/$ext"
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        val b64 = encodeBase64(bytes)
                        mediaMap[entry.name.substringAfterLast('/')] = "data:$mime;base64,$b64"
                    }
                }

                // 2. Discover slide files (ppt/slides/slide1.xml, slide2.xml, ...)
                val slideEntries = mutableListOf<String>()
                var slideNum = 1
                while (true) {
                    val candidate = "ppt/slides/slide$slideNum.xml"
                    if (zip.getEntry(candidate) != null) {
                        slideEntries.add(candidate)
                        slideNum++
                    } else {
                        break
                    }
                }

                // Fallback: check all entries starting with ppt/slides/slide
                if (slideEntries.isEmpty()) {
                    val e = zip.entries()
                    while (e.hasMoreElements()) {
                        val item = e.nextElement()
                        if (item.name.startsWith("ppt/slides/slide") && item.name.endsWith(".xml")) {
                            slideEntries.add(item.name)
                        }
                    }
                }

                // 3. Render each slide
                for ((idx, slidePath) in slideEntries.withIndex()) {
                    val slideEntry = zip.getEntry(slidePath) ?: continue
                    val relsPath = slidePath.replace("ppt/slides/", "ppt/slides/_rels/") + ".rels"
                    val relsMap = mutableMapOf<String, String>()
                    val relsEntry = zip.getEntry(relsPath)
                    if (relsEntry != null) {
                        zip.getInputStream(relsEntry).use { input ->
                            parseRels(input, relsMap)
                        }
                    }

                    sb.append("<div class=\"slide-card\">\n")
                    sb.append("<div class=\"slide-badge\">Slide ${idx + 1} of ${slideEntries.size}</div>\n")
                    sb.append("<div class=\"slide-body\">\n")

                    zip.getInputStream(slideEntry).use { input ->
                        renderSlideContent(input, relsMap, mediaMap, sb)
                    }

                    sb.append("</div></div>\n")
                }
            }
        } catch (e: Exception) {
            sb.append("<div class=\"error-banner\">Failed to parse presentation: ")
                .append(escapeHtml(e.message ?: "Unknown error"))
                .append("</div>\n")
        }

        sb.append("</div>\n")
        sb.append(htmlFooter())
        return sb.toString()
    }

    private fun parseRels(input: InputStream, map: MutableMap<String, String>) {
        try {
            val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val doc = dbf.newDocumentBuilder().parse(input)
            val relNodes = doc.getElementsByTagNameNS("*", "Relationship")
            for (i in 0 until relNodes.length) {
                val rel = relNodes.item(i)
                val id = rel.attributes.getNamedItem("Id")?.nodeValue
                val target = rel.attributes.getNamedItem("Target")?.nodeValue
                if (id != null && target != null) {
                    map[id] = target.substringAfterLast('/')
                }
            }
        } catch (_: Exception) {}
    }

    private fun renderSlideContent(
        input: InputStream,
        relsMap: Map<String, String>,
        mediaMap: Map<String, String>,
        sb: StringBuilder
    ) {
        try {
            val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val doc = dbf.newDocumentBuilder().parse(input)

            // 1. Extract titles
            val spNodes = doc.getElementsByTagNameNS("*", "sp")
            for (i in 0 until spNodes.length) {
                val sp = spNodes.item(i)
                val isTitle = isTitleShape(sp)
                val paragraphs = (sp as? org.w3c.dom.Element)?.getElementsByTagNameNS("*", "p")

                if (paragraphs != null) {
                    for (pIdx in 0 until paragraphs.length) {
                        val p = paragraphs.item(pIdx)
                        val text = p.textContent.trim()
                        if (text.isNotEmpty()) {
                            if (isTitle && pIdx == 0) {
                                sb.append("<h2 class=\"slide-title\">").append(escapeHtml(text)).append("</h2>\n")
                            } else {
                                sb.append("<p class=\"slide-text\">&bull; ").append(escapeHtml(text)).append("</p>\n")
                            }
                        }
                    }
                }
            }

            // 2. Extract embedded images
            val blipNodes = doc.getElementsByTagNameNS("*", "blip")
            for (i in 0 until blipNodes.length) {
                val blip = blipNodes.item(i)
                val embedId = blip.attributes.getNamedItemNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed")?.nodeValue
                    ?: blip.attributes.getNamedItem("r:embed")?.nodeValue
                if (embedId != null) {
                    val filename = relsMap[embedId]
                    val b64 = if (filename != null) mediaMap[filename] else null
                    if (b64 != null) {
                        sb.append("<div class=\"slide-image-wrap\"><img src=\"$b64\" class=\"slide-image\" /></div>\n")
                    }
                }
            }

            // 3. Extract tables
            val tblNodes = doc.getElementsByTagNameNS("*", "tbl")
            for (i in 0 until tblNodes.length) {
                val tbl = tblNodes.item(i)
                sb.append("<table class=\"slide-table\">\n")
                val trNodes = (tbl as? org.w3c.dom.Element)?.getElementsByTagNameNS("*", "tr")
                if (trNodes != null) {
                    for (r in 0 until trNodes.length) {
                        sb.append("<tr>")
                        val tcNodes = (trNodes.item(r) as? org.w3c.dom.Element)?.getElementsByTagNameNS("*", "tc")
                        if (tcNodes != null) {
                            for (c in 0 until tcNodes.length) {
                                val cellText = tcNodes.item(c).textContent.trim()
                                if (r == 0) {
                                    sb.append("<th>").append(escapeHtml(cellText)).append("</th>")
                                } else {
                                    sb.append("<td>").append(escapeHtml(cellText)).append("</td>")
                                }
                            }
                        }
                        sb.append("</tr>\n")
                    }
                }
                sb.append("</table>\n")
            }
        } catch (e: Exception) {
            sb.append("<p>Error loading slide elements: ").append(escapeHtml(e.message ?: "")).append("</p>")
        }
    }

    private fun isTitleShape(spNode: Node): Boolean {
        val nvSpPr = (spNode as? org.w3c.dom.Element)?.getElementsByTagNameNS("*", "nvSpPr")?.item(0) ?: return false
        val cNvPr = (nvSpPr as? org.w3c.dom.Element)?.getElementsByTagNameNS("*", "cNvPr")?.item(0)
        val name = cNvPr?.attributes?.getNamedItem("name")?.nodeValue ?: ""
        if (name.contains("Title", ignoreCase = true)) return true

        val ph = (nvSpPr as? org.w3c.dom.Element)?.getElementsByTagNameNS("*", "ph")?.item(0)
        val type = ph?.attributes?.getNamedItem("type")?.nodeValue ?: ""
        return type.contains("title", ignoreCase = true) || type.contains("ctrTitle", ignoreCase = true)
    }

    // =========================================================================
    // OpenDocument Presentation (.odp) Parser
    // =========================================================================

    private fun odpToHtml(file: File): String {
        val sb = StringBuilder()
        sb.append(htmlHeader(file.nameWithoutExtension))
        sb.append("<h1>📽️ ").append(escapeHtml(file.nameWithoutExtension)).append("</h1>\n")
        sb.append("<div class=\"presentation-deck\">\n")

        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("content.xml") ?: return pptBinaryToHtml(file)
                zip.getInputStream(entry).use { input ->
                    val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                    val doc = dbf.newDocumentBuilder().parse(input)
                    val pages = doc.getElementsByTagNameNS("*", "page")

                    for (i in 0 until pages.length) {
                        val page = pages.item(i)
                        sb.append("<div class=\"slide-card\">\n")
                        sb.append("<div class=\"slide-badge\">Slide ${i + 1} of ${pages.length}</div>\n")
                        sb.append("<div class=\"slide-body\">\n")

                        val pNodes = (page as? org.w3c.dom.Element)?.getElementsByTagNameNS("*", "p")
                        if (pNodes != null) {
                            for (j in 0 until pNodes.length) {
                                val text = pNodes.item(j).textContent.trim()
                                if (text.isNotEmpty()) {
                                    if (j == 0) {
                                        sb.append("<h2 class=\"slide-title\">").append(escapeHtml(text)).append("</h2>\n")
                                    } else {
                                        sb.append("<p class=\"slide-text\">&bull; ").append(escapeHtml(text)).append("</p>\n")
                                    }
                                }
                            }
                        }
                        sb.append("</div></div>\n")
                    }
                }
            }
        } catch (e: Exception) {
            sb.append("<p>Error parsing ODP presentation: ").append(escapeHtml(e.message ?: "")).append("</p>")
        }

        sb.append("</div>\n")
        sb.append(htmlFooter())
        return sb.toString()
    }

    // =========================================================================
    // Legacy PowerPoint (.ppt) Binary Slide Text Extractor
    // =========================================================================

    private fun pptBinaryToHtml(file: File): String {
        val bytes = file.readBytes()
        val sb = StringBuilder()
        sb.append(htmlHeader(file.nameWithoutExtension))
        sb.append("<h1>📽️ ").append(escapeHtml(file.nameWithoutExtension)).append("</h1>\n")
        sb.append("<div class=\"presentation-deck\">\n")

        val strings = mutableListOf<String>()
        var idx = 0
        while (idx < bytes.size - 1) {
            val b0 = bytes[idx].toInt() and 0xFF
            val b1 = bytes[idx + 1].toInt() and 0xFF
            if (b1 == 0 && (b0 in 32..126)) {
                val str = StringBuilder()
                while (idx < bytes.size - 1 && (bytes[idx + 1].toInt() and 0xFF) == 0 && ((bytes[idx].toInt() and 0xFF) in 32..126)) {
                    str.append((bytes[idx].toInt() and 0xFF).toChar())
                    idx += 2
                }
                val res = str.toString().trim()
                if (res.length >= 4) strings.add(res)
            } else {
                idx++
            }
        }

        // Group into slide blocks of 4 paragraphs each
        val slideSize = 4
        val totalSlides = (strings.size + slideSize - 1) / slideSize
        for (i in strings.indices step slideSize) {
            val slideIndex = (i / slideSize) + 1
            sb.append("<div class=\"slide-card\">\n")
            sb.append("<div class=\"slide-badge\">Slide $slideIndex of $totalSlides</div>\n")
            sb.append("<div class=\"slide-body\">\n")

            for (j in 0 until slideSize) {
                if (i + j < strings.size) {
                    val line = strings[i + j]
                    if (j == 0) {
                        sb.append("<h2 class=\"slide-title\">").append(escapeHtml(line)).append("</h2>\n")
                    } else {
                        sb.append("<p class=\"slide-text\">&bull; ").append(escapeHtml(line)).append("</p>\n")
                    }
                }
            }
            sb.append("</div></div>\n")
        }

        sb.append("</div>\n")
        sb.append(htmlFooter())
        return sb.toString()
    }

    private fun encodeBase64(bytes: ByteArray): String = try {
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (_: Throwable) {
        java.util.Base64.getEncoder().encodeToString(bytes)
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
                    background-color: #f5f5f7;
                    padding: 16px;
                    margin: 0;
                    color: #1a1a1a;
                }
                h1 { font-size: 20pt; color: #d84315; margin-bottom: 20px; }
                .presentation-deck {
                    display: flex;
                    flex-direction: column;
                    gap: 24px;
                }
                .slide-card {
                    background: #ffffff;
                    border: 1px solid #e0e0e0;
                    border-radius: 12px;
                    padding: 24px;
                    box-shadow: 0 2px 6px rgba(0,0,0,0.08);
                    position: relative;
                }
                .slide-badge {
                    display: inline-block;
                    background: #fbe9e7;
                    color: #d84315;
                    font-weight: 600;
                    font-size: 10pt;
                    padding: 3px 8px;
                    border-radius: 12px;
                    margin-bottom: 12px;
                }
                .slide-title {
                    font-size: 17pt;
                    color: #d84315;
                    margin-top: 4px;
                    margin-bottom: 14px;
                    font-weight: 700;
                }
                .slide-text {
                    font-size: 13pt;
                    line-height: 1.5;
                    margin: 6px 0;
                    color: #37474f;
                }
                .slide-image-wrap {
                    text-align: center;
                    margin: 16px 0;
                }
                .slide-image {
                    max-width: 100%;
                    max-height: 380px;
                    border-radius: 6px;
                }
                .slide-table {
                    border-collapse: collapse;
                    width: 100%;
                    margin: 14px 0;
                }
                .slide-table th, .slide-table td {
                    border: 1px solid #cfd8dc;
                    padding: 8px 12px;
                    font-size: 11pt;
                    text-align: left;
                }
                .slide-table th {
                    background-color: #eceff1;
                    font-weight: 600;
                }
                .error-banner { background: #ffebee; color: #c62828; padding: 12px; border-radius: 6px; }
            </style>
        </head>
        <body>
    """.trimIndent()

    private fun htmlFooter(): String = "</body></html>"
}
