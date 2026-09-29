package com.knowledgespike.ballbyball.getcricsheetdata

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration

interface CricsheetHttpClient {
    fun getText(uri: URI): String

    fun download(uri: URI, destination: Path)
}

class JdkHttpClientTransport(
    private val client: HttpClient = defaultHttpClient(),
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val retryDelay: (Int) -> Duration = { attempt ->
        Duration.ofSeconds(1L shl (attempt - 1))
    },
    private val sleeper: (Duration) -> Unit = { duration -> Thread.sleep(duration.toMillis()) }
) : CricsheetHttpClient {
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
    }

    override fun getText(uri: URI): String = send(uri, HttpResponse.BodyHandlers.ofString())

    override fun download(uri: URI, destination: Path) {
        send(uri, HttpResponse.BodyHandlers.ofFile(destination))
    }

    private fun <T> send(uri: URI, bodyHandler: HttpResponse.BodyHandler<T>): T {
        var attempt = 1
        while (attempt <= maxAttempts) {
            try {
                val response = client.send(request(uri), bodyHandler)
                if (response.statusCode() in 200..299) {
                    return response.body()
                }

                val failure = HttpTransferException(
                    uri = uri,
                    statusCode = response.statusCode(),
                    message = "HTTP ${response.statusCode()} from $uri"
                )
                if (!failure.retryable || attempt == maxAttempts) {
                    throw failure
                }
                waitBeforeRetry(uri, attempt, failure)
            } catch (exception: HttpTransferException) {
                if (!exception.retryable || attempt == maxAttempts) {
                    throw exception
                }
                waitBeforeRetry(uri, attempt, exception)
            } catch (exception: IOException) {
                if (attempt == maxAttempts) {
                    throw exception
                }
                waitBeforeRetry(uri, attempt, exception)
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                throw exception
            }
            attempt++
        }

        error("HTTP request exhausted retries for $uri")
    }

    private fun waitBeforeRetry(uri: URI, attempt: Int, exception: Exception) {
        val delay = retryDelay(attempt)
        LOGGER.warn("HTTP request to {} failed on attempt {}; retrying in {} ms", uri, attempt, delay.toMillis(), exception)
        sleeper(delay)
    }

    private fun request(uri: URI): HttpRequest = HttpRequest.newBuilder(uri)
        .timeout(TRANSFER_TIMEOUT)
        .header("Accept", "text/html,text/csv,application/zip,application/octet-stream")
        .GET()
        .build()

    companion object {
        private const val DEFAULT_MAX_ATTEMPTS = 4
        private val CONNECT_TIMEOUT = Duration.ofSeconds(10)
        private val TRANSFER_TIMEOUT = Duration.ofMinutes(10)
        private val LOGGER = org.slf4j.LoggerFactory.getLogger(JdkHttpClientTransport::class.java)

        private fun defaultHttpClient(): HttpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }
}

class HttpTransferException(
    val uri: URI,
    val statusCode: Int,
    message: String
) : IOException(message) {
    val retryable: Boolean = statusCode >= 500
}