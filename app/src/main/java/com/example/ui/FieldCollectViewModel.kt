package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSender
import com.example.data.model.CustomerRecord
import com.example.data.model.FieldType
import com.example.data.model.FormFieldDefinition
import com.example.data.model.SyncLogEntry
import com.example.data.model.SyncStatus
import com.example.data.repository.FieldDataRepository
import com.example.data.sync.CloudSyncManager
import com.example.data.sync.NetworkMonitor
import com.example.data.sync.SyncResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Locale

enum class AppTab(val title: String) {
    RECORDS("Records"),
    COLLECT("New Entry"),
    BUILDER("Form Builder"),
    LIVE_CHAT("Live Chat"),
    SYNC_HUB("Cloud Sync")
}

enum class SyncFilter {
    ALL,
    PENDING_ONLY,
    SYNCED_ONLY
}

class FieldCollectViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FieldDataRepository

    init {
        val db = AppDatabase.getDatabase(application)
        val networkMonitor = NetworkMonitor(application, viewModelScope)
        val syncManager = CloudSyncManager(
            customerDao = db.customerDao(),
            syncLogDao = db.syncLogDao(),
            networkMonitor = networkMonitor,
            coroutineScope = viewModelScope
        )
        repository = FieldDataRepository(
            customerDao = db.customerDao(),
            formFieldDao = db.formFieldDao(),
            syncLogDao = db.syncLogDao(),
            syncManager = syncManager,
            networkMonitor = networkMonitor
        )
    }

    // Tab Navigation
    private val _currentTab = MutableStateFlow(AppTab.RECORDS)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // Editing State
    private val _editingCustomer = MutableStateFlow<CustomerRecord?>(null)
    val editingCustomer: StateFlow<CustomerRecord?> = _editingCustomer.asStateFlow()

    // User Feedback Messages
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    // Search and Filter State
    val searchQuery = MutableStateFlow("")
    val syncFilter = MutableStateFlow(SyncFilter.ALL)

    // Raw Customers Flow
    val allCustomers: StateFlow<List<CustomerRecord>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Customers Flow
    val filteredCustomers: StateFlow<List<CustomerRecord>> = combine(
        allCustomers,
        searchQuery,
        syncFilter
    ) { customers, query, filter ->
        customers.filter { customer ->
            val matchesQuery = query.isBlank() ||
                    customer.name.contains(query, ignoreCase = true) ||
                    customer.serialNumber.contains(query, ignoreCase = true) ||
                    customer.mobileNumber.contains(query, ignoreCase = true) ||
                    customer.fatherName.contains(query, ignoreCase = true) ||
                    customer.relatedWork.contains(query, ignoreCase = true) ||
                    customer.replyToCustomer.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                SyncFilter.ALL -> true
                SyncFilter.PENDING_ONLY -> customer.syncStatus != SyncStatus.SYNCED
                SyncFilter.SYNCED_ONLY -> customer.syncStatus == SyncStatus.SYNCED
            }

            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Counts
    val totalCount: StateFlow<Int> = repository.totalCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val syncedCount: StateFlow<Int> = repository.syncedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val pendingCount: StateFlow<Int> = repository.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Dynamic Form Fields
    val activeFormFields: StateFlow<List<FormFieldDefinition>> = repository.activeFields
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allFormFields: StateFlow<List<FormFieldDefinition>> = repository.allFields
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cloud Sync State
    val syncLogs: StateFlow<List<SyncLogEntry>> = repository.syncLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val isOnline: StateFlow<Boolean> = repository.isOnline
    val isDeviceConnected: StateFlow<Boolean> = repository.isDeviceConnected
    val isSimulatingOffline: StateFlow<Boolean> = repository.isSimulatingOffline
    val isSyncing: StateFlow<Boolean> = repository.isSyncing
    val lastSyncTime: StateFlow<Long?> = repository.lastSyncTime
    val lastSyncResult: StateFlow<String?> = repository.lastSyncResult

    fun navigateToTab(tab: AppTab) {
        if (tab == AppTab.COLLECT && _currentTab.value != AppTab.COLLECT && _editingCustomer.value != null) {
            // Keep editing customer if already set
        } else if (tab != AppTab.COLLECT) {
            _editingCustomer.value = null
        }
        _currentTab.value = tab
    }

    fun startEditCustomer(customer: CustomerRecord) {
        _editingCustomer.value = customer
        _currentTab.value = AppTab.COLLECT
    }

    fun clearEditingCustomer() {
        _editingCustomer.value = null
    }

    suspend fun getSuggestedSerialNumber(): String {
        return repository.generateNextSerialNumber()
    }

    fun saveCustomer(
        serialNumber: String,
        name: String,
        fatherName: String,
        dob: String,
        mobileNumber: String,
        email: String,
        relatedWork: String,
        remarks: String,
        replyToCustomer: String = "",
        documentsJson: String = "[]",
        customFieldsJson: String
    ) {
        viewModelScope.launch {
            val current = _editingCustomer.value
            if (current != null) {
                val updated = current.copy(
                    serialNumber = serialNumber.trim(),
                    name = name.trim(),
                    fatherName = fatherName.trim(),
                    dateOfBirth = dob.trim(),
                    mobileNumber = mobileNumber.trim(),
                    email = email.trim(),
                    relatedWork = relatedWork.trim(),
                    remarks = remarks.trim(),
                    replyToCustomer = replyToCustomer.trim(),
                    documentsJson = documentsJson,
                    customFieldsJson = customFieldsJson
                )
                repository.updateCustomer(updated)
                _userMessage.emit("Updated record for ${updated.name}")
            } else {
                val newRecord = CustomerRecord(
                    serialNumber = serialNumber.trim(),
                    name = name.trim(),
                    fatherName = fatherName.trim(),
                    dateOfBirth = dob.trim(),
                    mobileNumber = mobileNumber.trim(),
                    email = email.trim(),
                    relatedWork = relatedWork.trim(),
                    remarks = remarks.trim(),
                    replyToCustomer = replyToCustomer.trim(),
                    documentsJson = documentsJson,
                    customFieldsJson = customFieldsJson,
                    syncStatus = SyncStatus.PENDING_SYNC
                )
                repository.saveCustomer(newRecord)
                _userMessage.emit("Customer ${newRecord.name} saved successfully!")
            }
            _editingCustomer.value = null
            _currentTab.value = AppTab.RECORDS
        }
    }

    fun deleteCustomer(customer: CustomerRecord) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
            _userMessage.emit("Record deleted for ${customer.name}")
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            val result: SyncResult = repository.syncNow()
            _userMessage.emit(result.message)
        }
    }

    fun toggleOfflineSimulation(simulateOffline: Boolean) {
        repository.toggleOfflineSimulation(simulateOffline)
        viewModelScope.launch {
            val msg = if (simulateOffline) {
                "Remote Field Offline Simulation: ON. Data saved locally."
            } else {
                "Connected Mode: ON. Syncing pending records to Cloud..."
            }
            _userMessage.emit(msg)
        }
    }

    // Form Builder Actions
    fun createFormField(
        label: String,
        fieldType: FieldType,
        isRequired: Boolean,
        placeholder: String,
        options: List<String>
    ) {
        viewModelScope.launch {
            val key = label.lowercase(Locale.getDefault())
                .replace("[^a-z0-9]".toRegex(), "_")
                .trim('_')
                .ifEmpty { "custom_field_${System.currentTimeMillis()}" }

            val optionsJson = if (fieldType == FieldType.DROPDOWN) {
                val jsonArray = JSONArray()
                options.forEach { opt ->
                    if (opt.isNotBlank()) jsonArray.put(opt.trim())
                }
                jsonArray.toString()
            } else "[]"

            val maxOrder = (allFormFields.value.maxOfOrNull { it.displayOrder } ?: 0) + 1

            val newField = FormFieldDefinition(
                fieldKey = key,
                label = label.trim(),
                fieldType = fieldType,
                optionsJson = optionsJson,
                isRequired = isRequired,
                placeholder = placeholder.trim(),
                displayOrder = maxOrder,
                isActive = true
            )
            repository.saveFormField(newField)
            _userMessage.emit("Custom field '${label}' added to customer form")
        }
    }

    fun toggleFieldStatus(field: FormFieldDefinition) {
        viewModelScope.launch {
            val updated = field.copy(isActive = !field.isActive)
            repository.updateFormField(updated)
            _userMessage.emit("Field '${field.label}' is now ${if (updated.isActive) "active" else "disabled"}")
        }
    }

    fun deleteFormField(field: FormFieldDefinition) {
        viewModelScope.launch {
            repository.deleteFormField(field)
            _userMessage.emit("Removed field '${field.label}'")
        }
    }

    fun clearSyncLogs() {
        viewModelScope.launch {
            repository.clearSyncLogs()
            _userMessage.emit("Sync event audit logs cleared")
        }
    }

    // Live Chat Support State
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = ChatSender.SUPPORT_AGENT,
                senderName = "Mahadev Helpdesk",
                text = "Namaste! Welcome to Mahadev Data Service Live Support. How can we assist you with customer data collection, documents, or cloud sync today?"
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAgentTyping = MutableStateFlow(false)
    val isAgentTyping: StateFlow<Boolean> = _isAgentTyping.asStateFlow()

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        val userMsg = ChatMessage(
            sender = ChatSender.USER,
            senderName = "Field Agent",
            text = text.trim()
        )
        _chatMessages.value = _chatMessages.value + userMsg

        // Intelligent simulated live agent response based on field operations context
        viewModelScope.launch {
            _isAgentTyping.value = true
            delay(1200)
            _isAgentTyping.value = false

            val lower = text.lowercase(Locale.getDefault())
            val replyText = when {
                lower.contains("document") || lower.contains("upload") || lower.contains("file") || lower.contains("proof") -> {
                    "You can attach up to 5 documents (Aadhaar, Land Record, Income Certificate, Photo ID, etc.) directly in the customer form. Uploaded documents are saved offline and sync to the cloud automatically."
                }
                lower.contains("sync") || lower.contains("cloud") || lower.contains("offline") -> {
                    "All records and document references are stored in the local SQLite Room database. Once connectivity is restored, tap 'Cloud Sync' or the top bar Sync icon to push all pending entries."
                }
                lower.contains("reply") || lower.contains("response") -> {
                    "The 'Reply to Customer' field allows you to record official resolutions, follow-up messages, or customer feedback that stays bound to the record."
                }
                lower.contains("serial") || lower.contains("number") -> {
                    "Serial numbers are automatically generated with format FLD-YYYY-XXXX. You can also customize or regenerate them using the shuffle button in the form."
                }
                lower.contains("delete") || lower.contains("remove") -> {
                    "To delete a record, expand the customer card on the Records tab and tap 'Delete'. You will be prompted to confirm."
                }
                lower.contains("hello") || lower.contains("hi") || lower.contains("help") -> {
                    "Hello! Mahadev Live Support is here 24/7 for field officers. You can ask about document uploads, form fields, syncing records, or customer queries."
                }
                else -> {
                    "Thank you for contacting Mahadev Data Service Support. Our field coordination desk has logged your query: \"$text\". A support executive will follow up or you can continue typing here."
                }
            }

            val supportReply = ChatMessage(
                sender = ChatSender.SUPPORT_AGENT,
                senderName = "Mahadev Support",
                text = replyText
            )
            _chatMessages.value = _chatMessages.value + supportReply
        }
    }

    fun clearChatHistory() {
        _chatMessages.value = listOf(
            ChatMessage(
                sender = ChatSender.SUPPORT_AGENT,
                senderName = "Mahadev Helpdesk",
                text = "Chat history cleared. How may we help you today?"
            )
        )
    }
}
