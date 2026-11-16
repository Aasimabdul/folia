package com.simbuild.folia.data

import android.content.Context
import android.content.SharedPreferences
import com.simbuild.folia.model.RecentDocument
import org.json.JSONArray
import org.json.JSONObject

class RecentDocumentsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("folia_recents", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_RECENTS = "key_recent_documents"
        private const val MAX_RECENTS = 50
    }

    fun getRecentDocuments(): List<RecentDocument> {
        val jsonString = prefs.getString(KEY_RECENTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<RecentDocument>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    RecentDocument(
                        uriString = obj.optString("uriString"),
                        title = obj.optString("title", "Document"),
                        format = obj.optString("format", "PDF"),
                        lastReadPage = obj.optInt("lastReadPage", 0),
                        pageCount = obj.optInt("pageCount", 1),
                        lastReadTime = obj.optLong("lastReadTime", System.currentTimeMillis()),
                        fileSize = obj.optLong("fileSize", 0L),
                        isFavorite = obj.optBoolean("isFavorite", false)
                    )
                )
            }
            list.sortedWith(compareByDescending<RecentDocument> { it.isFavorite }.thenByDescending { it.lastReadTime })
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveRecentDocument(doc: RecentDocument) {
        val current = getRecentDocuments().toMutableList()
        val index = current.indexOfFirst { it.uriString == doc.uriString }
        if (index != -1) {
            val existing = current[index]
            current[index] = doc.copy(
                isFavorite = existing.isFavorite,
                lastReadTime = System.currentTimeMillis()
            )
        } else {
            current.add(0, doc)
        }
        val trimmed = current.take(MAX_RECENTS)
        saveList(trimmed)
    }

    fun updateReadingProgress(uriString: String, page: Int, totalPages: Int) {
        val current = getRecentDocuments().toMutableList()
        val index = current.indexOfFirst { it.uriString == uriString }
        if (index != -1) {
            val existing = current[index]
            current[index] = existing.copy(
                lastReadPage = page,
                pageCount = if (totalPages > 0) totalPages else existing.pageCount,
                lastReadTime = System.currentTimeMillis()
            )
            saveList(current)
        }
    }

    fun toggleFavorite(uriString: String) {
        val current = getRecentDocuments().toMutableList()
        val index = current.indexOfFirst { it.uriString == uriString }
        if (index != -1) {
            val existing = current[index]
            current[index] = existing.copy(isFavorite = !existing.isFavorite)
            saveList(current)
        }
    }

    fun removeRecentDocument(uriString: String) {
        val current = getRecentDocuments().filterNot { it.uriString == uriString }
        saveList(current)
    }

    fun clearAll() {
        prefs.edit().remove(KEY_RECENTS).apply()
    }

    private fun saveList(list: List<RecentDocument>) {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("uriString", item.uriString)
                put("title", item.title)
                put("format", item.format)
                put("lastReadPage", item.lastReadPage)
                put("pageCount", item.pageCount)
                put("lastReadTime", item.lastReadTime)
                put("fileSize", item.fileSize)
                put("isFavorite", item.isFavorite)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_RECENTS, array.toString()).apply()
    }
}
