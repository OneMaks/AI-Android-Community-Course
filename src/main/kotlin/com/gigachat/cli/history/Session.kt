package com.gigachat.cli.history

import com.gigachat.cli.api.Message
import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val timestamp: String,
    val messages: List<Message>,
    val metadata: SessionMetadata? = null
)

@Serializable
data class SessionMetadata(
    val model: String,
    val temperature: Float,
    val max_tokens: Int,
    val system_prompt: String
)
