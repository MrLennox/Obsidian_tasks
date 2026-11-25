package de.obsidianwidget.todo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.obsidianwidget.todo.model.TaskPriority
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddDialogCard(
    targetFileLabel: String,
    isSaving: Boolean,
    errorText: String?,
    onDismiss: () -> Unit,
    onSubmit: (text: String, dueDate: String?, priority: TaskPriority) -> Unit,
    modifier: Modifier = Modifier
) {
    var taskText by remember { mutableStateOf("") }
    var selectedDueDate by remember { mutableStateOf<String?>(null) }
    var selectedPriority by remember { mutableStateOf(TaskPriority.NORMAL) }
    val focusRequester = remember { FocusRequester() }

    val todayIso = remember { LocalDate.now().toString() }
    val tomorrowIso = remember { LocalDate.now().plusDays(1).toString() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val submitAction = {
        if (taskText.isNotBlank() && !isSaving) {
            onSubmit(taskText.trim(), selectedDueDate, selectedPriority)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Neues Todo",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Wird in $targetFileLabel gespeichert",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = taskText,
                onValueChange = { taskText = it },
                label = { Text("Aufgabe") },
                placeholder = { Text("z. B. Milch kaufen") },
                singleLine = false,
                maxLines = 3,
                enabled = !isSaving,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submitAction() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = selectedDueDate == todayIso,
                    onClick = {
                        selectedDueDate = if (selectedDueDate == todayIso) null else todayIso
                    },
                    label = { Text("📅 Heute") }
                )
                FilterChip(
                    selected = selectedDueDate == tomorrowIso,
                    onClick = {
                        selectedDueDate = if (selectedDueDate == tomorrowIso) null else tomorrowIso
                    },
                    label = { Text("📅 Morgen") }
                )
                FilterChip(
                    selected = selectedPriority == TaskPriority.HIGH,
                    onClick = {
                        selectedPriority = if (selectedPriority == TaskPriority.HIGH) {
                            TaskPriority.NORMAL
                        } else {
                            TaskPriority.HIGH
                        }
                    },
                    label = { Text("⏫ Hoch") }
                )
                FilterChip(
                    selected = selectedPriority == TaskPriority.MEDIUM,
                    onClick = {
                        selectedPriority = if (selectedPriority == TaskPriority.MEDIUM) {
                            TaskPriority.NORMAL
                        } else {
                            TaskPriority.MEDIUM
                        }
                    },
                    label = { Text("🔼 Mittel") }
                )
            }

            if (!errorText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSaving
                ) {
                    Text("Abbrechen")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = submitAction,
                    enabled = taskText.isNotBlank() && !isSaving
                ) {
                    Text(if (isSaving) "Speichert…" else "Speichern")
                }
            }
        }
    }
}
