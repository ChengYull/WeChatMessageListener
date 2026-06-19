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
        SELECT groupName AS groupName, COUNT(*) AS count
        FROM message_record
        GROUP BY groupName
        ORDER BY count DESC
        """
    )
    fun groupsFlow(): Flow<List<GroupRow>>

    @Query(
        """
        SELECT sender AS nickname, COUNT(*) AS count
        FROM message_record
        WHERE groupName = :groupName
        GROUP BY sender
        ORDER BY count DESC
        """
    )
    fun membersFlow(groupName: String): Flow<List<StatsRow>>

    @Query(
        """
        SELECT * FROM message_record
        WHERE groupName = :groupName AND sender = :sender
        ORDER BY timestamp ASC
        """
    )
    fun messagesFlow(groupName: String, sender: String): Flow<List<MessageRecord>>

    @Query("DELETE FROM message_record")
    suspend fun clear()
}
