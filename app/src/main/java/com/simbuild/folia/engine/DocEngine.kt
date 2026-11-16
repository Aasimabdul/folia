package com.simbuild.folia.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Table of contents chapter/section record.
 */
data class TocItem(
    val title: String,
    val page: Int,
    val level: Int = 0
)

/**
 * In-document search result match.
 */
data class SearchResult(
    val page: Int,
    val snippet: String,
    val matchIndex: Int = 0
)

/**
 * Common modular abstraction for all document formats in Folia.
 */
interface DocEngine : AutoCloseable {
    val title: String
    val format: String
    val fileSize: Long
    val pageCount: Int

    /** Explicit capability status */
    val supportStatus: SupportLevel get() = SupportLevel.NATIVE_RENDERING

    /** True for reflowable documents: text wraps and adapts to screen width */
    val isReflowable: Boolean

    /** Page size in points (1/72 inch). */
    fun pageSize(index: Int): Pair<Float, Float>

    /** Render one page to a bitmap [widthPx] wide. */
    fun render(index: Int, widthPx: Int): Bitmap

    /** Reflow text documents for the current screen. */
    fun relayout(widthPt: Float, heightPt: Float, fontSizePt: Float)

    /** Extract plain text from page for search, clipboard copy, or accessibility. */
    fun extractText(index: Int): String? = null

    /** Retrieve table of contents (TOC / Outlines) if available. */
    fun tableOfContents(): List<TocItem> = emptyList()

    /** Search document for occurrences of query string. */
    fun search(query: String): List<SearchResult> = emptyList()
}

/**
 * Modular document dispatcher that sniffs formats and routes to the appropriate handler.
 */
object DocEngines {

    fun open(context: Context, uri: Uri): DocEngine {
        val name = displayName(context, uri)

        val dir = File(context.cacheDir, "open").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }

        // Stream SAF URI safely into a private temporary file
        val tempRaw = File(dir, "source_document")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempRaw.outputStream().use { out -> input.copyTo(out, 64 * 1024) }
        } ?: error("Cannot read document stream from provider: $uri")

        // 1. Identify format using magic bytes and container inspection
        val formatInfo = FormatRegistry.sniffFormat(tempRaw, uri, name)
        val ext = formatInfo.extension
        val rawFile = File(dir, "raw.$ext")
        tempRaw.renameTo(rawFile)

        val size = rawFile.length()

        // 2. Select appropriate handler according to format category
        return when (formatInfo.category) {
            FormatCategory.IMAGE_SCANNED -> {
                if (ext == "svg") {
                    val htmlFile = File(dir, "doc.html")
                    StructuredTextConverter.convertToHtmlFile(rawFile, ext, htmlFile)
                    MuPdfEngine.open(htmlFile.absolutePath, name, formatInfo.displayName, size, formatInfo.supportLevel)
                } else {
                    ImageDocEngine.open(rawFile, name, formatInfo.displayName, size)
                }
            }

            FormatCategory.SPREADSHEET -> {
                val htmlFile = File(dir, "doc.html")
                SpreadsheetConverter.convertToHtmlFile(rawFile, ext, htmlFile)
                MuPdfEngine.open(htmlFile.absolutePath, name, formatInfo.displayName, size, formatInfo.supportLevel)
            }

            FormatCategory.PRESENTATION -> {
                val htmlFile = File(dir, "doc.html")
                PresentationConverter.convertToHtmlFile(rawFile, ext, htmlFile)
                MuPdfEngine.open(htmlFile.absolutePath, name, formatInfo.displayName, size, formatInfo.supportLevel)
            }

            FormatCategory.STRUCTURED_TEXT_CODE -> {
                val htmlFile = File(dir, "doc.html")
                StructuredTextConverter.convertToHtmlFile(rawFile, ext, htmlFile)
                MuPdfEngine.open(htmlFile.absolutePath, name, formatInfo.displayName, size, formatInfo.supportLevel)
            }

            FormatCategory.ARCHIVE -> {
                val htmlFile = File(dir, "doc.html")
                ArchiveConverter.convertToHtmlFile(rawFile, ext, htmlFile)
                MuPdfEngine.open(htmlFile.absolutePath, name, formatInfo.displayName, size, formatInfo.supportLevel)
            }

            FormatCategory.WORD_PROCESSING -> {
                val htmlFile = File(dir, "doc.html")
                FormatConverter.convertToHtmlFile(rawFile, ext, htmlFile)
                MuPdfEngine.open(htmlFile.absolutePath, name, formatInfo.displayName, size, formatInfo.supportLevel)
            }

            FormatCategory.PDF_FIXED, FormatCategory.EBOOK_COMIC -> {
                if (formatInfo.supportLevel == SupportLevel.PARTIAL_SUPPORT) {
                    val htmlFile = File(dir, "doc.html")
                    UnsupportedDocEngine.convertToHtmlFile(rawFile, ext, formatInfo, htmlFile)
                    MuPdfEngine.open(htmlFile.absolutePath, name, formatInfo.displayName, size, formatInfo.supportLevel)
                } else {
                    MuPdfEngine.open(rawFile.absolutePath, name, formatInfo.displayName, size, formatInfo.supportLevel)
                }
            }

            FormatCategory.TECHNICAL_DTP -> {
                val htmlFile = File(dir, "doc.html")
                if (ext in listOf("eml", "msg", "ics", "vcf")) {
                    StructuredTextConverter.convertToHtmlFile(rawFile, ext, htmlFile)
                } else {
                    UnsupportedDocEngine.convertToHtmlFile(rawFile, ext, formatInfo, htmlFile)
                }
                MuPdfEngine.open(htmlFile.absolutePath, name, formatInfo.displayName, size, formatInfo.supportLevel)
            }
        }
    }

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(0)
                if (!name.isNullOrBlank()) return name
            }
        }
        return uri.lastPathSegment ?: "document"
    }
}
