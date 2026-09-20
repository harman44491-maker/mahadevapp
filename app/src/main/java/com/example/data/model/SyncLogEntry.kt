package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_log_entries")
data class SyncLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // "AUTO_SYNC", "MANUAL_SYNC", "OFFLINE_QUEUED", "CLOUD_SYNCED", "ERROR"
    val details: String,
    val itemsCount: Int = 0,
    val isSuccess: Boolean = true
)
