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
        application { module(FakeMatchRepository) }

        val response = client.get("/health")

        expectThat(response.status).isEqualTo(HttpStatusCode.OK)
        expectThat(response.bodyAsText()).contains("\"status\": \"ok\"")
    }

    @Test
    fun `matches rejects an invalid limit`() = testApplication {
        application { module(FakeMatchRepository) }

        val response = client.get("/api/matches?limit=101")

        expectThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    private object FakeMatchRepository : MatchRepository {
        override fun isHealthy(): Boolean = true

        override fun recentMatches(limit: Int): List<MatchSummary> = emptyList()
    }
}