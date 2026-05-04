package com.ntv2.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ntv2.app.core.database.entity.SelectedChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SelectedChannelDao {
    @Query("SELECT * FROM selected_channels")
    fun observeAll(): Flow<List<SelectedChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SelectedChannelEntity>)

    @Query("DELETE FROM selected_channels")
    suspend fun clearAll()
}
