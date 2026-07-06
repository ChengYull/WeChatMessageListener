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

    suspend fun deleteGroup(groupName: String) = dao.deleteGroup(groupName)
    suspend fun deleteGroup(groupName: String, dayStart: Long, dayEnd: Long) =
        dao.deleteGroup(groupName, dayStart, dayEnd)
    suspend fun deleteSender(groupName: String, sender: String) =
        dao.deleteSender(groupName, sender)
    suspend fun deleteSender(groupName: String, sender: String, dayStart: Long, dayEnd: Long) =
        dao.deleteSender(groupName, sender, dayStart, dayEnd)

    suspend fun getGroupMessages(groupName: String): List<MessageRecord> =
        dao.getGroupMessages(groupName)
    suspend fun getGroupMessages(groupName: String, dayStart: Long, dayEnd: Long): List<MessageRecord> =
        dao.getGroupMessages(groupName, dayStart, dayEnd)
    suspend fun getSenderMessages(groupName: String, sender: String): List<MessageRecord> =
        dao.getSenderMessages(groupName, sender)
    suspend fun getSenderMessages(groupName: String, sender: String, dayStart: Long, dayEnd: Long): List<MessageRecord> =
        dao.getSenderMessages(groupName, sender, dayStart, dayEnd)
}
