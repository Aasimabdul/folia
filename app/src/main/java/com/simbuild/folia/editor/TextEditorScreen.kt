package com.simbuild.folia.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbuild.folia.annotations.AnnotationExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Loaded {
    data object Loading : Loaded
    data class Failed(val message: String) : Loaded
    data class Ready(val decoded: EditorLogic.Decoded) : Loaded
}

private data class SavedInfo(val fileName: String, val location: String, val verified: Boolean, val uri: Uri, val isCopy: Boolean)

/**
 * Plain-text editor for TXT, Markdown, code and other text formats. Nothing is written until the user taps Save
 * (overwrite, after confirmation) or Save as (new file in a folder they pick). After writing, the file is read
 * back and compared, and the success message only appears when that check passes.
 */
@Composable
fun TextEditorScreen(
    uri: Uri,
    fileName: String,
    onClose: (openUri: Uri?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    var loaded by remember(uri) { mutableStateOf<Loaded>(Loaded.Loading) }
    var text by remember(uri) { mutableStateOf(TextFieldValue("")) }
    var savedText by remember(uri) { mutableStateOf("") }
    var hasBom by remember(uri) { mutableStateOf(false) }
    var history by remember(uri) { mutableStateOf(UndoHistory("")) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<SavedInfo?>(null) }
    var confirmOverwrite by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val dirty = loaded is Loaded.Ready && text.text != savedText

    LaunchedEffect(uri) {
        loaded = withContext(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val buf = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        buf.write(chunk, 0, n)
                        if (buf.size() > EditorLogic.MAX_EDIT_BYTES) return@use null
                    }
                    buf.toByteArray()
                } ?: return@withContext Loaded.Failed("This file is larger than ${EditorLogic.MAX_EDIT_BYTES / 1024} KB, so it is read-only in Folia.")
                val decoded = EditorLogic.decodeUtf8(bytes)
                    ?: return@withContext Loaded.Failed("This file is not UTF-8 text, so Folia will not edit it (editing could damage it).")
                Loaded.Ready(decoded)
            } catch (e: Exception) {
                Loaded.Failed("The file could not be opened for editing: ${e.message ?: "unknown error"}")
            }
        }
        (loaded as? Loaded.Ready)?.let {
            text = TextFieldValue(it.decoded.text)
            savedText = it.decoded.text
            hasBom = it.decoded.hasBom
            history = UndoHistory(it.decoded.text)
        }
    }

    // Group typing into undo steps: one step after a short pause
    LaunchedEffect(text.text) {
        if (loaded is Loaded.Ready) {
            delay(500)
            history.record(text.text)
        }
    }

    fun undo() {
        history.record(text.text)
        history.undo()?.let { text = TextFieldValue(it, TextRange(it.length)) }
    }

    fun redo() {
        history.redo()?.let { text = TextFieldValue(it, TextRange(it.length)) }
    }

    fun requestClose() {
        if (dirty) confirmDiscard = true else onClose(null)
    }

    BackHandler { requestClose() }

    /** Writes [content] to [target], then reads it back to prove it really is on disk. */
    suspend fun write(target: Uri, content: String, truncate: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = EditorLogic.encodeUtf8(content, hasBom)
            val out = context.contentResolver.openOutputStream(target, if (truncate) "wt" else "w")
                ?: error("The location cannot be written to.")
            out.use { it.write(bytes); it.flush() }
            val back = context.contentResolver.openInputStream(target)?.use { it.readBytes() }
            back != null && back.contentEquals(bytes)
        }
    }

    fun failureText(e: Throwable): String {
        val msg = e.message.orEmpty()
        return when {
            e is SecurityException -> "Permission denied: Android does not let Folia change this file. Use Save as to write a copy."
            msg.contains("ENOSPC", true) || msg.contains("space", true) -> "Not enough free storage to save."
            else -> "Saving failed: " + msg.ifBlank { "unknown error" }
        }
    }

    val saveAsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(EditorLogic.mimeFor(fileName))) { target ->
        if (target != null) {
            busy = true
            val snapshot = text.text
            scope.launch {
                val result = write(target, snapshot, truncate = true)
                busy = false
                result.fold(
                    onSuccess = { ok ->
                        if (ok) {
                            saved = SavedInfo(
                                AnnotationExporter.displayName(context.contentResolver, target),
                                AnnotationExporter.describeLocation(target),
                                true, target, isCopy = true
                            )
                        } else error = "The file was written but did not match when read back. It was not trusted."
                    },
                    onFailure = { error = failureText(it) }
                )
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { requestClose() }) { Text("Close") }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName + if (dirty) "  •" else "",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1
                )
                Text(
                    text = if (dirty) "Unsaved changes" else "All changes saved",
                    fontSize = 11.sp,
                    color = if (dirty) colors.error else colors.onSurfaceVariant
                )
            }
            TextButton(onClick = { undo() }, enabled = history.canUndo || dirty) { Text("Undo") }
            TextButton(onClick = { redo() }, enabled = history.canRedo) { Text("Redo") }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { saveAsLauncher.launch(fileName) }, enabled = loaded is Loaded.Ready && !busy) { Text("Save as...") }
            TextButton(onClick = { confirmOverwrite = true }, enabled = dirty && !busy) { Text("Save", fontWeight = FontWeight.Bold) }
        }

        when (val l = loaded) {
            is Loaded.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            is Loaded.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(l.message)
                TextButton(onClick = { onClose(null) }) { Text("Back to reading") }
            }
            is Loaded.Ready -> Column(modifier = Modifier.weight(1f).fillMaxWidth().imePadding()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(
                            color = colors.onBackground,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            fontFamily = if (EditorLogic.mimeFor(fileName) == "text/plain" && fileName.substringAfterLast('.').lowercase() == "txt") FontFamily.Default else FontFamily.Monospace
                        ),
                        cursorBrush = SolidColor(colors.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = "${text.text.length} characters  ·  ${text.text.count { it == '\n' } + 1} lines",
                    fontSize = 11.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).navigationBarsPadding()
                )
            }
        }
    }

    if (busy) {
        AlertDialog(onDismissRequest = { }, text = { Text("Saving...") }, confirmButton = { })
    }

    if (confirmOverwrite) {
        AlertDialog(
            onDismissRequest = { confirmOverwrite = false },
            title = { Text("Overwrite this file?") },
            text = {
                Text("\"$fileName\" in ${AnnotationExporter.describeLocation(uri)} will be replaced with your edited text. Use Save as to keep the original.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmOverwrite = false
                    busy = true
                    val snapshot = text.text
                    scope.launch {
                        val result = write(uri, snapshot, truncate = true)
                        busy = false
                        result.fold(
                            onSuccess = { ok ->
                                if (ok) {
                                    savedText = snapshot
                                    saved = SavedInfo(fileName, AnnotationExporter.describeLocation(uri), true, uri, isCopy = false)
                                } else {
                                    error = "The file was written but did not match when read back. Use Save as to keep your text safe."
                                }
                            },
                            onFailure = { error = failureText(it) }
                        )
                    }
                }) { Text("Overwrite") }
            },
            dismissButton = { TextButton(onClick = { confirmOverwrite = false }) { Text("Cancel") } }
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes. If you leave now they will be lost.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose(null) }) { Text("Discard", color = colors.error) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } }
        )
    }

    error?.let { msg ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Not saved") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }
        )
    }

    saved?.let { info ->
        AlertDialog(
            onDismissRequest = { saved = null },
            title = { Text(if (info.isCopy) "Copy saved" else "Saved") },
            text = {
                Column {
                    Text(info.fileName, fontWeight = FontWeight.SemiBold)
                    Text("Saved in: ${info.location}")
                    Text("Checked: the file was read back and matches your text.", fontSize = 12.sp)
                }
            },
            confirmButton = {
                if (info.isCopy) {
                    TextButton(onClick = { val u = info.uri; saved = null; onClose(u) }) { Text("Open copy") }
                } else {
                    TextButton(onClick = { saved = null; onClose(info.uri) }) { Text("Back to reading") }
                }
            },
            dismissButton = { TextButton(onClick = { saved = null }) { Text("Keep editing") } }
        )
    }
}
