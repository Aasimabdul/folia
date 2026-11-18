package com.simbuild.folia.library

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbuild.folia.model.RecentDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private data class Crumb(val docId: String, val name: String)

private data class Location(val treeUri: Uri, val rootName: String, val crumbs: List<Crumb>) {
    val currentDocId: String get() = crumbs.last().docId
}

private sealed interface ListState {
    data object Loading : ListState
    data class Ready(val entries: List<LibraryEntry>) : ListState
    data class Failed(val message: String, val needsRegrant: Boolean) : ListState
}

/**
 * The Library tab: a small file manager built on the Storage Access Framework.
 * Android only lets an app see folders the user picked, so the root screen shows exactly those folders
 * (plus recent files) and says so, instead of pretending to see the whole device.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    recentDocuments: List<RecentDocument>,
    onOpenFile: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()
    val repo = remember { LibraryRepository(context) }
    val colors = MaterialTheme.colorScheme

    var folders by remember { mutableStateOf(repo.folders) }
    var folderNames by remember { mutableStateOf(emptyMap<Uri, String>()) }
    var location by remember { mutableStateOf<Location?>(null) }
    var state by remember { mutableStateOf<ListState>(ListState.Loading) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(repo.sort) }
    var useGrid by remember { mutableStateOf(repo.useGrid) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var showSortMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<LibraryEntry?>(null) }
    var deleteTargets by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            SafBrowser.persistRead(context, uri)
            repo.add(uri)
            folders = repo.folders
        }
    }

    // Names of the granted folders (a provider query, so off the main thread)
    LaunchedEffect(folders) {
        val names = withContext(Dispatchers.IO) { folders.associateWith { SafBrowser.displayName(resolver, it) } }
        folderNames = names
    }

    // Load the current folder
    LaunchedEffect(location, refreshToken) {
        val loc = location ?: return@LaunchedEffect
        state = ListState.Loading
        selected = emptySet()
        state = withContext(Dispatchers.IO) {
            try {
                if (!SafBrowser.hasGrant(context, loc.treeUri)) {
                    ListState.Failed("Folia no longer has access to this folder.", needsRegrant = true)
                } else {
                    ListState.Ready(SafBrowser.list(resolver, loc.treeUri, loc.currentDocId))
                }
            } catch (e: SecurityException) {
                ListState.Failed("Permission denied for this folder.", needsRegrant = true)
            } catch (e: Exception) {
                ListState.Failed(e.message ?: "This folder could not be read (it may have been moved or deleted).", needsRegrant = false)
            }
        }
    }

    fun goUp() {
        val loc = location ?: return
        location = if (loc.crumbs.size > 1) loc.copy(crumbs = loc.crumbs.dropLast(1)) else null
        query = ""
    }

    BackHandler(enabled = location != null || selected.isNotEmpty()) {
        if (selected.isNotEmpty()) selected = emptySet() else goUp()
    }

    val entries = (state as? ListState.Ready)?.entries.orEmpty()
    val shown = remember(entries, query, sort) { LibraryLogic.arrange(entries, query, sort) }
    val selectedEntries = shown.filter { it.docId in selected }

    fun open(entry: LibraryEntry) {
        val loc = location ?: return
        if (entry.isDirectory) {
            location = loc.copy(crumbs = loc.crumbs + Crumb(entry.docId, entry.name))
            query = ""
        } else {
            onOpenFile(SafBrowser.documentUri(loc.treeUri, entry.docId))
        }
    }

    fun share(list: List<LibraryEntry>) {
        val loc = location ?: return
        val files = list.filter { !it.isDirectory }
        if (files.isEmpty()) {
            message = "Folders cannot be shared. Select files instead."
            return
        }
        val uris = files.map { SafBrowser.documentUri(loc.treeUri, it.docId) }
        val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris[0])
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            clipData = ClipData.newRawUri("Folia", uris[0]).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { message = "No app can handle sharing right now." }
    }

    Column(modifier = modifier.fillMaxSize()) {
        val loc = location
        if (loc == null) {
            RootScreen(
                folders = folders,
                folderNames = folderNames,
                recentDocuments = recentDocuments,
                onBrowseInternal = { picker.launch(SafBrowser.internalStorageHint) },
                onAddFolder = { picker.launch(null) },
                onOpenFolder = { uri ->
                    val name = folderNames[uri] ?: "Folder"
                    location = Location(uri, name, listOf(Crumb(SafBrowser.rootDocumentId(uri), name)))
                },
                onRemoveFolder = { uri ->
                    SafBrowser.releaseGrant(context, uri)
                    repo.remove(uri)
                    folders = repo.folders
                },
                onOpenRecent = { onOpenFile(Uri.parse(it.uriString)) }
            )
        } else {
            // Top bar: selection actions or title with navigation
            if (selected.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, contentDescription = "Clear selection") }
                    Text("${selected.size} selected", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { share(selectedEntries) }) { Icon(Icons.Default.Share, contentDescription = "Share") }
                    if (selectedEntries.size == 1 && selectedEntries[0].canRename) {
                        IconButton(onClick = { renameTarget = selectedEntries[0] }) { Icon(Icons.Default.Edit, contentDescription = "Rename") }
                    }
                    if (selectedEntries.isNotEmpty() && selectedEntries.all { it.canDelete }) {
                        IconButton(onClick = { deleteTargets = selectedEntries }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { goUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Text(
                        text = loc.crumbs.last().name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { refreshToken++ }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                }
            }

            // Breadcrumb: tap any part to jump back to it
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Library",
                    fontSize = 12.sp,
                    color = colors.primary,
                    modifier = Modifier.clickable { location = null; query = "" }
                )
                loc.crumbs.forEachIndexed { index, crumb ->
                    Text(" › ", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    Text(
                        text = crumb.name,
                        fontSize = 12.sp,
                        color = if (index == loc.crumbs.lastIndex) colors.onSurface else colors.primary,
                        maxLines = 1,
                        modifier = Modifier.clickable { location = loc.copy(crumbs = loc.crumbs.take(index + 1)); query = "" }
                    )
                }
            }
            Text(
                text = SafBrowser.describeLocation(loc.treeUri),
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            // Search + sort + view switch
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.surfaceContainer)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Search this folder", color = colors.onSurfaceVariant, fontSize = 14.sp)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = colors.onBackground, fontSize = 14.sp),
                            cursorBrush = SolidColor(colors.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box {
                    TextButton(onClick = { showSortMenu = true }) {
                        Text("${sort.key.label} ${if (sort.ascending) "↑" else "↓"}", fontSize = 13.sp)
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        LibrarySortKey.entries.forEach { key ->
                            DropdownMenuItem(
                                text = { Text(key.label + if (sort.key == key) (if (sort.ascending) "  ↑" else "  ↓") else "") },
                                onClick = {
                                    // Choosing the active key again flips the direction
                                    sort = if (sort.key == key) sort.copy(ascending = !sort.ascending) else LibrarySort(key, true)
                                    repo.sort = sort
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
                Row {
                    TextButton(onClick = { useGrid = false; repo.useGrid = false }) {
                        Text("List", fontSize = 13.sp, fontWeight = if (!useGrid) FontWeight.Bold else FontWeight.Normal)
                    }
                    TextButton(onClick = { useGrid = true; repo.useGrid = true }) {
                        Text("Grid", fontSize = 13.sp, fontWeight = if (useGrid) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            when (val s = state) {
                is ListState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
                is ListState.Failed -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = colors.error, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(s.message, textAlign = TextAlign.Center, color = colors.onSurface)
                    Spacer(Modifier.height(12.dp))
                    if (s.needsRegrant) {
                        TextButton(onClick = { picker.launch(loc.treeUri) }) { Text("Grant access again") }
                    }
                    TextButton(onClick = { refreshToken++ }) { Text("Try again") }
                }
                is ListState.Ready -> {
                    if (shown.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(if (query.isBlank()) "This folder is empty" else "No matches for \"$query\"", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (query.isBlank()) "Documents you put here will show up after a refresh." else "Try another name or extension.",
                                fontSize = 13.sp,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (useGrid) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(104.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            gridItems(shown, key = { it.docId }) { entry ->
                                val isSelected = entry.docId in selected
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isSelected) colors.primaryContainer else colors.surfaceContainer)
                                        .combinedClickable(
                                            onClick = { if (selected.isNotEmpty()) selected = toggle(selected, entry.docId) else open(entry) },
                                            onLongClick = { selected = toggle(selected, entry.docId) }
                                        )
                                        .padding(10.dp)
                                ) {
                                    EntryIcon(entry, size = 48)
                                    Spacer(Modifier.height(6.dp))
                                    Text(entry.name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = colors.onSurface)
                                    Text(if (entry.isDirectory) "Folder" else LibraryLogic.formatSize(entry.sizeBytes), fontSize = 10.sp, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
                        LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), modifier = Modifier.fillMaxSize()) {
                            items(shown, key = { it.docId }) { entry ->
                                val isSelected = entry.docId in selected
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) colors.primaryContainer else Color.Transparent)
                                        .combinedClickable(
                                            onClick = { if (selected.isNotEmpty()) selected = toggle(selected, entry.docId) else open(entry) },
                                            onLongClick = { selected = toggle(selected, entry.docId) }
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    EntryIcon(entry, size = 40)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurface)
                                        val date = if (entry.modifiedMillis > 0) dateFormat.format(Date(entry.modifiedMillis)) else ""
                                        val detail = if (entry.isDirectory) date else listOf(LibraryLogic.formatSize(entry.sizeBytes), date).filter { it.isNotEmpty() }.joinToString("  ·  ")
                                        if (detail.isNotEmpty()) Text(detail, fontSize = 12.sp, color = colors.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        var text by remember(target) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val loc = location
                    val clean = LibraryLogic.sanitizeFileName(text)
                    renameTarget = null
                    if (loc != null && clean != null && clean != target.name) {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                SafBrowser.rename(resolver, SafBrowser.documentUri(loc.treeUri, target.docId), clean)
                            }
                            message = if (ok) "Renamed to $clean" else "Could not rename (the folder may be read-only)."
                            refreshToken++
                        }
                    } else if (clean == null) message = "That name cannot be used."
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    if (deleteTargets.isNotEmpty()) {
        val targets = deleteTargets
        AlertDialog(
            onDismissRequest = { deleteTargets = emptyList() },
            title = { Text("Delete ${if (targets.size == 1) "\"${targets[0].name}\"" else "${targets.size} items"}?") },
            text = { Text("This permanently removes the ${if (targets.size == 1) "item" else "items"} from your device. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val loc = location
                    deleteTargets = emptyList()
                    if (loc != null) {
                        scope.launch {
                            val failed = withContext(Dispatchers.IO) {
                                targets.count { !SafBrowser.delete(resolver, SafBrowser.documentUri(loc.treeUri, it.docId)) }
                            }
                            message = if (failed == 0) "Deleted" else "$failed item(s) could not be deleted."
                            refreshToken++
                        }
                    }
                }) { Text("Delete", color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTargets = emptyList() }) { Text("Cancel") } }
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }
        )
    }
}

private fun toggle(set: Set<String>, id: String): Set<String> = if (id in set) set - id else set + id

@Composable
private fun RootScreen(
    folders: List<Uri>,
    folderNames: Map<Uri, String>,
    recentDocuments: List<RecentDocument>,
    onBrowseInternal: () -> Unit,
    onAddFolder: () -> Unit,
    onOpenFolder: (Uri) -> Unit,
    onRemoveFolder: (Uri) -> Unit,
    onOpenRecent: (RecentDocument) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text("Library", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            Spacer(Modifier.height(2.dp))
            Text(
                "Android only lets Folia see folders you choose. Pick a folder once and browse it here.",
                fontSize = 13.sp,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
        }

        item { SectionLabel("Device storage") }
        item {
            RootCard(
                title = "Internal storage",
                subtitle = "Choose a folder on your phone, e.g. Documents or Download",
                onClick = onBrowseInternal
            )
        }

        item { SectionLabel("Your folders") }
        if (folders.isEmpty()) {
            item { Text("No folders added yet.", fontSize = 13.sp, color = colors.onSurfaceVariant) }
        }
        items(folders, key = { it.toString() }) { uri ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    RootCard(
                        title = folderNames[uri] ?: "Folder",
                        subtitle = SafBrowser.describeLocation(uri),
                        onClick = { onOpenFolder(uri) }
                    )
                }
                IconButton(onClick = { onRemoveFolder(uri) }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove folder from Library")
                }
            }
        }
        item {
            TextButton(onClick = onAddFolder) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add another folder")
            }
        }

        item { SectionLabel("Recent files") }
        if (recentDocuments.isEmpty()) {
            item { Text("Files you open will appear here.", fontSize = 13.sp, color = colors.onSurfaceVariant) }
        }
        items(recentDocuments.take(8), key = { "recent-" + it.uriString }) { doc ->
            RootCard(title = doc.title, subtitle = doc.format.uppercase() + "  ·  " + LibraryLogic.formatSize(doc.fileSize), onClick = { onOpenRecent(doc) })
        }

        item { SectionLabel("App storage") }
        item {
            Text(
                "Folia keeps a temporary copy of the open document and your sketches in its private storage. " +
                    "Temporary copies are removed when you close the app; nothing leaves your phone.",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp)
    )
}

@Composable
private fun RootCard(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        FolderGlyph(size = 36)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurface)
            Text(subtitle, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun EntryIcon(entry: LibraryEntry, size: Int) {
    if (entry.isDirectory) {
        FolderGlyph(size)
    } else {
        val colors = MaterialTheme.colorScheme
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.secondaryContainer)
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(10.dp))
        ) {
            Text(
                text = entry.extension.take(4).uppercase().ifEmpty { "FILE" },
                fontSize = if (size >= 48) 12.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSecondaryContainer,
                maxLines = 1
            )
        }
    }
}

/** Folder icon drawn with two rounded rectangles (the core icon set has no folder glyph, and the extended set is huge). */
@Composable
private fun FolderGlyph(size: Int) {
    val tint = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(modifier = Modifier.size(size.dp)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(tint.copy(alpha = 0.55f), topLeft = Offset(w * 0.06f, h * 0.18f), size = Size(w * 0.42f, h * 0.25f), cornerRadius = CornerRadius(w * 0.08f))
        drawRoundRect(tint, topLeft = Offset(w * 0.06f, h * 0.30f), size = Size(w * 0.88f, h * 0.55f), cornerRadius = CornerRadius(w * 0.10f))
    }
}
