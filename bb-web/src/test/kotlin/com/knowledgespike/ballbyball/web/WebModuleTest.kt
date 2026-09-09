package com.knowledgespike.ballbyball.web

import com.knowledgespike.ballbyball.contracts.MatchSummary
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains

class WebModuleTest {
    @Test
    fun `matches renders data returned by the API`() = testApplication {
        val apiClient = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = Json.encodeToString(listOf(MatchSummary(1, 10, "match.json", "TEST", "2026"))),
                        status = HttpStatusCode.OK,
                        headers = io.ktor.http.headersOf("Content-Type", ContentType.Application.Json.toString())
                    )
                }
            }
            install(ContentNegotiation) { json() }
        }
        application { module("http://api", apiClient) }

        val response = client.get("/matches")

        expectThat(response.bodyAsText()).contains("match.json")
        apiClient.close()
    }
}