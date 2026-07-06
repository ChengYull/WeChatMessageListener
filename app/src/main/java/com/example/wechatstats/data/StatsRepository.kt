package com.example.wechatstats.data

import kotlinx.coroutines.flow.Flow

class StatsRepository(private val dao: MessageDao) {
    fun groupsFlow(): Flow<List<GroupRow>> = dao.groupsFlow()
    fun groupsFlow(dayStart: Long, dayEnd: Long): Flow<List<GroupRow>> =
        dao.groupsFlow(dayStart, dayEnd)

    fun membersFlow(groupName: String): Flow<List<StatsRow>> = dao.membersFlow(groupName)
    fun membersFlow(groupName: String, dayStart: Long, dayEnd: Long): Flow<List<StatsRow>> =
        dao.membersFlow(groupName, dayStart, dayEnd)

    fun messagesFlow(groupName: String, sender: String): Flow<List<MessageRecord>> =
        dao.messagesFlow(groupName, sender)
    fun messagesFlow(groupName: String, sender: String, dayStart: Long, dayEnd: Long): Flow<List<MessageRecord>> =
        dao.messagesFlow(groupName, sender, dayStart, dayEnd)

    suspend fun insert(record: MessageRecord) = dao.insert(record)
    suspend fun clear() = dao.clear()
}
