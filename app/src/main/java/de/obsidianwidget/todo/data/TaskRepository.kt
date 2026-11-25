package de.obsidianwidget.todo.data

import android.content.Context
import android.net.Uri
import de.obsidianwidget.todo.model.TaskPriority
import de.obsidianwidget.todo.model.WidgetSnapshot
import de.obsidianwidget.todo.parser.VaultTaskParser
import de.obsidianwidget.todo.widget.TodoGlanceWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object TaskRepository {

    private val mutex = Mutex()

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun cacheFile(context: Context): File {
        return File(context.applicationContext.filesDir, "widget_snapshot.json")
    }

    fun getCachedSnapshot(context: Context): WidgetSnapshot {
        val file = cacheFile(context)
        if (!file.exists()) return WidgetSnapshot()
        return runCatching {
            json.decodeFromString<WidgetSnapshot>(file.readText(Charsets.UTF_8))
        }.getOrDefault(WidgetSnapshot())
    }

    private fun saveCachedSnapshot(context: Context, snapshot: WidgetSnapshot) {
        runCatching {
            cacheFile(context).writeText(json.encodeToString(snapshot), Charsets.UTF_8)
        }
    }

    private fun hasPersistedReadPermission(context: Context, treeUri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any { perm ->
            perm.uri == treeUri && perm.isReadPermission
        }
    }

    suspend fun syncVault(context: Context): WidgetSnapshot = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        mutex.withLock {
            syncVaultLocked(appContext)
        }
    }

    private suspend fun syncVaultLocked(appContext: Context): WidgetSnapshot {
        val settings = AppSettings.getSnapshot(appContext)
        val uriString = settings.vaultTreeUri

        if (uriString.isNullOrBlank()) {
            val unconfigured = WidgetSnapshot(vaultConfigured = false)
            saveCachedSnapshot(appContext, unconfigured)
            TodoGlanceWidget.pushSnapshotToAllWidgets(appContext, unconfigured)
            return unconfigured
        }

        val treeUri = Uri.parse(uriString)
        if (!hasPersistedReadPermission(appContext, treeUri)) {
            val missingPerm = WidgetSnapshot(
                vaultConfigured = false,
                vaultName = settings.vaultDisplayName,
                errorMessage = "Ordner-Berechtigung abgelaufen. Bitte Vault erneut verbinden."
            )
            saveCachedSnapshot(appContext, missingPerm)
            TodoGlanceWidget.pushSnapshotToAllWidgets(appContext, missingPerm)
            return missingPerm
        }

        val snapshot = runCatching {
            val tasks = SafVaultScanner.scanVault(appContext, treeUri)
            val displayName = settings.vaultDisplayName.ifBlank {
                SafVaultScanner.resolveVaultDisplayName(appContext, treeUri)
            }
            WidgetSnapshot(
                vaultConfigured = true,
                vaultName = displayName,
                items = tasks,
                totalOpenCount = tasks.size,
                lastUpdatedEpochMs = System.currentTimeMillis(),
                errorMessage = null
            )
        }.getOrElse { err ->
            val previous = getCachedSnapshot(appContext)
            previous.copy(
                vaultConfigured = true,
                vaultName = settings.vaultDisplayName,
                lastUpdatedEpochMs = System.currentTimeMillis(),
                errorMessage = err.localizedMessage ?: "Fehler beim Lesen des Vaults"
            )
        }

        saveCachedSnapshot(appContext, snapshot)
        TodoGlanceWidget.pushSnapshotToAllWidgets(appContext, snapshot)
        return snapshot
    }

    suspend fun completeTask(context: Context, taskId: String): Boolean = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        mutex.withLock {
            val currentSnapshot = getCachedSnapshot(appContext)
            val targetTask = currentSnapshot.items.firstOrNull { it.id == taskId }
                ?: return@withLock false

            // Optimistic UI update so the widget responds immediately on checkbox tap
            val remaining = currentSnapshot.items.filterNot { it.id == taskId }
            val optimisticSnapshot = currentSnapshot.copy(
                items = remaining,
                totalOpenCount = remaining.size,
                lastUpdatedEpochMs = System.currentTimeMillis(),
                errorMessage = null
            )
            saveCachedSnapshot(appContext, optimisticSnapshot)
            TodoGlanceWidget.pushSnapshotToAllWidgets(appContext, optimisticSnapshot)

            val settings = AppSettings.getSnapshot(appContext)
            val docUri = Uri.parse(targetTask.documentUri)
            val originalText = SafVaultScanner.readDocumentText(appContext, docUri)
            if (originalText == null) {
                syncVaultLocked(appContext)
                return@withLock false
            }

            val updatedText = VaultTaskParser.markLineCompleted(
                fileContent = originalText,
                lineNumber = targetTask.lineNumber,
                expectedRawLine = targetTask.rawLine,
                format = settings.completionFormat
            )
            if (updatedText == null) {
                syncVaultLocked(appContext)
                return@withLock false
            }

            val written = SafVaultScanner.writeDocumentText(appContext, docUri, updatedText)
            if (!written) {
                val reverted = currentSnapshot.copy(
                    errorMessage = "Schreibzugriff auf ${targetTask.relativePath} fehlgeschlagen."
                )
                saveCachedSnapshot(appContext, reverted)
                TodoGlanceWidget.pushSnapshotToAllWidgets(appContext, reverted)
                return@withLock false
            }

            true
        }
    }

    suspend fun quickAddTask(
        context: Context,
        taskText: String,
        dueDate: String? = null,
        priority: TaskPriority = TaskPriority.NORMAL
    ): Result<WidgetSnapshot> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        mutex.withLock {
            runCatching {
                val trimmed = taskText.trim()
                require(trimmed.isNotEmpty()) { "Bitte einen Todo-Text eingeben." }

                val settings = AppSettings.getSnapshot(appContext)
                val treeUriStr = settings.vaultTreeUri
                    ?: error("Kein Obsidian-Vault ausgewählt.")
                val treeUri = Uri.parse(treeUriStr)

                val targetFileUri = SafVaultScanner.resolveOrCreateMarkdownFile(
                    context = appContext,
                    treeUri = treeUri,
                    relativePath = settings.quickAddFile
                ) ?: error("Zieldatei '${settings.quickAddFile}' konnte nicht erstellt werden.")

                val existingContent = SafVaultScanner.readDocumentText(appContext, targetFileUri).orEmpty()
                val newContent = VaultTaskParser.appendNewTask(
                    existingContent = existingContent,
                    taskText = trimmed,
                    dueDate = dueDate,
                    priority = priority
                )

                val written = SafVaultScanner.writeDocumentText(appContext, targetFileUri, newContent)
                if (!written) {
                    error("Konnte nicht in '${settings.quickAddFile}' schreiben.")
                }

                syncVaultLocked(appContext)
            }
        }
    }
}
