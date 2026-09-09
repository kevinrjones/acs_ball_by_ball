package com.knowledgespike.ballbyball.web

import com.knowledgespike.ballbyball.contracts.MatchSummary
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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

fun Application.module() {
    val apiBaseUrl = environment.config.property("api.baseUrl").getString().trimEnd('/')
    val client = HttpClientFactory.create()
    monitor.subscribe(ApplicationStopped) {
        client.close()
    }
    module(apiBaseUrl, client)
}

fun Application.module(apiBaseUrl: String, client: HttpClient) {
    install(CallLogging)

    routing {
        get("/") {
            call.respondResource("static/index.html")
        }
        get("/matches") {
            val response = client.get("$apiBaseUrl/api/matches")
            if (!response.status.isSuccess()) {
                call.respondText(
                    "The API is unavailable (${response.status.value}).",
                    status = HttpStatusCode.BadGateway
                )
                return@get
            }
            call.respondText(response.body<List<MatchSummary>>().toHtml(), ContentType.Text.Html)
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

private object HttpClientFactory {
    fun create(): HttpClient = HttpClient(io.ktor.client.engine.cio.CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json()
        }
    }
}