package com.gigachat.cli.config

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val credentials_key: String = "",
    val model: String = "GigaChat",
    val temperature: Float = 0.7f,
    val max_tokens: Int = 1024,
    val system_prompt: String = "",
    val scope: String = "GIGACHAT_API_PERS",
    val verify_ssl: Boolean = true
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (credentials_key.isBlank()) {
            errors.add("credentials_key обязателен")
        }

        if (model !in listOf("GigaChat", "GigaChat-Pro", "GigaChat-Max")) {
            errors.add("model должна быть одной из: GigaChat, GigaChat-Pro, GigaChat-Max")
        }

        if (temperature !in 0.0f..2.0f) {
            errors.add("temperature должна быть от 0.0 до 2.0")
        }

        if (max_tokens !in 1..32768) {
            errors.add("max_tokens должен быть от 1 до 32768")
        }

        return errors
    }

    fun maskCredentials(): String {
        return copy(credentials_key = "***").toString()
    }
}
