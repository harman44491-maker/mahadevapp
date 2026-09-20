package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FieldType
import com.example.data.model.FormFieldDefinition
import com.example.ui.theme.PrimaryTeal
import org.json.JSONArray

@Composable
fun FormBuilderScreen(
    formFields: List<FormFieldDefinition>,
    onCreateField: (
        label: String,
        type: FieldType,
        isRequired: Boolean,
        placeholder: String,
        options: List<String>
    ) -> Unit,
    onToggleActive: (FormFieldDefinition) -> Unit,
    onDeleteField: (FormFieldDefinition) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddFieldDialog by remember { mutableStateOf(false) }
    var fieldToDelete by remember { mutableStateOf<FormFieldDefinition?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Header Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryTeal.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PrimaryTeal),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Custom Form Builder",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryTeal
                                )
                                Text(
                                    text = "Configure dynamic survey fields for remote agents",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = { showAddFieldDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                            modifier = Modifier.testTag("btn_open_add_field_dialog")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Field", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Standard Mandatory Fields Overview Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Standard Customer Profile Fields (Built-in)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Serial Number • Customer Name • Father's Name • Date of Birth • Mobile Number • Email Address • Related Work • Field Remarks",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Section: Custom Dynamic Fields
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dynamic Custom Fields (${formFields.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Active in registration form",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }

        if (formFields.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Custom Fields Configured",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Click 'Add Field' above to customize the survey questionnaire with numbers, dropdowns, dates, or checkboxes.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(formFields, key = { it.id }) { field ->
                FormFieldCardItem(
                    field = field,
                    onToggleActive = { onToggleActive(field) },
                    onDelete = { fieldToDelete = field }
                )
            }
        }
    }

    // Add Field Dialog
    if (showAddFieldDialog) {
        AddFieldDialog(
            onDismiss = { showAddFieldDialog = false },
            onConfirm = { label, type, isRequired, placeholder, options ->
                onCreateField(label, type, isRequired, placeholder, options)
                showAddFieldDialog = false
            }
        )
    }

    // Delete Field Dialog
    fieldToDelete?.let { field ->
        AlertDialog(
            onDismissRequest = { fieldToDelete = null },
            title = { Text("Delete Custom Field?") },
            text = {
                Text("Are you sure you want to remove '${field.label}'? It will no longer be collected on new customer forms.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteField(field)
                        fieldToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_field_btn")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { fieldToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FormFieldCardItem(
    field: FormFieldDefinition,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("field_card_${field.fieldKey}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val (icon, tint) = when (field.fieldType) {
                    FieldType.TEXT -> Pair(Icons.Default.TextFields, PrimaryTeal)
                    FieldType.NUMBER -> Pair(Icons.Default.Numbers, Color(0xFF2563EB))
                    FieldType.DATE -> Pair(Icons.Default.CalendarMonth, Color(0xFF7C3AED))
                    FieldType.DROPDOWN -> Pair(Icons.Default.FormatListBulleted, Color(0xFFD97706))
                    FieldType.CHECKBOX -> Pair(Icons.Default.CheckBox, Color(0xFF059669))
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = field.label,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (field.isActive) MaterialTheme.colorScheme.onSurface else Color.Gray
                        )
                        if (field.isRequired) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "*", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${field.fieldType.displayName} • Key: ${field.fieldKey}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = field.isActive,
                    onCheckedChange = { onToggleActive() },
                    modifier = Modifier.testTag("toggle_active_${field.fieldKey}")
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_field_${field.fieldKey}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete field",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddFieldDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        label: String,
        type: FieldType,
        isRequired: Boolean,
        placeholder: String,
        options: List<String>
    ) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(FieldType.TEXT) }
    var isRequired by remember { mutableStateOf(false) }
    var placeholder by remember { mutableStateOf("") }
    var optionInput by remember { mutableStateOf("") }
    val optionsList = remember { mutableStateListOf<String>() }

    var labelError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Add Custom Form Field", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = label,
                        onValueChange = {
                            label = it
                            labelError = it.isBlank()
                        },
                        label = { Text("Field Label *") },
                        placeholder = { Text("e.g. Land Area, ID Card No, Crop Type") },
                        isError = labelError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_field_label_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                item {
                    Text(
                        text = "Field Type",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FieldType.values().take(3).forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type.displayName, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FieldType.values().drop(3).forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type.displayName, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // If dropdown, allow adding choices
                if (selectedType == FieldType.DROPDOWN) {
                    item {
                        Column {
                            Text(
                                text = "Dropdown Options",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = optionInput,
                                    onValueChange = { optionInput = it },
                                    label = { Text("New Option") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        if (optionInput.isNotBlank()) {
                                            optionsList.add(optionInput.trim())
                                            optionInput = ""
                                        }
                                    },
                                    modifier = Modifier.testTag("btn_add_dropdown_option")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Option", tint = PrimaryTeal)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            optionsList.forEachIndexed { idx, opt ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "• $opt", fontSize = 12.sp)
                                    IconButton(
                                        onClick = { optionsList.removeAt(idx) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = placeholder,
                        onValueChange = { placeholder = it },
                        label = { Text("Helper / Placeholder Text") },
                        placeholder = { Text("Optional hint for field operator") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Mandatory Field (Required)", fontSize = 13.sp)
                        Switch(
                            checked = isRequired,
                            onCheckedChange = { isRequired = it },
                            modifier = Modifier.testTag("switch_is_required")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (label.isBlank()) {
                        labelError = true
                    } else {
                        onConfirm(
                            label,
                            selectedType,
                            isRequired,
                            placeholder,
                            optionsList.toList()
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                modifier = Modifier.testTag("confirm_create_field_btn")
            ) {
                Text("Add Field")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
