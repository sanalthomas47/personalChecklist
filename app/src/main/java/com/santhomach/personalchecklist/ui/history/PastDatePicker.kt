package com.santhomach.personalchecklist.ui.history

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The date picker works in UTC midnights; these convert without any time-zone shift. */
internal fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
internal fun pickerMillisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/** Picks a date within [range] (past days only), starting on its last day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastDatePicker(
    range: ClosedRange<LocalDate>,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val years = range.start.year..range.endInclusive.year
    val state = rememberDatePickerState(
        initialSelectedDateMillis = range.endInclusive.toPickerMillis(),
        yearRange = years,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = pickerMillisToDate(utcTimeMillis) in range
            override fun isSelectableYear(year: Int) = year in years
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(pickerMillisToDate(it)) } },
                enabled = state.selectedDateMillis != null,
            ) { Text("Open") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(
            state,
            title = {
                Text("Update a past date", Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp))
            },
        )
    }
}
