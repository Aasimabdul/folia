package com.simbuild.folia

import com.simbuild.folia.library.LibraryEntry
import com.simbuild.folia.library.LibraryLogic
import com.simbuild.folia.library.LibrarySort
import com.simbuild.folia.library.LibrarySortKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryLogicTest {

    private fun file(id: String, name: String, size: Long = 10, modified: Long = 0) =
        LibraryEntry(id, name, false, size, modified, "application/octet-stream")

    private fun folder(id: String, name: String) = LibraryEntry(id, name, true, -1, 0, "vnd.android.document/directory")

    private val sample = listOf(
        file("1", "zeta.pdf", size = 500, modified = 30),
        file("2", "Alpha.txt", size = 900, modified = 10),
        folder("3", "Books"),
        file("4", "beta.epub", size = 100, modified = 20),
        file("5", ".hidden.pdf"),
        file("1", "zeta.pdf") // duplicate id
    )

    @Test
    fun foldersComeFirstAndDuplicatesAndHiddenFilesAreRemoved() {
        val out = LibraryLogic.arrange(sample, "", LibrarySort())
        assertEquals(listOf("Books", "Alpha.txt", "beta.epub", "zeta.pdf"), out.map { it.name })
    }

    @Test
    fun sortsBySizeDateAndTypeInBothDirections() {
        assertEquals(listOf("beta.epub", "zeta.pdf", "Alpha.txt"),
            LibraryLogic.arrange(sample, "", LibrarySort(LibrarySortKey.SIZE)).filter { !it.isDirectory }.map { it.name })
        assertEquals(listOf("zeta.pdf", "beta.epub", "Alpha.txt"),
            LibraryLogic.arrange(sample, "", LibrarySort(LibrarySortKey.DATE, ascending = false)).filter { !it.isDirectory }.map { it.name })
        assertEquals(listOf("beta.epub", "zeta.pdf", "Alpha.txt").sortedBy { it.substringAfterLast('.') },
            LibraryLogic.arrange(sample, "", LibrarySort(LibrarySortKey.TYPE)).filter { !it.isDirectory }.map { it.name })
    }

    @Test
    fun searchMatchesNameOrExtension() {
        assertEquals(listOf("zeta.pdf"), LibraryLogic.arrange(sample, "zet", LibrarySort()).map { it.name })
        assertEquals(listOf("zeta.pdf"), LibraryLogic.arrange(sample, ".pdf", LibrarySort()).map { it.name })
    }

    @Test
    fun formatsSizes() {
        assertEquals("-", LibraryLogic.formatSize(-1))
        assertEquals("512 B", LibraryLogic.formatSize(512))
        assertEquals("1.5 KB", LibraryLogic.formatSize(1536))
        assertEquals("2.0 MB", LibraryLogic.formatSize(2L * 1024 * 1024))
    }

    @Test
    fun sanitizesNamesAndPicksUniqueOnes() {
        assertEquals("a_b.pdf", LibraryLogic.sanitizeFileName(" a/b.pdf "))
        assertNull(LibraryLogic.sanitizeFileName("  ..  "))
        val taken = setOf("report.pdf", "report (1).pdf")
        assertEquals("report (2).pdf", LibraryLogic.uniqueName("report.pdf") { it in taken })
        assertEquals("new.pdf", LibraryLogic.uniqueName("new.pdf") { it in taken })
    }
}
