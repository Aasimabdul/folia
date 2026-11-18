package com.simbuild.folia.annotations

import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class InkTool { PEN, HIGHLIGHTER }

/**
 * One drawn line. Coordinates are fractions of the page (0..1 of its width and height) and the thickness is a
 * fraction of the page width, so a stroke stays glued to the same spot when the page is zoomed, resized or rotated.
 * [points] holds x0, y0, x1, y1, ...
 */
data class Stroke(
    val tool: InkTool,
    val color: Int,
    val widthFraction: Float,
    val points: List<Float>
)

/**
 * All sketches for one document, kept apart from the document itself (the original file is never touched).
 * Pure Kotlin so it can be unit tested; the UI listens through [onChange].
 */
class AnnotationStore {
    private sealed interface Action {
        data class Add(val page: Int, val stroke: Stroke) : Action
        data class Remove(val page: Int, val index: Int, val stroke: Stroke) : Action
        data class Clear(val snapshot: Map<Int, List<Stroke>>) : Action
        data class Batch(val actions: List<Action>) : Action
    }

    private val pages = HashMap<Int, MutableList<Stroke>>()
    private val undoStack = ArrayDeque<Action>()
    private val redoStack = ArrayDeque<Action>()
    private var group: MutableList<Action>? = null

    /** Called after every change (draw, erase, undo, redo, clear, load). */
    var onChange: () -> Unit = {}

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val isEmpty: Boolean get() = pages.values.all { it.isEmpty() }
    val strokeCount: Int get() = pages.values.sumOf { it.size }

    fun strokes(page: Int): List<Stroke> = pages[page].orEmpty()

    fun pagesWithStrokes(): List<Int> = pages.filterValues { it.isNotEmpty() }.keys.sorted()

    fun add(page: Int, stroke: Stroke) {
        if (stroke.points.size < 4) return
        pages.getOrPut(page) { ArrayList() }.add(stroke)
        record(Action.Add(page, stroke))
    }

    /** Starts one undo step that covers a whole eraser drag. */
    fun beginGroup() {
        if (group == null) group = ArrayList()
    }

    fun endGroup() {
        val g = group ?: return
        group = null
        if (g.isNotEmpty()) pushUndo(if (g.size == 1) g[0] else Action.Batch(g))
    }

    /**
     * Removes every stroke on [page] that passes within [radius] (a fraction of the page width) of the point.
     * [aspect] is page height divided by width so the hit circle stays round. Returns true if something was erased.
     */
    fun erase(page: Int, x: Float, y: Float, radius: Float, aspect: Float): Boolean {
        val list = pages[page] ?: return false
        var removedAny = false
        var i = list.size - 1
        while (i >= 0) {
            val s = list[i]
            if (hits(s, x, y, radius + s.widthFraction / 2, aspect)) {
                list.removeAt(i)
                record(Action.Remove(page, i, s))
                removedAny = true
            }
            i--
        }
        return removedAny
    }

    fun clearAll() {
        if (isEmpty) return
        val snapshot = pages.mapValues { it.value.toList() }.filterValues { it.isNotEmpty() }
        pages.clear()
        record(Action.Clear(snapshot))
    }

    fun undo() {
        val action = undoStack.removeLastOrNull() ?: return
        revert(action)
        redoStack.addLast(action)
        onChange()
    }

    fun redo() {
        val action = redoStack.removeLastOrNull() ?: return
        apply(action)
        undoStack.addLast(action)
        onChange()
    }

    private fun record(action: Action) {
        val g = group
        if (g != null) {
            g.add(action)
            redoStack.clear()
            onChange()
        } else {
            pushUndo(action)
        }
    }

    private fun pushUndo(action: Action) {
        undoStack.addLast(action)
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        onChange()
    }

    private fun apply(action: Action) {
        when (action) {
            is Action.Add -> pages.getOrPut(action.page) { ArrayList() }.add(action.stroke)
            is Action.Remove -> pages[action.page]?.let { list ->
                val idx = list.indexOf(action.stroke)
                if (idx >= 0) list.removeAt(idx)
            }
            is Action.Clear -> pages.clear()
            is Action.Batch -> action.actions.forEach { apply(it) }
        }
    }

    private fun revert(action: Action) {
        when (action) {
            is Action.Add -> pages[action.page]?.let { list ->
                val idx = list.lastIndexOf(action.stroke)
                if (idx >= 0) list.removeAt(idx)
            }
            is Action.Remove -> {
                val list = pages.getOrPut(action.page) { ArrayList() }
                list.add(min(action.index, list.size), action.stroke)
            }
            is Action.Clear -> {
                pages.clear()
                action.snapshot.forEach { (p, l) -> pages[p] = l.toMutableList() }
            }
            is Action.Batch -> action.actions.asReversed().forEach { revert(it) }
        }
    }

    // ----- persistence: a tiny line based text format, easy to inspect and to validate -----

    fun serialize(): String {
        val sb = StringBuilder(HEADER).append('\n')
        for (page in pagesWithStrokes()) {
            sb.append("P ").append(page).append('\n')
            for (s in pages.getValue(page)) {
                sb.append("S ").append(s.tool.ordinal).append(' ').append(s.color).append(' ')
                    .append(String.format(Locale.US, "%.5f", s.widthFraction)).append(' ')
                var i = 0
                while (i + 1 < s.points.size) {
                    if (i > 0) sb.append(';')
                    sb.append(String.format(Locale.US, "%.5f,%.5f", s.points[i], s.points[i + 1]))
                    i += 2
                }
                sb.append('\n')
            }
        }
        return sb.toString()
    }

    /** Replaces the content with [text]. Unknown or damaged lines are skipped; returns the number of strokes loaded. */
    fun load(text: String): Int {
        pages.clear()
        undoStack.clear()
        redoStack.clear()
        group = null
        var page = -1
        var count = 0
        val lines = text.lineSequence()
        val first = lines.firstOrNull()
        if (first == null || !first.startsWith("FOLIA-ANN")) {
            onChange()
            return 0
        }
        for (line in lines.drop(1)) {
            if (line.startsWith("P ")) {
                page = line.substring(2).trim().toIntOrNull() ?: -1
            } else if (line.startsWith("S ") && page >= 0) {
                val parts = line.split(' ', limit = 5)
                if (parts.size < 5) continue
                val tool = InkTool.entries.getOrNull(parts[1].toIntOrNull() ?: -1) ?: continue
                val color = parts[2].toIntOrNull() ?: continue
                val width = parts[3].toFloatOrNull() ?: continue
                val pts = ArrayList<Float>()
                var ok = true
                for (pair in parts[4].split(';')) {
                    val xy = pair.split(',')
                    val x = xy.getOrNull(0)?.toFloatOrNull()
                    val y = xy.getOrNull(1)?.toFloatOrNull()
                    if (x == null || y == null) { ok = false; break }
                    pts.add(x.coerceIn(0f, 1f)); pts.add(y.coerceIn(0f, 1f))
                }
                if (!ok || pts.size < 4 || width <= 0f || width > 0.2f) continue
                pages.getOrPut(page) { ArrayList() }.add(Stroke(tool, color, width, pts))
                count++
            }
        }
        onChange()
        return count
    }

    private fun hits(s: Stroke, x: Float, y: Float, radius: Float, aspect: Float): Boolean {
        val p = s.points
        var i = 0
        while (i + 3 < p.size) {
            if (segmentDistance(x, y * aspect, p[i], p[i + 1] * aspect, p[i + 2], p[i + 3] * aspect) <= radius) return true
            i += 2
        }
        return false
    }

    private fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        val t = if (lenSq == 0f) 0f else max(0f, min(1f, ((px - ax) * dx + (py - ay) * dy) / lenSq))
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    private companion object {
        const val HEADER = "FOLIA-ANN 1"
        const val MAX_UNDO = 200
    }
}
