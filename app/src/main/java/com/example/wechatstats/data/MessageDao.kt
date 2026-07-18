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

    // ── 全量查询 ──

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

    // ── 按天查询 ──

    @Query(
        """
        SELECT groupName AS groupName, COUNT(*) AS count
        FROM message_record
        WHERE timestamp >= :dayStart AND timestamp < :dayEnd
        GROUP BY groupName
        ORDER BY count DESC
        """
    )
    fun groupsFlow(dayStart: Long, dayEnd: Long): Flow<List<GroupRow>>

    @Query(
        """
        SELECT sender AS nickname, COUNT(*) AS count
        FROM message_record
        WHERE groupName = :groupName
          AND timestamp >= :dayStart AND timestamp < :dayEnd
        GROUP BY sender
        ORDER BY count DESC
        """
    )
    fun membersFlow(groupName: String, dayStart: Long, dayEnd: Long): Flow<List<StatsRow>>

    @Query(
        """
        SELECT * FROM message_record
        WHERE groupName = :groupName AND sender = :sender
          AND timestamp >= :dayStart AND timestamp < :dayEnd
        ORDER BY timestamp ASC
        """
    )
    fun messagesFlow(groupName: String, sender: String, dayStart: Long, dayEnd: Long): Flow<List<MessageRecord>>

    // ── 每日统计（日历图）──

    @Query(
        """
        SELECT ((timestamp + 28800000) / 86400000) * 86400000 AS bucketStartMillis, COUNT(*) AS count
        FROM message_record
        WHERE timestamp >= :dayStart AND timestamp < :dayEnd
        GROUP BY bucketStartMillis
        ORDER BY bucketStartMillis ASC
        """
    )
    fun dailyCountsFlow(dayStart: Long, dayEnd: Long): Flow<List<ChartPoint>>

    // ── 5 分钟发言频次曲线 ──

    @Query(
        """
        SELECT (timestamp / 300000) * 300000 AS bucketStartMillis, COUNT(*) AS count
        FROM message_record
        WHERE timestamp >= :dayStart AND timestamp < :dayEnd
        GROUP BY bucketStartMillis
        ORDER BY bucketStartMillis ASC
        """
    )
    fun chartFlow(dayStart: Long, dayEnd: Long): Flow<List<ChartPoint>>

    @Query(
        """
        SELECT (timestamp / 300000) * 300000 AS bucketStartMillis, COUNT(*) AS count
        FROM message_record
        WHERE timestamp >= :dayStart AND timestamp < :dayEnd
          AND groupName = :groupName
        GROUP BY bucketStartMillis
        ORDER BY bucketStartMillis ASC
        """
    )
    fun chartFlow(dayStart: Long, dayEnd: Long, groupName: String): Flow<List<ChartPoint>>

    @Query(
        """
        SELECT (timestamp / 300000) * 300000 AS bucketStartMillis, COUNT(*) AS count
        FROM message_record
        WHERE timestamp >= :dayStart AND timestamp < :dayEnd
          AND groupName = :groupName AND sender = :sender
        GROUP BY bucketStartMillis
        ORDER BY bucketStartMillis ASC
        """
    )
    fun chartFlow(dayStart: Long, dayEnd: Long, groupName: String, sender: String): Flow<List<ChartPoint>>

    @Query("DELETE FROM message_record")
    suspend fun clear()

    // ── 按条件删除 ──

    @Query("DELETE FROM message_record WHERE groupName = :groupName")
    suspend fun deleteGroup(groupName: String)

    @Query("DELETE FROM message_record WHERE groupName = :groupName AND timestamp >= :dayStart AND timestamp < :dayEnd")
    suspend fun deleteGroup(groupName: String, dayStart: Long, dayEnd: Long)

    @Query("DELETE FROM message_record WHERE groupName = :groupName AND sender = :sender")
    suspend fun deleteSender(groupName: String, sender: String)

    @Query("DELETE FROM message_record WHERE groupName = :groupName AND sender = :sender AND timestamp >= :dayStart AND timestamp < :dayEnd")
    suspend fun deleteSender(groupName: String, sender: String, dayStart: Long, dayEnd: Long)

    // ── 一次性查询（非 Flow，用于导出）──

    @Query("SELECT * FROM message_record WHERE groupName = :groupName ORDER BY timestamp ASC")
    suspend fun getGroupMessages(groupName: String): List<MessageRecord>

    @Query("SELECT * FROM message_record WHERE groupName = :groupName AND timestamp >= :dayStart AND timestamp < :dayEnd ORDER BY timestamp ASC")
    suspend fun getGroupMessages(groupName: String, dayStart: Long, dayEnd: Long): List<MessageRecord>

    @Query("SELECT * FROM message_record WHERE groupName = :groupName AND sender = :sender ORDER BY timestamp ASC")
    suspend fun getSenderMessages(groupName: String, sender: String): List<MessageRecord>

    @Query("SELECT * FROM message_record WHERE groupName = :groupName AND sender = :sender AND timestamp >= :dayStart AND timestamp < :dayEnd ORDER BY timestamp ASC")
    suspend fun getSenderMessages(groupName: String, sender: String, dayStart: Long, dayEnd: Long): List<MessageRecord>

    @Query("SELECT * FROM message_record ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<MessageRecord>

    @Query("SELECT * FROM message_record WHERE timestamp >= :dayStart AND timestamp < :dayEnd ORDER BY timestamp ASC")
    suspend fun getAllMessages(dayStart: Long, dayEnd: Long): List<MessageRecord>
}
