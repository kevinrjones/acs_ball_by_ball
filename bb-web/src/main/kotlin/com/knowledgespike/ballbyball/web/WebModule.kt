package com.knowledgespike.ballbyball.web

import com.knowledgespike.ballbyball.contracts.MatchSummary
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondResource
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

fun Application.module() {
    val apiBaseUrl = environment.config.property("api.baseUrl").getString().trimEnd('/')
    val client = HttpClientFactory.create()
    monitor.subscribe(ApplicationStopped) {
        client.close()
    }
    moduleWithClient(apiBaseUrl, client)
}

fun Application.moduleWithClient(apiBaseUrl: String, client: HttpClient) {
    val applicationLog = LoggerFactory.getLogger("com.knowledgespike.ballbyball.web")
    install(CallLogging)

    routing {
        get("/") {
            call.respondResource("static/index.html")
        }
        get("/matches") {
            try {
                val response = client.get("$apiBaseUrl/api/matches")
                if (!response.status.isSuccess()) {
                    call.respondText(
                        "The API is unavailable (${response.status.value}).",
                        status = HttpStatusCode.BadGateway
                    )
                    return@get
                }
                call.respondText(response.body<List<MatchSummary>>().toHtml(), ContentType.Text.Html)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                applicationLog.warn("API request failed", cause)
                call.respondText(
                    "The API is unavailable.",
                    status = HttpStatusCode.BadGateway
                )
            }
        }
        staticResources("/static", "static")
    }
}

private fun List<MatchSummary>.toHtml(): String = if (isEmpty()) {
    "<p>No matches found.</p>"
} else {
    buildString {
        append("<ul>")
        this@toHtml.forEach { match ->
            append("<li><strong>${match.fileName.escapeHtml()}</strong> ")
            append("(${match.matchType.escapeHtml()}, ${match.season.escapeHtml()})</li>")
        }
        append("</ul>")
    }
}

private fun String.escapeHtml(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

internal object HttpClientFactory {
    fun create(): HttpClient = HttpClient(io.ktor.client.engine.cio.CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 5_000
            connectTimeoutMillis = 2_000
            socketTimeoutMillis = 5_000
        }
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json()
        }
    }
}