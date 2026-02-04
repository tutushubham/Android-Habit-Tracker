package com.tutushubham.pokidex.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tutushubham.pokidex.core.data.DailyFocusOverrideEntity
import com.tutushubham.pokidex.core.domain.model.Domain
import java.time.LocalDate

@Dao
interface DailyFocusOverrideDao {
    @Query("SELECT * FROM daily_focus_override WHERE domain = :domain AND date = :date")
    suspend fun getOverride(domain: Domain, date: LocalDate): DailyFocusOverrideEntity?

    @Upsert
    suspend fun setOverride(entity: DailyFocusOverrideEntity)

    @Query("DELETE FROM daily_focus_override WHERE domain = :domain AND date = :date")
    suspend fun clearOverride(domain: Domain, date: LocalDate)
}
