package com.simbuild.folia

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import com.simbuild.folia.data.RecentDocumentsRepository
import com.simbuild.folia.data.SettingsRepository
import com.simbuild.folia.engine.DocEngine
import com.simbuild.folia.engine.DocEngines
import com.simbuild.folia.model.RecentDocument
import com.simbuild.folia.ui.home.HomeScreen
import com.simbuild.folia.ui.theme.AppThemeMode
import com.simbuild.folia.ui.theme.FoliaTheme
import com.simbuild.folia.ui.theme.PageStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A document handed to Folia by another app. The id makes every hand-over a new value, so opening the
 * same file twice from the file manager still opens it again.
 */
private data class IncomingFile(val uri: Uri, val id: Long = System.nanoTime())

class MainActivity : ComponentActivity() {

    private var incoming by mutableStateOf<IncomingFile?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming = documentFrom(intent)
        setContent {
            val settings = remember { SettingsRepository(applicationContext) }
            var themeMode by remember { mutableStateOf(settings.themeMode) }
            var pageStyle by remember { mutableStateOf(settings.pageStyle) }
            var dimPages by remember { mutableStateOf(settings.dimPagesInDarkMode) }

            FoliaTheme(themeMode = themeMode) {
                App(
                    incoming = incoming,
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        settings.themeMode = mode
                    },
                    pageStyle = pageStyle,
                    onPageStyleChange = { style ->
                        pageStyle = style
                        settings.pageStyle = style
                    },
                    dimPagesInDarkMode = dimPages,
                    onDimPagesChange = { dim ->
                        dimPages = dim
                        settings.dimPagesInDarkMode = dim
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        documentFrom(intent)?.let { incoming = it }
    }

    /** "Open with" (VIEW) and the share sheet (SEND) both carry a content URI for the document. */
    private fun documentFrom(intent: Intent?): IncomingFile? {
        if (intent == null) return null
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return null
        // Keep read access for the Recent list when the sender allows it (not every provider does)
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        return IncomingFile(uri)
    }
}

@Composable
private fun App(
    incoming: IncomingFile?,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    pageStyle: PageStyle,
    onPageStyleChange: (PageStyle) -> Unit,
    dimPagesInDarkMode: Boolean,
    onDimPagesChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { RecentDocumentsRepository(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    var recentDocs by remember { mutableStateOf(repository.getRecentDocuments()) }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var engine by remember { mutableStateOf<DocEngine?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var initialPage by remember { mutableIntStateOf(0) }
    // Bumped whenever a document is (re)opened so the same file can be opened again
    var reloadToken by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { picked ->
        if (picked != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    picked,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Not all ContentProviders support persistable URI permissions
            }
            initialPage = 0
            currentUri = picked
        }
    }

    LaunchedEffect(incoming) {
        if (incoming != null) {
            val existing = recentDocs.firstOrNull { it.uriString == incoming.uri.toString() }
            initialPage = existing?.lastReadPage ?: 0
            currentUri = incoming.uri
            reloadToken++
        }
    }

    // Open the document off the main thread; always close previous engine
    LaunchedEffect(currentUri, reloadToken) {
        val target = currentUri ?: return@LaunchedEffect
        isLoading = true
        engine?.close()
        engine = null

        val opened = try {
            withContext(Dispatchers.IO) {
                DocEngines.open(context, target)
            }
        } catch (t: Throwable) {
            val msg = t.message ?: "Failed to open document"
            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            null
        }

        if (opened != null) {
            engine = opened
            repository.saveRecentDocument(
                RecentDocument(
                    uriString = target.toString(),
                    title = opened.title,
                    format = opened.format,
                    lastReadPage = initialPage,
                    pageCount = opened.pageCount,
                    fileSize = opened.fileSize
                )
            )
            recentDocs = repository.getRecentDocuments()
        }
        isLoading = false
    }

    DisposableEffect(Unit) {
        onDispose { engine?.close() }
    }

    val currentEngine = engine
    if (currentEngine != null && currentUri != null) {
        BackHandler {
            engine?.close()
            engine = null
            currentUri = null
            recentDocs = repository.getRecentDocuments()
        }

        ReaderScreen(
            engine = currentEngine,
            initialPage = initialPage,
            onBack = {
                engine?.close()
                engine = null
                currentUri = null
                recentDocs = repository.getRecentDocuments()
            },
            onPageChanged = { page, totalPages ->
                currentUri?.let { uri ->
                    repository.updateReadingProgress(uri.toString(), page, totalPages)
                }
            },
            pageStyle = pageStyle,
            dimPagesInDarkMode = dimPagesInDarkMode,
            onPageStyleChange = onPageStyleChange
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            HomeScreen(
                recentDocuments = recentDocs,
                onOpenDocument = { picker.launch(arrayOf("*/*")) },
                onSelectRecent = { uri ->
                    val existing = recentDocs.firstOrNull { it.uriString == uri.toString() }
                    initialPage = existing?.lastReadPage ?: 0
                    currentUri = uri
                },
                onToggleFavorite = { uriString ->
                    repository.toggleFavorite(uriString)
                    recentDocs = repository.getRecentDocuments()
                },
                onRemoveRecent = { uriString ->
                    repository.removeRecentDocument(uriString)
                    recentDocs = repository.getRecentDocuments()
                },
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                pageStyle = pageStyle,
                onPageStyleChange = onPageStyleChange,
                dimPagesInDarkMode = dimPagesInDarkMode,
                onDimPagesChange = onDimPagesChange
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
