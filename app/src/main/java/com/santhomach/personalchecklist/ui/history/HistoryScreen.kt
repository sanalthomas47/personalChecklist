package com.santhomach.personalchecklist.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.santhomach.personalchecklist.data.ChecklistRepository
import com.santhomach.personalchecklist.data.MissedPeriod
import com.santhomach.personalchecklist.data.pastDateRange
import com.santhomach.personalchecklist.data.db.ChecklistType
import com.santhomach.personalchecklist.ui.common.ProgressSummary
import com.santhomach.personalchecklist.ui.common.currentPeriodName
import com.santhomach.personalchecklist.ui.common.displayPeriod
import com.santhomach.personalchecklist.ui.common.label
import com.santhomach.personalchecklist.ui.common.repoViewModelFactory
import com.santhomach.personalchecklist.util.LogicalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repository: ChecklistRepository, private val checklistId: Long) : ViewModel() {
    val checklist = repository.observeChecklist(checklistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val days = repository.observeHistory(checklistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Dates that "Update a past date" can pick from; null until a period has finished. */
    val pastDates = combine(repository.observeChecklist(checklistId).filterNotNull(), days) { checklist, days ->
        pastDateRange(checklist.type, checklist.createdAt, days.lastOrNull()?.date, LogicalDate.today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _missedPeriod = MutableStateFlow(MissedPeriod.LONG)
    val missedPeriod = _missedPeriod.asStateFlow()

    /** Null until the first result for the current period arrives. */
    val missedItems = _missedPeriod
        .flatMapLatest { period -> repository.observeMissedItems(checklistId, period) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMissedPeriod(period: MissedPeriod) {
        _missedPeriod.value = period
    }

    /** Finds (or fills in) the copy for a past [date] and hands its id to [onOpened]. */
    fun openPastDate(date: LocalDate, onOpened: (dayId: Long) -> Unit) = viewModelScope.launch {
        onOpened(repository.pastDay(checklistId, date))
    }

    companion object {
        val Factory = repoViewModelFactory { repo, handle ->
            HistoryViewModel(repo, checkNotNull(handle.get<Long>("checklistId")))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenDay: (Long) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
    /** Opens a past copy straight into edit mode. */
    onEditDay: (Long) -> Unit = onOpenDay,
) {
    val checklist by viewModel.checklist.collectAsStateWithLifecycle()
    val days by viewModel.days.collectAsStateWithLifecycle()
    val missedPeriod by viewModel.missedPeriod.collectAsStateWithLifecycle()
    val missedItems by viewModel.missedItems.collectAsStateWithLifecycle()
    val type = checklist?.type ?: ChecklistType.DAILY
    val current = type.periodStart(LogicalDate.today())
    val currentSuffix = " (${type.currentPeriodName()})"
    val pastDates by viewModel.pastDates.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History · ${checklist?.title.orEmpty()}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "missed") {
                MissedItemsSection(missedItems, type, missedPeriod, viewModel::setMissedPeriod)
            }
            item(key = "history-header") {
                Text(
                    "${type.label()} history",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item(key = "update-past") {
                OutlinedButton(
                    onClick = { showPicker = true },
                    enabled = pastDates != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Update a past date", Modifier.padding(start = 8.dp))
                }
            }
            items(days, key = { it.dayId }) { day ->
                Card(onClick = { onOpenDay(day.dayId) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            type.displayPeriod(day.date) + if (day.date == current) currentSuffix else "",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        ProgressSummary(day.done, day.total, Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }

    val range = pastDates
    if (showPicker && range != null) {
        PastDatePicker(
            range = range,
            onPick = { date ->
                showPicker = false
                viewModel.openPastDate(date, onEditDay)
            },
            onDismiss = { showPicker = false },
        )
    }
}
