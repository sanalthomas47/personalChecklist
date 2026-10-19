package com.santhomach.personalchecklist.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.santhomach.personalchecklist.ChecklistApp
import com.santhomach.personalchecklist.data.ChecklistRepository
import com.santhomach.personalchecklist.data.backup.Backup
import com.santhomach.personalchecklist.data.backup.BackupJson
import com.santhomach.personalchecklist.data.backup.BackupStorage
import com.santhomach.personalchecklist.data.backup.InvalidBackupException
import com.santhomach.personalchecklist.data.db.ChecklistType
import com.santhomach.personalchecklist.data.db.ChecklistWithProgress
import com.santhomach.personalchecklist.ui.common.DeleteChecklistDialog
import com.santhomach.personalchecklist.ui.common.NameDialog
import com.santhomach.personalchecklist.ui.common.ProgressSummary
import com.santhomach.personalchecklist.ui.common.label
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class MainViewModel(
    private val repository: ChecklistRepository,
    private val backupStorage: BackupStorage,
) : ViewModel() {
    val checklists = repository.observeChecklists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** One-off messages for the snackbar. */
    val messages = _messages.receiveAsFlow()

    private val _pendingImport = MutableStateFlow<Backup?>(null)

    /** A backup that was read and validated, waiting for the user to confirm replacing all data. */
    val pendingImport = _pendingImport.asStateFlow()

    fun create(title: String, type: ChecklistType) =
        viewModelScope.launch { repository.createChecklist(title, type) }

    fun rename(id: Long, title: String) =
        viewModelScope.launch { repository.renameChecklist(id, title) }

    fun delete(id: Long) = viewModelScope.launch { repository.deleteChecklist(id) }

    fun showMessage(text: String) = viewModelScope.launch { _messages.send(text) }

    fun exportBackup() = viewModelScope.launch {
        val message = try {
            val json = BackupJson.encode(repository.exportBackup())
            "Backup saved to Downloads/${
                backupStorage.saveToDownloads(
                    BackupStorage.fileName(),
                    json
                )
            }"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Export failed: ${e.message}"
        }
        _messages.send(message)
    }

    fun onImportFileChosen(uri: Uri) = viewModelScope.launch {
        try {
            _pendingImport.value = BackupJson.decode(backupStorage.read(uri))
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidBackupException) {
            _messages.send("Couldn't import: ${e.message}")
        } catch (e: Exception) {
            _messages.send("Couldn't read the file: ${e.message}")
        }
    }

    fun confirmImport() = viewModelScope.launch {
        val backup = _pendingImport.value ?: return@launch
        _pendingImport.value = null
        val message = try {
            repository.importBackup(backup)
            "Restored ${checklistCount(backup.checklists.size)} from backup"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Import failed, nothing was changed: ${e.message}"
        }
        _messages.send(message)
    }

    fun cancelImport() {
        _pendingImport.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ChecklistApp
                MainViewModel(app.repository, app.backupStorage)
            }
        }
    }
}

internal fun checklistCount(count: Int) = if (count == 1) "1 checklist" else "$count checklists"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenChecklist: (Long) -> Unit,
    viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory),
) {
    val checklists by viewModel.checklists.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ChecklistWithProgress?>(null) }
    var deleting by remember { mutableStateOf<ChecklistWithProgress?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it, withDismissAction = true) }
    }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::onImportFileChosen)
        }
    val storagePermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.exportBackup() else viewModel.showMessage("Storage permission is needed to save to Downloads.")
        }

    fun export() {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else viewModel.exportBackup()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Checklists") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Export to JSON") },
                            onClick = { menuOpen = false; export() },
                        )
                        DropdownMenuItem(
                            text = { Text("Import from JSON") },
                            // Some file managers don't label .json files as JSON, so accept the usual fallbacks.
                            onClick = {
                                menuOpen = false
                                importLauncher.launch(
                                    arrayOf(
                                        "application/json",
                                        "application/octet-stream",
                                        "text/plain"
                                    )
                                )
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
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
    pendingImport?.let { backup ->
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text("Replace all checklists?") },
            text = {
                Text(
                    "This backup from ${backupDateLabel(backup.exportedAt)} has " +
                            "${checklistCount(backup.checklists.size)}. All your current checklists and their " +
                            "history will be replaced. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImport() }) {
                    Text("Replace", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("Cancel") } },
        )
    }
}

private fun backupDateLabel(exportedAt: String): String = try {
    LocalDateTime.parse(exportedAt).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
} catch (e: DateTimeParseException) {
    exportedAt
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
    Card(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
    ) {
        Column(Modifier.padding(start = 16.dp, bottom = 16.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    checklist.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Checklist options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = { menuOpen = false; onRename() })
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
            AssistChip(onClick = onClick, label = { Text(checklist.type.label()) })
            ProgressSummary(
                checklist.done,
                checklist.total,
                Modifier.padding(top = 8.dp, end = 12.dp)
            )
        }
    }
}
