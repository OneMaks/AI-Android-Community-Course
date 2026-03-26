package com.gigachat.cli

import com.gigachat.cli.api.FunctionDefinition
import com.gigachat.cli.api.GigaChatClient
import com.gigachat.cli.api.JsonStructuredResponse
import com.gigachat.cli.api.Message
import com.gigachat.cli.commands.CommandHandler
import com.gigachat.cli.commands.CommandResult
import com.gigachat.cli.commands.SessionConfig
import com.gigachat.cli.config.ConfigManager
import com.gigachat.cli.history.HistoryManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun main() = runBlocking {
    val configManager = ConfigManager()
    var appConfig = configManager.loadConfig()

    if (appConfig == null) {
        appConfig = configManager.initializeConfig()
        if (appConfig == null) {
            println("[Ошибка: не удалось инициализировать конфигурацию]")
            return@runBlocking
        }
    }

    val errors = appConfig.validate()
    if (errors.isNotEmpty()) {
        println("[Ошибки конфигурации:]")
        errors.forEach { println("  - $it") }
        return@runBlocking
    }

    val historyManager = HistoryManager(configManager.getHistoryDir())
    val sessionConfig = SessionConfig.fromAppConfig(appConfig)
    val commandHandler = CommandHandler(configManager, historyManager, sessionConfig)
    val client = GigaChatClient(appConfig)

    val messages = mutableListOf<Message>()

    println("\nGigaChat CLI — модель: ${appConfig.model} | /help для справки\n")

    var running = true

    while (running) {
        print("You> ")
        val input = readlnOrNull()?.trim()

        if (input.isNullOrBlank()) {
            continue
        }

        val commandResult = commandHandler.handleCommand(input, messages)

        when (commandResult) {
            is CommandResult.NotACommand -> {
                messages.add(Message(role = "user", content = input))

                val currentConfig = commandHandler.getSessionConfig()

                val messagesToSend = buildList {
                    val systemPrompt = buildString {
                        if (currentConfig.systemPrompt.isNotBlank()) {
                            append(currentConfig.systemPrompt)
                        }
                        if (currentConfig.jsonMode) {
                            if (isNotEmpty()) append("\n\n")
                            append(
                                "Отвечай ТОЛЬКО валидным JSON без markdown-блоков и без лишнего текста. " +
                                "Используй строго эти поля: " +
                                "\"subject\" (строка, тема сообщения), " +
                                "\"data\" (строка, развёрнутый ответ на запрос пользователя), " +
                                "\"date\" (строка, текущая дата в формате ISO 8601), " +
                                "\"tags\" (массив строк, список тегов по теме)."
                            )
                        }
                    }
                    if (systemPrompt.isNotBlank()) {
                        add(Message(role = "system", content = systemPrompt))
                    }
                    addAll(messages)
                }

                val updatedConfig = currentConfig.toAppConfig()
                val currentClient = GigaChatClient(updatedConfig)

                val functions = if (currentConfig.functionDate) listOf(
                    FunctionDefinition(
                        name = "function_date",
                        description = "Возвращает текущую дату и время пользователя в формате ISO 8601",
                        parameters = buildJsonObject {
                            put("type", "object")
                            put("properties", buildJsonObject {})
                        }
                    )
                ) else null

                val result = currentClient.sendChatRequest(messagesToSend, functions)

                result.fold(
                    onSuccess = { response ->
                        if (response.choices.isEmpty()) {
                            println("GigaChat> [Пустой ответ от модели]")
                        } else {
                            val choice = response.choices[0]
                            val assistantMessage = choice.message

                            if (choice.finish_reason == "function_call" &&
                                assistantMessage.function_call?.name == "function_date"
                            ) {
                                println("Выполняется function_date...")

                                val currentDateTime = ZonedDateTime.now()
                                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                                messages.add(assistantMessage)
                                messages.add(
                                    Message(
                                        role = "function",
                                        name = "function_date",
                                        content = "{\"datetime\":\"$currentDateTime\"}"
                                    )
                                )

                                val messagesToSend2 = buildList {
                                    val systemPrompt = buildString {
                                        if (currentConfig.systemPrompt.isNotBlank()) {
                                            append(currentConfig.systemPrompt)
                                        }
                                        if (currentConfig.jsonMode) {
                                            if (isNotEmpty()) append("\n\n")
                                            append(
                                                "Отвечай ТОЛЬКО валидным JSON без markdown-блоков и без лишнего текста. " +
                                                "Используй строго эти поля: " +
                                                "\"subject\" (строка, тема сообщения), " +
                                                "\"data\" (строка, развёрнутый ответ на запрос пользователя), " +
                                                "\"date\" (строка, текущая дата в формате ISO 8601), " +
                                                "\"tags\" (массив строк, список тегов по теме)."
                                            )
                                        }
                                    }
                                    if (systemPrompt.isNotBlank()) {
                                        add(Message(role = "system", content = systemPrompt))
                                    }
                                    addAll(messages)
                                }

                                val result2 = currentClient.sendChatRequest(messagesToSend2, functions)
                                result2.fold(
                                    onSuccess = { response2 ->
                                        if (response2.choices.isEmpty()) {
                                            println("GigaChat> [Пустой ответ от модели]")
                                        } else {
                                            val finalMessage = response2.choices[0].message
                                            messages.add(finalMessage)
                                            if (currentConfig.jsonMode) {
                                                printJsonResponse(finalMessage.content)
                                            } else {
                                                println("GigaChat> ${finalMessage.content}\n")
                                            }
                                        }
                                    },
                                    onFailure = { exception ->
                                        println("GigaChat> [Ошибка: ${exception.message}]\n")
                                    }
                                )
                            } else {
                                messages.add(assistantMessage)
                                if (currentConfig.jsonMode) {
                                    printJsonResponse(assistantMessage.content)
                                } else {
                                    println("GigaChat> ${assistantMessage.content}\n")
                                }
                            }
                        }
                    },
                    onFailure = { exception ->
                        println("GigaChat> [Ошибка: ${exception.message}]\n")
                    }
                )

                currentClient.close()
            }

            is CommandResult.Message -> {
                println(commandResult.text)
                println()
            }

            is CommandResult.ClearHistory -> {
                messages.clear()
                println("[История диалога очищена]\n")
            }

            is CommandResult.Exit -> {
                running = false
            }

            is CommandResult.Handled -> {
                // Команда обработана, ничего не делаем
            }
        }
    }

    client.close()
    println("До свидания!")
}

private val lenientJson = Json { ignoreUnknownKeys = true }

private fun extractJsonObject(raw: String): String {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    return if (start != -1 && end > start) raw.substring(start, end + 1) else raw.trim()
}

private fun printJsonResponse(raw: String) {
    val parsed = runCatching {
        lenientJson.decodeFromString<JsonStructuredResponse>(extractJsonObject(raw))
    }.getOrNull()

    if (parsed == null) {
        println("GigaChat> [Ошибка: не удалось распарсить JSON-ответ]")
        println("GigaChat> $raw\n")
        return
    }

    val separator = "─".repeat(40)
    println("GigaChat> [JSON Mode]")
    println("  Subject : ${parsed.subject}")
    println("  Date    : ${parsed.date}")
    println("  Tags    : ${parsed.tags.joinToString(", ")}")
    println("  $separator")
    println("  ${parsed.data}")
    println()
}
