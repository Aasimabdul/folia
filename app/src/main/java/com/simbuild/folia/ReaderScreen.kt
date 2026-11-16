package com.simbuild.folia

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbuild.folia.engine.DocEngine
import com.simbuild.folia.engine.PageCache
import com.simbuild.folia.ui.theme.DarkReadingBg
import com.simbuild.folia.ui.theme.LightReadingBg
import com.simbuild.folia.ui.theme.ReadingThemeMode
import com.simbuild.folia.ui.theme.SepiaReadingBg
import com.simbuild.folia.ui.theme.getFormatBadgeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-featured Reader Screen for Folia:
 * - Immersive reading with auto-hide toolbars on tap
 * - Smooth vertical scrolling with byte-budgeted LRU cache
 * - Interactive page scrubber / jump-to-page slider
 * - Reading themes (AMOLED Dark, Light Paper, Warm Sepia)
 * - Reflow text scaling (A- / A+) for EPUB/MOBI/TXT/Office
 * - Double-tap / gesture zoom & pan
 * - Document metadata / info dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    engine: DocEngine,
    initialPage: Int = 0,
    onBack: () -> Unit,
    onPageChanged: (page: Int, totalPages: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isControlsVisible by remember { mutableStateOf(true) }
    var readingTheme by remember { mutableStateOf(ReadingThemeMode.DARK) }
    var fontSizePt by remember { mutableFloatStateOf(14f) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val bgColor = when (readingTheme) {
        ReadingThemeMode.DARK -> DarkReadingBg
        ReadingThemeMode.LIGHT -> LightReadingBg
        ReadingThemeMode.SEPIA -> SepiaReadingBg
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }

        val cache = remember(engine) { PageCache() }
        var pageCount by remember(engine) { mutableIntStateOf(engine.pageCount) }
        var layoutVersion by remember(engine) { mutableIntStateOf(0) }

        // Relayout reflowable formats (EPUB/MOBI/TXT/Office/MD)
        LaunchedEffect(engine, widthPx, heightPx, fontSizePt) {
            if (engine.isReflowable) {
                withContext(Dispatchers.Default) {
                    engine.relayout(
                        widthPt = widthPx / density.density,
                        heightPt = heightPx / density.density,
                        fontSizePt = fontSizePt,
                    )
                }
                cache.clear()
                pageCount = engine.pageCount
                layoutVersion++
            }
        }

        val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))

        // Track and report reading progress
        val currentPage by remember {
            derivedStateOf { (listState.firstVisibleItemIndex).coerceIn(0, (pageCount - 1).coerceAtLeast(0)) }
        }

        LaunchedEffect(listState, pageCount) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { page ->
                    onPageChanged(page, pageCount)
                }
        }

        // Zoom & Pan state
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isControlsVisible = !isControlsVisible
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(count = pageCount, key = { "$layoutVersion-$it" }) { index ->
                    PageItem(engine, cache, index, widthPx)
                }
            }
        }

        // Top App Bar Controls
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(getFormatBadgeColor(engine.format))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = engine.format.uppercase(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = engine.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Reflow font size buttons
                    if (engine.isReflowable) {
                        IconButton(onClick = { fontSizePt = (fontSizePt - 2f).coerceAtLeast(10f) }) {
                            Text("A-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        IconButton(onClick = { fontSizePt = (fontSizePt + 2f).coerceAtMost(28f) }) {
                            Text("A+", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }

                    // Theme toggle button
                    IconButton(onClick = {
                        readingTheme = when (readingTheme) {
                            ReadingThemeMode.DARK -> ReadingThemeMode.SEPIA
                            ReadingThemeMode.SEPIA -> ReadingThemeMode.LIGHT
                            ReadingThemeMode.LIGHT -> ReadingThemeMode.DARK
                        }
                    }) {
                        val iconColor = when (readingTheme) {
                            ReadingThemeMode.DARK -> Color(0xFF90CAF9)
                            ReadingThemeMode.SEPIA -> Color(0xFFD7CCC8)
                            ReadingThemeMode.LIGHT -> Color(0xFFFFB74D)
                        }
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(iconColor)
                        )
                    }

                    // Document details dialog
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = "Document Info")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        }

        // Bottom Page Control Scrubber Bar
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (currentPage > 0) {
                                    coroutineScope.launch { listState.animateScrollToItem(currentPage - 1) }
                                }
                            },
                            enabled = currentPage > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Page")
                        }

                        Text(
                            text = "Page ${currentPage + 1} of $pageCount",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )

                        IconButton(
                            onClick = {
                                if (currentPage < pageCount - 1) {
                                    coroutineScope.launch { listState.animateScrollToItem(currentPage + 1) }
                                }
                            },
                            enabled = currentPage < pageCount - 1
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Page")
                        }
                    }

                    if (pageCount > 1) {
                        Slider(
                            value = currentPage.toFloat(),
                            onValueChange = { targetPage ->
                                coroutineScope.launch {
                                    listState.scrollToItem(targetPage.toInt().coerceIn(0, pageCount - 1))
                                }
                            },
                            valueRange = 0f..(pageCount - 1).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showInfoDialog) {
        DocumentInfoDialog(
            engine = engine,
            currentPage = listState.firstVisibleItemIndex + 1,
            onDismiss = { showInfoDialog = false }
        )
    }
}

@Composable
private fun PageItem(engine: DocEngine, cache: PageCache, index: Int, widthPx: Int) {
    val density = LocalDensity.current

    val aspect = remember(engine, index) {
        runCatching { engine.pageSize(index).let { (w, h) -> h / w } }.getOrDefault(1.4f)
    }
    val heightDp = with(density) { (widthPx * aspect).toInt().toDp() }

    val bitmap by produceState(initialValue = cache.get(index), engine, index, widthPx) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                runCatching { engine.render(index, widthPx) }.getOrNull()?.also { cache.put(index, it) }
            }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(heightDp)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun DocumentInfoDialog(
    engine: DocEngine,
    currentPage: Int,
    onDismiss: () -> Unit
) {
    val sizeText = remember(engine.fileSize) {
        val bytes = engine.fileSize
        when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Document Info", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoItem("Title", engine.title)
                InfoItem("Format", engine.format)
                InfoItem("Total Pages", "${engine.pageCount}")
                InfoItem("Current Page", "$currentPage")
                if (engine.fileSize > 0) {
                    InfoItem("File Size", sizeText)
                }
                InfoItem("Layout Mode", if (engine.isReflowable) "Reflowable (Adjustable text)" else "Fixed Page")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun InfoItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontWeight = FontWeight.Normal)
    }
}
