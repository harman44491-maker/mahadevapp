package com.example.data.sync

import com.example.data.db.CustomerDao
import com.example.data.db.SyncLogDao
import com.example.data.model.CustomerRecord
import com.example.data.model.SyncLogEntry
import com.example.data.model.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CloudSyncManager(
    private val customerDao: CustomerDao,
    private val syncLogDao: SyncLogDao,
    private val networkMonitor: NetworkMonitor,
    private val coroutineScope: CoroutineScope
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<String?>(null)
    val lastSyncResult: StateFlow<String?> = _lastSyncResult.asStateFlow()

    init {
        // Auto-sync listener: whenever network state becomes online, trigger auto-sync
        coroutineScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                if (isOnline) {
                    syncPendingRecords(isManual = false)
                }
            }
        }
    }

    /**
     * Synchronize pending offline records with the remote cloud
     */
    suspend fun syncPendingRecords(isManual: Boolean = true): SyncResult = withContext(Dispatchers.IO) {
        if (_isSyncing.value) {
            return@withContext SyncResult(success = false, message = "Sync already in progress")
        }

        if (!networkMonitor.isOnline.value) {
            val pendingCount = customerDao.getPendingSyncCustomers().size
            val offlineMsg = if (networkMonitor.isSimulatingOffline.value) {
                "Offline Mode Active: $pendingCount records queued locally in SQLite"
            } else {
                "No Internet Connection: $pendingCount records queued for remote sync"
            }
            syncLogDao.insertLog(
                SyncLogEntry(
                    eventType = "OFFLINE_QUEUED",
                    details = offlineMsg,
                    itemsCount = pendingCount,
                    isSuccess = true
                )
            )
            _lastSyncResult.value = offlineMsg
            return@withContext SyncResult(success = false, message = offlineMsg)
        }

        _isSyncing.value = true
        try {
            val pendingCustomers = customerDao.getPendingSyncCustomers()
            if (pendingCustomers.isEmpty()) {
                val upToDateMsg = "All customer records are already synchronized with Cloud"
                _lastSyncTime.value = System.currentTimeMillis()
                _lastSyncResult.value = upToDateMsg
                if (isManual) {
                    syncLogDao.insertLog(
                        SyncLogEntry(
                            eventType = "MANUAL_SYNC",
                            details = upToDateMsg,
                            itemsCount = 0,
                            isSuccess = true
                        )
                    )
                }
                return@withContext SyncResult(success = true, message = upToDateMsg, syncedCount = 0)
            }

            // Mark records as currently syncing in local database
            val pendingIds = pendingCustomers.map { it.id }
            customerDao.updateSyncStatus(pendingIds, SyncStatus.SYNCING, null, null)

            // Simulate cloud server round-trip network latency
            delay(1200)

            // Perform batch cloud upload simulation
            val now = System.currentTimeMillis()
            val timeFormatted = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))
            customerDao.updateSyncStatus(pendingIds, SyncStatus.SYNCED, now, null)

            val successMsg = "Successfully synchronized ${pendingCustomers.size} customer record(s) to Cloud ($timeFormatted)"
            _lastSyncTime.value = now
            _lastSyncResult.value = successMsg

            syncLogDao.insertLog(
                SyncLogEntry(
                    eventType = if (isManual) "MANUAL_SYNC" else "AUTO_SYNC",
                    details = successMsg,
                    itemsCount = pendingCustomers.size,
                    isSuccess = true
                )
            )

            return@withContext SyncResult(
                success = true,
                message = successMsg,
                syncedCount = pendingCustomers.size
            )
        } catch (e: Exception) {
            val errorMsg = "Cloud Sync failed: ${e.localizedMessage ?: "Network error"}"
            _lastSyncResult.value = errorMsg
            syncLogDao.insertLog(
                SyncLogEntry(
                    eventType = "ERROR",
                    details = errorMsg,
                    itemsCount = 0,
                    isSuccess = false
                )
            )
            return@withContext SyncResult(success = false, message = errorMsg)
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        syncLogDao.clearLogs()
    }
}

data class SyncResult(
    val success: Boolean,
    val message: String,
    val syncedCount: Int = 0
)
