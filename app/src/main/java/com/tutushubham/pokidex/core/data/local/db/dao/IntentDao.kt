package com.tutushubham.pokidex.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tutushubham.pokidex.core.data.IntentEntity
import java.time.LocalDate

@Dao
interface IntentDao {
    @Query("SELECT * FROM intents WHERE startDate <= :endDate AND endDate >= :startDate")
    suspend fun getIntentsForDateRange(startDate: LocalDate, endDate: LocalDate): List<IntentEntity>

    @Query("SELECT * FROM intents WHERE id = :id")
    suspend fun getIntentById(id: String): IntentEntity?

    @Insert
    suspend fun insert(intent: IntentEntity)

    @Update
    suspend fun update(intent: IntentEntity)
}
