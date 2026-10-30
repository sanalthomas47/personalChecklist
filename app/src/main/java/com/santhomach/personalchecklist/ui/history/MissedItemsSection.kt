package com.santhomach.personalchecklist.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.santhomach.personalchecklist.data.MissedItemsReport
import com.santhomach.personalchecklist.data.MissedPeriod
import com.santhomach.personalchecklist.data.db.ChecklistType
import com.santhomach.personalchecklist.data.db.MissedItemStat
import com.santhomach.personalchecklist.ui.common.periodUnit

/** "7 days" / "30 days" / "All time" for daily checklists, "4 weeks" / "12 weeks" / "All time" for weekly. */
fun MissedPeriod.label(type: ChecklistType): String =
    length(type)?.let { "$it ${type.periodUnit()}s" } ?: "All time"

private fun count(count: Int, unit: String) = if (count == 1) "1 $unit" else "$count ${unit}s"

private fun percent(missed: Int, total: Int) = if (total == 0) 0 else missed * 100 / total

fun missedLabel(missed: Int, total: Int, unit: String = "day"): String =
    "$missed/$total ${unit}s · ${percent(missed, total)}%"

/**
 * Ranked bars of the items left unticked most often on past days (or weeks). Bars share one scale
 * (the number of past periods in the range) so their lengths compare directly.
 */
@Composable
fun MissedItemsSection(
    report: MissedItemsReport?,
    type: ChecklistType,
    period: MissedPeriod,
    onPeriodChange: (MissedPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = type.periodUnit()
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Most missed items", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MissedPeriod.entries.forEach { option ->
                    FilterChip(
                        selected = option == period,
                        onClick = { onPeriodChange(option) },
                        label = { Text(option.label(type)) },
                    )
                }
            }
            when {
                report == null -> Unit
                report.pastPeriods == 0 -> SecondaryText("Missed items show up here once a full $unit has passed.")
                else -> {
                    val current = if (type == ChecklistType.WEEKLY) "this week" else "today"
                    SecondaryText("Based on ${count(report.pastPeriods, unit)} before $current")
                    if (report.missed.isEmpty()) {
                        Text("Nothing missed in this period.", style = MaterialTheme.typography.bodyMedium)
                    }
                    report.missed.forEach { MissedItemBar(it, scale = report.pastPeriods, unit = unit) }
                    if (report.neverMissed.isNotEmpty()) {
                        SecondaryText("Never missed: ${report.neverMissed.joinToString(", ")}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondaryText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MissedItemBar(stat: MissedItemStat, scale: Int, unit: String) {
    val barColor = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(4.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription =
                    "${stat.text}, missed ${stat.missed} of ${count(stat.total, unit)}, ${percent(stat.missed, stat.total)} percent"
            },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stat.text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                missedLabel(stat.missed, stat.total, unit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        // Track is a lighter step of the bar's own colour.
        Box(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(8.dp)
                .clip(shape)
                .background(barColor.copy(alpha = 0.16f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((stat.missed.toFloat() / scale.coerceAtLeast(1)).coerceIn(0f, 1f))
                    .widthIn(min = 4.dp)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(barColor),
            )
        }
    }
}
