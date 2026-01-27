package com.tutushubham.pokidex.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tutushubham.pokidex.core.data.CaptureEntity

@Dao
interface CaptureDao {
    @Query("SELECT * FROM captures WHERE resolved = 0")
    suspend fun getUnresolvedCaptures(): List<CaptureEntity>

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun getCaptureById(id: String): CaptureEntity?

    @Insert
    suspend fun insert(capture: CaptureEntity)

    @Update
    suspend fun update(capture: CaptureEntity)
}
