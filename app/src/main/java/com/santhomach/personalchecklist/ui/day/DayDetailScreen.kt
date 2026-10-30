package com.santhomach.personalchecklist.ui.day

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import com.santhomach.personalchecklist.ui.common.repoViewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class DayDetailViewModel(repository: ChecklistRepository, dayId: Long) : ViewModel() {
    val day = repository.observeDay(dayId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The checklist this copy belongs to, so a weekly copy can be titled by its week. */
    val checklist = repository.observeDay(dayId)
        .flatMapLatest { day -> day?.let { repository.observeChecklist(it.checklistId) } ?: emptyFlow() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val items = repository.observeItems(dayId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        val Factory = repoViewModelFactory { repo, handle ->
            DayDetailViewModel(repo, checkNotNull(handle.get<Long>("dayId")))
        }
    }
}

/** Read-only view of one past day (or week). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    onBack: () -> Unit,
    viewModel: DayDetailViewModel = viewModel(factory = DayDetailViewModel.Factory),
) {
    val day by viewModel.day.collectAsStateWithLifecycle()
    val checklist by viewModel.checklist.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()

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
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
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
                    onCheckedChange = null,
                    onDelete = null,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
