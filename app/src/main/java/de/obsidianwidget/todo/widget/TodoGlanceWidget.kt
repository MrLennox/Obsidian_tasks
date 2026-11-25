package de.obsidianwidget.todo.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.obsidianwidget.todo.R
import de.obsidianwidget.todo.data.AppSettings
import de.obsidianwidget.todo.data.TaskRepository
import de.obsidianwidget.todo.model.TaskPriority
import de.obsidianwidget.todo.model.TodoItem
import de.obsidianwidget.todo.model.WidgetSnapshot
import de.obsidianwidget.todo.ui.MainActivity
import de.obsidianwidget.todo.ui.OpenNoteActivity
import de.obsidianwidget.todo.ui.QuickAddActivity
import de.obsidianwidget.todo.ui.theme.ObsidianGlanceColorScheme
import de.obsidianwidget.todo.worker.VaultSyncWorker
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.time.LocalDate

class TodoGlanceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val fallbackSnapshot = TaskRepository.getCachedSnapshot(context)

        provideContent {
            val prefs = currentState<Preferences>()
            val rawJson = prefs[SNAPSHOT_JSON_KEY]
            val snapshot = if (!rawJson.isNullOrBlank()) {
                runCatching {
                    TaskRepository.json.decodeFromString<WidgetSnapshot>(rawJson)
                }.getOrDefault(fallbackSnapshot)
            } else {
                fallbackSnapshot
            }

            GlanceTheme(colors = ObsidianGlanceColorScheme.colors) {
                TodoWidgetRoot(context = context, snapshot = snapshot)
            }
        }
    }

    companion object {
        const val MAX_WIDGET_ITEMS = 50
        val SNAPSHOT_JSON_KEY = stringPreferencesKey("snapshot_json")
        val REVISION_KEY = longPreferencesKey("revision")
        val TASK_ID_PARAM = ActionParameters.Key<String>("task_id")

        suspend fun pushSnapshotToAllWidgets(context: Context, snapshot: WidgetSnapshot) {
            val appContext = context.applicationContext
            val manager = GlanceAppWidgetManager(appContext)
            val glanceIds = manager.getGlanceIds(TodoGlanceWidget::class.java)
            if (glanceIds.isEmpty()) return

            val trimmedSnapshot = snapshot.copy(
                items = snapshot.items.take(MAX_WIDGET_ITEMS)
            )
            val encoded = TaskRepository.json.encodeToString(trimmedSnapshot)
            val now = System.currentTimeMillis()
            val widget = TodoGlanceWidget()

            for (glanceId in glanceIds) {
                updateAppWidgetState(appContext, glanceId) { prefs ->
                    prefs[SNAPSHOT_JSON_KEY] = encoded
                    prefs[REVISION_KEY] = now
                }
                widget.update(appContext, glanceId)
            }
        }
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoGlanceWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val pendingResult = goAsync()
        MainScope().launch {
            try {
                val settings = AppSettings.getSnapshot(context)
                if (!settings.vaultTreeUri.isNullOrBlank()) {
                    VaultSyncWorker.schedule(context)
                    TaskRepository.syncVault(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class ToggleTodoAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[TodoGlanceWidget.TASK_ID_PARAM] ?: return
        TaskRepository.completeTask(context, taskId)
    }
}

class RefreshVaultAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        TaskRepository.syncVault(context)
    }
}

@Composable
private fun TodoWidgetRoot(context: Context, snapshot: WidgetSnapshot) {
    val todayIso = LocalDate.now().toString()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(12.dp)
    ) {
        WidgetHeader(snapshot = snapshot)

        if (!snapshot.errorMessage.isNullOrBlank()) {
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = snapshot.errorMessage,
                style = TextStyle(
                    color = GlanceTheme.colors.error,
                    fontSize = 11.sp
                ),
                maxLines = 2
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        when {
            !snapshot.vaultConfigured -> {
                UnconfiguredState()
            }

            snapshot.items.isEmpty() -> {
                EmptyTasksState()
            }

            else -> {
                val displayed = snapshot.items.take(TodoGlanceWidget.MAX_WIDGET_ITEMS)
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(displayed, itemId = { it.id.hashCode().toLong() }) { item ->
                        TodoWidgetRow(
                            context = context,
                            item = item,
                            todayIso = todayIso
                        )
                    }
                    if (snapshot.totalOpenCount > displayed.size) {
                        item {
                            val remaining = snapshot.totalOpenCount - displayed.size
                            Text(
                                text = "… und $remaining weitere in der App",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 11.sp
                                ),
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clickable(actionStartActivity<MainActivity>())
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetHeader(snapshot: WidgetSnapshot) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Todos",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            if (snapshot.vaultConfigured) {
                Spacer(modifier = GlanceModifier.width(8.dp))
                Box(
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.primaryContainer)
                        .cornerRadius(10.dp)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = snapshot.totalOpenCount.toString(),
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimaryContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        Box(
            modifier = GlanceModifier
                .size(32.dp)
                .background(GlanceTheme.colors.secondaryContainer)
                .cornerRadius(16.dp)
                .clickable(actionRunCallback<RefreshVaultAction>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Aktualisieren",
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                modifier = GlanceModifier.size(18.dp)
            )
        }

        Spacer(modifier = GlanceModifier.width(6.dp))

        Box(
            modifier = GlanceModifier
                .size(32.dp)
                .background(GlanceTheme.colors.primary)
                .cornerRadius(16.dp)
                .clickable(actionStartActivity<QuickAddActivity>()),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_add),
                contentDescription = "Neues Todo",
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                modifier = GlanceModifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun UnconfiguredState() {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(14.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Tippe hier, um deinen Obsidian-Vault auszuwählen",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
private fun EmptyTasksState() {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Keine offenen Todos 🎉",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
private fun TodoWidgetRow(
    context: Context,
    item: TodoItem,
    todayIso: String
) {
    val openIntent = Intent(context, OpenNoteActivity::class.java).apply {
        putExtra(OpenNoteActivity.EXTRA_REL_PATH, item.relativePath)
        data = Uri.parse("obsidian-todo://open/${Uri.encode(item.id)}")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    val isOverdueOrToday = item.dueDate != null && item.dueDate <= todayIso

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        CheckBox(
            checked = false,
            onCheckedChange = actionRunCallback<ToggleTodoAction>(
                actionParametersOf(TodoGlanceWidget.TASK_ID_PARAM to item.id)
            ),
            text = ""
        )

        Spacer(modifier = GlanceModifier.width(4.dp))

        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .padding(top = 2.dp)
                .clickable(actionStartActivity(openIntent))
        ) {
            Text(
                text = item.cleanTitle,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 2
            )

            val metaParts = buildList {
                if (item.priority != TaskPriority.NORMAL && item.priority.symbol.isNotEmpty()) {
                    add(item.priority.symbol)
                }
                if (item.dueDate != null) {
                    add("📅 ${item.dueDate}")
                }
                add(item.noteName)
            }

            Text(
                text = metaParts.joinToString(" · "),
                style = TextStyle(
                    color = if (isOverdueOrToday) {
                        GlanceTheme.colors.error
                    } else {
                        GlanceTheme.colors.onSurfaceVariant
                    },
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
    }
}
