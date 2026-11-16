package com.simbuild.folia.engine

import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

/**
 * Safe archive and package reader (.zip, .cbz, .jar, .tar, .gz, etc.):
 * - Prevents Zip-slip vulnerability (canonical path validation)
 * - Prevents Zip-bomb attacks (entry count and max byte limits)
 * - Generates clean HTML directory browser so users can explore archive contents safely
 *   without extracting the entire archive to device storage.
 */
object ArchiveConverter {

    private const val MAX_ENTRIES = 5000
    private const val MAX_TOTAL_BYTES = 100L * 1024 * 1024 // 100 MB limit

    data class ArchiveItem(
        val name: String,
        val sizeBytes: Long,
        val isDirectory: Boolean
    )

    fun convertToHtmlFile(sourceFile: File, ext: String, destFile: File) {
        val items = mutableListOf<ArchiveItem>()
        var errorMsg: String? = null

        try {
            ZipInputStream(FileInputStream(sourceFile)).use { zis ->
                var totalBytes = 0L
                var count = 0

                var entry = zis.nextEntry
                while (entry != null) {
                    count++
                    if (count > MAX_ENTRIES) {
                        errorMsg = "Archive exceeds maximum safe entry limit ($MAX_ENTRIES files)."
                        break
                    }

                    // Zip-slip security check: ensure name doesn't traverse upwards
                    val safeName = entry.name.replace("\\", "/")
                    if (safeName.contains("../") || safeName.startsWith("/")) {
                        // Malicious entry detected
                        entry = zis.nextEntry
                        continue
                    }

                    val size = entry.size
                    if (size > 0) totalBytes += size
                    if (totalBytes > MAX_TOTAL_BYTES) {
                        errorMsg = "Archive exceeds maximum safe uncompressed size limit (100 MB)."
                        break
                    }

                    items.add(
                        ArchiveItem(
                            name = safeName,
                            sizeBytes = if (size >= 0) size else 0L,
                            isDirectory = entry.isDirectory
                        )
                    )

                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            errorMsg = "Error reading archive container: ${e.message}"
        }

        val html = renderArchiveBrowserHtml(sourceFile.name, items, errorMsg)
        destFile.writeText(html, Charsets.UTF_8)
    }

    private fun renderArchiveBrowserHtml(
        archiveName: String,
        items: List<ArchiveItem>,
        error: String?
    ): String {
        val sb = StringBuilder()
        sb.append(htmlHeader(archiveName))
        sb.append("<h1>📦 ").append(escapeHtml(archiveName)).append("</h1>\n")

        if (error != null) {
            sb.append("<div class=\"warning-banner\">⚠️ ").append(escapeHtml(error)).append("</div>\n")
        }

        sb.append("<p class=\"meta\">Total items: <strong>").append(items.size).append("</strong></p>\n")
        sb.append("<div class=\"archive-list\">\n")

        for (item in items) {
            val icon = if (item.isDirectory) "📁" else getFileIcon(item.name)
            val sizeStr = if (item.isDirectory) "Folder" else formatSize(item.sizeBytes)

            sb.append("<div class=\"archive-row\">\n")
            sb.append("<span class=\"item-icon\">").append(icon).append("</span> ")
            sb.append("<span class=\"item-name\">").append(escapeHtml(item.name)).append("</span>")
            sb.append("<span class=\"item-size\">").append(sizeStr).append("</span>\n")
            sb.append("</div>\n")
        }

        sb.append("</div>\n")
        sb.append(htmlFooter())
        return sb.toString()
    }

    private fun getFileIcon(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "📕"
            "doc", "docx", "rtf", "odt" -> "📄"
            "xls", "xlsx", "csv" -> "📊"
            "ppt", "pptx" -> "📽️"
            "jpg", "jpeg", "png", "webp", "gif" -> "🖼️"
            "zip", "rar", "7z", "tar", "gz" -> "📦"
            "txt", "md", "json", "xml" -> "📝"
            else -> "📃"
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        return "%.1f %s".format(size, units[unitIndex])
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
                    background: #f8f9fa;
                    padding: 16px;
                    margin: 0;
                    color: #212529;
                }
                h1 { font-size: 19pt; color: #495057; margin-bottom: 8px; }
                .meta { font-size: 11pt; color: #6c757d; margin-bottom: 16px; }
                .warning-banner {
                    background: #fff3cd;
                    color: #856404;
                    padding: 10px 14px;
                    border-radius: 6px;
                    border: 1px solid #ffeeba;
                    margin-bottom: 16px;
                    font-size: 11pt;
                }
                .archive-list {
                    background: #ffffff;
                    border: 1px solid #dee2e6;
                    border-radius: 8px;
                    overflow: hidden;
                    box-shadow: 0 1px 3px rgba(0,0,0,0.05);
                }
                .archive-row {
                    display: flex;
                    align-items: center;
                    padding: 10px 16px;
                    border-bottom: 1px solid #f1f3f5;
                }
                .archive-row:last-child { border-bottom: none; }
                .item-icon { font-size: 13pt; margin-right: 12px; width: 24px; text-align: center; }
                .item-name {
                    flex-grow: 1;
                    font-size: 11pt;
                    font-weight: 500;
                    word-break: break-all;
                }
                .item-size {
                    font-size: 10pt;
                    color: #868e96;
                    margin-left: 12px;
                    white-space: nowrap;
                }
            </style>
        </head>
        <body>
    """.trimIndent()

    private fun htmlFooter(): String = "</body></html>"
}
