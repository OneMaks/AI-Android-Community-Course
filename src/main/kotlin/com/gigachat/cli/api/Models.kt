package com.gigachat.cli.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class Message(
    val role: String,
    val content: String = "",
    val function_call: FunctionCall? = null,
    val name: String? = null
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: JsonElement
)

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Float,
    val max_tokens: Int,
    val stream: Boolean = false,
    val functions: List<FunctionDefinition>? = null
)

@Serializable
data class ChatResponse(
    val choices: List<Choice>,
    val created: Long? = null,
    val model: String? = null,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val message: Message,
    val index: Int = 0,
    val finish_reason: String? = null
)

@Serializable
data class Usage(
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null,
    val total_tokens: Int? = null
)

@Serializable
data class TokenResponse(
    val access_token: String,
    val expires_at: Long
)

@Serializable
data class ErrorResponse(
    val error: ErrorDetail? = null,
    val message: String? = null
)

@Serializable
data class ErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

@Serializable
data class JsonStructuredResponse(
    val subject: String,
    val data: String,
    val date: String,
    val tags: List<String>
)
