package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.FormFieldDefinition
import kotlinx.coroutines.flow.Flow

@Dao
interface FormFieldDao {
    @Query("SELECT * FROM form_field_definitions ORDER BY displayOrder ASC, id ASC")
    fun getAllFieldsFlow(): Flow<List<FormFieldDefinition>>

    @Query("SELECT * FROM form_field_definitions WHERE isActive = 1 ORDER BY displayOrder ASC, id ASC")
    fun getActiveFieldsFlow(): Flow<List<FormFieldDefinition>>

    @Query("SELECT * FROM form_field_definitions WHERE fieldKey = :key LIMIT 1")
    suspend fun getFieldByKey(key: String): FormFieldDefinition?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertField(field: FormFieldDefinition): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultFields(fields: List<FormFieldDefinition>)

    @Update
    suspend fun updateField(field: FormFieldDefinition)

    @Delete
    suspend fun deleteField(field: FormFieldDefinition)

    @Query("DELETE FROM form_field_definitions WHERE id = :id")
    suspend fun deleteFieldById(id: Long)
}
