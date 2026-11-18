package com.simbuild.folia.annotations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** What the finger or stylus does while annotating. MOVE keeps normal scrolling and zooming. */
enum class AnnotateTool(val label: String) {
    MOVE("Move"),
    PEN("Pen"),
    HIGHLIGHTER("Highlight"),
    ERASER("Eraser")
}

/** Small palette: dark ink, red, blue, green, and the classic highlighter yellow. */
val InkPalette = listOf(
    Color(0xFF1B1B1F),
    Color(0xFFD93025),
    Color(0xFF1A73E8),
    Color(0xFF188038),
    Color(0xFFFBBC04)
)

/** Thickness choices as a fraction of page width (thin / medium / thick). */
val InkWidths = listOf(0.0025f, 0.005f, 0.010f)
private val InkWidthLabels = listOf("Thin", "Medium", "Thick")

data class InkSettings(
    val tool: AnnotateTool = AnnotateTool.PEN,
    val colorIndex: Int = 0,
    val widthIndex: Int = 1,
    val stylusOnly: Boolean = false
) {
    val color: Color get() = InkPalette[colorIndex.coerceIn(0, InkPalette.lastIndex)]
    val widthFraction: Float get() = InkWidths[widthIndex.coerceIn(0, InkWidths.lastIndex)]
}

/**
 * Transparent layer on top of one page. It draws the saved strokes and (when a drawing tool is active) captures
 * touch input, so scrolling the list does not fight with drawing. Coordinates are fractions of the page, so the
 * layer stays aligned while zooming, scrolling or rotating.
 */
@Composable
fun PageInkLayer(
    pageIndex: Int,
    store: AnnotationStore,
    version: Int,
    ink: InkSettings,
    aspect: Float,
    modifier: Modifier = Modifier
) {
    val live = remember(pageIndex) { mutableStateListOf<Float>() }
    val capturing = ink.tool != AnnotateTool.MOVE

    val inputModifier = if (!capturing) Modifier else Modifier.pointerInput(pageIndex, ink.tool, ink.colorIndex, ink.widthIndex, ink.stylusOnly) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (ink.stylusOnly && down.type != PointerType.Stylus && down.type != PointerType.Eraser) return@awaitEachGesture
            val w = size.width.toFloat().coerceAtLeast(1f)
            val h = size.height.toFloat().coerceAtLeast(1f)
            fun nx(x: Float) = (x / w).coerceIn(0f, 1f)
            fun ny(y: Float) = (y / h).coerceIn(0f, 1f)
            val eraser = ink.tool == AnnotateTool.ERASER || down.type == PointerType.Eraser
            val eraseRadius = 0.025f

            down.consume()
            if (eraser) {
                store.beginGroup()
                store.erase(pageIndex, nx(down.position.x), ny(down.position.y), eraseRadius, aspect)
            } else {
                live.clear()
                live.add(nx(down.position.x)); live.add(ny(down.position.y))
            }

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val x = nx(change.position.x)
                val y = ny(change.position.y)
                if (eraser) {
                    store.erase(pageIndex, x, y, eraseRadius, aspect)
                } else {
                    val lx = live[live.size - 2]
                    val ly = live[live.size - 1]
                    if (abs(x - lx) + abs(y - ly) > 0.0012f) {
                        live.add(x); live.add(y)
                    }
                }
                change.consume()
            }

            if (eraser) {
                store.endGroup()
            } else if (live.isNotEmpty()) {
                val highlighter = ink.tool == AnnotateTool.HIGHLIGHTER
                val points = if (live.size == 2) listOf(live[0], live[1], live[0], live[1]) else live.toList()
                val argb = if (highlighter) ink.color.copy(alpha = 0.38f) else ink.color
                store.add(
                    pageIndex,
                    Stroke(
                        tool = if (highlighter) InkTool.HIGHLIGHTER else InkTool.PEN,
                        color = argb.toArgb(),
                        widthFraction = if (highlighter) ink.widthFraction * 3.2f else ink.widthFraction,
                        points = points
                    )
                )
                live.clear()
            }
        }
    }

    Canvas(modifier = modifier.then(inputModifier)) {
        if (version < 0) return@Canvas // reads the version so the layer redraws whenever the store changes
        val strokes = store.strokes(pageIndex)
        for (s in strokes) drawInk(s.points, Color(s.color), s.widthFraction * size.width, s.tool == InkTool.HIGHLIGHTER)
        if (live.size >= 2) {
            val highlighter = ink.tool == AnnotateTool.HIGHLIGHTER
            val c = if (highlighter) ink.color.copy(alpha = 0.38f) else ink.color
            val wf = if (highlighter) ink.widthFraction * 3.2f else ink.widthFraction
            drawInk(live.toList(), c, wf * size.width, highlighter)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInk(points: List<Float>, color: Color, widthPx: Float, highlighter: Boolean) {
    if (points.size < 2) return
    val path = Path()
    path.moveTo(points[0] * size.width, points[1] * size.height)
    var i = 2
    while (i + 1 < points.size) {
        path.lineTo(points[i] * size.width, points[i + 1] * size.height)
        i += 2
    }
    if (points.size == 4 && points[0] == points[2] && points[1] == points[3]) {
        path.lineTo(points[0] * size.width + 0.1f, points[1] * size.height) // a tap becomes a dot
    }
    drawPath(
        path = path,
        color = color,
        style = DrawStroke(
            width = widthPx,
            cap = if (highlighter) StrokeCap.Square else StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

/** Bottom toolbar shown while annotating. Every control is at least 40 dp high and has a text label. */
@Composable
fun AnnotationToolbar(
    ink: InkSettings,
    onInkChange: (InkSettings) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onSaveCopy: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface.copy(alpha = 0.97f),
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnnotateTool.entries.forEach { tool ->
                    ToolChip(label = tool.label, selected = ink.tool == tool) { onInkChange(ink.copy(tool = tool)) }
                }
                Spacer(Modifier.width(6.dp))
                InkPalette.forEachIndexed { index, c ->
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (ink.colorIndex == index) 3.dp else 1.dp, if (ink.colorIndex == index) colors.primary else colors.outlineVariant, CircleShape)
                            .clickable { onInkChange(ink.copy(colorIndex = index)) }
                            .semantics { contentDescription = "Colour ${index + 1}" }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InkWidthLabels.forEachIndexed { index, label ->
                    ToolChip(label = label, selected = ink.widthIndex == index) { onInkChange(ink.copy(widthIndex = index)) }
                }
                ToolChip(label = "Stylus only", selected = ink.stylusOnly) { onInkChange(ink.copy(stylusOnly = !ink.stylusOnly)) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    TextButton(onClick = onUndo, enabled = canUndo) { Text("Undo", fontSize = 13.sp) }
                    TextButton(onClick = onRedo, enabled = canRedo) { Text("Redo", fontSize = 13.sp) }
                    TextButton(onClick = onClear) { Text("Clear all", fontSize = 13.sp, color = colors.error) }
                }
                Row {
                    TextButton(onClick = onSaveCopy) { Text("Save copy", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    TextButton(onClick = onDone) { Text("Done", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
            Text(
                text = "Sketches are saved automatically and kept apart from the original file.",
                fontSize = 11.sp,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToolChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) colors.primaryContainer else colors.surfaceContainer)
            .border(1.dp, if (selected) colors.primary else colors.outlineVariant, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface
        )
    }
}
