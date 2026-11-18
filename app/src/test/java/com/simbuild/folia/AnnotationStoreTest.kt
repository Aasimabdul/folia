package com.simbuild.folia

import com.simbuild.folia.annotations.AnnotationStore
import com.simbuild.folia.annotations.InkTool
import com.simbuild.folia.annotations.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationStoreTest {
    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, tool: InkTool = InkTool.PEN) =
        Stroke(tool, 0xFF112233.toInt(), 0.004f, listOf(x0, y0, x1, y1))

    @Test
    fun addUndoRedo() {
        val s = AnnotationStore()
        s.add(0, line(0.1f, 0.1f, 0.5f, 0.5f))
        s.add(0, line(0.2f, 0.2f, 0.6f, 0.6f))
        assertEquals(2, s.strokes(0).size)
        s.undo()
        assertEquals(1, s.strokes(0).size)
        s.redo()
        assertEquals(2, s.strokes(0).size)
        s.add(1, line(0f, 0f, 1f, 1f))
        assertFalse("a new stroke clears redo", s.canRedo)
    }

    @Test
    fun eraseDragIsOneUndoStep() {
        val s = AnnotationStore()
        s.add(0, line(0.1f, 0.5f, 0.3f, 0.5f))
        s.add(0, line(0.6f, 0.5f, 0.9f, 0.5f))
        s.add(0, line(0.1f, 0.9f, 0.9f, 0.9f))
        s.beginGroup()
        assertTrue(s.erase(0, 0.2f, 0.5f, 0.02f, 1.4f))
        assertTrue(s.erase(0, 0.7f, 0.5f, 0.02f, 1.4f))
        s.endGroup()
        assertEquals(1, s.strokes(0).size)
        s.undo()
        assertEquals(3, s.strokes(0).size)
        s.redo()
        assertEquals(1, s.strokes(0).size)
    }

    @Test
    fun clearAllCanBeUndone() {
        val s = AnnotationStore()
        s.add(0, line(0f, 0f, 1f, 1f))
        s.add(2, line(0f, 0f, 1f, 1f))
        s.clearAll()
        assertTrue(s.isEmpty)
        s.undo()
        assertEquals(listOf(0, 2), s.pagesWithStrokes())
    }

    @Test
    fun serializeAndLoadRoundTrip() {
        val s = AnnotationStore()
        s.add(3, Stroke(InkTool.HIGHLIGHTER, 0x66FFEB3B, 0.012f, listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f)))
        s.add(0, line(0.25f, 0.25f, 0.75f, 0.75f))
        val copy = AnnotationStore()
        assertEquals(2, copy.load(s.serialize()))
        assertEquals(listOf(0, 3), copy.pagesWithStrokes())
        val h = copy.strokes(3).single()
        assertEquals(InkTool.HIGHLIGHTER, h.tool)
        assertEquals(0x66FFEB3B, h.color)
        assertEquals(6, h.points.size)
    }

    @Test
    fun loadIgnoresDamagedData() {
        val s = AnnotationStore()
        assertEquals(0, s.load("not an annotation file"))
        assertEquals(1, s.load("FOLIA-ANN 1\nP 0\nS 0 -1 0.004 0.1,0.1;0.2,0.2\nS 9 1 0.004 0.1,0.1;0.2,0.2\nS 0 1 x 0.1,0.1;0.2,0.2\nS 0 1 0.004 junk\n"))
    }
}
