package com.santhomach.personalchecklist.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ChecklistDao {
    @Insert
    suspend fun insert(checklist: ChecklistEntity): Long

    @Query("UPDATE checklists SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("DELETE FROM checklists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM checklists WHERE id = :id")
    fun observe(id: Long): Flow<ChecklistEntity?>

    @Query("SELECT * FROM checklists ORDER BY createdAt, id")
    suspend fun getAll(): List<ChecklistEntity>

    /** Removes every checklist; days and items go with them through the foreign-key cascade. */
    @Query("DELETE FROM checklists")
    suspend fun deleteAll()

    /** Progress comes from each checklist's latest day: the current day, week or month, or a one-time checklist's only day. */
    @Query(
        """
        SELECT c.id, c.title, c.type,
               COALESCE(SUM(i.isChecked), 0) AS done,
               COUNT(i.id) AS total
        FROM checklists c
        LEFT JOIN checklist_days d
               ON d.checklistId = c.id
              AND d.date = (SELECT MAX(date) FROM checklist_days WHERE checklistId = c.id)
        LEFT JOIN items i ON i.dayId = d.id
        GROUP BY c.id
        ORDER BY c.createdAt
        """
    )
    fun observeAllWithProgress(): Flow<List<ChecklistWithProgress>>
}

@Dao
interface DayDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(day: ChecklistDayEntity): Long

    @Query("SELECT * FROM checklist_days WHERE checklistId = :checklistId AND date = :date")
    suspend fun get(checklistId: Long, date: LocalDate): ChecklistDayEntity?

    @Query("SELECT * FROM checklist_days WHERE checklistId = :checklistId ORDER BY date DESC LIMIT 1")
    suspend fun latest(checklistId: Long): ChecklistDayEntity?

    @Query("SELECT * FROM checklist_days WHERE checklistId = :checklistId ORDER BY date")
    suspend fun getAll(checklistId: Long): List<ChecklistDayEntity>

    @Query("SELECT * FROM checklist_days WHERE checklistId = :checklistId ORDER BY date DESC LIMIT 1")
    fun observeLatest(checklistId: Long): Flow<ChecklistDayEntity?>

    @Query("SELECT * FROM checklist_days WHERE id = :dayId")
    fun observe(dayId: Long): Flow<ChecklistDayEntity?>

    @Query(
        """
        SELECT d.id AS dayId, d.date,
               COALESCE(SUM(i.isChecked), 0) AS done,
               COUNT(i.id) AS total
        FROM checklist_days d
        LEFT JOIN items i ON i.dayId = d.id
        WHERE d.checklistId = :checklistId
        GROUP BY d.id
        ORDER BY d.date DESC
        """
    )
    fun observeHistory(checklistId: Long): Flow<List<DaySummary>>

    /**
     * Per item text, on how many days (or weeks, or months) in [from, before) it was left unticked, most missed first.
     * Counts days rather than rows, so an item listed twice on one day still counts once.
     */
    @Query(
        """
        SELECT i.text AS text,
               COUNT(DISTINCT CASE WHEN i.isChecked = 0 THEN d.id END) AS missed,
               COUNT(DISTINCT d.id) AS total
        FROM items i
        JOIN checklist_days d ON d.id = i.dayId
        WHERE d.checklistId = :checklistId AND d.date >= :from AND d.date < :before
        GROUP BY i.text
        ORDER BY missed DESC, CAST(missed AS REAL) / total DESC, i.text COLLATE NOCASE
        """
    )
    fun observeMissedItems(checklistId: Long, from: LocalDate, before: LocalDate): Flow<List<MissedItemStat>>

    @Query("SELECT COUNT(*) FROM checklist_days WHERE checklistId = :checklistId AND date >= :from AND date < :before")
    fun observeDayCount(checklistId: Long, from: LocalDate, before: LocalDate): Flow<Int>
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE dayId = :dayId ORDER BY position, id")
    fun observeByDay(dayId: Long): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE dayId = :dayId ORDER BY position, id")
    suspend fun getByDay(dayId: Long): List<ItemEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM items WHERE dayId = :dayId")
    suspend fun maxPosition(dayId: Long): Int

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Insert
    suspend fun insertAll(items: List<ItemEntity>)

    @Query("UPDATE items SET isChecked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: Long)
}
