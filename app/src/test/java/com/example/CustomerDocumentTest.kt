package com.example

import com.example.data.model.CustomerDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CustomerDocumentTest {

    @Test
    fun testSerializationAndDeserialization() {
        val docs = listOf(
            CustomerDocument(
                id = "doc-1",
                name = "aadhaar_card.pdf",
                type = "Aadhaar Card",
                fileUri = "content://media/external/files/123",
                sizeBytes = 204800L,
                dateAdded = 1690000000000L
            ),
            CustomerDocument(
                id = "doc-2",
                name = "land_record.jpg",
                type = "Land / Revenue Record",
                fileUri = "content://media/external/images/456",
                sizeBytes = 512000L,
                dateAdded = 1690000050000L
            )
        )

        val jsonString = CustomerDocument.serializeListToJson(docs)
        assertTrue("JSON should contain aadhaar_card.pdf, but was: $jsonString", jsonString.contains("aadhaar_card.pdf"))
        assertTrue("JSON should contain Land, but was: $jsonString", jsonString.contains("Land"))

        val parsedDocs = CustomerDocument.parseListFromJson(jsonString)
        assertEquals(2, parsedDocs.size)
        assertEquals("doc-1", parsedDocs[0].id)
        assertEquals("aadhaar_card.pdf", parsedDocs[0].name)
        assertEquals("Aadhaar Card", parsedDocs[0].type)
        assertEquals(204800L, parsedDocs[0].sizeBytes)

        assertEquals("doc-2", parsedDocs[1].id)
        assertEquals("land_record.jpg", parsedDocs[1].name)
    }

    @Test
    fun testEmptyAndMalformedJsonGracefulHandling() {
        val emptyDocs = CustomerDocument.parseListFromJson("")
        assertTrue(emptyDocs.isEmpty())

        val invalidDocs = CustomerDocument.parseListFromJson("invalid-json")
        assertTrue(invalidDocs.isEmpty())

        val emptyArrayDocs = CustomerDocument.parseListFromJson("[]")
        assertTrue(emptyArrayDocs.isEmpty())
    }
}
