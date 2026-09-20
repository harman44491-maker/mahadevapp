package com.example

import com.example.data.model.CustomerRecord
import com.example.data.model.FieldType
import com.example.data.model.FormFieldDefinition
import com.example.data.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FieldCollectUnitTest {

    @Test
    fun customerRecord_creationAndDefaultValues() {
        val record = CustomerRecord(
            serialNumber = "FLD-2026-0001",
            name = "Ramesh Kumar",
            fatherName = "Sohan Lal",
            dateOfBirth = "1988-04-12",
            mobileNumber = "9876543210",
            email = "ramesh@example.com",
            relatedWork = "Agriculture / Farming",
            remarks = "Remote village survey verified",
            replyToCustomer = "Application verified and approved for solar irrigation subsidy",
            customFieldsJson = "{\"annual_income\":\"120000\"}"
        )

        assertEquals("FLD-2026-0001", record.serialNumber)
        assertEquals("Ramesh Kumar", record.name)
        assertEquals("Application verified and approved for solar irrigation subsidy", record.replyToCustomer)
        assertEquals(SyncStatus.PENDING_SYNC, record.syncStatus)
        assertNotNull(record.remoteId)
        assertTrue(record.createdAt > 0)

        // Parse custom field json
        val json = JSONObject(record.customFieldsJson)
        assertEquals("120000", json.getString("annual_income"))
    }

    @Test
    fun formFieldDefinition_creationAndOptions() {
        val field = FormFieldDefinition(
            fieldKey = "crop_type",
            label = "Primary Crop Cultivated",
            fieldType = FieldType.DROPDOWN,
            optionsJson = "[\"Wheat\", \"Paddy\", \"Cotton\", \"Mustard\"]",
            isRequired = true
        )

        assertEquals("crop_type", field.fieldKey)
        assertEquals(FieldType.DROPDOWN, field.fieldType)
        assertTrue(field.isRequired)
        assertTrue(field.isActive)
    }
}
