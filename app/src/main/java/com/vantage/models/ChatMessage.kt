package com.vantage.models

data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)
