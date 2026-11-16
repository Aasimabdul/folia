package com.simbuild.folia.engine

import java.io.File

/**
 * Converts text-based documents (Markdown, CSV, JSON, code) into styled HTML
 * so MuPDF can render them with high typographic quality as reflowable documents.
 */
object FormatConverter {

    fun convertToHtmlFile(sourceFile: File, ext: String, destFile: File) {
        val text = sourceFile.readText(Charsets.UTF_8)
        val htmlContent = when (ext.lowercase()) {
            "md", "markdown" -> markdownToHtml(text)
            "csv" -> csvToHtml(text)
            "json", "xml", "kt", "java", "py", "c", "cpp", "js", "ts", "html", "htm" -> codeToHtml(text, ext)
            else -> textToHtml(text)
        }
        destFile.writeText(htmlContent, Charsets.UTF_8)
    }

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

    private fun htmlHeader(title: String): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8"/>
            <title>$title</title>
            <style>
                body {
                    font-family: sans-serif;
                    font-size: 14pt;
                    line-height: 1.6;
                    color: #1a1a1a;
                    padding: 16px;
                    margin: 0;
                }
                h1 { font-size: 22pt; margin-top: 18px; margin-bottom: 8px; border-bottom: 2px solid #e0e0e0; padding-bottom: 6px; }
                h2 { font-size: 18pt; margin-top: 16px; margin-bottom: 6px; color: #2e7d32; }
                h3 { font-size: 15pt; margin-top: 12px; margin-bottom: 4px; }
                p { margin: 6px 0; text-align: justify; }
                p.spacer { margin: 4px 0; }
                blockquote {
                    margin: 12px 0;
                    padding: 8px 16px;
                    background: #f5f5f5;
                    border-left: 4px solid #2e7d32;
                    font-style: italic;
                }
                ul { margin: 8px 0; padding-left: 24px; }
                li { margin-bottom: 4px; }
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
                    margin: 16px 0;
                }
                table.data-table th, table.data-table td {
                    border: 1px solid #cccccc;
                    padding: 8px 10px;
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
