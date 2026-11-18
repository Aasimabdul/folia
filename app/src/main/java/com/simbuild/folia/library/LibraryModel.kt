package com.simbuild.folia.library

import java.util.Locale

/** One file or folder inside a folder the user granted access to. Pure data, no Android types. */
data class LibraryEntry(
    val docId: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedMillis: Long,
    val mimeType: String,
    val canRename: Boolean = false,
    val canDelete: Boolean = false
) {
    /** Lower-case extension without the dot; empty for folders and files without one. */
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase(Locale.ROOT)
}

enum class LibrarySortKey(val label: String) {
    NAME("Name"),
    DATE("Date modified"),
    SIZE("Size"),
    TYPE("File type")
}

data class LibrarySort(val key: LibrarySortKey = LibrarySortKey.NAME, val ascending: Boolean = true)

object LibraryLogic {

    /**
     * Prepares a raw directory listing for display: removes duplicates (same document id), optionally hides dot files,
     * filters by [query] (name or extension) and sorts. Folders always come first so navigation stays predictable.
     */
    fun arrange(
        entries: List<LibraryEntry>,
        query: String,
        sort: LibrarySort,
        showHidden: Boolean = false
    ): List<LibraryEntry> {
        val q = query.trim()
        val filtered = entries
            .distinctBy { it.docId }
            .filter { showHidden || !it.name.startsWith(".") }
            .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.extension == q.lowercase(Locale.ROOT).removePrefix(".") }

        val byName = compareBy<LibraryEntry> { it.name.lowercase(Locale.ROOT) }
        val comparator: Comparator<LibraryEntry> = when (sort.key) {
            LibrarySortKey.NAME -> byName
            LibrarySortKey.DATE -> compareBy<LibraryEntry> { it.modifiedMillis }.then(byName)
            LibrarySortKey.SIZE -> compareBy<LibraryEntry> { it.sizeBytes }.then(byName)
            LibrarySortKey.TYPE -> compareBy<LibraryEntry> { it.extension }.then(byName)
        }
        val directed = if (sort.ascending) comparator else comparator.reversed()

        val folders = filtered.filter { it.isDirectory }.sortedWith(if (sort.key == LibrarySortKey.NAME) directed else byName)
        val files = filtered.filter { !it.isDirectory }.sortedWith(directed)
        return folders + files
    }

    /** Human readable size; "-" when the provider does not report one. */
    fun formatSize(bytes: Long): String {
        if (bytes < 0) return "-"
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (value >= 100) String.format(Locale.US, "%.0f %s", value, units[unit])
        else String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    /** A name that is safe to use for rename: no path separators, not blank. Returns null if it cannot be used. */
    fun sanitizeFileName(input: String): String? {
        val cleaned = input.trim().replace(Regex("[\\\\/:*?\"<>|\\u0000]"), "_").trim('.', ' ')
        return cleaned.takeIf { it.isNotEmpty() && it.length <= 200 }
    }

    /** "name (1).ext", "name (2).ext" ... until [exists] says the name is free. */
    fun uniqueName(base: String, exists: (String) -> Boolean): String {
        if (!exists(base)) return base
        val stem = base.substringBeforeLast('.', base)
        val ext = base.substringAfterLast('.', "")
        var n = 1
        while (true) {
            val candidate = if (ext.isEmpty()) "$stem ($n)" else "$stem ($n).$ext"
            if (!exists(candidate)) return candidate
            n++
        }
    }
}
