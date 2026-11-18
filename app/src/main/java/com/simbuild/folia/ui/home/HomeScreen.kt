package com.simbuild.folia.ui.home

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbuild.folia.model.RecentDocument
import com.simbuild.folia.ui.theme.AppThemeMode
import com.simbuild.folia.ui.theme.PageStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Editorial, literary home screen matching the Figma design:
 * - Serendipitous "A little space to read" header
 * - Pill search bar
 * - Hero "Continue reading" card with resume action
 * - "Your library" with filter tabs (Recent, Saved, Downloaded)
 * - Custom book cover miniatures
 * - Floating bottom navigation bar
 */
@Composable
fun HomeScreen(
    recentDocuments: List<RecentDocument>,
    onOpenDocument: () -> Unit,
    onSelectRecent: (Uri) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onThemeModeChange: (AppThemeMode) -> Unit = {},
    pageStyle: PageStyle = PageStyle.ORIGINAL,
    onPageStyleChange: (PageStyle) -> Unit = {},
    dimPagesInDarkMode: Boolean = true,
    onDimPagesChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Follow the theme that is actually applied (System / Light / Dark), not only the phone's setting
    val colors = MaterialTheme.colorScheme
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("Recent") }
    var selectedBottomNav by remember { mutableIntStateOf(0) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val pillBg = colors.surfaceContainer
    val subtleColor = colors.onSurfaceVariant
    val primaryColor = colors.primary
    val cardBg = colors.surfaceContainerHigh
    val addBtnBg = colors.primaryContainer

    val filteredDocs = remember(recentDocuments, searchQuery, selectedTab) {
        recentDocuments.filter { doc ->
            val matchesSearch = searchQuery.isBlank() ||
                    doc.title.contains(searchQuery, ignoreCase = true) ||
                    doc.format.contains(searchQuery, ignoreCase = true)

            val matchesTab = when (selectedTab) {
                "Recent" -> true
                "Saved" -> doc.isFavorite
                "Downloaded" -> true
                else -> true
            }
            matchesSearch && matchesTab
        }
    }

    val continueDoc = recentDocuments.firstOrNull()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            FolioBottomNavBar(
                selectedIndex = selectedBottomNav,
                onSelectIndex = { index ->
                    selectedBottomNav = index
                    if (index == 1) selectedTab = "Recent"
                    if (index == 2) selectedTab = "Saved"
                    if (index == 3) showSettingsDialog = true
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Header: FOLIO tag, title & add button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FOLIO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = subtleColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "A quieter way to read.",
                            fontFamily = FontFamily.Serif,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Everything you read, in one calm space.",
                            fontSize = 13.sp,
                            color = subtleColor
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(addBtnBg)
                            .clickable { onOpenDocument() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Open Document",
                            tint = primaryColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // 2. Search bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(pillBg)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = subtleColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search your documents",
                                color = subtleColor,
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(primaryColor),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = subtleColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 3. Continue reading section
            item {
                Text(
                    text = "Continue reading",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                if (continueDoc != null) {
                    ContinueReadingHeroCard(
                        doc = continueDoc,
                        cardBg = cardBg,
                        primaryColor = primaryColor,
                        subtleColor = subtleColor,
                        onResume = { onSelectRecent(Uri.parse(continueDoc.uriString)) },
                        onToggleFavorite = { onToggleFavorite(continueDoc.uriString) }
                    )
                } else {
                    EmptyHeroCard(
                        cardBg = cardBg,
                        primaryColor = primaryColor,
                        subtleColor = subtleColor,
                        onOpen = onOpenDocument
                    )
                }
            }

            // 4. "Your library" section header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your library",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "View all",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = subtleColor,
                        modifier = Modifier.clickable { selectedTab = "Recent" }
                    )
                }
            }

            // 5. Library tabs (Recent, Saved, Downloaded)
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Recent", "Saved", "Downloaded").forEach { tab ->
                        val isSelected = selectedTab == tab
                        val tabBg = if (isSelected) primaryColor else pillBg
                        val tabText = if (isSelected) colors.onPrimary else subtleColor

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(tabBg)
                                .clickable { selectedTab = tab }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = tab,
                                color = tabText,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 6. Document list items
            if (filteredDocs.isEmpty()) {
                item {
                    Text(
                        text = "No documents found in this section.",
                        fontSize = 13.sp,
                        color = subtleColor,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(filteredDocs, key = { it.uriString }) { doc ->
                    LibraryDocumentRow(
                        doc = doc,
                        subtleColor = subtleColor,
                        onClick = { onSelectRecent(Uri.parse(doc.uriString)) },
                        onToggleFavorite = { onToggleFavorite(doc.uriString) },
                        onRemove = { onRemoveRecent(doc.uriString) }
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            pageStyle = pageStyle,
            onPageStyleChange = onPageStyleChange,
            dimPagesInDarkMode = dimPagesInDarkMode,
            onDimPagesChange = onDimPagesChange,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

/**
 * Featured "Continue reading" card matching the Figma layout.
 */
@Composable
private fun ContinueReadingHeroCard(
    doc: RecentDocument,
    cardBg: Color,
    primaryColor: Color,
    subtleColor: Color,
    onResume: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val total = doc.pageCount.coerceAtLeast(1)
    val current = (doc.lastReadPage + 1).coerceAtMost(total)
    val percent = ((current.toFloat() / total) * 100).toInt()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onResume() }
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PICK UP WHERE YOU LEFT OFF",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = primaryColor
                )
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (doc.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = doc.title,
                    fontFamily = FontFamily.Serif,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${doc.format} · Universal Document",
                    fontSize = 13.sp,
                    color = subtleColor
                )
            }

            HorizontalDivider(color = primaryColor.copy(alpha = 0.15f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Page $current of $total · $percent%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(primaryColor)
                        .clickable { onResume() }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Resume →",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Welcoming empty-state hero card when no recent files are present.
 */
@Composable
private fun EmptyHeroCard(
    cardBg: Color,
    primaryColor: Color,
    subtleColor: Color,
    onOpen: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "START YOUR READING JOURNEY",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = primaryColor
            )
            Text(
                text = "Open any book or document",
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Supports PDF, Word, EPUB, Excel, PPTX, Comics, and Code.",
                fontSize = 13.sp,
                color = subtleColor
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(primaryColor)
                    .clickable { onOpen() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Open Document →",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Clean document row matching the Figma list item style.
 */
@Composable
private fun LibraryDocumentRow(
    doc: RecentDocument,
    subtleColor: Color,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val formattedDate = remember(doc.lastReadTime) {
        val now = System.currentTimeMillis()
        val diff = now - doc.lastReadTime
        when {
            diff < 24 * 60 * 60 * 1000L -> "Opened today"
            diff < 48 * 60 * 60 * 1000L -> "Opened yesterday"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(doc.lastReadTime))
        }
    }

    // Palette for thumbnail book cover: pastel on light themes, deep muted tones on dark themes
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val coverBg = remember(doc.format, isDark) {
        val fmt = doc.format.uppercase()
        if (isDark) {
            when (fmt) {
                "PDF" -> Color(0xFF4A3530)
                "DOCX", "DOC" -> Color(0xFF2D3F55)
                "EPUB", "MOBI" -> Color(0xFF2F4A3A)
                "XLSX", "CSV" -> Color(0xFF384A2B)
                "PPTX" -> Color(0xFF55402D)
                else -> Color(0xFF353A47)
            }
        } else {
            when (fmt) {
                "PDF" -> Color(0xFFE8DFD8)
                "DOCX", "DOC" -> Color(0xFFD8E2EF)
                "EPUB", "MOBI" -> Color(0xFFD9E4D7)
                "XLSX", "CSV" -> Color(0xFFE2EED9)
                "PPTX" -> Color(0xFFEFE2D8)
                else -> Color(0xFFE5E5E5)
            }
        }
    }
    val coverText = if (isDark) Color(0xFFE9ECF3) else Color(0xFF333333)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Miniature Book Cover
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 60.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(coverBg)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = doc.title.take(12),
                fontFamily = FontFamily.Serif,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 10.sp,
                color = coverText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = doc.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${doc.format} · ${doc.pageCount} pages · $formattedDate",
                fontSize = 12.sp,
                color = subtleColor
            )
        }

        // Three dots action
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = subtleColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Open") },
                    onClick = {
                        menuExpanded = false
                        onClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (doc.isFavorite) "Remove from Saved" else "Save to Favorites") },
                    onClick = {
                        menuExpanded = false
                        onToggleFavorite()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Remove from Recents") },
                    onClick = {
                        menuExpanded = false
                        onRemove()
                    }
                )
            }
        }
    }
}

/**
 * Bottom navigation bar with 4 tabs matching Figma.
 */
@Composable
private fun FolioBottomNavBar(
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        val items = listOf(
            Triple(0, "Home", Icons.Default.Home),
            Triple(1, "Library", Icons.AutoMirrored.Filled.List),
            Triple(2, "Saved", Icons.Default.Favorite),
            Triple(3, "Settings", Icons.Default.Settings)
        )

        items.forEach { (index, label, icon) ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onSelectIndex(index) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
private fun SettingsDialog(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    pageStyle: PageStyle,
    onPageStyleChange: (PageStyle) -> Unit,
    dimPagesInDarkMode: Boolean,
    onDimPagesChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SettingsSectionTitle("App theme")
                AppThemeMode.entries.forEach { mode ->
                    SettingsRadioRow(
                        selected = themeMode == mode,
                        title = when (mode) {
                            AppThemeMode.SYSTEM -> "System default"
                            AppThemeMode.LIGHT -> "Light"
                            AppThemeMode.DARK -> "Dark"
                        },
                        onClick = { onThemeModeChange(mode) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SettingsSectionTitle("Document pages")
                PageStyle.entries.forEach { style ->
                    SettingsRadioRow(
                        selected = pageStyle == style,
                        title = style.label,
                        subtitle = style.description,
                        onClick = { onPageStyleChange(style) }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Soften white pages in dark mode")
                        Text(
                            text = "Slightly dims bright pages at night. Applies to original colours.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = dimPagesInDarkMode, onCheckedChange = onDimPagesChange)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Folia ${com.simbuild.folia.BuildConfig.VERSION_NAME} · open source",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "100% offline. No analytics, no network permission.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SettingsRadioRow(
    selected: Boolean,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
