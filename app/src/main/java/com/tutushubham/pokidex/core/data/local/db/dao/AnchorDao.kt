package com.tutushubham.pokidex.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tutushubham.pokidex.core.data.AnchorEntity
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain

@Dao
interface AnchorDao {
    @Query("SELECT * FROM anchors")
    suspend fun getAllAnchors(): List<AnchorEntity>

    @Query("SELECT * FROM anchors WHERE block = :block AND domain = :domain")
    suspend fun getAnchorByBlockAndDomain(block: DayBlock, domain: Domain): AnchorEntity?

    @Insert
    suspend fun insert(anchor: AnchorEntity)

    @Update
    suspend fun update(anchor: AnchorEntity)

    @Query("DELETE FROM anchors WHERE id = :id")
    suspend fun deleteById(id: String)
}
