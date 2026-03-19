package com.gigachat.cli.history

import com.gigachat.cli.api.Message
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class HistoryManager(private val historyDir: File) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    fun saveSession(session: Session): Boolean {
        return try {
            val filename = "${session.timestamp}.json"
            val file = File(historyDir, filename)
            val content = json.encodeToString(session)
            file.writeText(content)
            true
        } catch (e: Exception) {
            println("[Ошибка сохранения истории] ${e.message}")
            false
        }
    }

    fun getSavedSessions(): List<String> {
        return try {
            historyDir.listFiles { file -> file.extension == "json" }
                ?.map { it.nameWithoutExtension }
                ?.sortedDescending()
                ?: emptyList()
        } catch (e: Exception) {
            println("[Ошибка чтения истории] ${e.message}")
            emptyList()
        }
    }

    fun loadSession(timestamp: String): Session? {
        return try {
            val file = File(historyDir, "$timestamp.json")
            if (!file.exists()) {
                return null
            }
            val content = file.readText()
            json.decodeFromString<Session>(content)
        } catch (e: Exception) {
            println("[Ошибка загрузки сессии] ${e.message}")
            null
        }
    }

    fun saveCurrentSession(
        messages: List<Message>,
        model: String,
        temperature: Float,
        maxTokens: Int,
        systemPrompt: String
    ): String {
        val timestamp = LocalDateTime.now().format(timestampFormatter)
        val metadata = SessionMetadata(
            model = model,
            temperature = temperature,
            max_tokens = maxTokens,
            system_prompt = systemPrompt
        )
        val session = Session(
            timestamp = timestamp,
            messages = messages,
            metadata = metadata
        )
        saveSession(session)
        return timestamp
    }

    fun exportSessionToText(messages: List<Message>, outputPath: String? = null): Boolean {
        return try {
            val timestamp = LocalDateTime.now().format(timestampFormatter)
            val defaultPath = File(System.getProperty("user.home"), "gigachat-$timestamp.txt")
            val file = if (outputPath != null) File(outputPath) else defaultPath

            val content = buildString {
                appendLine("=== GigaChat Диалог ===")
                appendLine("Время: $timestamp")
                appendLine("=" .repeat(50))
                appendLine()

                messages.forEach { message ->
                    when (message.role) {
                        "system" -> {
                            appendLine("[SYSTEM]")
                            appendLine(message.content)
                        }
                        "user" -> {
                            appendLine("You>")
                            appendLine(message.content)
                        }
                        "assistant" -> {
                            appendLine("GigaChat>")
                            appendLine(message.content)
                        }
                    }
                    appendLine()
                    appendLine("-".repeat(50))
                    appendLine()
                }
            }

            file.writeText(content)
            println("[Диалог сохранён в ${file.absolutePath}]")
            true
        } catch (e: Exception) {
            println("[Ошибка экспорта] ${e.message}")
            false
        }
    }
}
