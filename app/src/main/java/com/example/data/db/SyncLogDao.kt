package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SyncLogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_log_entries ORDER BY timestamp DESC LIMIT 100")
    fun getLatestLogsFlow(): Flow<List<SyncLogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SyncLogEntry)

    @Query("DELETE FROM sync_log_entries")
    suspend fun clearLogs()
}
