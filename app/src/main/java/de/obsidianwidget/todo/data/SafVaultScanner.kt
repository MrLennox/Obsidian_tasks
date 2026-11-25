package de.obsidianwidget.todo.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import de.obsidianwidget.todo.model.TodoItem
import de.obsidianwidget.todo.parser.VaultTaskParser

object SafVaultScanner {

    private val childProjection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE
    )

    fun resolveVaultDisplayName(context: Context, treeUri: Uri): String {
        return runCatching {
            DocumentFile.fromTreeUri(context, treeUri)?.name
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: treeUri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
            ?: "Obsidian Vault"
    }

    /**
     * Recursively scans all non-hidden Markdown files in the SAF tree URI and returns sorted open tasks.
     */
    fun scanVault(context: Context, treeUri: Uri): List<TodoItem> {
        val resolver = context.contentResolver
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add(rootDocId to "")

        val collected = mutableListOf<TodoItem>()

        while (queue.isNotEmpty()) {
            val (dirDocId, prefix) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)

            resolver.query(childrenUri, childProjection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val childId = if (idCol >= 0) cursor.getString(idCol) else continue
                    val name = if (nameCol >= 0) cursor.getString(nameCol) else continue
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else ""

                    if (shouldSkipEntry(name)) continue

                    val relPath = if (prefix.isEmpty()) name else "$prefix/$name"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        queue.add(childId to relPath)
                    } else if (name.endsWith(".md", ignoreCase = true)) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        val content = readDocumentText(context, docUri) ?: continue
                        collected.addAll(
                            VaultTaskParser.parseMarkdown(
                                content = content,
                                documentUri = docUri.toString(),
                                relativePath = relPath
                            )
                        )
                    }
                }
            }
        }

        return VaultTaskParser.sortTasks(collected)
    }

    fun readDocumentText(context: Context, docUri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(docUri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        }.getOrNull()
    }

    fun writeDocumentText(context: Context, docUri: Uri, content: String): Boolean {
        val resolver = context.contentResolver
        val modes = listOf("wt", "rwt")
        for (mode in modes) {
            val success = runCatching {
                resolver.openOutputStream(docUri, mode)?.use { stream ->
                    stream.write(content.toByteArray(Charsets.UTF_8))
                    stream.flush()
                    true
                } ?: false
            }.getOrDefault(false)
            if (success) return true
        }
        return false
    }

    /**
     * Finds or creates a Markdown file inside the Vault tree URI at [relativePath] (e.g. "Inbox.md").
     */
    fun resolveOrCreateMarkdownFile(
        context: Context,
        treeUri: Uri,
        relativePath: String
    ): Uri? {
        val resolver = context.contentResolver
        val rawSegments = relativePath
            .replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }

        val segments = if (rawSegments.isEmpty()) {
            listOf("Inbox.md")
        } else {
            rawSegments.dropLast(1) + ensureMdExtension(rawSegments.last())
        }

        var currentDirDocId = DocumentsContract.getTreeDocumentId(treeUri)

        for (folderName in segments.dropLast(1)) {
            val existingDirId = findChildDocumentId(
                context = context,
                treeUri = treeUri,
                parentDocId = currentDirDocId,
                targetName = folderName,
                expectDir = true
            )
            currentDirDocId = existingDirId ?: run {
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDirDocId)
                val createdUri = DocumentsContract.createDocument(
                    resolver,
                    parentUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    folderName
                ) ?: return null
                DocumentsContract.getDocumentId(createdUri)
            }
        }

        val targetFileName = segments.last()
        val existingFileId = findChildDocumentId(
            context = context,
            treeUri = treeUri,
            parentDocId = currentDirDocId,
            targetName = targetFileName,
            expectDir = false
        )
        if (existingFileId != null) {
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, existingFileId)
        }

        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDirDocId)
        return DocumentsContract.createDocument(
            resolver,
            parentUri,
            "text/markdown",
            targetFileName
        )
    }

    private fun findChildDocumentId(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        targetName: String,
        expectDir: Boolean
    ): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        context.contentResolver.query(childrenUri, childProjection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = if (idCol >= 0) cursor.getString(idCol) else continue
                val name = if (nameCol >= 0) cursor.getString(nameCol) else continue
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else ""
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                if (isDir == expectDir && name.equals(targetName, ignoreCase = true)) {
                    return id
                }
            }
        }
        return null
    }

    private fun ensureMdExtension(fileName: String): String {
        return if (fileName.endsWith(".md", ignoreCase = true)) fileName else "$fileName.md"
    }

    private fun shouldSkipEntry(name: String): Boolean {
        return name.startsWith(".") || name == "node_modules"
    }
}
