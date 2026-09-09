package com.knowledgespike.ballbyball.api

import com.knowledgespike.ballbyball.api.application.port.out.DatabaseHealth
import com.knowledgespike.ballbyball.api.application.port.out.MatchRepository
import com.knowledgespike.ballbyball.api.bootstrap.moduleWithDependencies
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
        application { moduleWithDependencies(FakeMatchRepository(), FakeDatabaseHealth(healthy = true)) }

        val response = client.get("/health")

        expectThat(response.status).isEqualTo(HttpStatusCode.OK)
        expectThat(response.bodyAsText()).contains("\"status\": \"ok\"")
    }

    @Test
    fun `health reports service unavailable when database is unhealthy`() = testApplication {
        application { moduleWithDependencies(FakeMatchRepository(), FakeDatabaseHealth(healthy = false)) }

        val response = client.get("/health")

        expectThat(response.status).isEqualTo(HttpStatusCode.ServiceUnavailable)
        expectThat(response.bodyAsText()).contains("\"status\": \"unavailable\"")
    }

    @Test
    fun `matches rejects an invalid limit`() = testApplication {
        application { moduleWithDependencies(FakeMatchRepository(), FakeDatabaseHealth(healthy = true)) }

        val response = client.get("/api/matches?limit=101")

        expectThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    @Test
    fun `matches rejects a non numeric limit`() = testApplication {
        application { moduleWithDependencies(FakeMatchRepository(), FakeDatabaseHealth(healthy = true)) }

        val response = client.get("/api/matches?limit=not-a-number")

        expectThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    @Test
    fun `matches serializes repository results`() = testApplication {
        application {
            moduleWithDependencies(
                FakeMatchRepository(matches = listOf(MatchSummary(1, 10, "match.json", "TEST", "2026"))),
                FakeDatabaseHealth(healthy = true)
            )
        }

        val response = client.get("/api/matches?limit=1")

        expectThat(response.status).isEqualTo(HttpStatusCode.OK)
        expectThat(response.bodyAsText()).contains("\"matches\": [")
        expectThat(response.bodyAsText()).contains("\"fileName\": \"match.json\"")
    }

    private data class FakeMatchRepository(
        val matches: List<MatchSummary> = emptyList()
    ) : MatchRepository {
        override suspend fun recentMatches(limit: Int): List<MatchSummary> = matches.take(limit)
    }

    private data class FakeDatabaseHealth(val healthy: Boolean) : DatabaseHealth {
        override suspend fun isHealthy(): Boolean = healthy
    }
}