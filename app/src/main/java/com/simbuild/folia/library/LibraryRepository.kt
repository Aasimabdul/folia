package com.simbuild.folia.library

import android.content.Context
import android.net.Uri

/** Remembers which folders the user added to the Library (only the URIs; the files stay where they are). */
class LibraryRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("folia_library", Context.MODE_PRIVATE)

    var folders: List<Uri>
        get() = (prefs.getString(KEY_FOLDERS, "") ?: "")
            .split('\n')
            .filter { it.isNotBlank() }
            .map { Uri.parse(it) }
            .distinct()
        private set(value) {
            prefs.edit().putString(KEY_FOLDERS, value.joinToString("\n") { it.toString() }).apply()
        }

    var useGrid: Boolean
        get() = prefs.getBoolean(KEY_GRID, false)
        set(value) { prefs.edit().putBoolean(KEY_GRID, value).apply() }

    var sort: LibrarySort
        get() = LibrarySort(
            key = LibrarySortKey.entries.firstOrNull { it.name == prefs.getString(KEY_SORT, null) } ?: LibrarySortKey.NAME,
            ascending = prefs.getBoolean(KEY_ASC, true)
        )
        set(value) { prefs.edit().putString(KEY_SORT, value.key.name).putBoolean(KEY_ASC, value.ascending).apply() }

    fun add(uri: Uri) {
        if (uri !in folders) folders = folders + uri
    }

    fun remove(uri: Uri) {
        folders = folders - uri
    }

    private companion object {
        const val KEY_FOLDERS = "folders"
        const val KEY_GRID = "grid"
        const val KEY_SORT = "sort"
        const val KEY_ASC = "sort_asc"
    }
}
