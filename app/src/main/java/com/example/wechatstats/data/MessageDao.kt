package com.example.wechatstats.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: MessageRecord): Long

    @Query(
        """
        SELECT sender AS nickname, COUNT(*) AS count
        FROM message_record
        GROUP BY sender
        ORDER BY count DESC
        """
    )
    fun statsFlow(): Flow<List<StatsRow>>

    @Query("DELETE FROM message_record")
    suspend fun clear()
}
