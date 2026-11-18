package com.simbuild.folia.annotations

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.simbuild.folia.engine.DocEngine
import java.io.OutputStream

/** What a finished export looks like, for the confirmation dialog. */
data class ExportResult(
    val uri: Uri,
    val fileName: String,
    val location: String,
    val pagesWritten: Int,
    val verified: Boolean,
    val note: String?
)

/** Progress of "Save annotated copy" for the dialog. */
sealed interface ExportState {
    data class Running(val done: Int, val total: Int) : ExportState
    data class Done(val result: ExportResult) : ExportState
    data class Failed(val message: String) : ExportState
}

/**
 * Writes a copy of the document with the sketches burned in, as a new PDF. Pages become pictures in that copy
 * (text is not selectable there), which is why it is always a new file and never replaces the original.
 */
object AnnotationExporter {

    private const val PAGE_WIDTH_PT = 595            // A4-ish width in points
    private const val RENDER_WIDTH_PX = 1100          // sharp on screen, modest in size
    private const val ALL_PAGES_LIMIT = 150           // above this only annotated pages are written (memory safety)

    /** Pages that will be written for [engine] given the sketches in [store]. */
    fun pagesToExport(engine: DocEngine, store: AnnotationStore): List<Int> =
        if (engine.pageCount <= ALL_PAGES_LIMIT) (0 until engine.pageCount).toList()
        else store.pagesWithStrokes().filter { it < engine.pageCount }

    fun writePdf(
        engine: DocEngine,
        store: AnnotationStore,
        pages: List<Int>,
        out: OutputStream,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ) {
        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { n, pageIndex ->
                val (wPt, hPt) = engine.pageSize(pageIndex)
                val aspect = if (wPt > 0f) hPt / wPt else 1.414f
                val heightPt = (PAGE_WIDTH_PT * aspect).toInt().coerceAtLeast(1)
                val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, heightPt, n + 1).create()
                val page = pdf.startPage(info)
                val bitmap: Bitmap = engine.render(pageIndex, RENDER_WIDTH_PX)
                try {
                    val canvas = page.canvas
                    val dest = android.graphics.Rect(0, 0, PAGE_WIDTH_PT, heightPt)
                    canvas.drawBitmap(bitmap, null, dest, Paint(Paint.FILTER_BITMAP_FLAG))
                    drawStrokes(canvas, store.strokes(pageIndex), PAGE_WIDTH_PT.toFloat(), heightPt.toFloat())
                } finally {
                    bitmap.recycle()
                }
                pdf.finishPage(page)
                onProgress(n + 1, pages.size)
            }
            pdf.writeTo(out)
            out.flush()
        } finally {
            pdf.close()
        }
    }

    /** Draws strokes on a canvas of the given size (same maths the on-screen layer uses). */
    fun drawStrokes(canvas: Canvas, strokes: List<Stroke>, width: Float, height: Float) {
        for (s in strokes) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeCap = if (s.tool == InkTool.HIGHLIGHTER) Paint.Cap.SQUARE else Paint.Cap.ROUND
                strokeWidth = s.widthFraction * width
                color = s.color
            }
            val path = Path()
            var i = 0
            while (i + 1 < s.points.size) {
                val x = s.points[i] * width
                val y = s.points[i + 1] * height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                i += 2
            }
            if (s.points.size == 4 && s.points[0] == s.points[2] && s.points[1] == s.points[3]) {
                path.lineTo(s.points[0] * width + 0.1f, s.points[1] * height)
            }
            canvas.drawPath(path, paint)
        }
    }

    /** Opens the written file again and checks that it really is a PDF with the expected number of pages. */
    fun verify(context: Context, uri: Uri, expectedPages: Int): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd: ParcelFileDescriptor ->
            PdfRenderer(pfd).use { it.pageCount == expectedPages }
        } ?: false
    }.getOrDefault(false)

    fun displayName(resolver: ContentResolver, uri: Uri): String =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "document"

    /**
     * A truthful description of where a saved file is. Storage Access Framework URIs are not file paths, so we
     * only describe what the provider tells us (volume and folder), and say so when we cannot know more.
     */
    fun describeLocation(uri: Uri): String {
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        if (uri.authority == "com.android.externalstorage.documents" && docId != null && docId.contains(':')) {
            val volume = docId.substringBefore(':')
            val path = docId.substringAfter(':').substringBeforeLast('/', "")
            val volumeName = if (volume.equals("primary", true)) "Internal storage" else "Storage $volume"
            return if (path.isEmpty()) volumeName else "$volumeName / ${path.replace("/", " / ")}"
        }
        if (uri.authority == "com.android.providers.downloads.documents") return "Downloads"
        return "the location you chose in the system file picker (${uri.authority ?: "unknown provider"})"
    }
}
