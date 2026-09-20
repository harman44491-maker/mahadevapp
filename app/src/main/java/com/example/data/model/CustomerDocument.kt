package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a document or attachment associated with a customer record.
 * Supports up to 5 documents per customer.
 */
data class CustomerDocument(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val type: String, // e.g. "Aadhaar Card", "Land Record", "Income Certificate", "Passport", "Voter ID", "Other"
    val fileUri: String = "",
    val sizeBytes: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("type", type)
            put("fileUri", fileUri)
            put("sizeBytes", sizeBytes)
            put("dateAdded", dateAdded)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): CustomerDocument {
            return CustomerDocument(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                name = json.optString("name", "Document"),
                type = json.optString("type", "General Document"),
                fileUri = json.optString("fileUri", ""),
                sizeBytes = json.optLong("sizeBytes", 0L),
                dateAdded = json.optLong("dateAdded", System.currentTimeMillis())
            )
        }

        fun parseListFromJson(jsonString: String): List<CustomerDocument> {
            if (jsonString.isBlank() || jsonString == "[]") return emptyList()
            return try {
                val array = JSONArray(jsonString)
                val list = mutableListOf<CustomerDocument>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i)
                    if (obj != null) {
                        list.add(fromJson(obj))
                    }
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun listToJsonString(documents: List<CustomerDocument>): String {
            val array = JSONArray()
            documents.forEach { doc ->
                array.put(doc.toJson())
            }
            return array.toString()
        }

        fun serializeListToJson(documents: List<CustomerDocument>): String {
            return listToJsonString(documents)
        }
    }
}
