package com.example.ui.screens

import android.app.DatePickerDialog
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CustomerDocument
import com.example.data.model.CustomerRecord
import com.example.data.model.FieldType
import com.example.data.model.FormFieldDefinition
import com.example.ui.theme.PrimaryTeal
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomerFormScreen(
    editingCustomer: CustomerRecord?,
    activeFields: List<FormFieldDefinition>,
    isOnline: Boolean,
    onSaveCustomer: (
        serialNumber: String,
        name: String,
        fatherName: String,
        dob: String,
        mobile: String,
        email: String,
        relatedWork: String,
        remarks: String,
        replyToCustomer: String,
        documentsJson: String,
        customFieldsJson: String
    ) -> Unit,
    onCancel: () -> Unit,
    onGenerateSerial: suspend () -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Form Field States
    var serialNumber by remember { mutableStateOf(editingCustomer?.serialNumber ?: "") }
    var name by remember { mutableStateOf(editingCustomer?.name ?: "") }
    var fatherName by remember { mutableStateOf(editingCustomer?.fatherName ?: "") }
    var dob by remember { mutableStateOf(editingCustomer?.dateOfBirth ?: "") }
    var mobileNumber by remember { mutableStateOf(editingCustomer?.mobileNumber ?: "") }
    var email by remember { mutableStateOf(editingCustomer?.email ?: "") }
    var relatedWork by remember { mutableStateOf(editingCustomer?.relatedWork ?: "") }
    var remarks by remember { mutableStateOf(editingCustomer?.remarks ?: "") }
    var replyToCustomer by remember { mutableStateOf(editingCustomer?.replyToCustomer ?: "") }

    // Uploaded Documents State (up to 5 documents)
    val uploadedDocuments = remember {
        mutableStateListOf<CustomerDocument>().apply {
            if (editingCustomer != null) {
                addAll(CustomerDocument.parseListFromJson(editingCustomer.documentsJson))
            }
        }
    }
    var showDocTypeDialog by remember { mutableStateOf(false) }
    var pendingDocUri by remember { mutableStateOf<Uri?>(null) }
    var pendingDocName by remember { mutableStateOf("") }
    var pendingDocSize by remember { mutableStateOf(0L) }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "Document_${uploadedDocuments.size + 1}"
            var fileSize = 0L
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                    }
                }
            } catch (e: Exception) {
                // Fallback to default name
            }

            pendingDocUri = uri
            pendingDocName = fileName
            pendingDocSize = fileSize
            showDocTypeDialog = true
        }
    }

    // Dynamic Custom Fields State
    val customFieldValues = remember { mutableStateMapOf<String, String>() }

    // Validation State
    var nameError by remember { mutableStateOf(false) }
    var serialError by remember { mutableStateOf(false) }

    // Initialize custom fields from existing record if editing
    LaunchedEffect(editingCustomer) {
        if (editingCustomer != null) {
            serialNumber = editingCustomer.serialNumber
            name = editingCustomer.name
            fatherName = editingCustomer.fatherName
            dob = editingCustomer.dateOfBirth
            mobileNumber = editingCustomer.mobileNumber
            email = editingCustomer.email
            relatedWork = editingCustomer.relatedWork
            remarks = editingCustomer.remarks
            replyToCustomer = editingCustomer.replyToCustomer

            uploadedDocuments.clear()
            uploadedDocuments.addAll(CustomerDocument.parseListFromJson(editingCustomer.documentsJson))

            customFieldValues.clear()
            if (editingCustomer.customFieldsJson.isNotBlank()) {
                try {
                    val json = JSONObject(editingCustomer.customFieldsJson)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        customFieldValues[key] = json.optString(key, "")
                    }
                } catch (e: Exception) {
                    // Ignore malformed json
                }
            }
        } else if (serialNumber.isBlank()) {
            // Auto generate next serial number for new record
            serialNumber = onGenerateSerial()
        }
    }

    // Common Related Work Options
    val workOptions = listOf(
        "Agriculture / Farming",
        "Daily Wage / Labor",
        "Retail / Shopkeeper",
        "Construction Work",
        "Transport / Driver",
        "Handicrafts / Artisan",
        "Tailoring / Textile",
        "Self-Employed / Services"
    )

    // Date Picker Setup for Date of Birth
    val calendar = Calendar.getInstance()
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formatted = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                dob = formatted
            },
            calendar.get(Calendar.YEAR) - 25,
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Form Title & Subtitle Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryTeal.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PrimaryTeal),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (editingCustomer != null) Icons.Default.Save else Icons.Default.Badge,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (editingCustomer != null) "Edit Customer Record" else "New Customer Registration",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryTeal
                        )
                        Text(
                            text = if (isOnline) "Real-time sync enabled • Cloud Connected" else "Offline-First Mode • Will queue locally in SQLite",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section 1: Core Identification Fields
        item {
            Text(
                text = "1. Customer Identification",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Serial Number
        item {
            OutlinedTextField(
                value = serialNumber,
                onValueChange = {
                    serialNumber = it
                    serialError = it.isBlank()
                },
                label = { Text("Serial Number *") },
                placeholder = { Text("e.g. FLD-2026-0001") },
                leadingIcon = {
                    Icon(Icons.Default.Badge, contentDescription = null, tint = PrimaryTeal)
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                serialNumber = onGenerateSerial()
                            }
                        },
                        modifier = Modifier.testTag("btn_auto_serial")
                    ) {
                        Icon(Icons.Default.Autorenew, contentDescription = "Auto Generate Serial")
                    }
                },
                isError = serialError,
                supportingText = {
                    if (serialError) {
                        Text("Serial number is required", color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Unique identifier for field tracking")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_serial_number"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Customer Name
        item {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = it.isBlank()
                },
                label = { Text("Full Customer Name *") },
                placeholder = { Text("Enter customer name") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryTeal)
                },
                isError = nameError,
                supportingText = {
                    if (nameError) {
                        Text("Customer name is required", color = MaterialTheme.colorScheme.error)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_customer_name"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Father Name
        item {
            OutlinedTextField(
                value = fatherName,
                onValueChange = { fatherName = it },
                label = { Text("Father's / Guardian's Name") },
                placeholder = { Text("Enter father's name") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_father_name"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Date of Birth (DatePicker trigger)
        item {
            OutlinedTextField(
                value = dob,
                onValueChange = { dob = it },
                label = { Text("Date of Birth (YYYY-MM-DD)") },
                placeholder = { Text("Select or enter date of birth") },
                leadingIcon = {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = PrimaryTeal)
                },
                trailingIcon = {
                    IconButton(
                        onClick = { datePickerDialog.show() },
                        modifier = Modifier.testTag("btn_pick_dob")
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_dob"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Section 2: Contact & Demographics
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "2. Contact & Livelihood",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Mobile Number
        item {
            OutlinedTextField(
                value = mobileNumber,
                onValueChange = { mobileNumber = it },
                label = { Text("Mobile Number") },
                placeholder = { Text("e.g. 9876543210") },
                leadingIcon = {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = PrimaryTeal)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_mobile_number"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Email
        item {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                placeholder = { Text("e.g. customer@example.com") },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null, tint = Color.Gray)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_email"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Related Work
        item {
            Column {
                OutlinedTextField(
                    value = relatedWork,
                    onValueChange = { relatedWork = it },
                    label = { Text("Related Work / Occupation") },
                    placeholder = { Text("Select below or type custom occupation") },
                    leadingIcon = {
                        Icon(Icons.Default.BusinessCenter, contentDescription = null, tint = PrimaryTeal)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_related_work"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Suggestion chips for quick selection
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    workOptions.forEach { opt ->
                        val isSelected = relatedWork.equals(opt, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { relatedWork = opt },
                            label = { Text(opt, fontSize = 11.sp) },
                            modifier = Modifier.testTag("chip_work_${opt.take(4)}")
                        )
                    }
                }
            }
        }

        // Remarks
        item {
            OutlinedTextField(
                value = remarks,
                onValueChange = { remarks = it },
                label = { Text("Field Remarks / Observations") },
                placeholder = { Text("Add remote inspection notes, household verification status, etc.") },
                leadingIcon = {
                    Icon(Icons.Default.Notes, contentDescription = null, tint = Color.Gray)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_remarks"),
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Reply to Customer
        item {
            OutlinedTextField(
                value = replyToCustomer,
                onValueChange = { replyToCustomer = it },
                label = { Text("Reply to Customer") },
                placeholder = { Text("Enter official reply, action taken, resolution message, or follow-up note") },
                leadingIcon = {
                    Icon(Icons.Default.Reply, contentDescription = null, tint = PrimaryTeal)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_reply_to_customer"),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Section 2.5: Customer Document Uploads (up to 5 documents)
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("customer_documents_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PrimaryTeal.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = null,
                                    tint = PrimaryTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Customer Documents",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Attach verification proofs (Max 5 documents)",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        // Counter Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (uploadedDocuments.size >= 5) PrimaryTeal else PrimaryTeal.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${uploadedDocuments.size}/5",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uploadedDocuments.size >= 5) Color.White else PrimaryTeal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Document List
                    if (uploadedDocuments.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(vertical = 16.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "No documents attached yet",
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "Upload Aadhaar, Land Record, Voter ID, Ration Card, etc.",
                                    fontSize = 11.sp,
                                    color = Color.Gray.copy(alpha = 0.8f)
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uploadedDocuments.forEachIndexed { index, doc ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("doc_item_$index"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(PrimaryTeal.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Description,
                                                    contentDescription = null,
                                                    tint = PrimaryTeal,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column {
                                                Text(
                                                    text = doc.type,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp,
                                                    color = PrimaryTeal
                                                )
                                                Text(
                                                    text = doc.name,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (doc.sizeBytes > 0) {
                                                    val sizeKb = doc.sizeBytes / 1024
                                                    Text(
                                                        text = "$sizeKb KB",
                                                        fontSize = 10.sp,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = { uploadedDocuments.removeAt(index) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("remove_doc_$index")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove document",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Add Document Button (Disabled when 5 reached)
                    OutlinedButton(
                        onClick = {
                            documentPickerLauncher.launch(
                                arrayOf(
                                    "image/*",
                                    "application/pdf",
                                    "application/msword",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                )
                            )
                        },
                        enabled = uploadedDocuments.size < 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_upload_document"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uploadedDocuments.size < 5) "Upload Customer Document (${5 - uploadedDocuments.size} remaining)" else "Maximum 5 Documents Uploaded",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Section 3: Dynamic Custom Form Builder Fields
        if (activeFields.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "3. Custom Form Builder Fields (${activeFields.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            items(activeFields.size) { index ->
                val fieldDef = activeFields[index]
                DynamicFormFieldRenderer(
                    fieldDef = fieldDef,
                    currentValue = customFieldValues[fieldDef.fieldKey] ?: "",
                    onValueChange = { newValue ->
                        customFieldValues[fieldDef.fieldKey] = newValue
                    }
                )
            }
        }

        // Action Buttons: Save & Cancel
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_cancel_customer")
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val hasErrors = name.isBlank() || serialNumber.isBlank()
                        nameError = name.isBlank()
                        serialError = serialNumber.isBlank()

                        if (!hasErrors) {
                            // Convert dynamic fields to JSON
                            val json = JSONObject()
                            customFieldValues.forEach { (k, v) ->
                                if (v.isNotBlank()) {
                                    json.put(k, v)
                                }
                            }

                            onSaveCustomer(
                                serialNumber,
                                name,
                                fatherName,
                                dob,
                                mobileNumber,
                                email,
                                relatedWork,
                                remarks,
                                replyToCustomer,
                                CustomerDocument.serializeListToJson(uploadedDocuments),
                                json.toString()
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("btn_save_customer")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (editingCustomer != null) "Update Record" else "Save Record",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Document Type Selection Dialog
    if (showDocTypeDialog && pendingDocUri != null) {
        val documentTypes = listOf(
            "Aadhaar Card",
            "PAN Card",
            "Voter ID / EPIC",
            "Ration Card",
            "Land / Revenue Record",
            "Income / Caste Certificate",
            "Bank Passbook Proof",
            "Photo ID / Verification",
            "Other Customer Document"
        )
        var selectedType by remember { mutableStateOf(documentTypes[0]) }

        AlertDialog(
            onDismissRequest = {
                showDocTypeDialog = false
                pendingDocUri = null
            },
            title = {
                Text(
                    text = "Select Document Category",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "File: $pendingDocName",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Choose the type of customer document uploaded:",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        documentTypes.take(6).forEach { docType ->
                            val isSelected = selectedType == docType
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) PrimaryTeal.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedType = docType }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = if (isSelected) PrimaryTeal else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = docType,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) PrimaryTeal else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newDoc = CustomerDocument(
                            name = pendingDocName,
                            type = selectedType,
                            fileUri = pendingDocUri.toString(),
                            sizeBytes = pendingDocSize
                        )
                        uploadedDocuments.add(newDoc)
                        showDocTypeDialog = false
                        pendingDocUri = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Text("Attach Document")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showDocTypeDialog = false
                        pendingDocUri = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DynamicFormFieldRenderer(
    fieldDef: FormFieldDefinition,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    val labelWithRequirement = if (fieldDef.isRequired) "${fieldDef.label} *" else fieldDef.label

    when (fieldDef.fieldType) {
        FieldType.TEXT -> {
            OutlinedTextField(
                value = currentValue,
                onValueChange = onValueChange,
                label = { Text(labelWithRequirement) },
                placeholder = { Text(fieldDef.placeholder.ifBlank { "Enter ${fieldDef.label}" }) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_field_${fieldDef.fieldKey}"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        FieldType.NUMBER -> {
            OutlinedTextField(
                value = currentValue,
                onValueChange = onValueChange,
                label = { Text(labelWithRequirement) },
                placeholder = { Text(fieldDef.placeholder.ifBlank { "Enter number" }) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_field_${fieldDef.fieldKey}"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        FieldType.DATE -> {
            val context = LocalContext.current
            val cal = Calendar.getInstance()
            val picker = remember {
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        val formatted = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day)
                        onValueChange(formatted)
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                )
            }

            OutlinedTextField(
                value = currentValue,
                onValueChange = onValueChange,
                label = { Text(labelWithRequirement) },
                placeholder = { Text("YYYY-MM-DD") },
                trailingIcon = {
                    IconButton(onClick = { picker.show() }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_field_${fieldDef.fieldKey}"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        FieldType.DROPDOWN -> {
            var expanded by remember { mutableStateOf(false) }
            val options = remember(fieldDef.optionsJson) {
                try {
                    val array = JSONArray(fieldDef.optionsJson)
                    List(array.length()) { array.getString(it) }
                } catch (e: Exception) {
                    emptyList()
                }
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = currentValue,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(labelWithRequirement) },
                    placeholder = { Text("Select option") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .testTag("custom_field_${fieldDef.fieldKey}"),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onValueChange(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        FieldType.CHECKBOX -> {
            val isChecked = currentValue.equals("true", ignoreCase = true) || currentValue == "Yes"

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .clickable { onValueChange(if (!isChecked) "Yes" else "No") }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = labelWithRequirement,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = isChecked,
                    onCheckedChange = { checked ->
                        onValueChange(if (checked) "Yes" else "No")
                    },
                    modifier = Modifier.testTag("custom_field_${fieldDef.fieldKey}")
                )
            }
        }
    }
}
