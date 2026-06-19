package com.example.wechatstats.data

import kotlinx.coroutines.flow.Flow

class StatsRepository(private val dao: MessageDao) {
    fun groupsFlow(): Flow<List<GroupRow>> = dao.groupsFlow()
    fun membersFlow(groupName: String): Flow<List<StatsRow>> = dao.membersFlow(groupName)
    fun messagesFlow(groupName: String, sender: String): Flow<List<MessageRecord>> =
        dao.messagesFlow(groupName, sender)

    suspend fun insert(record: MessageRecord) = dao.insert(record)
    suspend fun clear() = dao.clear()
}
