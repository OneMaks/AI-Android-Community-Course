package com.gigachat.cli

import com.gigachat.cli.api.GigaChatClient
import com.gigachat.cli.api.Message
import com.gigachat.cli.commands.CommandHandler
import com.gigachat.cli.commands.CommandResult
import com.gigachat.cli.commands.SessionConfig
import com.gigachat.cli.config.ConfigManager
import com.gigachat.cli.history.HistoryManager
import kotlinx.coroutines.runBlocking

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

                val messagesToSend = buildList {
                    val currentConfig = commandHandler.getSessionConfig()
                    if (currentConfig.systemPrompt.isNotBlank()) {
                        add(Message(role = "system", content = currentConfig.systemPrompt))
                    }
                    addAll(messages)
                }

                val updatedConfig = commandHandler.getSessionConfig().toAppConfig()
                val currentClient = GigaChatClient(updatedConfig)

                val result = currentClient.sendChatRequest(messagesToSend)

                result.fold(
                    onSuccess = { response ->
                        if (response.choices.isEmpty()) {
                            println("GigaChat> [Пустой ответ от модели]")
                        } else {
                            val assistantMessage = response.choices[0].message
                            messages.add(assistantMessage)
                            println("GigaChat> ${assistantMessage.content}\n")
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
