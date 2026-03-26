package com.tutushubham.pokidex.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tutushubham.pokidex.core.data.UserIntentStatsEntity

@Dao
interface UserIntentStatsDao {
    @Query("SELECT * FROM user_intent_stats WHERE intentId = :intentId")
    suspend fun getStats(intentId: String): UserIntentStatsEntity?

    @Query("SELECT * FROM user_intent_stats")
    suspend fun getAll(): List<UserIntentStatsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: UserIntentStatsEntity)

    @Query("DELETE FROM user_intent_stats")
    suspend fun deleteAll()
}
