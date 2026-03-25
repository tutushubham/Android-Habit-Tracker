package com.tutushubham.pokidex.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tutushubham.pokidex.core.data.SessionEntity
import java.time.LocalDate

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE date = :date")
    suspend fun getSessionsForDate(date: LocalDate): List<SessionEntity>

    @Query("SELECT COUNT(*) FROM sessions WHERE intentId = :intentId AND status = 2")
    suspend fun getCompletedUnitsForIntent(intentId: String): Int

    @Query("SELECT COUNT(DISTINCT date) FROM sessions WHERE intentId = :intentId AND status = 2")
    suspend fun getDistinctDaysWorkedForIntent(intentId: String): Int

    @Query("SELECT COALESCE(SUM(actualMinutes), 0) FROM sessions WHERE intentId = :intentId AND status = 2 AND actualMinutes IS NOT NULL")
    suspend fun getTotalActualMinutesForIntent(intentId: String): Int

    @Query("SELECT COUNT(*) FROM sessions WHERE intentId = :intentId AND status = 3")
    suspend fun getSkippedSessionCountForIntent(intentId: String): Int

    @Query("SELECT * FROM sessions WHERE date >= :cutoffDate ORDER BY intentId ASC, date DESC")
    suspend fun getSessionsSince(cutoffDate: LocalDate): List<SessionEntity>

    @Insert
    suspend fun insert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)
}
