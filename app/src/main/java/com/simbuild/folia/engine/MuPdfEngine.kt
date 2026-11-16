package com.simbuild.folia.engine

import android.graphics.Bitmap
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.android.AndroidDrawDevice

/**
 * MuPDF engine implementation for Folia.
 * Handles rendering, bounds calculation, and text reflow.
 */
class MuPdfEngine private constructor(
    private val doc: Document,
    override val title: String,
    override val format: String,
    override val fileSize: Long
) : DocEngine {

    private val lock = Any()

    override val isReflowable: Boolean = doc.isReflowable

    @Volatile
    override var pageCount: Int = doc.countPages()
        private set

    override fun pageSize(index: Int): Pair<Float, Float> = synchronized(lock) {
        val page = doc.loadPage(index)
        try {
            val b = page.bounds
            (b.x1 - b.x0) to (b.y1 - b.y0)
        } finally {
            page.destroy()
        }
    }

    override fun render(index: Int, widthPx: Int): Bitmap = synchronized(lock) {
        val page = doc.loadPage(index)
        try {
            val b = page.bounds
            val scale = widthPx / (b.x1 - b.x0)
            AndroidDrawDevice.drawPage(page, Matrix(scale))
        } finally {
            page.destroy()
        }
    }

    override fun relayout(widthPt: Float, heightPt: Float, fontSizePt: Float) {
        if (!isReflowable) return
        synchronized(lock) {
            doc.layout(widthPt, heightPt, fontSizePt)
            pageCount = doc.countPages()
        }
    }

    override fun close() {
        synchronized(lock) { doc.destroy() }
    }

    companion object {
        fun open(path: String, title: String = "Document", format: String = "DOC", fileSize: Long = 0L): MuPdfEngine {
            val doc = Document.openDocument(path)
            return MuPdfEngine(doc, title, format, fileSize)
        }
    }
}
