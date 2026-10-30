package com.santhomach.personalchecklist.data

import androidx.room.withTransaction
import com.santhomach.personalchecklist.data.backup.Backup
import com.santhomach.personalchecklist.data.backup.BackupChecklist
import com.santhomach.personalchecklist.data.backup.BackupDay
import com.santhomach.personalchecklist.data.backup.BackupItem
import com.santhomach.personalchecklist.data.backup.BackupJson
import com.santhomach.personalchecklist.data.db.AppDatabase
import com.santhomach.personalchecklist.data.db.ChecklistDayEntity
import com.santhomach.personalchecklist.data.db.ChecklistEntity
import com.santhomach.personalchecklist.data.db.ChecklistType
import com.santhomach.personalchecklist.data.db.ItemEntity
import com.santhomach.personalchecklist.util.LogicalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class ChecklistRepository(
    private val db: AppDatabase,
    private val today: () -> LocalDate = { LogicalDate.today() },
) {
    private val checklistDao = db.checklistDao()
    private val dayDao = db.dayDao()
    private val itemDao = db.itemDao()

    fun observeChecklists() = checklistDao.observeAllWithProgress()
    fun observeChecklist(id: Long) = checklistDao.observe(id)
    fun observeCurrentDay(checklistId: Long) = dayDao.observeLatest(checklistId)
    fun observeDay(dayId: Long) = dayDao.observe(dayId)
    fun observeItems(dayId: Long) = itemDao.observeByDay(dayId)
    fun observeHistory(checklistId: Long) = dayDao.observeHistory(checklistId)

    /**
     * Which items were left unticked on past days (or weeks, for a weekly checklist) within [period];
     * the current day or week is excluded.
     */
    fun observeMissedItems(checklistId: Long, period: MissedPeriod): Flow<MissedItemsReport> = flow {
        val type = checklistDao.observe(checklistId).first()?.type ?: return@flow
        val before = type.periodStart(today())
        val from = period.from(before, type)
        emitAll(
            combine(
                dayDao.observeDayCount(checklistId, from, before),
                dayDao.observeMissedItems(checklistId, from, before),
            ) { pastPeriods, stats ->
                val (missed, neverMissed) = stats.partition { it.missed > 0 }
                MissedItemsReport(pastPeriods, missed, neverMissed.map { it.text })
            }
        )
    }

    /**
     * Creates the current copy of every daily and weekly checklist that doesn't have one yet (today's
     * for daily, this week's for weekly), copying the items of its most recent copy with all
     * checkboxes cleared. Safe to call any number of times.
     */
    suspend fun ensureToday() {
        val today = today()
        db.withTransaction {
            for (checklist in checklistDao.getAll().filter { it.type.repeats }) {
                val date = checklist.type.periodStart(today)
                if (dayDao.get(checklist.id, date) != null) continue
                val previous = dayDao.latest(checklist.id)
                val newDayId = dayDao.insert(ChecklistDayEntity(checklistId = checklist.id, date = date))
                if (previous != null && previous.date < date) {
                    itemDao.insertAll(
                        itemDao.getByDay(previous.id).map {
                            it.copy(id = 0, dayId = newDayId, isChecked = false)
                        }
                    )
                }
            }
        }
    }

    suspend fun createChecklist(title: String, type: ChecklistType): Long = db.withTransaction {
        val id = checklistDao.insert(ChecklistEntity(title = title.trim(), type = type))
        dayDao.insert(ChecklistDayEntity(checklistId = id, date = type.periodStart(today())))
        id
    }

    suspend fun renameChecklist(id: Long, title: String) = checklistDao.rename(id, title.trim())

    suspend fun deleteChecklist(id: Long) = checklistDao.delete(id)

    suspend fun addItem(dayId: Long, text: String) {
        itemDao.insert(ItemEntity(dayId = dayId, text = text.trim(), position = itemDao.maxPosition(dayId) + 1))
    }

    suspend fun setChecked(itemId: Long, checked: Boolean) = itemDao.setChecked(itemId, checked)

    suspend fun deleteItem(itemId: Long) = itemDao.delete(itemId)

    /** Snapshot of all checklists, every day and every item, for a JSON backup. */
    suspend fun exportBackup(now: LocalDateTime = LocalDateTime.now()): Backup = db.withTransaction {
        Backup(
            exportedAt = now.truncatedTo(ChronoUnit.SECONDS).toString(),
            checklists = checklistDao.getAll().map { checklist ->
                BackupChecklist(
                    title = checklist.title,
                    type = checklist.type,
                    createdAt = checklist.createdAt,
                    days = dayDao.getAll(checklist.id).map { day ->
                        BackupDay(
                            date = day.date.toString(),
                            items = itemDao.getByDay(day.id).map { BackupItem(it.text, it.isChecked) },
                        )
                    },
                )
            },
        )
    }

    /**
     * Replaces all data with the contents of [backup] in one transaction, so a failure leaves the
     * existing data untouched. Daily and weekly checklists then get their current copy if the backup
     * doesn't have one.
     */
    suspend fun importBackup(backup: Backup) {
        db.withTransaction {
            checklistDao.deleteAll()
            for (checklist in backup.checklists) {
                val checklistId = checklistDao.insert(
                    ChecklistEntity(title = checklist.title.trim(), type = checklist.type, createdAt = checklist.createdAt)
                )
                val days = checklist.days.ifEmpty {
                    listOf(BackupDay(checklist.type.periodStart(today()).toString(), emptyList()))
                }
                for (day in days) {
                    val date = BackupJson.parseDate(day.date, checklist.title)
                    val dayId = dayDao.insert(ChecklistDayEntity(checklistId = checklistId, date = date))
                    itemDao.insertAll(
                        day.items.mapIndexed { index, item ->
                            ItemEntity(dayId = dayId, text = item.text.trim(), isChecked = item.isChecked, position = index)
                        }
                    )
                }
            }
        }
        ensureToday()
    }
}
