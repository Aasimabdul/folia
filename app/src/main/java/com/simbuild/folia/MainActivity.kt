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
import com.simbuild.folia.data.RecentDocumentsRepository
import com.simbuild.folia.engine.DocEngine
import com.simbuild.folia.engine.DocEngines
import com.simbuild.folia.model.RecentDocument
import com.simbuild.folia.ui.home.HomeScreen
import com.simbuild.folia.ui.theme.FoliaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var incoming by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
        setContent {
            FoliaTheme {
                App(incoming)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) incoming = intent.data
    }
}

@Composable
private fun App(incoming: Uri?) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { RecentDocumentsRepository(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    var recentDocs by remember { mutableStateOf(repository.getRecentDocuments()) }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var engine by remember { mutableStateOf<DocEngine?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var initialPage by remember { mutableIntStateOf(0) }

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
            initialPage = 0
            currentUri = incoming
        }
    }

    // Open the document off the main thread; always close previous engine
    LaunchedEffect(currentUri) {
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
            }
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
                }
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
