package com.gigachat.cli.commands

import com.gigachat.cli.api.Message
import com.gigachat.cli.config.AppConfig
import com.gigachat.cli.config.ConfigManager
import com.gigachat.cli.history.HistoryManager

class CommandHandler(
    private val configManager: ConfigManager,
    private val historyManager: HistoryManager,
    private var sessionConfig: SessionConfig
) {
    private val availableModels = listOf("GigaChat", "GigaChat-Pro", "GigaChat-Max")

    fun handleCommand(input: String, messages: List<Message>): CommandResult {
        if (!input.startsWith("/")) {
            return CommandResult.NotACommand
        }

        val parts = input.trim().split(" ", limit = 2)
        val command = parts[0]
        val args = if (parts.size > 1) parts[1] else ""

        return when (command) {
            "/help" -> handleHelp()
            "/config" -> handleConfig()
            "/history" -> handleHistory(args)
            "/save" -> handleSave(messages, args)
            "/clear" -> handleClear()
            "/exit" -> handleExit(messages)
            "/model" -> handleModel(args)
            "/temp" -> handleTemp(args)
            "/tokens" -> handleTokens(args)
            "/system" -> handleSystem(args)
            "/json" -> handleJson(args)
            "/function_date" -> handleFunctionDate(args)
            "/set" -> handleSet(args)
            else -> CommandResult.Message("Неизвестная команда. Введите /help.")
        }
    }

    private fun handleHelp(): CommandResult {
        val helpText = """

Доступные команды:

Навигация и утилиты:
  /help              - Показать эту справку
  /config            - Показать текущие настройки сессии
  /history           - Показать список сохранённых сессий
  /history <N>       - Показать содержимое N-й сессии
  /save [path]       - Сохранить диалог в текстовый файл
  /clear             - Очистить историю текущей сессии
  /exit              - Сохранить сессию и выйти

Настройки "на лету":
  /model <name>      - Сменить модель (GigaChat, GigaChat-Pro, GigaChat-Max)
  /temp <value>      - Установить температуру (0.0-2.0)
  /tokens <n>        - Установить лимит токенов (1-32768)
  /system <text>     - Установить системный промпт
  /system off        - Отключить системный промпт
  /json true/false   - Включить/выключить режим структурированного JSON-вывода
  /function_date true/false - Включить/выключить function calling для текущей даты/времени
  /set save          - Сохранить настройки в config.json
  /set reset         - Сбросить настройки к значениям из config.json
        """.trimIndent()

        return CommandResult.Message(helpText)
    }

    private fun handleConfig(): CommandResult {
        val config = """

Текущие настройки сессии:
  credentials_key: ***
  model: ${sessionConfig.model}
  temperature: ${sessionConfig.temperature}
  max_tokens: ${sessionConfig.maxTokens}
  system_prompt: ${if (sessionConfig.systemPrompt.isBlank()) "<не задан>" else "\"${sessionConfig.systemPrompt}\""}
  scope: ${sessionConfig.scope}
  json_mode: ${sessionConfig.jsonMode}
  function_date: ${sessionConfig.functionDate}
        """.trimIndent()

        return CommandResult.Message(config)
    }

    private fun handleHistory(args: String): CommandResult {
        val sessions = historyManager.getSavedSessions()

        if (args.isBlank()) {
            if (sessions.isEmpty()) {
                return CommandResult.Message("Нет сохранённых сессий")
            }

            val list = buildString {
                appendLine("\nСохранённые сессии:")
                sessions.forEachIndexed { index, timestamp ->
                    appendLine("  ${index + 1}. $timestamp")
                }
                appendLine("\nИспользуйте /history <N> для просмотра содержимого")
            }

            return CommandResult.Message(list)
        }

        val index = args.toIntOrNull()
        if (index == null || index < 1 || index > sessions.size) {
            return CommandResult.Message("[Ошибка: неверный номер сессии]")
        }

        val timestamp = sessions[index - 1]
        val session = historyManager.loadSession(timestamp)

        if (session == null) {
            return CommandResult.Message("[Ошибка: не удалось загрузить сессию]")
        }

        val content = buildString {
            appendLine("\n=== Сессия: $timestamp ===")
            session.metadata?.let { meta ->
                appendLine("Модель: ${meta.model}, Temperature: ${meta.temperature}, Max tokens: ${meta.max_tokens}")
                if (meta.system_prompt.isNotBlank()) {
                    appendLine("System prompt: ${meta.system_prompt}")
                }
            }
            appendLine()

            session.messages.forEach { message ->
                when (message.role) {
                    "system" -> appendLine("[SYSTEM] ${message.content}")
                    "user" -> appendLine("You> ${message.content}")
                    "assistant" -> appendLine("GigaChat> ${message.content}")
                }
                appendLine()
            }
        }

        return CommandResult.Message(content)
    }

    private fun handleSave(messages: List<Message>, args: String): CommandResult {
        val path = args.ifBlank { null }
        historyManager.exportSessionToText(messages, path)
        return CommandResult.Handled
    }

    private fun handleClear(): CommandResult {
        return CommandResult.ClearHistory
    }

    private fun handleExit(messages: List<Message>): CommandResult {
        if (messages.isNotEmpty()) {
            historyManager.saveCurrentSession(
                messages = messages,
                model = sessionConfig.model,
                temperature = sessionConfig.temperature,
                maxTokens = sessionConfig.maxTokens,
                systemPrompt = sessionConfig.systemPrompt
            )
            println("[Сессия сохранена]")
        }
        return CommandResult.Exit
    }

    private fun handleModel(args: String): CommandResult {
        if (args.isBlank()) {
            return CommandResult.Message("[Ошибка: укажите модель]")
        }

        if (args !in availableModels) {
            return CommandResult.Message("[Ошибка: неизвестная модель. Доступны: ${availableModels.joinToString(", ")}]")
        }

        sessionConfig = sessionConfig.copy(model = args)
        return CommandResult.Message("[Настройка применена] model = $args")
    }

    private fun handleTemp(args: String): CommandResult {
        val temp = args.toFloatOrNull()

        if (temp == null) {
            return CommandResult.Message("[Ошибка: температура должна быть числом]")
        }

        if (temp !in 0.0f..2.0f) {
            return CommandResult.Message("[Ошибка: температура должна быть от 0.0 до 2.0]")
        }

        sessionConfig = sessionConfig.copy(temperature = temp)
        return CommandResult.Message("[Настройка применена] temperature = $temp")
    }

    private fun handleTokens(args: String): CommandResult {
        val tokens = args.toIntOrNull()

        if (tokens == null) {
            return CommandResult.Message("[Ошибка: лимит токенов должен быть числом]")
        }

        if (tokens !in 1..32768) {
            return CommandResult.Message("[Ошибка: лимит токенов должен быть от 1 до 32768]")
        }

        sessionConfig = sessionConfig.copy(maxTokens = tokens)
        return CommandResult.Message("[Настройка применена] max_tokens = $tokens")
    }

    private fun handleSystem(args: String): CommandResult {
        if (args.equals("off", ignoreCase = true)) {
            sessionConfig = sessionConfig.copy(systemPrompt = "")
            return CommandResult.Message("[Настройка применена] system_prompt = <отключен>")
        }

        if (args.isBlank()) {
            return CommandResult.Message("[Ошибка: укажите текст системного промпта или 'off' для отключения]")
        }

        sessionConfig = sessionConfig.copy(systemPrompt = args)
        return CommandResult.Message("[Настройка применена] system_prompt = \"$args\"")
    }

    private fun handleJson(args: String): CommandResult {
        return when (args.trim().lowercase()) {
            "true" -> {
                sessionConfig = sessionConfig.copy(jsonMode = true)
                CommandResult.Message("[Настройка применена] json_mode = true")
            }
            "false" -> {
                sessionConfig = sessionConfig.copy(jsonMode = false)
                CommandResult.Message("[Настройка применена] json_mode = false")
            }
            "" -> CommandResult.Message("[JSON режим: ${if (sessionConfig.jsonMode) "включён" else "выключен"}]")
            else -> CommandResult.Message("[Ошибка: используйте /json true или /json false]")
        }
    }

    private fun handleFunctionDate(args: String): CommandResult {
        return when (args.trim().lowercase()) {
            "true" -> {
                sessionConfig = sessionConfig.copy(functionDate = true)
                CommandResult.Message("[Настройка применена] function_date = true")
            }
            "false" -> {
                sessionConfig = sessionConfig.copy(functionDate = false)
                CommandResult.Message("[Настройка применена] function_date = false")
            }
            "" -> CommandResult.Message("[function_date: ${if (sessionConfig.functionDate) "включён" else "выключен"}]")
            else -> CommandResult.Message("[Ошибка: используйте /function_date true или /function_date false]")
        }
    }

    private fun handleSet(args: String): CommandResult {
        return when (args.trim().lowercase()) {
            "save" -> {
                val newConfig = AppConfig(
                    credentials_key = sessionConfig.credentialsKey,
                    model = sessionConfig.model,
                    temperature = sessionConfig.temperature,
                    max_tokens = sessionConfig.maxTokens,
                    system_prompt = sessionConfig.systemPrompt,
                    scope = sessionConfig.scope
                )

                if (configManager.saveConfig(newConfig)) {
                    CommandResult.Message("[Настройки сохранены в config.json]")
                } else {
                    CommandResult.Message("[Ошибка сохранения настроек]")
                }
            }
            "reset" -> {
                val config = configManager.loadConfig()
                if (config != null) {
                    sessionConfig = SessionConfig.fromAppConfig(config)
                    CommandResult.Message("[Настройки сброшены к значениям из config.json]")
                } else {
                    CommandResult.Message("[Ошибка загрузки config.json]")
                }
            }
            else -> {
                CommandResult.Message("[Ошибка: используйте /set save или /set reset]")
            }
        }
    }

    fun getSessionConfig(): SessionConfig = sessionConfig
}

data class SessionConfig(
    val credentialsKey: String,
    val model: String,
    val temperature: Float,
    val maxTokens: Int,
    val systemPrompt: String,
    val scope: String,
    val jsonMode: Boolean = false,
    val functionDate: Boolean = false
) {
    companion object {
        fun fromAppConfig(config: AppConfig): SessionConfig {
            return SessionConfig(
                credentialsKey = config.credentials_key,
                model = config.model,
                temperature = config.temperature,
                maxTokens = config.max_tokens,
                systemPrompt = config.system_prompt,
                scope = config.scope
            )
        }
    }

    fun toAppConfig(): AppConfig {
        return AppConfig(
            credentials_key = credentialsKey,
            model = model,
            temperature = temperature,
            max_tokens = maxTokens,
            system_prompt = systemPrompt,
            scope = scope
        )
    }
}

sealed class CommandResult {
    object NotACommand : CommandResult()
    object Handled : CommandResult()
    object ClearHistory : CommandResult()
    object Exit : CommandResult()
    data class Message(val text: String) : CommandResult()
}
