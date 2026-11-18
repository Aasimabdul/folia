package com.simbuild.folia

import com.simbuild.folia.editor.EditorLogic
import com.simbuild.folia.editor.UndoHistory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorLogicTest {

    @Test
    fun onlyTextFormatsAreEditable() {
        assertTrue(EditorLogic.isEditable("notes.TXT"))
        assertTrue(EditorLogic.isEditable("README.md"))
        assertFalse(EditorLogic.isEditable("report.docx"))
        assertFalse(EditorLogic.isEditable("book.pdf"))
        assertFalse(EditorLogic.isEditable("noextension"))
    }

    @Test
    fun decodesUtf8AndKeepsBomInformation() {
        val plain = EditorLogic.decodeUtf8("héllo\nworld".toByteArray())!!
        assertEquals("héllo\nworld", plain.text)
        assertFalse(plain.hasBom)

        val withBom = EditorLogic.decodeUtf8(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "x".toByteArray())!!
        assertEquals("x", withBom.text)
        assertTrue(withBom.hasBom)
        assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 'x'.code.toByte()), EditorLogic.encodeUtf8("x", true))
    }

    @Test
    fun refusesBinaryAndNonUtf8Data() {
        assertNull(EditorLogic.decodeUtf8(byteArrayOf(0x41, 0x00, 0x42)))
        assertNull(EditorLogic.decodeUtf8(byteArrayOf(0xC3.toByte(), 0x28)))
    }

    @Test
    fun undoHistoryWalksBackAndForward() {
        val h = UndoHistory("a")
        h.record("ab"); h.record("abc")
        assertEquals("ab", h.undo())
        assertEquals("a", h.undo())
        assertNull(h.undo())
        assertEquals("ab", h.redo())
        h.record("abx")
        assertFalse("recording after undo drops redo steps", h.canRedo)
        assertEquals("abx", h.current)
    }

    @Test
    fun undoHistoryHonoursItsLimit() {
        val h = UndoHistory("0", limit = 3)
        for (i in 1..10) h.record(i.toString())
        var steps = 0
        while (h.undo() != null) steps++
        assertEquals(2, steps)
    }
}
