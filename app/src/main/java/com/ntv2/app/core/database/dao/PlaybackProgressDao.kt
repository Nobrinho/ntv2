package com.ntv2.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ntv2.app.core.database.entity.PlaybackProgressEntity

@Dao
interface PlaybackProgressDao {
    @Query("SELECT * FROM playback_progress WHERE mediaId = :mediaId")
    suspend fun get(mediaId: String): PlaybackProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackProgressEntity)

    @Query("DELETE FROM playback_progress WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: String)
}
