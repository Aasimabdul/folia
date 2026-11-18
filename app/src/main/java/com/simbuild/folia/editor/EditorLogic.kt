package com.simbuild.folia.editor

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** Plain-text formats Folia can edit safely (the file is text, so editing the text cannot damage hidden structure). */
object EditorLogic {
    val EDITABLE_EXTENSIONS = setOf(
        "txt", "text", "md", "markdown", "log", "csv", "tsv", "json", "xml", "yaml", "yml", "ini", "cfg", "conf",
        "toml", "properties", "html", "htm", "css", "js", "ts", "kt", "java", "py", "c", "h", "cpp", "hpp", "cs",
        "go", "rs", "rb", "php", "sh", "bat", "sql", "tex", "srt", "vtt", "lrc", "gradle"
    )

    /** Largest file opened in the editor (a single text field gets sluggish beyond this). */
    const val MAX_EDIT_BYTES = 512 * 1024

    fun isEditable(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) in EDITABLE_EXTENSIONS

    fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "md", "markdown" -> "text/markdown"
        "json" -> "application/json"
        "xml" -> "text/xml"
        "html", "htm" -> "text/html"
        "csv" -> "text/csv"
        else -> "text/plain"
    }

    /** Decoded text plus whether the file started with a UTF-8 byte order mark (kept when saving). */
    data class Decoded(val text: String, val hasBom: Boolean)

    /**
     * Strict UTF-8 decoding. Returns null when the bytes are not valid UTF-8 text (binary or another encoding), so the
     * editor refuses instead of silently corrupting the file on save.
     */
    fun decodeUtf8(bytes: ByteArray): Decoded? {
        val hasBom = bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        val start = if (hasBom) 3 else 0
        if (bytes.any { it == 0.toByte() }) return null
        return try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            Decoded(decoder.decode(ByteBuffer.wrap(bytes, start, bytes.size - start)).toString(), hasBom)
        } catch (_: CharacterCodingException) {
            null
        }
    }

    fun encodeUtf8(text: String, withBom: Boolean): ByteArray {
        val body = text.toByteArray(Charsets.UTF_8)
        return if (withBom) byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body else body
    }
}

/** Snapshot based undo/redo for the text editor. */
class UndoHistory(initial: String, private val limit: Int = 100) {
    private val states = ArrayList<String>().apply { add(initial) }
    private var index = 0

    val current: String get() = states[index]
    val canUndo: Boolean get() = index > 0
    val canRedo: Boolean get() = index < states.size - 1

    /** Remembers [text] as a new step (ignored if it equals the current step). Drops any redo steps. */
    fun record(text: String) {
        if (text == states[index]) return
        while (states.size > index + 1) states.removeAt(states.size - 1)
        states.add(text)
        index++
        if (states.size > limit) {
            states.removeAt(0)
            index--
        }
    }

    fun undo(): String? = if (canUndo) states[--index] else null
    fun redo(): String? = if (canRedo) states[++index] else null
}
