package com.tutushubham.pokidex.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tutushubham.pokidex.core.data.FocusEntity
import com.tutushubham.pokidex.core.domain.model.Domain

@Dao
interface FocusDao {
    @Query("SELECT * FROM focuses WHERE id = :id")
    suspend fun getFocusById(id: String): FocusEntity?

    @Query("SELECT * FROM focuses WHERE domain = :domain")
    suspend fun getFocusesByDomain(domain: Domain): List<FocusEntity>

    @Query("SELECT * FROM focuses")
    suspend fun getAllFocuses(): List<FocusEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(focus: FocusEntity)

    @Update
    suspend fun update(focus: FocusEntity)

    @Query("DELETE FROM focuses WHERE id = :id")
    suspend fun delete(id: String)
}
