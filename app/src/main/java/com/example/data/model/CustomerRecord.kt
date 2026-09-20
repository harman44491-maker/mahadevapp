package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "customer_records")
data class CustomerRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val remoteId: String = UUID.randomUUID().toString(),
    val serialNumber: String,
    val name: String,
    val fatherName: String,
    val dateOfBirth: String,
    val mobileNumber: String,
    val email: String,
    val relatedWork: String,
    val remarks: String,
    val replyToCustomer: String = "",
    val documentsJson: String = "[]", // JSON array of up to 5 CustomerDocument items
    val customFieldsJson: String = "{}", // JSON map of fieldKey -> value
    val syncStatus: SyncStatus = SyncStatus.PENDING_SYNC,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedTimestamp: Long = System.currentTimeMillis(),
    val lastSyncedTimestamp: Long? = null,
    val syncError: String? = null
)
