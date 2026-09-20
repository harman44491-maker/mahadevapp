package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.AppTab
import com.example.ui.FieldCollectViewModel
import com.example.ui.components.NetworkStatusPill
import com.example.ui.theme.PrimaryTeal
import com.example.ui.theme.StatusPendingAmber
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: FieldCollectViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isDeviceConnected by viewModel.isDeviceConnected.collectAsStateWithLifecycle()
    val isSimulatingOffline by viewModel.isSimulatingOffline.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val syncedCount by viewModel.syncedCount.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
    val lastSyncResult by viewModel.lastSyncResult.collectAsStateWithLifecycle()

    val filteredCustomers by viewModel.filteredCustomers.collectAsStateWithLifecycle()
    val allFields by viewModel.allFormFields.collectAsStateWithLifecycle()
    val activeFields by viewModel.activeFormFields.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val syncFilter by viewModel.syncFilter.collectAsStateWithLifecycle()
    val editingCustomer by viewModel.editingCustomer.collectAsStateWithLifecycle()

    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isAgentTyping by viewModel.isAgentTyping.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Listen for user messages to show in snackbar
    LaunchedEffect(Unit) {
        viewModel.userMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_field_logo),
                            contentDescription = "App Icon",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Mahadev Data Service",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = PrimaryTeal
                        )
                    }
                },
                actions = {
                    NetworkStatusPill(
                        isOnline = isOnline,
                        isSimulatingOffline = isSimulatingOffline,
                        modifier = Modifier.testTag("network_status_pill")
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = { viewModel.triggerSync() },
                        enabled = !isSyncing && isOnline,
                        modifier = Modifier.testTag("topbar_sync_btn")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryTeal
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync Cloud",
                                tint = if (isOnline) PrimaryTeal else Color.Gray
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                // Records Tab
                NavigationBarItem(
                    selected = currentTab == AppTab.RECORDS,
                    onClick = { viewModel.navigateToTab(AppTab.RECORDS) },
                    icon = {
                        Icon(Icons.Default.ListAlt, contentDescription = "Records")
                    },
                    label = { Text("Records", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryTeal,
                        selectedTextColor = PrimaryTeal,
                        indicatorColor = PrimaryTeal.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_tab_records")
                )

                // New Entry Tab
                NavigationBarItem(
                    selected = currentTab == AppTab.COLLECT,
                    onClick = {
                        viewModel.clearEditingCustomer()
                        viewModel.navigateToTab(AppTab.COLLECT)
                    },
                    icon = {
                        Icon(Icons.Default.AddCircle, contentDescription = "New Entry")
                    },
                    label = { Text(if (editingCustomer != null) "Editing" else "New Entry", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryTeal,
                        selectedTextColor = PrimaryTeal,
                        indicatorColor = PrimaryTeal.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_tab_collect")
                )

                // Custom Form Builder Tab
                NavigationBarItem(
                    selected = currentTab == AppTab.BUILDER,
                    onClick = { viewModel.navigateToTab(AppTab.BUILDER) },
                    icon = {
                        Icon(Icons.Default.Tune, contentDescription = "Form Builder")
                    },
                    label = { Text("Builder", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryTeal,
                        selectedTextColor = PrimaryTeal,
                        indicatorColor = PrimaryTeal.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_tab_builder")
                )

                // Live Chat Support Tab
                NavigationBarItem(
                    selected = currentTab == AppTab.LIVE_CHAT,
                    onClick = { viewModel.navigateToTab(AppTab.LIVE_CHAT) },
                    icon = {
                        Icon(Icons.Default.SupportAgent, contentDescription = "Live Chat")
                    },
                    label = { Text("Live Chat", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryTeal,
                        selectedTextColor = PrimaryTeal,
                        indicatorColor = PrimaryTeal.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_tab_chat")
                )

                // Cloud Sync Tab with Pending Badge
                NavigationBarItem(
                    selected = currentTab == AppTab.SYNC_HUB,
                    onClick = { viewModel.navigateToTab(AppTab.SYNC_HUB) },
                    icon = {
                        if (pendingCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = StatusPendingAmber,
                                        contentColor = Color.White
                                    ) {
                                        Text(text = pendingCount.toString())
                                    }
                                }
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = "Cloud Sync")
                            }
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = "Cloud Sync")
                        }
                    },
                    label = { Text("Cloud Sync", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryTeal,
                        selectedTextColor = PrimaryTeal,
                        indicatorColor = PrimaryTeal.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_tab_sync")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.RECORDS -> CustomerListScreen(
                    customers = filteredCustomers,
                    allFields = allFields,
                    totalCount = totalCount,
                    syncedCount = syncedCount,
                    pendingCount = pendingCount,
                    isOnline = isOnline,
                    isSyncing = isSyncing,
                    searchQuery = searchQuery,
                    onSearchChange = { viewModel.searchQuery.value = it },
                    syncFilter = syncFilter,
                    onFilterChange = { viewModel.syncFilter.value = it },
                    onAddNewClick = {
                        viewModel.clearEditingCustomer()
                        viewModel.navigateToTab(AppTab.COLLECT)
                    },
                    onEditClick = { customer ->
                        viewModel.startEditCustomer(customer)
                    },
                    onDeleteClick = { customer ->
                        viewModel.deleteCustomer(customer)
                    },
                    onSyncNowClick = { viewModel.triggerSync() }
                )

                AppTab.COLLECT -> CustomerFormScreen(
                    editingCustomer = editingCustomer,
                    activeFields = activeFields,
                    isOnline = isOnline,
                    onSaveCustomer = { serial, name, father, dob, mobile, email, work, remarks, reply, docsJson, customJson ->
                        viewModel.saveCustomer(
                            serialNumber = serial,
                            name = name,
                            fatherName = father,
                            dob = dob,
                            mobileNumber = mobile,
                            email = email,
                            relatedWork = work,
                            remarks = remarks,
                            replyToCustomer = reply,
                            documentsJson = docsJson,
                            customFieldsJson = customJson
                        )
                    },
                    onCancel = {
                        viewModel.clearEditingCustomer()
                        viewModel.navigateToTab(AppTab.RECORDS)
                    },
                    onGenerateSerial = { viewModel.getSuggestedSerialNumber() }
                )

                AppTab.BUILDER -> FormBuilderScreen(
                    formFields = allFields,
                    onCreateField = { label, type, isRequired, placeholder, options ->
                        viewModel.createFormField(
                            label = label,
                            fieldType = type,
                            isRequired = isRequired,
                            placeholder = placeholder,
                            options = options
                        )
                    },
                    onToggleActive = { field ->
                        viewModel.toggleFieldStatus(field)
                    },
                    onDeleteField = { field ->
                        viewModel.deleteFormField(field)
                    }
                )

                AppTab.LIVE_CHAT -> LiveChatScreen(
                    messages = chatMessages,
                    isAgentTyping = isAgentTyping,
                    isOnline = isOnline,
                    onSendMessage = { viewModel.sendChatMessage(it) },
                    onClearChat = { viewModel.clearChatHistory() }
                )

                AppTab.SYNC_HUB -> CloudSyncScreen(
                    isOnline = isOnline,
                    isDeviceConnected = isDeviceConnected,
                    isSimulatingOffline = isSimulatingOffline,
                    isSyncing = isSyncing,
                    pendingCount = pendingCount,
                    syncedCount = syncedCount,
                    lastSyncTime = lastSyncTime,
                    lastSyncResult = lastSyncResult,
                    syncLogs = syncLogs,
                    onTriggerSync = { viewModel.triggerSync() },
                    onToggleOfflineSimulation = { viewModel.toggleOfflineSimulation(it) },
                    onClearLogs = { viewModel.clearSyncLogs() }
                )
            }
        }
    }
}
