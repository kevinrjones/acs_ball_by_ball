package com.knowledgespike.ballbyball.web.adapter.out.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig

object HttpClientFactory {
    const val DEFAULT_REQUEST_TIMEOUT_MILLIS: Long = 60_000L
    const val DEFAULT_CONNECT_TIMEOUT_MILLIS: Long = 30_000L
    const val DEFAULT_SOCKET_TIMEOUT_MILLIS: Long = 60_000L

    fun create(
        requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
        connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
        socketTimeoutMillis: Long = DEFAULT_SOCKET_TIMEOUT_MILLIS
    ): HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            this.requestTimeoutMillis = requestTimeoutMillis
            this.connectTimeoutMillis = connectTimeoutMillis
            this.socketTimeoutMillis = socketTimeoutMillis
        }
        install(ContentNegotiation) { json() }
    }

    fun fromConfig(config: ApplicationConfig): HttpClient {
        val requestTimeout = config.propertyOrNull("httpClient.requestTimeoutMillis")?.getString()?.toLongOrNull()
            ?: DEFAULT_REQUEST_TIMEOUT_MILLIS
        val connectTimeout = config.propertyOrNull("httpClient.connectTimeoutMillis")?.getString()?.toLongOrNull()
            ?: DEFAULT_CONNECT_TIMEOUT_MILLIS
        val socketTimeout = config.propertyOrNull("httpClient.socketTimeoutMillis")?.getString()?.toLongOrNull()
            ?: DEFAULT_SOCKET_TIMEOUT_MILLIS
        return create(requestTimeout, connectTimeout, socketTimeout)
    }
}