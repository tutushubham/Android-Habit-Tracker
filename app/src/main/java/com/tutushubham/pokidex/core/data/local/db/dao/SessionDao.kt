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

    @Insert
    suspend fun insert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)
}
