package de.obsidianwidget.todo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.obsidianwidget.todo.model.CompletionFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore(name = "obsidian_todo_settings")

data class UserSettings(
    val vaultTreeUri: String? = null,
    val vaultDisplayName: String = "",
    val quickAddFile: String = DEFAULT_QUICK_ADD_FILE,
    val completionFormat: CompletionFormat = CompletionFormat.OBSIDIAN_TASKS
) {
    companion object {
        const val DEFAULT_QUICK_ADD_FILE = "Inbox.md"
    }
}

object AppSettings {

    private val KEY_VAULT_TREE_URI = stringPreferencesKey("vault_tree_uri")
    private val KEY_VAULT_DISPLAY_NAME = stringPreferencesKey("vault_display_name")
    private val KEY_QUICK_ADD_FILE = stringPreferencesKey("quick_add_file")
    private val KEY_COMPLETION_FORMAT = stringPreferencesKey("completion_format")

    fun observe(context: Context): Flow<UserSettings> {
        return context.applicationContext.appSettingsDataStore.data.map { prefs ->
            val formatName = prefs[KEY_COMPLETION_FORMAT]
            val format = CompletionFormat.entries.firstOrNull { it.name == formatName }
                ?: CompletionFormat.OBSIDIAN_TASKS
            UserSettings(
                vaultTreeUri = prefs[KEY_VAULT_TREE_URI],
                vaultDisplayName = prefs[KEY_VAULT_DISPLAY_NAME].orEmpty(),
                quickAddFile = prefs[KEY_QUICK_ADD_FILE]?.takeIf { it.isNotBlank() }
                    ?: UserSettings.DEFAULT_QUICK_ADD_FILE,
                completionFormat = format
            )
        }
    }

    suspend fun getSnapshot(context: Context): UserSettings {
        return observe(context).first()
    }

    suspend fun setVault(context: Context, treeUri: String, displayName: String) {
        context.applicationContext.appSettingsDataStore.edit { prefs ->
            prefs[KEY_VAULT_TREE_URI] = treeUri
            prefs[KEY_VAULT_DISPLAY_NAME] = displayName
        }
    }

    suspend fun clearVault(context: Context) {
        context.applicationContext.appSettingsDataStore.edit { prefs ->
            prefs.remove(KEY_VAULT_TREE_URI)
            prefs.remove(KEY_VAULT_DISPLAY_NAME)
        }
    }

    suspend fun setQuickAddFile(context: Context, relativePath: String) {
        val cleaned = relativePath.trim().ifEmpty { UserSettings.DEFAULT_QUICK_ADD_FILE }
        context.applicationContext.appSettingsDataStore.edit { prefs ->
            prefs[KEY_QUICK_ADD_FILE] = cleaned
        }
    }

    suspend fun setCompletionFormat(context: Context, format: CompletionFormat) {
        context.applicationContext.appSettingsDataStore.edit { prefs ->
            prefs[KEY_COMPLETION_FORMAT] = format.name
        }
    }
}
