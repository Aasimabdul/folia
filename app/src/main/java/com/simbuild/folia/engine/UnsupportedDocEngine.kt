package com.simbuild.folia.engine

import java.io.File

/**
 * Honest, informative handler for proprietary or desktop-only formats
 * (.dwg, .dxf, .indd, .pages, .numbers, .key, .pub, .vsd, .one):
 *
 * Never pretends a format is supported if a full offline mobile engine does not exist.
 * Discloses the exact technical limitations, extracts any readable metadata or plain text streams,
 * and advises the user on safe conversion alternatives (e.g., exporting as PDF).
 */
object UnsupportedDocEngine {

    fun convertToHtmlFile(sourceFile: File, ext: String, info: FormatInfo, destFile: File) {
        val sb = StringBuilder()
        sb.append(htmlHeader(sourceFile.nameWithoutExtension))

        sb.append("<div class=\"container\">\n")
        sb.append("<div class=\"badge\">").append(escapeHtml(info.supportLevel.label)).append("</div>\n")
        sb.append("<h1>").append(escapeHtml(info.displayName)).append("</h1>\n")

        sb.append("<div class=\"info-card\">\n")
        sb.append("<h3>Format Compatibility Details</h3>\n")
        sb.append("<p><strong>Extension:</strong> .").append(escapeHtml(ext)).append("</p>\n")
        sb.append("<p><strong>MIME Type:</strong> ").append(escapeHtml(info.mimeType)).append("</p>\n")
        sb.append("<p><strong>Category:</strong> ").append(escapeHtml(info.category.displayName)).append("</p>\n")
        sb.append("<p><strong>Status:</strong> ").append(escapeHtml(info.supportLevel.description)).append("</p>\n")
        sb.append("<p><strong>Offline Limitation:</strong> ").append(escapeHtml(info.knownLimitations)).append("</p>\n")
        sb.append("</div>\n")

        // Try extracting readable text chunks from the binary file
        val extractedStrings = extractReadableStrings(sourceFile)
        if (extractedStrings.isNotEmpty()) {
            sb.append("<div class=\"extracted-section\">\n")
            sb.append("<h3>📄 Extracted Text Content (${extractedStrings.size} blocks found)</h3>\n")
            for (str in extractedStrings.take(200)) {
                sb.append("<p class=\"extracted-line\">").append(escapeHtml(str)).append("</p>\n")
            }
            if (extractedStrings.size > 200) {
                sb.append("<p class=\"more-notice\">... ${extractedStrings.size - 200} more blocks truncated ...</p>\n")
            }
            sb.append("</div>\n")
        }

        sb.append("<div class=\"recommendation-card\">\n")
        sb.append("<h3>💡 Recommended Solution</h3>\n")
        sb.append("<p>To view this file with full graphic layout on mobile without uploading private files to third-party servers, open it in its authoring tool on desktop and select <strong>File &rarr; Export as PDF</strong>.</p>\n")
        sb.append("</div>\n")

        sb.append("</div>\n")
        sb.append(htmlFooter())
        destFile.writeText(sb.toString(), Charsets.UTF_8)
    }

    private fun extractReadableStrings(file: File): List<String> {
        val results = mutableListOf<String>()
        val bytes = try { file.readBytes() } catch (_: Exception) { return results }

        var idx = 0
        val sb = StringBuilder()
        while (idx < bytes.size) {
            val b = bytes[idx].toInt() and 0xFF
            if (b in 32..126) {
                sb.append(b.toChar())
            } else if (b == 10 || b == 13) {
                if (sb.length >= 4) {
                    val trimmed = sb.toString().trim()
                    if (trimmed.length >= 4 && !trimmed.all { it.isDigit() }) {
                        results.add(trimmed)
                    }
                }
                sb.clear()
            } else {
                if (sb.length >= 4) {
                    val trimmed = sb.toString().trim()
                    if (trimmed.length >= 4 && !trimmed.all { it.isDigit() }) {
                        results.add(trimmed)
                    }
                }
                sb.clear()
            }
            idx++
        }
        return results
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
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    background: #fdfdfd;
                    padding: 20px;
                    margin: 0;
                    color: #212121;
                    line-height: 1.6;
                }
                .container { max-width: 800px; margin: 0 auto; }
                .badge {
                    display: inline-block;
                    background: #fff3e0;
                    color: #e65100;
                    font-weight: 700;
                    font-size: 11pt;
                    padding: 4px 10px;
                    border-radius: 14px;
                    margin-bottom: 12px;
                }
                h1 { font-size: 20pt; margin-top: 0; color: #263238; }
                .info-card {
                    background: #ffffff;
                    border: 1px solid #e0e0e0;
                    border-radius: 8px;
                    padding: 16px;
                    margin-bottom: 20px;
                    box-shadow: 0 1px 3px rgba(0,0,0,0.05);
                }
                .info-card h3 { margin-top: 0; color: #37474f; font-size: 14pt; }
                .info-card p { margin: 6px 0; font-size: 11pt; }
                .extracted-section {
                    background: #f5f5f5;
                    border: 1px solid #e0e0e0;
                    border-radius: 8px;
                    padding: 16px;
                    margin-bottom: 20px;
                }
                .extracted-section h3 { margin-top: 0; font-size: 13pt; color: #2e7d32; }
                .extracted-line { margin: 4px 0; font-family: monospace; font-size: 10pt; }
                .more-notice { font-style: italic; color: #757575; font-size: 10pt; }
                .recommendation-card {
                    background: #e8f5e9;
                    border: 1px solid #c8e6c9;
                    border-radius: 8px;
                    padding: 16px;
                }
                .recommendation-card h3 { margin-top: 0; color: #1b5e20; font-size: 13pt; }
                .recommendation-card p { margin: 4px 0; font-size: 11pt; color: #2e7d32; }
            </style>
        </head>
        <body>
    """.trimIndent()

    private fun htmlFooter(): String = "</body></html>"
}
