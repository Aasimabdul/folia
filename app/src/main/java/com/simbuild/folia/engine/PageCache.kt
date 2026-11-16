package com.simbuild.folia.engine

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Byte-budgeted bitmap cache. This is the main RAM control in the app:
 * no matter how long the document is, at most [maxBytes] of page bitmaps stay in memory.
 * Default is ~1/16 of the app heap, so a 4 GB phone stays calm.
 */
class PageCache(maxBytes: Int = (Runtime.getRuntime().maxMemory() / 16).toInt().coerceIn(8 shl 20, 32 shl 20)) {

    private val cache = object : LruCache<Int, Bitmap>(maxBytes) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.allocationByteCount
        // Do not recycle() here: a bitmap may still be on screen. GC frees it once Compose drops it.
    }

    fun get(index: Int): Bitmap? = cache.get(index)
    fun put(index: Int, bitmap: Bitmap) { cache.put(index, bitmap) }
    fun clear() = cache.evictAll()
}
