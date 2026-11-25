package de.obsidianwidget.todo.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.glance.appwidget.GlanceAppWidgetManager
import de.obsidianwidget.todo.data.AppSettings
import de.obsidianwidget.todo.data.SafVaultScanner
import de.obsidianwidget.todo.data.TaskRepository
import de.obsidianwidget.todo.data.UserSettings
import de.obsidianwidget.todo.model.CompletionFormat
import de.obsidianwidget.todo.model.TaskPriority
import de.obsidianwidget.todo.model.TodoItem
import de.obsidianwidget.todo.model.WidgetSnapshot
import de.obsidianwidget.todo.ui.theme.ObsidianTodoTheme
import de.obsidianwidget.todo.widget.TodoGlanceWidget
import de.obsidianwidget.todo.widget.TodoWidgetReceiver
import de.obsidianwidget.todo.worker.VaultSyncWorker
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ObsidianTodoTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings by AppSettings.observe(context).collectAsState(initial = UserSettings())
    var snapshot by remember { mutableStateOf(TaskRepository.getCachedSnapshot(context)) }
    var isSyncing by remember { mutableStateOf(false) }

    var quickAddPathInput by remember(settings.quickAddFile) {
        mutableStateOf(settings.quickAddFile)
    }
    var showQuickAddDialog by remember { mutableStateOf(false) }
    var quickAddSaving by remember { mutableStateOf(false) }
    var quickAddError by remember { mutableStateOf<String?>(null) }

    fun refreshNow() {
        if (isSyncing) return
        isSyncing = true
        scope.launch {
            snapshot = TaskRepository.syncVault(context)
            isSyncing = false
        }
    }

    LaunchedEffect(Unit) {
        val currentSettings = AppSettings.getSnapshot(context)
        if (!currentSettings.vaultTreeUri.isNullOrBlank()) {
            VaultSyncWorker.schedule(context)
            refreshNow()
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }
        val displayName = SafVaultScanner.resolveVaultDisplayName(context, uri)
        scope.launch {
            AppSettings.setVault(context, uri.toString(), displayName)
            VaultSyncWorker.schedule(context)
            isSyncing = true
            snapshot = TaskRepository.syncVault(context)
            isSyncing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Obsidian Todos") },
                actions = {
                    if (snapshot.vaultConfigured) {
                        IconButton(onClick = { refreshNow() }, enabled = !isSyncing) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Vault synchronisieren"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (snapshot.vaultConfigured) {
                FloatingActionButton(
                    onClick = {
                        quickAddError = null
                        showQuickAddDialog = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Neues Todo hinzufügen"
                    )
                }
            }
        }
    ) { innerPadding ->
        val todayIso = remember { LocalDate.now().toString() }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isSyncing) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            item {
                VaultConnectionCard(
                    snapshot = snapshot,
                    onPickVault = { folderPickerLauncher.launch(null) }
                )
            }

            item {
                WidgetAndSettingsCard(
                    quickAddPathInput = quickAddPathInput,
                    onQuickAddPathChange = { quickAddPathInput = it },
                    onSaveQuickAddPath = {
                        scope.launch {
                            AppSettings.setQuickAddFile(context, quickAddPathInput)
                            Toast.makeText(context, "Zieldatei gespeichert", Toast.LENGTH_SHORT).show()
                        }
                    },
                    selectedFormat = settings.completionFormat,
                    onSelectFormat = { format ->
                        scope.launch { AppSettings.setCompletionFormat(context, format) }
                    },
                    onPinWidget = {
                        scope.launch {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val manager = GlanceAppWidgetManager(context)
                                val pinned = manager.requestPinGlanceAppWidget(
                                    receiver = TodoWidgetReceiver::class.java,
                                    preview = TodoGlanceWidget()
                                )
                                if (!pinned) {
                                    Toast.makeText(
                                        context,
                                        "Bitte Widget über den Startbildschirm hinzufügen.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "Bitte Widget über den Startbildschirm hinzufügen.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )
            }

            if (snapshot.vaultConfigured) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Offene Todos (${snapshot.totalOpenCount})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (!snapshot.errorMessage.isNullOrBlank()) {
                    item {
                        Text(
                            text = snapshot.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (snapshot.items.isEmpty() && !isSyncing) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = "Keine offenen Todos gefunden 🎉",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(snapshot.items, key = { it.id }) { item ->
                        TodoListRow(
                            item = item,
                            todayIso = todayIso,
                            onComplete = {
                                scope.launch {
                                    TaskRepository.completeTask(context, item.id)
                                    snapshot = TaskRepository.getCachedSnapshot(context)
                                }
                            },
                            onOpenInObsidian = {
                                val openIntent = Intent(context, OpenNoteActivity::class.java).apply {
                                    putExtra(OpenNoteActivity.EXTRA_REL_PATH, item.relativePath)
                                }
                                context.startActivity(openIntent)
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showQuickAddDialog) {
        Dialog(onDismissRequest = { if (!quickAddSaving) showQuickAddDialog = false }) {
            QuickAddDialogCard(
                targetFileLabel = settings.quickAddFile,
                isSaving = quickAddSaving,
                errorText = quickAddError,
                onDismiss = { showQuickAddDialog = false },
                onSubmit = { text, dueDate, priority ->
                    quickAddSaving = true
                    quickAddError = null
                    scope.launch {
                        val result = TaskRepository.quickAddTask(
                            context = context,
                            taskText = text,
                            dueDate = dueDate,
                            priority = priority
                        )
                        quickAddSaving = false
                        result.onSuccess { updated ->
                            snapshot = updated
                            showQuickAddDialog = false
                            Toast.makeText(context, "Todo gespeichert", Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            quickAddError = err.localizedMessage ?: "Speichern fehlgeschlagen."
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun VaultConnectionCard(
    snapshot: WidgetSnapshot,
    onPickVault: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Obsidian Vault",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (snapshot.vaultConfigured) {
                Text(
                    text = "Verbunden mit: ${snapshot.vaultName}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Alle .md-Dateien im Vault werden lokal gelesen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onPickVault) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Vault-Ordner wechseln")
                }
            } else {
                Text(
                    text = "Wähle deinen lokalen Obsidian-Vault-Ordner auf dem Smartphone aus.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onPickVault) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Obsidian-Vault auswählen")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetAndSettingsCard(
    quickAddPathInput: String,
    onQuickAddPathChange: (String) -> Unit,
    onSaveQuickAddPath: () -> Unit,
    selectedFormat: CompletionFormat,
    onSelectFormat: (CompletionFormat) -> Unit,
    onPinWidget: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Homescreen-Widget & Format",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onPinWidget,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Widgets, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Widget zum Startbildschirm hinzufügen")
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = quickAddPathInput,
                    onValueChange = onQuickAddPathChange,
                    label = { Text("Quick-Add Zieldatei") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onSaveQuickAddPath) {
                    Text("OK")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Format beim Abhaken",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CompletionFormat.entries.forEach { format ->
                    FilterChip(
                        selected = selectedFormat == format,
                        onClick = { onSelectFormat(format) },
                        label = { Text(format.labelDe) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TodoListRow(
    item: TodoItem,
    todayIso: String,
    onComplete: () -> Unit,
    onOpenInObsidian: () -> Unit
) {
    val isOverdueOrToday = item.dueDate != null && item.dueDate <= todayIso

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenInObsidian)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = false,
            onCheckedChange = { onComplete() }
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.cleanTitle,
                style = MaterialTheme.typography.bodyLarge
            )
            val metaParts = buildList {
                if (item.priority != TaskPriority.NORMAL && item.priority.symbol.isNotEmpty()) {
                    add(item.priority.symbol)
                }
                if (item.dueDate != null) {
                    add("📅 ${item.dueDate}")
                }
                add(item.relativePath)
            }
            Text(
                text = metaParts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (isOverdueOrToday) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
