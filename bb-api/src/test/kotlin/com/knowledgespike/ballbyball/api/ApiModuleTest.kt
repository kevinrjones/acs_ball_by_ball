package com.knowledgespike.ballbyball.api

import com.knowledgespike.ballbyball.contracts.MatchSummary
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo

class ApiModuleTest {
    @Test
    fun `health reports repository status`() = testApplication {
        application { moduleWithRepository(FakeMatchRepository(healthy = true)) }

        val response = client.get("/health")

        expectThat(response.status).isEqualTo(HttpStatusCode.OK)
        expectThat(response.bodyAsText()).contains("\"status\": \"ok\"")
    }

    @Test
    fun `health reports service unavailable when database is unhealthy`() = testApplication {
        application { moduleWithRepository(FakeMatchRepository(healthy = false)) }

        val response = client.get("/health")

        expectThat(response.status).isEqualTo(HttpStatusCode.ServiceUnavailable)
        expectThat(response.bodyAsText()).contains("\"status\": \"unavailable\"")
    }

    @Test
    fun `matches rejects an invalid limit`() = testApplication {
        application { moduleWithRepository(FakeMatchRepository(healthy = true)) }

        val response = client.get("/api/matches?limit=101")

        expectThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    @Test
    fun `matches rejects a non numeric limit`() = testApplication {
        application { moduleWithRepository(FakeMatchRepository(healthy = true)) }

        val response = client.get("/api/matches?limit=not-a-number")

        expectThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    @Test
    fun `matches serializes repository results`() = testApplication {
        application {
            moduleWithRepository(
                FakeMatchRepository(
                    healthy = true,
                    matches = listOf(MatchSummary(1, 10, "match.json", "TEST", "2026"))
                )
            )
        }

        val response = client.get("/api/matches?limit=1")

        expectThat(response.status).isEqualTo(HttpStatusCode.OK)
        expectThat(response.bodyAsText()).contains("\"fileName\": \"match.json\"")
    }

    private data class FakeMatchRepository(
        val healthy: Boolean,
        val matches: List<MatchSummary> = emptyList()
    ) : MatchRepository {
        override suspend fun isHealthy(): Boolean = healthy

        override suspend fun recentMatches(limit: Int): List<MatchSummary> = matches.take(limit)
    }
}