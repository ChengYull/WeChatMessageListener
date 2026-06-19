package com.example.wechatstats.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "message_record",
    indices = [Index(value = ["notificationKey"], unique = true)]
)
data class MessageRecord(
    val notificationKey: String,
    val groupName: String,
    val sender: String,
    val text: String,
    val timestamp: Long,
    @PrimaryKey(autoGenerate = true) val id: Long = 0
)
