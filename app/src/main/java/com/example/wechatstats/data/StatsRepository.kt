package com.example.wechatstats.data

import kotlinx.coroutines.flow.Flow

class StatsRepository(private val dao: MessageDao) {
    fun statsFlow(): Flow<List<StatsRow>> = dao.statsFlow()

    suspend fun insert(record: MessageRecord) = dao.insert(record)

    suspend fun clear() = dao.clear()
}
