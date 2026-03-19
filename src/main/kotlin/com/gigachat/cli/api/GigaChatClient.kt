package com.gigachat.cli.api

import com.gigachat.cli.config.AppConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.util.*

class GigaChatClient(private val config: AppConfig) {
    private var accessToken: String? = null
    private var tokenExpiresAt: Long = 0
    private val tokenMutex = Mutex()

    private val client = HttpClient(CIO) {
        engine {
            https {
                trustManager = object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate>? = null
                }
            }
        }

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = false
            })
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }

    private suspend fun refreshToken(): Boolean {
        return tokenMutex.withLock {
            try {
                val response: HttpResponse = client.post("https://ngw.devices.sberbank.ru:9443/api/v2/oauth") {
                    header("Authorization", config.credentials_key)
                    header("RqUID", UUID.randomUUID().toString())
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("scope=${config.scope}")
                }

                if (response.status.isSuccess()) {
                    val tokenResponse = response.body<TokenResponse>()
                    accessToken = tokenResponse.access_token
                    tokenExpiresAt = tokenResponse.expires_at
                    true
                } else {
                    val errorBody = response.bodyAsText()
                    println("[Ошибка OAuth: ${response.status.value}] $errorBody")
                    false
                }
            } catch (e: Exception) {
                println("[Ошибка получения токена] ${e.message}")
                false
            }
        }
    }

    private suspend fun ensureValidToken(): Boolean {
        val currentTime = System.currentTimeMillis()

        if (accessToken == null || tokenExpiresAt - currentTime < 60_000) {
            return refreshToken()
        }

        return true
    }

    suspend fun sendChatRequest(messages: List<Message>): Result<ChatResponse> {
        if (!ensureValidToken()) {
            return Result.failure(Exception("Не удалось получить токен авторизации"))
        }

        return try {
            val request = ChatRequest(
                model = config.model,
                messages = messages,
                temperature = config.temperature,
                max_tokens = config.max_tokens
            )

            val response: HttpResponse = client.post("https://gigachat.devices.sberbank.ru/api/v1/chat/completions") {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            when {
                response.status.isSuccess() -> {
                    val chatResponse = response.body<ChatResponse>()
                    Result.success(chatResponse)
                }
                response.status.value == 401 -> {
                    if (refreshToken()) {
                        sendChatRequest(messages)
                    } else {
                        Result.failure(Exception("Ошибка авторизации: проверьте credentials_key"))
                    }
                }
                else -> {
                    val errorBody = response.bodyAsText()
                    Result.failure(Exception("Ошибка API [${response.status.value}]: $errorBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка сети: ${e.message}"))
        }
    }

    fun close() {
        client.close()
    }
}
