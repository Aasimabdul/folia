package com.simbuild.folia.library

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Folder browsing on top of the Storage Access Framework. Folia only ever sees folders the user picked, which is
 * what Android's scoped storage allows; nothing here tries to reach other directories.
 */
object SafBrowser {

    private val COLUMNS = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_FLAGS
    )

    /** The picker opens on the device's main storage when it can (a hint; the user can still choose elsewhere). */
    val internalStorageHint: Uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A")

    fun rootDocumentId(treeUri: Uri): String = DocumentsContract.getTreeDocumentId(treeUri)

    fun documentUri(treeUri: Uri, docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

    /** Lists the direct children of [parentDocId]. Throws if access was revoked or the folder is gone. */
    fun list(resolver: ContentResolver, treeUri: Uri, parentDocId: String): List<LibraryEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val out = ArrayList<LibraryEntry>()
        val cursor = resolver.query(childrenUri, COLUMNS, null, null, null)
            ?: throw java.io.IOException("This folder is not available.")
        cursor.use {
            while (it.moveToNext()) {
                val mime = it.getString(2) ?: ""
                val flags = it.getInt(5)
                out += LibraryEntry(
                    docId = it.getString(0) ?: continue,
                    name = it.getString(1) ?: "(unnamed)",
                    isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                    sizeBytes = if (it.isNull(4)) -1L else it.getLong(4),
                    modifiedMillis = if (it.isNull(3)) 0L else it.getLong(3),
                    mimeType = mime,
                    canRename = flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0,
                    canDelete = flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0
                )
            }
        }
        return out
    }

    /** Display name of a granted folder (for the root list and breadcrumbs). */
    fun displayName(resolver: ContentResolver, treeUri: Uri): String {
        val docUri = documentUri(treeUri, rootDocumentId(treeUri))
        return runCatching {
            resolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: rootDocumentId(treeUri).substringAfter(':').ifEmpty { "Folder" }
    }

    /** Where the folder lives, as far as the provider tells us, e.g. "Internal storage / Documents / Books". */
    fun describeLocation(treeUri: Uri): String {
        val id = rootDocumentId(treeUri)
        val volume = id.substringBefore(':')
        val path = id.substringAfter(':', "")
        val volumeName = if (volume.equals("primary", ignoreCase = true)) "Internal storage" else volume
        return if (path.isEmpty()) volumeName else "$volumeName / ${path.replace("/", " / ")}"
    }

    fun rename(resolver: ContentResolver, docUri: Uri, newName: String): Boolean =
        runCatching { DocumentsContract.renameDocument(resolver, docUri, newName) != null }.getOrDefault(false)

    fun delete(resolver: ContentResolver, docUri: Uri): Boolean =
        runCatching { DocumentsContract.deleteDocument(resolver, docUri) }.getOrDefault(false)

    /** Keeps the grant across restarts. */
    fun persistRead(context: Context, treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.recoverCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun releaseGrant(context: Context, treeUri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    /** True if Android still lets this app read the folder. */
    fun hasGrant(context: Context, treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }
}
