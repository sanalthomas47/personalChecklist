package com.santhomach.personalchecklist.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.santhomach.personalchecklist.data.ChecklistRepository
import com.santhomach.personalchecklist.data.db.ChecklistType
import com.santhomach.personalchecklist.data.db.ChecklistWithProgress
import com.santhomach.personalchecklist.ui.common.DeleteChecklistDialog
import com.santhomach.personalchecklist.ui.common.NameDialog
import com.santhomach.personalchecklist.ui.common.ProgressSummary
import com.santhomach.personalchecklist.ui.common.label
import com.santhomach.personalchecklist.ui.common.repoViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val repository: ChecklistRepository) : ViewModel() {
    val checklists = repository.observeChecklists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(title: String, type: ChecklistType) = viewModelScope.launch { repository.createChecklist(title, type) }
    fun rename(id: Long, title: String) = viewModelScope.launch { repository.renameChecklist(id, title) }
    fun delete(id: Long) = viewModelScope.launch { repository.deleteChecklist(id) }

    companion object {
        val Factory = repoViewModelFactory { repo, _ -> MainViewModel(repo) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenChecklist: (Long) -> Unit,
    viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory),
) {
    val checklists by viewModel.checklists.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ChecklistWithProgress?>(null) }
    var deleting by remember { mutableStateOf<ChecklistWithProgress?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("My Checklists") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New checklist") },
            )
        },
    ) { padding ->
        val list = checklists
        if (list != null && list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No checklists yet.\nTap \"New checklist\" to create one.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding),
            ) {
                items(list.orEmpty(), key = { it.id }) { checklist ->
                    ChecklistCard(
                        checklist = checklist,
                        onClick = { onOpenChecklist(checklist.id) },
                        onRename = { renaming = checklist },
                        onDelete = { deleting = checklist },
                    )
                }
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "New checklist",
            confirmLabel = "Create",
            showTypeSelector = true,
            onConfirm = { name, type -> viewModel.create(name, type); showCreate = false },
            onDismiss = { showCreate = false },
        )
    }
    renaming?.let { checklist ->
        NameDialog(
            title = "Rename checklist",
            confirmLabel = "Save",
            initialName = checklist.title,
            onConfirm = { name, _ -> viewModel.rename(checklist.id, name); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { checklist ->
        DeleteChecklistDialog(
            title = checklist.title,
            onConfirm = { viewModel.delete(checklist.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChecklistCard(
    checklist: ChecklistWithProgress,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })) {
        Column(Modifier.padding(start = 16.dp, bottom = 16.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    checklist.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(top = 8.dp),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Checklist options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
            AssistChip(onClick = onClick, label = { Text(checklist.type.label()) })
            ProgressSummary(checklist.done, checklist.total, Modifier.padding(top = 8.dp, end = 12.dp))
        }
    }
}
