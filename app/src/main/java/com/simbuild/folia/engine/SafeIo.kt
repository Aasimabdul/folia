package com.simbuild.folia.engine

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** Hard limits that keep a malicious or broken document from exhausting memory or storage. */
object SafeLimits {
    /** Largest document Folia copies into its private cache (512 MB). */
    const val MAX_DOCUMENT_BYTES = 512L * 1024 * 1024

    /** Largest single XML part (document.xml, sheet1.xml, slide1.xml ...) read from an Office/ODF package. */
    const val MAX_XML_PART_BYTES = 48L * 1024 * 1024

    /** Largest single embedded image that is inlined into converted HTML. */
    const val MAX_MEDIA_BYTES = 8L * 1024 * 1024

    /** Total embedded image data inlined into one converted document. */
    const val MAX_TOTAL_MEDIA_BYTES = 48L * 1024 * 1024

    /** Packages with more entries than this are treated as hostile. */
    const val MAX_ZIP_ENTRIES = 20_000
}

/** Thrown when a document exceeds a safety limit. The message is shown to the user. */
class DocumentTooLargeException(message: String) : IOException(message)

/** Fails with [DocumentTooLargeException] as soon as more than [limit] bytes have been read. */
class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) add(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) add(n.toLong())
        return n
    }

    override fun skip(n: Long): Long {
        val skipped = super.skip(n)
        if (skipped > 0) add(skipped)
        return skipped
    }

    override fun markSupported(): Boolean = false

    private fun add(n: Long) {
        count += n
        if (count > limit) throw DocumentTooLargeException("Document part is larger than the safe limit (${limit / (1024 * 1024)} MB).")
    }
}

/** Tracks how much embedded media has been inlined so far, so one document cannot balloon memory. */
class MediaBudget(private val total: Long = SafeLimits.MAX_TOTAL_MEDIA_BYTES) {
    private var used = 0L

    /** True (and the size is counted) if an image of [size] bytes still fits. */
    fun tryTake(size: Long): Boolean {
        if (size < 0 || size > SafeLimits.MAX_MEDIA_BYTES || used + size > total) return false
        used += size
        return true
    }
}

object SafeZip {
    /** Opens an entry for reading with a hard cap on the uncompressed size (zip-bomb protection). */
    fun open(zip: ZipFile, entry: ZipEntry, limit: Long = SafeLimits.MAX_XML_PART_BYTES): InputStream =
        LimitedInputStream(zip.getInputStream(entry), limit)

    /** Reads a whole entry into memory, but never more than [limit] bytes. */
    fun readBytes(zip: ZipFile, entry: ZipEntry, limit: Long = SafeLimits.MAX_MEDIA_BYTES): ByteArray =
        open(zip, entry, limit).use { it.readBytes() }

    /** Rejects packages with an absurd number of entries before anything is parsed. */
    fun checkEntryCount(zip: ZipFile) {
        if (zip.size() > SafeLimits.MAX_ZIP_ENTRIES) {
            throw DocumentTooLargeException("This file contains too many parts (${zip.size()}) and was not opened.")
        }
    }
}

object SafeXml {
    /**
     * Namespace-aware XML parser factory with XXE / entity-expansion protections switched on.
     * Not every Android XML implementation knows every feature, so each setting is best effort.
     */
    fun factory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { isXIncludeAware = false }
        runCatching { isExpandEntityReferences = false }
        runCatching { setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
    }
}
