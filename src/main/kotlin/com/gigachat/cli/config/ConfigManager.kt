package com.gigachat.cli.config

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ConfigManager {
    private val configDir = File(System.getProperty("user.home"), ".config/gigachat-cli")
    private val configFile = File(configDir, "config.json")
    private val historyDir = File(configDir, "history")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        ensureDirectoriesExist()
    }

    private fun ensureDirectoriesExist() {
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        if (!historyDir.exists()) {
            historyDir.mkdirs()
        }
    }

    fun loadConfig(): AppConfig? {
        return try {
            if (!configFile.exists()) {
                return null
            }
            val content = configFile.readText()
            json.decodeFromString<AppConfig>(content)
        } catch (e: Exception) {
            println("[Ошибка: не удалось загрузить конфиг] ${e.message}")
            null
        }
    }

    fun saveConfig(config: AppConfig): Boolean {
        return try {
            val content = json.encodeToString(config)
            configFile.writeText(content)
            true
        } catch (e: Exception) {
            println("[Ошибка: не удалось сохранить конфиг] ${e.message}")
            false
        }
    }

    fun initializeConfig(): AppConfig? {
        println("Первый запуск GigaChat CLI")
        println("Для работы необходим credentials_key из личного кабинета GigaChat")
        println("Формат: Basic <base64-строка>")
        print("Введите credentials_key: ")

        val credentialsKey = readlnOrNull()?.trim() ?: ""

        if (credentialsKey.isBlank()) {
            println("[Ошибка: credentials_key не может быть пустым]")
            return null
        }

        val config = AppConfig(credentials_key = credentialsKey)

        return if (saveConfig(config)) {
            println("[Конфигурация создана в ${configFile.absolutePath}]")
            config
        } else {
            null
        }
    }

    fun getHistoryDir(): File = historyDir
}
