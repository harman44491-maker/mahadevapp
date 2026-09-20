package com.example.data.repository

import com.example.data.db.CustomerDao
import com.example.data.db.FormFieldDao
import com.example.data.db.SyncLogDao
import com.example.data.model.CustomerRecord
import com.example.data.model.FormFieldDefinition
import com.example.data.model.SyncLogEntry
import com.example.data.model.SyncStatus
import com.example.data.sync.CloudSyncManager
import com.example.data.sync.NetworkMonitor
import com.example.data.sync.SyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class FieldDataRepository(
    private val customerDao: CustomerDao,
    private val formFieldDao: FormFieldDao,
    private val syncLogDao: SyncLogDao,
    private val syncManager: CloudSyncManager,
    private val networkMonitor: NetworkMonitor
) {
    // Flow streams for UI
    val allCustomers: Flow<List<CustomerRecord>> = customerDao.getAllCustomersFlow()
    val totalCount: Flow<Int> = customerDao.getTotalCustomerCountFlow()
    val syncedCount: Flow<Int> = customerDao.getSyncedCountFlow()
    val pendingCount: Flow<Int> = customerDao.getPendingCountFlow()

    val allFields: Flow<List<FormFieldDefinition>> = formFieldDao.getAllFieldsFlow()
    val activeFields: Flow<List<FormFieldDefinition>> = formFieldDao.getActiveFieldsFlow()

    val syncLogs: Flow<List<SyncLogEntry>> = syncLogDao.getLatestLogsFlow()

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
    val isDeviceConnected: StateFlow<Boolean> = networkMonitor.isDeviceConnected
    val isSimulatingOffline: StateFlow<Boolean> = networkMonitor.isSimulatingOffline
    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
    val lastSyncTime: StateFlow<Long?> = syncManager.lastSyncTime
    val lastSyncResult: StateFlow<String?> = syncManager.lastSyncResult

    suspend fun getCustomerById(id: Long): CustomerRecord? = customerDao.getCustomerById(id)

    suspend fun saveCustomer(customer: CustomerRecord): Long {
        val id = customerDao.insertCustomer(customer)
        val isOnlineNow = networkMonitor.isOnline.value

        // Log entry creation
        val logDetails = if (isOnlineNow) {
            "Customer '${customer.name}' (#${customer.serialNumber}) saved. Triggering real-time cloud sync..."
        } else {
            "Customer '${customer.name}' (#${customer.serialNumber}) saved locally. Queued for remote synchronization."
        }

        syncLogDao.insertLog(
            SyncLogEntry(
                eventType = if (isOnlineNow) "AUTO_SYNC" else "OFFLINE_QUEUED",
                details = logDetails,
                itemsCount = 1,
                isSuccess = true
            )
        )

        // If online, immediately sync this new record with cloud
        if (isOnlineNow) {
            syncManager.syncPendingRecords(isManual = false)
        }

        return id
    }

    suspend fun updateCustomer(customer: CustomerRecord) {
        // Mark as PENDING_SYNC upon update
        val updated = customer.copy(
            syncStatus = SyncStatus.PENDING_SYNC,
            lastModifiedTimestamp = System.currentTimeMillis()
        )
        customerDao.updateCustomer(updated)
        if (networkMonitor.isOnline.value) {
            syncManager.syncPendingRecords(isManual = false)
        }
    }

    suspend fun deleteCustomer(customer: CustomerRecord) {
        customerDao.deleteCustomer(customer)
        syncLogDao.insertLog(
            SyncLogEntry(
                eventType = "RECORD_DELETED",
                details = "Deleted record '${customer.name}' (#${customer.serialNumber})",
                itemsCount = 1,
                isSuccess = true
            )
        )
    }

    suspend fun generateNextSerialNumber(): String {
        val maxId = customerDao.getMaxId() ?: 0L
        val nextNum = maxId + 1
        return String.format(Locale.getDefault(), "FLD-2026-%04d", nextNum)
    }

    // Form builder operations
    suspend fun saveFormField(field: FormFieldDefinition): Long = formFieldDao.insertField(field)

    suspend fun updateFormField(field: FormFieldDefinition) = formFieldDao.updateField(field)

    suspend fun deleteFormField(field: FormFieldDefinition) = formFieldDao.deleteField(field)

    // Sync operations
    suspend fun syncNow(): SyncResult = syncManager.syncPendingRecords(isManual = true)

    suspend fun clearSyncLogs() = syncManager.clearLogs()

    fun toggleOfflineSimulation(simulateOffline: Boolean) {
        networkMonitor.setSimulatingOffline(simulateOffline)
    }
}
