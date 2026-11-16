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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.simbuild.folia.engine.SearchResult
import com.simbuild.folia.engine.TocItem
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
 * Universal Reader Screen:
 * - Immersive reading with auto-hide toolbars on tap
 * - Smooth vertical scrolling with byte-budgeted LRU cache
 * - Find-in-document search with match navigation
 * - Table of Contents (TOC / Outlines) chapter navigation
 * - Reading themes (AMOLED Dark, Light Paper, Warm Sepia)
 * - Reflow text scaling (A- / A+) for EPUB/Word/Markdown/Code/Office
 * - Double-tap / gesture zoom & pan
 * - Detailed format capability and security audit dialog
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
    var showTocDialog by remember { mutableStateOf(false) }

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

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

        // Relayout reflowable formats
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

        // Search execution
        LaunchedEffect(searchQuery) {
            if (searchQuery.length >= 2) {
                withContext(Dispatchers.Default) {
                    val results = engine.search(searchQuery)
                    searchResults = results
                    currentMatchIndex = 0
                    if (results.isNotEmpty()) {
                        listState.animateScrollToItem(results[0].page)
                    }
                }
            } else {
                searchResults = emptyList()
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
            Column {
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Find in document...", fontSize = 14.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                if (searchResults.isNotEmpty()) {
                                    Text(
                                        text = "${currentMatchIndex + 1}/${searchResults.size}",
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    IconButton(
                                        onClick = {
                                            if (currentMatchIndex > 0) {
                                                currentMatchIndex--
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(searchResults[currentMatchIndex].page)
                                                }
                                            }
                                        },
                                        enabled = currentMatchIndex > 0
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev Match")
                                    }
                                    IconButton(
                                        onClick = {
                                            if (currentMatchIndex < searchResults.size - 1) {
                                                currentMatchIndex++
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(searchResults[currentMatchIndex].page)
                                                }
                                            }
                                        },
                                        enabled = currentMatchIndex < searchResults.size - 1
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Match")
                                    }
                                }
                                IconButton(onClick = {
                                    isSearchActive = false
                                    searchQuery = ""
                                    searchResults = emptyList()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close search")
                                }
                            }
                        } else {
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
                        if (!isSearchActive) {
                            // Find in document button
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Find in document")
                            }

                            // Table of contents button
                            IconButton(onClick = { showTocDialog = true }) {
                                Text("📑", fontSize = 16.sp)
                            }

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
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )
            }
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
        if (showInfoDialog) {
            DocumentInfoDialog(
                engine = engine,
                currentPage = currentPage + 1,
                onDismiss = { showInfoDialog = false }
            )
        }
    
        if (showTocDialog) {
            val toc = remember(engine) { engine.tableOfContents() }
            TocDialog(
                tocItems = toc,
                onSelectPage = { page ->
                    coroutineScope.launch {
                        listState.animateScrollToItem(page.coerceIn(0, (engine.pageCount - 1).coerceAtLeast(0)))
                    }
                    showTocDialog = false
                },
                onDismiss = { showTocDialog = false }
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
    private fun TocDialog(
        tocItems: List<TocItem>,
        onSelectPage: (Int) -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Table of Contents", fontWeight = FontWeight.Bold) },
            text = {
                if (tocItems.isEmpty()) {
                    Text("No table of contents outline available for this document.")
                } else {
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(tocItems) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPage(item.page) }
                                    .padding(vertical = 10.dp, horizontal = (item.level * 16).dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.title,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "p. ${item.page + 1}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        )
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
                    InfoItem("Capability", engine.supportStatus.label)
                    InfoItem("Total Pages", "${engine.pageCount}")
                    InfoItem("Current Page", "$currentPage")
                    if (engine.fileSize > 0) {
                        InfoItem("File Size", sizeText)
                    }
                    InfoItem("Layout Mode", if (engine.isReflowable) "Reflowable (Adjustable text)" else "Fixed Page")
                    InfoItem("Security", "100% Offline • Sandboxed")
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        )
    }

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
