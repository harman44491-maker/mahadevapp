package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "form_field_definitions")
data class FormFieldDefinition(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fieldKey: String,
    val label: String,
    val fieldType: FieldType,
    val optionsJson: String = "[]", // JSON array of string options for DROPDOWN
    val isRequired: Boolean = false,
    val placeholder: String = "",
    val displayOrder: Int = 0,
    val isActive: Boolean = true
)
