package com.simbuild.folia.annotations

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Sketches live next to the app, not inside the document: one small text file per document in private storage.
 * The original file is never modified, and reopening the document brings the strokes back.
 */
object AnnotationStorage {

    fun fileFor(context: Context, documentKey: String): File {
        val dir = File(context.filesDir, "annotations").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256").digest(documentKey.toByteArray(Charsets.UTF_8))
        val name = digest.take(16).joinToString("") { "%02x".format(it) }
        return File(dir, "$name.fol")
    }

    fun load(context: Context, documentKey: String, store: AnnotationStore): Int {
        val file = fileFor(context, documentKey)
        if (!file.isFile || file.length() > MAX_BYTES) return 0
        return runCatching { store.load(file.readText(Charsets.UTF_8)) }.getOrDefault(0)
    }

    /** Writes atomically (temp file, then rename) so a crash mid-save can never leave a half-written file. */
    fun save(context: Context, documentKey: String, store: AnnotationStore): Boolean = runCatching {
        val file = fileFor(context, documentKey)
        if (store.isEmpty) {
            file.delete()
        } else {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(store.serialize(), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.delete()
                check(tmp.renameTo(file)) { "rename failed" }
            }
        }
        true
    }.getOrDefault(false)

    private const val MAX_BYTES = 8L * 1024 * 1024
}
