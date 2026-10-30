package com.santhomach.personalchecklist.ui.checklist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.santhomach.personalchecklist.data.ChecklistRepository
import com.santhomach.personalchecklist.data.db.ChecklistType
import com.santhomach.personalchecklist.ui.common.DeleteChecklistDialog
import com.santhomach.personalchecklist.ui.common.ItemRow
import com.santhomach.personalchecklist.ui.common.NameDialog
import com.santhomach.personalchecklist.ui.common.ProgressSummary
import com.santhomach.personalchecklist.ui.common.display
import com.santhomach.personalchecklist.ui.common.displayPeriod
import com.santhomach.personalchecklist.ui.common.label
import com.santhomach.personalchecklist.ui.common.repoViewModelFactory
import com.santhomach.personalchecklist.ui.common.resetSchedule
import com.santhomach.personalchecklist.util.LogicalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistViewModel(private val repository: ChecklistRepository, val checklistId: Long) :
    ViewModel() {
    val checklist = repository.observeChecklist(checklistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The newest copy: today's for daily, this week's for weekly, or a one-time checklist's only one. */
    val day = repository.observeCurrentDay(checklistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val items = repository.observeCurrentDay(checklistId)
        .flatMapLatest { day -> day?.let { repository.observeItems(it.id) } ?: emptyFlow() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addItem(text: String) = viewModelScope.launch {
        day.value?.let { repository.addItem(it.id, text) }
    }

    fun setChecked(itemId: Long, checked: Boolean) =
        viewModelScope.launch { repository.setChecked(itemId, checked) }

    fun deleteItem(itemId: Long) = viewModelScope.launch { repository.deleteItem(itemId) }
    fun rename(title: String) =
        viewModelScope.launch { repository.renameChecklist(checklistId, title) }

    fun delete() = viewModelScope.launch { repository.deleteChecklist(checklistId) }

    companion object {
        val Factory = repoViewModelFactory { repo, handle ->
            ChecklistViewModel(repo, checkNotNull(handle.get<Long>("checklistId")))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    onBack: () -> Unit,
    onOpenHistory: (Long) -> Unit,
    viewModel: ChecklistViewModel = viewModel(factory = ChecklistViewModel.Factory),
) {
    val checklist by viewModel.checklist.collectAsStateWithLifecycle()
    val day by viewModel.day.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    var newItem by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val repeats = checklist?.type?.repeats == true

    fun submit() {
        if (newItem.isNotBlank()) {
            viewModel.addItem(newItem)
            newItem = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(checklist?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (repeats) {
                        IconButton(onClick = { onOpenHistory(viewModel.checklistId) }) {
                            Icon(Icons.Filled.DateRange, contentDescription = "History")
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = { menuOpen = false; showRename = true })
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { menuOpen = false; showDelete = true })
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    checklist?.type?.takeIf { repeats }?.let { type -> day?.date?.let(type::displayPeriod) }
                        ?: LogicalDate.today().display(),
                    style = MaterialTheme.typography.titleLarge,
                )
                checklist?.let {
                    Text(
                        it.type.label() + " checklist" +
                            if (it.type == ChecklistType.WEEKLY || it.type == ChecklistType.MONTHLY) {
                                " · resets ${it.type.resetSchedule()}"
                            } else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ProgressSummary(
                    items.count { it.isChecked },
                    items.size,
                    Modifier.padding(top = 12.dp)
                )
            }
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(items, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        onCheckedChange = { viewModel.setChecked(item.id, it) },
                        onDelete = { viewModel.deleteItem(item.id) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newItem,
                    onValueChange = { newItem = it },
                    placeholder = { Text("Add an item") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(
                    onClick = { submit() },
                    enabled = newItem.isNotBlank() && day != null,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add item")
                }
            }
        }
    }

    if (showRename) {
        NameDialog(
            title = "Rename checklist",
            confirmLabel = "Save",
            initialName = checklist?.title.orEmpty(),
            onConfirm = { name, _ -> viewModel.rename(name); showRename = false },
            onDismiss = { showRename = false },
        )
    }
    if (showDelete) {
        DeleteChecklistDialog(
            title = checklist?.title.orEmpty(),
            onConfirm = { viewModel.delete(); showDelete = false; onBack() },
            onDismiss = { showDelete = false },
        )
    }
}
