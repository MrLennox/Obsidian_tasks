package de.obsidianwidget.todo.model

import kotlinx.serialization.Serializable

@Serializable
enum class TaskPriority(
    val rank: Int,
    val symbol: String,
    val labelDe: String
) {
    HIGHEST(0, "🔺", "Höchste"),
    HIGH(1, "⏫", "Hoch"),
    MEDIUM(2, "🔼", "Mittel"),
    NORMAL(3, "", "Normal"),
    LOW(4, "🔽", "Niedrig"),
    LOWEST(5, "⏬", "Niedrigste")
}

@Serializable
enum class CompletionFormat(val labelDe: String) {
    OBSIDIAN_TASKS("Obsidian Tasks (✅ YYYY-MM-DD)"),
    DATAVIEW("Dataview ([completion:: YYYY-MM-DD])"),
    PLAIN("Einfach (nur - [x])")
}

@Serializable
data class TodoItem(
    val id: String,
    val cleanTitle: String,
    val rawLine: String,
    val documentUri: String,
    val relativePath: String,
    val noteName: String,
    val lineNumber: Int,
    val dueDate: String? = null,
    val priority: TaskPriority = TaskPriority.NORMAL
)

@Serializable
data class WidgetSnapshot(
    val vaultConfigured: Boolean = false,
    val vaultName: String = "",
    val items: List<TodoItem> = emptyList(),
    val totalOpenCount: Int = 0,
    val lastUpdatedEpochMs: Long = 0L,
    val errorMessage: String? = null
)
