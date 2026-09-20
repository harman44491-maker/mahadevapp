package com.example.data.db

import androidx.room.TypeConverter
import com.example.data.model.FieldType
import com.example.data.model.SyncStatus

class Converters {
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus?): String {
        return status?.name ?: SyncStatus.PENDING_SYNC.name
    }

    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus {
        return try {
            if (value != null) SyncStatus.valueOf(value) else SyncStatus.PENDING_SYNC
        } catch (e: Exception) {
            SyncStatus.PENDING_SYNC
        }
    }

    @TypeConverter
    fun fromFieldType(type: FieldType?): String {
        return type?.name ?: FieldType.TEXT.name
    }

    @TypeConverter
    fun toFieldType(value: String?): FieldType {
        return try {
            if (value != null) FieldType.valueOf(value) else FieldType.TEXT
        } catch (e: Exception) {
            FieldType.TEXT
        }
    }
}
