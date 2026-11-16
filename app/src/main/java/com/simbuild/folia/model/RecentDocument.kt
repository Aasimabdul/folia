package com.simbuild.folia.model

/**
 * Represents a document in the user's reading history.
 */
data class RecentDocument(
    val uriString: String,
    val title: String,
    val format: String,
    val lastReadPage: Int = 0,
    val pageCount: Int = 1,
    val lastReadTime: Long = System.currentTimeMillis(),
    val fileSize: Long = 0L,
    val isFavorite: Boolean = false
) {
    val progressPercent: Int
        get() = if (pageCount > 0) {
            (((lastReadPage + 1).toFloat() / pageCount.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else 0
}
