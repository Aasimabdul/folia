package com.simbuild.folia.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Common abstraction for all document formats in Folia.
 */
interface DocEngine : AutoCloseable {
    val title: String
    val format: String
    val fileSize: Long
    val pageCount: Int

    /** true for EPUB/MOBI/FB2/TXT/HTML/Markdown/Office: text reflows to the screen */
    val isReflowable: Boolean

    /** Page size in points (1/72 inch). */
    fun pageSize(index: Int): Pair<Float, Float>

    /** Render one page to a bitmap [widthPx] wide. */
    fun render(index: Int, widthPx: Int): Bitmap

    /** Reflow text documents for the current screen. */
    fun relayout(widthPt: Float, heightPt: Float, fontSizePt: Float)
}

object DocEngines {

    fun open(context: Context, uri: Uri): DocEngine {
        val name = displayName(context, uri)
        val ext = name.substringAfterLast('.', "").lowercase()
        val formatName = detectFormatName(ext)

        val dir = File(context.cacheDir, "open").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val rawFile = File(dir, "raw.$ext")

        context.contentResolver.openInputStream(uri)?.use { input ->
            rawFile.outputStream().use { out -> input.copyTo(out, 64 * 1024) }
        } ?: error("Cannot open document stream")

        val targetFile = when (ext) {
            "md", "markdown", "csv" -> {
                val htmlFile = File(dir, "doc.html")
                FormatConverter.convertToHtmlFile(rawFile, ext, htmlFile)
                htmlFile
            }
            "djvu", "djv" -> throw UnsupportedOperationException("DJVU support is in development")
            "cbr", "rar" -> throw UnsupportedOperationException("CBR support is in development")
            else -> rawFile
        }

        val size = rawFile.length()
        return MuPdfEngine.open(
            path = targetFile.absolutePath,
            title = name,
            format = formatName,
            fileSize = size
        )
    }

    fun detectFormatName(ext: String): String = when (ext.lowercase()) {
        "pdf" -> "PDF"
        "epub" -> "EPUB"
        "mobi" -> "MOBI"
        "fb2" -> "FB2"
        "docx", "doc" -> "DOCX"
        "xlsx", "xls" -> "XLSX"
        "pptx", "ppt" -> "PPTX"
        "cbz" -> "CBZ"
        "cbr" -> "CBR"
        "xps", "oxps" -> "XPS"
        "md", "markdown" -> "MARKDOWN"
        "txt", "text", "log" -> "TXT"
        "csv" -> "CSV"
        "html", "htm" -> "HTML"
        else -> ext.uppercase().ifEmpty { "DOC" }
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
