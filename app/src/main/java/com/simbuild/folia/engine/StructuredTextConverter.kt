package com.simbuild.folia.engine

import java.io.File

/**
 * Structured text, source code, email, calendar, and safe markup converter:
 * - Code files (.py, .java, .kt, .c, .cpp, .js, .ts, .json, .yaml, .sql, .sh, etc.) with line numbers
 * - Email messages (.eml, .mht, .mhtml)
 * - Calendar (.ics) & Contacts (.vcf)
 * - Safe HTML sanitization: treats all scripts, styles, and active content as untrusted input.
 */
object StructuredTextConverter {

    fun convertToHtmlFile(sourceFile: File, ext: String, destFile: File) {
        val text = sourceFile.readText(Charsets.UTF_8)
        val html = when (ext.lowercase()) {
            "eml", "mht", "mhtml" -> emailToHtml(text, sourceFile.nameWithoutExtension)
            "vcf" -> vcfToHtml(text, sourceFile.nameWithoutExtension)
            "ics" -> icsToHtml(text, sourceFile.nameWithoutExtension)
            "html", "htm", "xhtml" -> sanitizeHtml(text, sourceFile.nameWithoutExtension)
            "rst" -> rstToHtml(text, sourceFile.nameWithoutExtension)
            "tex" -> codeToHtml(text, "LaTeX", sourceFile.nameWithoutExtension)
            else -> codeToHtml(text, ext.uppercase(), sourceFile.nameWithoutExtension)
        }
        destFile.writeText(html, Charsets.UTF_8)
    }

    // =========================================================================
    // Code with Line Numbers and Syntax Presentation
    // =========================================================================

    private fun codeToHtml(code: String, language: String, title: String): String {
        val sb = StringBuilder()
        sb.append(htmlHeader(title))
        sb.append("<div class=\"code-header\"><span class=\"lang-badge\">").append(escapeHtml(language)).append("</span> ")
        sb.append("<span class=\"code-filename\">").append(escapeHtml(title)).append("</span></div>\n")
        sb.append("<div class=\"code-editor-wrap\"><table class=\"code-table\">\n")

        val lines = code.lines()
        for ((idx, line) in lines.withIndex()) {
            val lineNum = idx + 1
            val formattedLine = highlightKeywords(escapeHtml(line))
            sb.append("<tr>")
            sb.append("<td class=\"line-num\">").append(lineNum).append("</td>")
            sb.append("<td class=\"line-code\"><pre>").append(if (formattedLine.isEmpty()) "&nbsp;" else formattedLine).append("</pre></td>")
            sb.append("</tr>\n")
        }

        sb.append("</table></div>\n")
        sb.append(htmlFooter())
        return sb.toString()
    }

    // Fast keyword highlighting without external runtimes.
    // One single-pass regex: replacing keyword by keyword would also match the word "class"
    // inside the <span class="kw"> markup that earlier replacements inserted, corrupting the HTML.
    private val keywordRegex = Regex(
        "\\b(?:" + listOf(
            "fun", "class", "val", "var", "import", "package", "return", "if", "else", "for", "while",
            "public", "private", "protected", "override", "def", "from", "async", "await", "function",
            "const", "let", "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "JOIN"
        ).joinToString("|") + ")\\b"
    )

    private fun highlightKeywords(escaped: String): String =
        keywordRegex.replace(escaped) { "<span class=\"kw\">${it.value}</span>" }

    // =========================================================================
    // Safe HTML Sanitization (Active Content Blocked)
    // =========================================================================

    private fun sanitizeHtml(rawHtml: String, title: String): String {
        // Strip <script>...</script>, <iframe...>, <object...>, onload=, onclick=, javascript:
        var clean = rawHtml.replace(Regex("(?is)<script.*?</script>"), "")
        clean = clean.replace(Regex("(?is)<iframe.*?</iframe>"), "")
        clean = clean.replace(Regex("(?is)<object.*?</object>"), "")
        clean = clean.replace(Regex("(?i)\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)"), "")
        clean = clean.replace(Regex("(?i)javascript:"), "")

        // If no <html> wrapper, add standard header/footer
        return if (!clean.contains("<html", ignoreCase = true)) {
            htmlHeader(title) + clean + htmlFooter()
        } else {
            clean
        }
    }

    // =========================================================================
    // Email Message (.eml, .mht) Parser
    // =========================================================================

    private fun emailToHtml(emlText: String, title: String): String {
        val sb = StringBuilder()
        sb.append(htmlHeader(title))

        val headers = mutableMapOf<String, String>()
        val bodyBuilder = StringBuilder()
        var inHeaders = true

        for (line in emlText.lines()) {
            if (inHeaders) {
                if (line.isBlank()) {
                    inHeaders = false
                    continue
                }
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim()
                    val value = line.substring(colon + 1).trim()
                    headers[key] = value
                }
            } else {
                bodyBuilder.append(line).append("\n")
            }
        }

        sb.append("<div class=\"email-card\">\n")
        sb.append("<h1 class=\"email-subject\">✉️ ").append(escapeHtml(headers["Subject"] ?: title)).append("</h1>\n")
        sb.append("<div class=\"email-meta\">\n")
        headers["From"]?.let { sb.append("<p><strong>From:</strong> ").append(escapeHtml(it)).append("</p>\n") }
        headers["To"]?.let { sb.append("<p><strong>To:</strong> ").append(escapeHtml(it)).append("</p>\n") }
        headers["Date"]?.let { sb.append("<p><strong>Date:</strong> ").append(escapeHtml(it)).append("</p>\n") }
        sb.append("</div><hr class=\"divider\"/>\n")

        sb.append("<div class=\"email-body\">\n")
        for (line in bodyBuilder.toString().lines()) {
            if (line.isBlank()) {
                sb.append("<p class=\"spacer\">&nbsp;</p>\n")
            } else {
                sb.append("<p>").append(escapeHtml(line)).append("</p>\n")
            }
        }
        sb.append("</div></div>\n")
        sb.append(htmlFooter())
        return sb.toString()
    }

    // =========================================================================
    // Contact vCard (.vcf) Parser
    // =========================================================================

    private fun vcfToHtml(vcfText: String, title: String): String {
        val sb = StringBuilder()
        sb.append(htmlHeader(title))
        sb.append("<h1>👤 Contact Card</h1>\n")
        sb.append("<div class=\"contact-card\">\n")

        for (line in vcfText.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("FN:")) {
                sb.append("<h2 class=\"contact-name\">").append(escapeHtml(trimmed.substring(3))).append("</h2>\n")
            } else if (trimmed.startsWith("TEL")) {
                val num = trimmed.substringAfter(':')
                sb.append("<p>📞 <strong>Phone:</strong> ").append(escapeHtml(num)).append("</p>\n")
            } else if (trimmed.startsWith("EMAIL")) {
                val email = trimmed.substringAfter(':')
                sb.append("<p>✉️ <strong>Email:</strong> ").append(escapeHtml(email)).append("</p>\n")
            } else if (trimmed.startsWith("ORG:")) {
                sb.append("<p>🏢 <strong>Organization:</strong> ").append(escapeHtml(trimmed.substring(4))).append("</p>\n")
            } else if (trimmed.startsWith("TITLE:")) {
                sb.append("<p>💼 <strong>Title:</strong> ").append(escapeHtml(trimmed.substring(6))).append("</p>\n")
            }
        }

        sb.append("</div>\n")
        sb.append(htmlFooter())
        return sb.toString()
    }

    // =========================================================================
    // Calendar (.ics) Parser
    // =========================================================================

    private fun icsToHtml(icsText: String, title: String): String {
        val sb = StringBuilder()
        sb.append(htmlHeader(title))
        sb.append("<h1>📅 Calendar Event</h1>\n")
        sb.append("<div class=\"contact-card\">\n")

        var inEvent = false
        for (line in icsText.lines()) {
            val trimmed = line.trim()
            if (trimmed == "BEGIN:VEVENT") inEvent = true
            if (trimmed == "END:VEVENT") inEvent = false

            if (inEvent) {
                if (trimmed.startsWith("SUMMARY:")) {
                    sb.append("<h2 class=\"contact-name\">").append(escapeHtml(trimmed.substring(8))).append("</h2>\n")
                } else if (trimmed.startsWith("DTSTART")) {
                    sb.append("<p>🕒 <strong>Start:</strong> ").append(escapeHtml(trimmed.substringAfter(':'))).append("</p>\n")
                } else if (trimmed.startsWith("DTEND")) {
                    sb.append("<p>🏁 <strong>End:</strong> ").append(escapeHtml(trimmed.substringAfter(':'))).append("</p>\n")
                } else if (trimmed.startsWith("LOCATION:")) {
                    sb.append("<p>📍 <strong>Location:</strong> ").append(escapeHtml(trimmed.substring(9))).append("</p>\n")
                } else if (trimmed.startsWith("DESCRIPTION:")) {
                    sb.append("<p>📝 <strong>Details:</strong> ").append(escapeHtml(trimmed.substring(12))).append("</p>\n")
                }
            }
        }

        sb.append("</div>\n")
        sb.append(htmlFooter())
        return sb.toString()
    }

    // =========================================================================
    // reStructuredText (.rst) Parser
    // =========================================================================

    private fun rstToHtml(rstText: String, title: String): String {
        val sb = StringBuilder()
        sb.append(htmlHeader(title))
        val lines = rstText.lines()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val nextLine = if (i + 1 < lines.size) lines[i + 1] else ""

            if (nextLine.isNotEmpty() && nextLine.all { it == '=' || it == '-' || it == '~' } && nextLine.length >= line.length) {
                val headerTag = if (nextLine[0] == '=') "h1" else "h2"
                sb.append("<").append(headerTag).append(">").append(escapeHtml(line.trim())).append("</").append(headerTag).append(">\n")
                i += 2
                continue
            }

            val trimmed = line.trim()
            if (trimmed.startsWith(".. ")) {
                sb.append("<blockquote>").append(escapeHtml(trimmed.substring(3))).append("</blockquote>\n")
            } else if (trimmed.isEmpty()) {
                sb.append("<p class=\"spacer\">&nbsp;</p>\n")
            } else {
                sb.append("<p>").append(escapeHtml(trimmed)).append("</p>\n")
            }
            i++
        }

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
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    padding: 16px;
                    margin: 0;
                    color: #212121;
                    background: #ffffff;
                }
                .code-header {
                    background: #f1f3f5;
                    padding: 8px 12px;
                    border-radius: 6px 6px 0 0;
                    border: 1px solid #dee2e6;
                    border-bottom: none;
                    display: flex;
                    align-items: center;
                    gap: 8px;
                }
                .lang-badge {
                    background: #1976d2;
                    color: white;
                    font-size: 9pt;
                    font-weight: 700;
                    padding: 2px 6px;
                    border-radius: 4px;
                }
                .code-filename { font-weight: 600; font-size: 11pt; color: #495057; }
                .code-editor-wrap {
                    border: 1px solid #dee2e6;
                    border-radius: 0 0 6px 6px;
                    overflow-x: auto;
                    background: #fafafa;
                }
                table.code-table {
                    border-collapse: collapse;
                    width: 100%;
                    font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
                    font-size: 11pt;
                }
                td.line-num {
                    width: 45px;
                    text-align: right;
                    padding: 3px 10px;
                    color: #adb5bd;
                    background: #f8f9fa;
                    border-right: 1px solid #e9ecef;
                    user-select: none;
                    font-size: 10pt;
                }
                td.line-code {
                    padding: 3px 12px;
                    white-space: pre;
                }
                pre { margin: 0; font-family: inherit; }
                .kw { color: #d32f2f; font-weight: bold; }
                .email-card, .contact-card {
                    background: #ffffff;
                    border: 1px solid #e0e0e0;
                    border-radius: 8px;
                    padding: 20px;
                    box-shadow: 0 1px 3px rgba(0,0,0,0.05);
                }
                .email-subject { font-size: 18pt; color: #1565c0; margin-top: 0; }
                .email-meta p { margin: 4px 0; color: #555555; font-size: 11pt; }
                .divider { border: 0; border-top: 1px solid #e0e0e0; margin: 16px 0; }
                .contact-name { font-size: 18pt; color: #2e7d32; margin-top: 0; }
                p.spacer { margin: 6px 0; }
            </style>
        </head>
        <body>
    """.trimIndent()

    private fun htmlFooter(): String = "</body></html>"
}
