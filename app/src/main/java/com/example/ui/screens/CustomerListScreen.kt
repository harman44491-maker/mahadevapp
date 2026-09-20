package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CustomerDocument
import com.example.data.model.CustomerRecord
import com.example.data.model.FormFieldDefinition
import com.example.ui.SyncFilter
import com.example.ui.components.SyncNotificationBanner
import com.example.ui.components.SyncStatusBadge
import com.example.ui.theme.PrimaryTeal
import com.example.ui.theme.StatusPendingAmber
import com.example.ui.theme.StatusSyncedGreen
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CustomerListScreen(
    customers: List<CustomerRecord>,
    allFields: List<FormFieldDefinition>,
    totalCount: Int,
    syncedCount: Int,
    pendingCount: Int,
    isOnline: Boolean,
    isSyncing: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    syncFilter: SyncFilter,
    onFilterChange: (SyncFilter) -> Unit,
    onAddNewClick: () -> Unit,
    onEditClick: (CustomerRecord) -> Unit,
    onDeleteClick: (CustomerRecord) -> Unit,
    onSyncNowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var customerToDelete by remember { mutableStateOf<CustomerRecord?>(null) }
    var expandedCustomerId by remember { mutableStateOf<Long?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // Offline / Pending Notification Banner
            item {
                SyncNotificationBanner(
                    pendingCount = pendingCount,
                    isOnline = isOnline,
                    isSyncing = isSyncing,
                    onSyncClick = onSyncNowClick
                )
            }

            // Stats Summary Cards Row
            item {
                StatsHeaderSection(
                    totalCount = totalCount,
                    syncedCount = syncedCount,
                    pendingCount = pendingCount
                )
            }

            // Search Bar & Filter Chips
            item {
                SearchAndFilterSection(
                    query = searchQuery,
                    onQueryChange = onSearchChange,
                    activeFilter = syncFilter,
                    onFilterChange = onFilterChange
                )
            }

            // Customer Records List
            if (customers.isEmpty()) {
                item {
                    EmptyCustomerState(
                        hasQuery = searchQuery.isNotBlank() || syncFilter != SyncFilter.ALL,
                        onAddNew = onAddNewClick
                    )
                }
            } else {
                items(customers, key = { it.id }) { customer ->
                    CustomerCardItem(
                        customer = customer,
                        formFields = allFields,
                        isExpanded = expandedCustomerId == customer.id,
                        onToggleExpand = {
                            expandedCustomerId = if (expandedCustomerId == customer.id) null else customer.id
                        },
                        onEdit = { onEditClick(customer) },
                        onDelete = { customerToDelete = customer }
                    )
                }
            }
        }

        // Floating Action Button to Add Customer
        FloatingActionButton(
            onClick = onAddNewClick,
            containerColor = PrimaryTeal,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("fab_add_customer")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Customer")
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "New Customer", fontWeight = FontWeight.Bold)
            }
        }
    }

    // Delete Confirmation Dialog
    customerToDelete?.let { customer ->
        AlertDialog(
            onDismissRequest = { customerToDelete = null },
            title = { Text(text = "Delete Record?") },
            text = {
                Text(
                    text = "Are you sure you want to remove customer #${customer.serialNumber} (${customer.name})? This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick(customer)
                        customerToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { customerToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatsHeaderSection(
    totalCount: Int,
    syncedCount: Int,
    pendingCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(
            title = "Collected",
            count = totalCount.toString(),
            icon = Icons.Default.Group,
            iconTint = PrimaryTeal,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Synced Cloud",
            count = syncedCount.toString(),
            icon = Icons.Default.CloudDone,
            iconTint = StatusSyncedGreen,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Pending Sync",
            count = pendingCount.toString(),
            icon = Icons.Default.CloudUpload,
            iconTint = StatusPendingAmber,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = count,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SearchAndFilterSection(
    query: String,
    onQueryChange: (String) -> Unit,
    activeFilter: SyncFilter,
    onFilterChange: (SyncFilter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("customer_search_input"),
            placeholder = { Text("Search by name, serial #, phone...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = PrimaryTeal)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = activeFilter == SyncFilter.ALL,
                onClick = { onFilterChange(SyncFilter.ALL) },
                label = { Text("All Records") },
                modifier = Modifier.testTag("filter_all")
            )
            FilterChip(
                selected = activeFilter == SyncFilter.PENDING_ONLY,
                onClick = { onFilterChange(SyncFilter.PENDING_ONLY) },
                label = { Text("Pending Sync") },
                modifier = Modifier.testTag("filter_pending")
            )
            FilterChip(
                selected = activeFilter == SyncFilter.SYNCED_ONLY,
                onClick = { onFilterChange(SyncFilter.SYNCED_ONLY) },
                label = { Text("Cloud Synced") },
                modifier = Modifier.testTag("filter_synced")
            )
        }
    }
}

@Composable
private fun CustomerCardItem(
    customer: CustomerRecord,
    formFields: List<FormFieldDefinition>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onToggleExpand)
            .testTag("customer_card_${customer.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Serial Number Pill + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrimaryTeal.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = customer.serialNumber.ifBlank { "ID #${customer.id}" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = PrimaryTeal
                    )
                }

                SyncStatusBadge(status = customer.syncStatus)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Customer Name
            Text(
                text = customer.name,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Sub-details: Father Name & Mobile
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "S/O: ${customer.fatherName.ifBlank { "N/A" }}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = customer.mobileNumber.ifBlank { "No phone" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Related Work Badge
            if (customer.relatedWork.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BusinessCenter,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Work: ${customer.relatedWork}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick preview of Reply to Customer if available
            if (customer.replyToCustomer.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(PrimaryTeal.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Reply,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Reply: ${customer.replyToCustomer}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryTeal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Expand / Collapse Chevron Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand details",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expanded Details Section
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    DetailItemRow(label = "Date of Birth", value = customer.dateOfBirth.ifBlank { "Not provided" })
                    DetailItemRow(label = "Email Address", value = customer.email.ifBlank { "Not provided" })
                    DetailItemRow(label = "Remote Field Remarks", value = customer.remarks.ifBlank { "None" })
                    DetailItemRow(label = "Reply to Customer", value = customer.replyToCustomer.ifBlank { "None" })

                    // Render Uploaded Documents if present
                    val customerDocs = CustomerDocument.parseListFromJson(customer.documentsJson)
                    if (customerDocs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = PrimaryTeal,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Uploaded Documents (${customerDocs.size}/5):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryTeal
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            customerDocs.forEach { doc ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = PrimaryTeal,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${doc.type}:",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            color = PrimaryTeal
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = doc.name,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Render Custom Form Builder Fields if present
                    val customFieldsMap = parseCustomJson(customer.customFieldsJson)
                    if (customFieldsMap.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Custom Form Fields:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryTeal
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        customFieldsMap.forEach { (key, value) ->
                            val matchingDef = formFields.find { it.fieldKey == key }
                            val label = matchingDef?.label ?: key.replace("_", " ").replaceFirstChar { it.uppercase() }
                            DetailItemRow(label = label, value = value)
                        }
                    }

                    // Metadata
                    Spacer(modifier = Modifier.height(6.dp))
                    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(customer.createdAt))
                    Text(
                        text = "Collected on: $dateStr",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )

                    // Card Action Buttons (Edit / Delete)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.testTag("delete_customer_${customer.id}")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier.testTag("edit_customer_${customer.id}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Form", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItemRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.55f)
        )
    }
}

@Composable
private fun EmptyCustomerState(
    hasQuery: Boolean,
    onAddNew: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 24.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(PrimaryTeal.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = null,
                tint = PrimaryTeal,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (hasQuery) "No matching customers found" else "No Customer Records Yet",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasQuery) "Try adjusting your search terms or filters" else "Collect customer information offline during remote field operations and sync anytime.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = onAddNew,
            modifier = Modifier.testTag("empty_state_add_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Create First Customer Record")
        }
    }
}

private fun parseCustomJson(jsonStr: String): Map<String, String> {
    if (jsonStr.isBlank() || jsonStr == "{}") return emptyMap()
    return try {
        val jsonObj = JSONObject(jsonStr)
        val result = mutableMapOf<String, String>()
        val keys = jsonObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = jsonObj.optString(key, "")
        }
        result
    } catch (e: Exception) {
        emptyMap()
    }
}
