package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CustomerRecord
import com.example.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customer_records ORDER BY createdAt DESC")
    fun getAllCustomersFlow(): Flow<List<CustomerRecord>>

    @Query("SELECT * FROM customer_records WHERE syncStatus = :status ORDER BY createdAt DESC")
    fun getCustomersBySyncStatusFlow(status: SyncStatus): Flow<List<CustomerRecord>>

    @Query("SELECT * FROM customer_records WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSyncCustomers(): List<CustomerRecord>

    @Query("SELECT * FROM customer_records WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Long): CustomerRecord?

    @Query("SELECT * FROM customer_records WHERE serialNumber = :serialNumber LIMIT 1")
    suspend fun getCustomerBySerialNumber(serialNumber: String): CustomerRecord?

    @Query("SELECT COUNT(*) FROM customer_records")
    fun getTotalCustomerCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM customer_records WHERE syncStatus = 'SYNCED'")
    fun getSyncedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM customer_records WHERE syncStatus != 'SYNCED'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT MAX(id) FROM customer_records")
    suspend fun getMaxId(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerRecord): Long

    @Update
    suspend fun updateCustomer(customer: CustomerRecord)

    @Delete
    suspend fun deleteCustomer(customer: CustomerRecord)

    @Query("DELETE FROM customer_records WHERE id = :id")
    suspend fun deleteCustomerById(id: Long)

    @Query("UPDATE customer_records SET syncStatus = :status, lastSyncedTimestamp = :syncedTime, syncError = :error WHERE id IN (:ids)")
    suspend fun updateSyncStatus(ids: List<Long>, status: SyncStatus, syncedTime: Long?, error: String?)
}
