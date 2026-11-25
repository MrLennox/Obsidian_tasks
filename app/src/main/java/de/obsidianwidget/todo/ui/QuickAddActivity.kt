package de.obsidianwidget.todo.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.obsidianwidget.todo.data.AppSettings
import de.obsidianwidget.todo.data.TaskRepository
import de.obsidianwidget.todo.data.UserSettings
import de.obsidianwidget.todo.ui.theme.ObsidianTodoTheme
import kotlinx.coroutines.launch

class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ObsidianTodoTheme {
                val scope = rememberCoroutineScope()
                var targetFile by remember { mutableStateOf(UserSettings.DEFAULT_QUICK_ADD_FILE) }
                var vaultConfigured by remember { mutableStateOf(true) }
                var isSaving by remember { mutableStateOf(false) }
                var errorText by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    val settings = AppSettings.getSnapshot(this@QuickAddActivity)
                    targetFile = settings.quickAddFile
                    vaultConfigured = !settings.vaultTreeUri.isNullOrBlank()
                    if (!vaultConfigured) {
                        Toast.makeText(
                            this@QuickAddActivity,
                            "Bitte zuerst deinen Obsidian-Vault auswählen.",
                            Toast.LENGTH_SHORT
                        ).show()
                        startActivity(Intent(this@QuickAddActivity, MainActivity::class.java))
                        finish()
                    }
                }

                if (vaultConfigured) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .imePadding()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { if (!isSaving) finish() }
                            )
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        QuickAddDialogCard(
                            targetFileLabel = targetFile,
                            isSaving = isSaving,
                            errorText = errorText,
                            onDismiss = { finish() },
                            onSubmit = { text, dueDate, priority ->
                                isSaving = true
                                errorText = null
                                scope.launch {
                                    val result = TaskRepository.quickAddTask(
                                        context = this@QuickAddActivity,
                                        taskText = text,
                                        dueDate = dueDate,
                                        priority = priority
                                    )
                                    isSaving = false
                                    result.onSuccess {
                                        Toast.makeText(
                                            this@QuickAddActivity,
                                            "Todo gespeichert",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        finish()
                                    }.onFailure { err ->
                                        errorText = err.localizedMessage ?: "Speichern fehlgeschlagen."
                                    }
                                }
                            },
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                        )
                    }
                }
            }
        }
    }
}
