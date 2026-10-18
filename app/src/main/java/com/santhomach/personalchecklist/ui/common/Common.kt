package com.santhomach.personalchecklist.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.santhomach.personalchecklist.ChecklistApp
import com.santhomach.personalchecklist.data.ChecklistRepository
import com.santhomach.personalchecklist.data.db.ChecklistType
import com.santhomach.personalchecklist.data.db.ItemEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds a ViewModel with the app's repository and the nav arguments in [SavedStateHandle]. */
inline fun <reified VM : ViewModel> repoViewModelFactory(
    crossinline create: (ChecklistRepository, SavedStateHandle) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ChecklistApp
        create(app.repository, createSavedStateHandle())
    }
}

fun LocalDate.display(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", locale))

fun ChecklistType.label(): String = when (this) {
    ChecklistType.DAILY -> "Daily"
    ChecklistType.ONE_TIME -> "One-time"
}

fun progressLabel(done: Int, total: Int): String =
    if (total == 0) "No items" else "$done/$total · ${done * 100 / total}%"

@Composable
fun ProgressSummary(done: Int, total: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(progressLabel(done, total), style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Dialog for naming a checklist. With [showTypeSelector] it is the create dialog and also asks
 * for the checklist type; without it, it is the rename dialog.
 */
@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    showTypeSelector: Boolean = false,
    onConfirm: (name: String, type: ChecklistType) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var type by rememberSaveable { mutableStateOf(ChecklistType.DAILY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showTypeSelector) {
                    val types = ChecklistType.entries
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        types.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = type == option,
                                onClick = { type = option },
                                shape = SegmentedButtonDefaults.itemShape(index, types.size),
                            ) { Text(option.label()) }
                        }
                    }
                    Text(
                        when (type) {
                            ChecklistType.DAILY -> "A fresh unticked copy is made every day at 2am."
                            ChecklistType.ONE_TIME -> "A single list that is never copied."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), type) }, enabled = name.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DeleteChecklistDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete checklist?") },
        text = { Text("Delete \"$title\" and all its history? This can't be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One checklist item. Without [onDelete] the row is read-only (used for past days). */
@Composable
fun ItemRow(
    item: ItemEntity,
    onCheckedChange: ((Boolean) -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.isChecked, onCheckedChange = onCheckedChange)
        Text(
            item.text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
            color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete item") }
        }
    }
}
