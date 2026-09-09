package com.knowledgespike.ballbyball.web

import com.knowledgespike.ballbyball.contracts.MatchSummary
import com.knowledgespike.ballbyball.contracts.RecentMatchesResponse
import com.knowledgespike.ballbyball.web.adapter.out.api.KtorMatchApiClient
import com.knowledgespike.ballbyball.web.bootstrap.moduleWithApiClient
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
import strikt.assertions.isEqualTo
import java.io.IOException

class WebModuleTest {
    @Test
    fun `matches renders data returned by the API`() = testApplication {
        val apiClient = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = Json.encodeToString(
                            RecentMatchesResponse(listOf(MatchSummary(1, 10, "match.json", "TEST", "2026")))
                        ),
                        status = HttpStatusCode.OK,
                        headers = io.ktor.http.headersOf("Content-Type", ContentType.Application.Json.toString())
                    )
                }
            }
            install(ContentNegotiation) { json() }
        }
        application { moduleWithApiClient(KtorMatchApiClient("http://api", apiClient)) }

        val response = client.get("/matches")

        expectThat(response.bodyAsText()).contains("match.json")
        apiClient.close()
    }

    @Test
    fun `matches returns bad gateway when the API responds unsuccessfully`() = testApplication {
        val apiClient = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(content = "unavailable", status = HttpStatusCode.ServiceUnavailable)
                }
            }
        }
        application { moduleWithApiClient(KtorMatchApiClient("http://api", apiClient)) }

        val response = client.get("/matches")

        expectThat(response.status).isEqualTo(HttpStatusCode.BadGateway)
        expectThat(response.bodyAsText()).contains("The API is unavailable (503).")
        apiClient.close()
    }

    @Test
    fun `matches returns bad gateway when the API connection fails`() = testApplication {
        val apiClient = HttpClient(MockEngine) {
            engine {
                addHandler { throw IOException("API unavailable") }
            }
        }
        application { moduleWithApiClient(KtorMatchApiClient("http://api", apiClient)) }

        val response = client.get("/matches")

        expectThat(response.status).isEqualTo(HttpStatusCode.BadGateway)
        expectThat(response.bodyAsText()).contains("The API is unavailable.")
        apiClient.close()
    }

    @Test
    fun `matches returns bad gateway when the API returns malformed JSON`() = testApplication {
        val apiClient = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = "not-json",
                        status = HttpStatusCode.OK,
                        headers = io.ktor.http.headersOf(
                            "Content-Type",
                            ContentType.Application.Json.toString()
                        )
                    )
                }
            }
            install(ContentNegotiation) { json() }
        }
        application { moduleWithApiClient(KtorMatchApiClient("http://api", apiClient)) }

        val response = client.get("/matches")

        expectThat(response.status).isEqualTo(HttpStatusCode.BadGateway)
        expectThat(response.bodyAsText()).contains("The API is unavailable.")
        apiClient.close()
    }
}