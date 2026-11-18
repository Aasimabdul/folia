package com.simbuild.folia

import android.os.Build
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.simbuild.folia.ui.theme.findActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
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
import com.simbuild.folia.ui.theme.PageStyle
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
 * - Page styles: original colours (default), dark pages, sepia; set in Settings or with the top-bar button
 * - Bottom bar with page thumbnails (tap to jump), previous/next buttons and a page counter that stays visible in immersive mode
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
    pageStyle: PageStyle = PageStyle.ORIGINAL,
    dimPagesInDarkMode: Boolean = true,
    onPageStyleChange: (PageStyle) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isControlsVisible by remember { mutableStateOf(true) }
    ImmersiveSystemBars()
    // Page colours come from the saved page style (default: original colours, also in dark mode).
    val appIsDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val readingTheme = pageStyle.resolve(appIsDark)
    // Original (white) pages in a dark app get slightly dimmed so they are not blinding at night
    val dimOriginalPages = dimPagesInDarkMode && appIsDark && readingTheme == ReadingThemeMode.LIGHT
    val pageFilter = remember(readingTheme, dimOriginalPages) { pageColorFilter(readingTheme, dimOriginalPages) }
    var fontSizePt by remember { mutableFloatStateOf(14f) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showTocDialog by remember { mutableStateOf(false) }

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    // The "desk" behind the pages: original pages sit on a dark desk when the app is dark
    val bgColor = when {
        readingTheme == ReadingThemeMode.DARK -> DarkReadingBg
        readingTheme == ReadingThemeMode.SEPIA -> SepiaReadingBg
        appIsDark -> DarkReadingBg
        else -> LightReadingBg
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
        // Small separate budget (4 MB) for the thumbnail strip so it never competes with the page cache
        val thumbCache = remember(engine) { PageCache(maxBytes = 4 shl 20) }
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
                thumbCache.clear()
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
                    PageItem(engine, cache, index, widthPx, pageFilter)
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
                            IconButton(onClick = { onPageStyleChange(pageStyle.next()) }) {
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
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
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
                        PageThumbnailStrip(
                            engine = engine,
                            cache = thumbCache,
                            pageCount = pageCount,
                            currentPage = currentPage,
                            layoutVersion = layoutVersion,
                            colorFilter = pageFilter,
                            onSelect = { page ->
                                coroutineScope.launch { listState.scrollToItem(page.coerceIn(0, pageCount - 1)) }
                            }
                        )
                    }
                }
            }
        }

        // Small page counter that stays visible while the bars are hidden
        AnimatedVisibility(
            visible = !isControlsVisible && pageCount > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = "${currentPage + 1} / $pageCount",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
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
    }

    @Composable
    private fun PageItem(engine: DocEngine, cache: PageCache, index: Int, widthPx: Int, colorFilter: ColorFilter?) {
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
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    
    /**
     * Horizontal strip of small page previews above the slider. Only the few visible thumbnails are composed and
     * rendered (about 140 px wide each), and they live in their own 4 MB cache, so RAM stays flat for any page count.
     */
    @Composable
    private fun PageThumbnailStrip(
        engine: DocEngine,
        cache: PageCache,
        pageCount: Int,
        currentPage: Int,
        layoutVersion: Int,
        colorFilter: ColorFilter?,
        onSelect: (Int) -> Unit
    ) {
        val stripState = rememberLazyListState()

        // Keep the current page near the left of the strip as the reader scrolls or the slider moves
        LaunchedEffect(currentPage, pageCount) {
            stripState.scrollToItem((currentPage - 1).coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
        }

        LazyRow(
            state = stripState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(count = pageCount, key = { "$layoutVersion-thumb-$it" }) { index ->
                PageThumbnail(
                    engine = engine,
                    cache = cache,
                    index = index,
                    selected = index == currentPage,
                    colorFilter = colorFilter,
                    onClick = { onSelect(index) }
                )
            }
        }
    }

    @Composable
    private fun PageThumbnail(
        engine: DocEngine,
        cache: PageCache,
        index: Int,
        selected: Boolean,
        colorFilter: ColorFilter?,
        onClick: () -> Unit
    ) {
        val bitmap by produceState(initialValue = cache.get(index), engine, index) {
            if (value == null) {
                value = withContext(Dispatchers.Default) {
                    runCatching { engine.render(index, THUMBNAIL_WIDTH_PX) }.getOrNull()?.also { cache.put(index, it) }
                }
            }
        }
        val primary = MaterialTheme.colorScheme.primary

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(52.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
        ) {
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(
                        width = if (selected) 2.dp else 0.5.dp,
                        color = if (selected) primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Page ${index + 1} preview",
                        contentScale = ContentScale.Fit,
                        colorFilter = colorFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Text(
                text = "${index + 1}",
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
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

private const val THUMBNAIL_WIDTH_PX = 140

/**
 * Pages are rendered once as white-paper bitmaps and recolored at draw time, so switching the page style is
 * instant and costs no extra memory (the page cache is not rebuilt).
 *
 * Each channel is mapped linearly: black -> `ink`, white -> `paper`.
 *  - DARK:  white paper becomes the app's ink-navy (#12161E) and black text becomes soft off-white (#E6E8EE).
 *  - SEPIA: warm paper (#FBF0D9) with brown ink (#3E2D1D).
 *  - LIGHT: the original colours. With [dimOriginal] (dark app only) white is softened to about 88 % so the
 *           page is not blinding at night, while every colour keeps its hue.
 * Photos inside a page are recolored too in DARK and SEPIA, which is the usual trade-off of a night mode.
 */
private fun pageColorFilter(theme: ReadingThemeMode, dimOriginal: Boolean): ColorFilter? = when (theme) {
    ReadingThemeMode.LIGHT ->
        if (dimOriginal) channelMap(ink = intArrayOf(0, 0, 0), paper = intArrayOf(224, 224, 224)) else null
    ReadingThemeMode.DARK -> channelMap(ink = intArrayOf(230, 232, 238), paper = intArrayOf(18, 22, 30))
    ReadingThemeMode.SEPIA -> channelMap(ink = intArrayOf(62, 45, 29), paper = intArrayOf(251, 240, 217))
}

private fun channelMap(ink: IntArray, paper: IntArray): ColorFilter {
    fun scale(c: Int) = (paper[c] - ink[c]) / 255f
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                scale(0), 0f, 0f, 0f, ink[0].toFloat(),
                0f, scale(1), 0f, 0f, ink[1].toFloat(),
                0f, 0f, scale(2), 0f, ink[2].toFloat(),
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}

/**
 * True full-screen reading: hides the status and navigation bars while the reader is on screen.
 * Swiping from an edge shows them briefly (transient) and they hide again by themselves.
 * The previous state is restored when the reader leaves the screen, and the effect is re-applied after
 * rotation or when the window regains focus. Content may extend under the display cutout in landscape.
 */
@Composable
private fun ImmersiveSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousBehavior = controller.systemBarsBehavior
            val previousCutout = if (Build.VERSION.SDK_INT >= 28) window.attributes.layoutInDisplayCutoutMode else 0

            fun hideBars() {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }

            if (Build.VERSION.SDK_INT >= 28) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            hideBars()
            // Some devices bring the bars back when the window loses and regains focus (dialogs, split screen)
            val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus -> if (hasFocus) hideBars() }
            view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)

            onDispose {
                view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = previousBehavior
                if (Build.VERSION.SDK_INT >= 28) {
                    window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = previousCutout }
                }
            }
        }
    }
}
