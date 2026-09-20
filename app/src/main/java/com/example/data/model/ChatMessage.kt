package com.example.data.model

import java.util.UUID

enum class ChatSender {
    USER,
    SUPPORT_AGENT,
    SYSTEM
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: ChatSender,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isDelivered: Boolean = true
)
