package com.santhomach.personalchecklist.ui.day

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.santhomach.personalchecklist.data.ChecklistRepository
import com.santhomach.personalchecklist.ui.common.ItemRow
import com.santhomach.personalchecklist.ui.common.ProgressSummary
import com.santhomach.personalchecklist.ui.common.displayPeriod
import com.santhomach.personalchecklist.ui.common.periodUnit
import com.santhomach.personalchecklist.ui.common.repoViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DayDetailViewModel(
    private val repository: ChecklistRepository,
    dayId: Long,
    startEditing: Boolean = false,
) : ViewModel() {
    val day = repository.observeDay(dayId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The checklist this copy belongs to, so a weekly copy can be titled by its week. */
    val checklist = repository.observeDay(dayId)
        .flatMapLatest { day ->
            day?.let { repository.observeChecklist(it.checklistId) } ?: emptyFlow()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val items = repository.observeItems(dayId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Past days open read-only; editing has to be switched on, so ticks aren't changed by accident. */
    private val _editing = MutableStateFlow(startEditing)
    val editing = _editing.asStateFlow()

    fun setEditing(editing: Boolean) {
        _editing.value = editing
    }

    fun setChecked(itemId: Long, checked: Boolean) = viewModelScope.launch {
        if (_editing.value) repository.setChecked(itemId, checked)
    }

    companion object {
        val Factory = repoViewModelFactory { repo, handle ->
            DayDetailViewModel(
                repo,
                checkNotNull(handle.get<Long>("dayId")),
                startEditing = handle.get<Boolean>("edit") ?: false,
            )
        }
    }
}

/** One past day (or week, or month). Read-only until Edit is tapped; then items can be ticked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    onBack: () -> Unit,
    viewModel: DayDetailViewModel = viewModel(factory = DayDetailViewModel.Factory),
) {
    val day by viewModel.day.collectAsStateWithLifecycle()
    val checklist by viewModel.checklist.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val date = day?.date
                    val type = checklist?.type
                    Text(if (date != null && type != null) type.displayPeriod(date) else "")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (editing) {
                        IconButton(onClick = { viewModel.setEditing(false) }) {
                            Icon(Icons.Filled.Check, contentDescription = "Done editing")
                        }
                    } else {
                        IconButton(onClick = { viewModel.setEditing(true) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (editing) {
                item(key = "editing-banner") {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "Editing a past ${checklist?.type?.periodUnit() ?: "day"}: tick what you did.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            item(key = "progress") {
                ProgressSummary(
                    items.count { it.isChecked },
                    items.size,
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
            }
            items(items, key = { it.id }) { item ->
                ItemRow(
                    item,
                    onCheckedChange = if (editing) {
                        { checked -> viewModel.setChecked(item.id, checked) }
                    } else null,
                    onDelete = null,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
