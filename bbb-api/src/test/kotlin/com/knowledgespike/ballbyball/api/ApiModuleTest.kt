package com.knowledgespike.ballbyball.api

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import com.knowledgespike.ballbyball.api.bootstrap.moduleWithDependencies
import com.knowledgespike.ballbyball.api.feature.health.domain.DatabaseHealth
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchScoresheetPage
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchSearchCriteria
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchSearchMatch
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchSearchPage
import com.knowledgespike.ballbyball.api.feature.matches.domain.repository.MatchRepository
import com.knowledgespike.ballbyball.contracts.MatchSummary
import com.knowledgespike.ballbyball.types.values.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse

class ApiModuleTest {
    private val algorithm = Algorithm.HMAC256("test-secret-key-for-jwt-verification")
    private val testVerifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer("https://ids.local:8443")
        .withAnyOfAudience("bb.api", "acs-bbb")
        .build()

    private fun createMachineToken(audience: String = "bb.api", scope: String = "bb.api.read"): String = JWT.create()
        .withIssuer("https://ids.local:8443")
        .withAudience(audience)
        .withClaim("client_id", "bbweb")
        .withClaim("scope", listOf(scope))
        .sign(algorithm)


    @Test
    fun `health reports repository status`() = testApplication {
        application { moduleWithDependencies(FakeMatchRepository(), FakeDatabaseHealth(healthy = true), jwtVerifier = testVerifier) }

        val response = client.get("/health")

        expectThat(response.status).isEqualTo(HttpStatusCode.OK)
        expectThat(response.bodyAsText()).contains("\"status\": \"ok\"")
    }

    @Test
    fun `health reports service unavailable when database is unhealthy`() = testApplication {
        application { moduleWithDependencies(FakeMatchRepository(), FakeDatabaseHealth(healthy = false), jwtVerifier = testVerifier) }

        val response = client.get("/health")

        expectThat(response.status).isEqualTo(HttpStatusCode.ServiceUnavailable)
        expectThat(response.bodyAsText()).contains("\"status\": \"unavailable\"")
    }

    @Test
    fun `heartbeat alive endpoint is public and succeeds without credentials`() = testApplication {
        application { moduleWithDependencies(FakeMatchRepository(), FakeDatabaseHealth(healthy = true), jwtVerifier = testVerifier) }

        val response = client.get("/api/heartbeat/alive")

        expectThat(response.status).isEqualTo(HttpStatusCode.OK)
        val body = response.bodyAsText()
        expectThat(body).contains("\"result\":")
        expectThat(body).contains("\"timeGenerated\":")
        expectThat(body).contains("\"message\": \"Heartbeat: Alive\"")
    }

    @Test
    fun `matches rejects unauthenticated requests with 401`() = testApplication {
        application { moduleWithDependencies(FakeMatchRepository(), FakeDatabaseHealth(healthy = true), jwtVerifier = testVerifier) }

        val response = client.get("/api/matches")

        expectThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `matches rejects an invalid limit with machine token`() = testApplication {
        application { moduleWithDependencies(FakeMatchRepository(), FakeDatabaseHealth(healthy = true), jwtVerifier = testVerifier) }

        val response = client.get("/api/matches?limit=101") {
            header(HttpHeaders.Authorization, "Bearer ${createMachineToken()}")
        }

        expectThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        val body = response.bodyAsText()
        expectThat(body).contains("\"errorMessage\": \"limit must be between 1 and 100\"")
        expectThat(body).contains("\"timeGenerated\":")
    }

    @Test
    fun `matches rejects a non numeric limit with machine token`() = testApplication {
        application { moduleWithDependencies(FakeMatchRepository(), FakeDatabaseHealth(healthy = true), jwtVerifier = testVerifier) }

        val response = client.get("/api/matches?limit=not-a-number") {
            header(HttpHeaders.Authorization, "Bearer ${createMachineToken()}")
        }

        expectThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    @Test
    fun `historical search requires authentication and validates all boundaries`() = testApplication {
        application { moduleWithDependencies(FakeMatchRepository(), FakeDatabaseHealth(healthy = true), jwtVerifier = testVerifier) }

        val anonymous = client.get("/api/matches/search?team=Test&opponents=India")
        val invalid = client.get("/api/matches/search?team=&opponents=&page=0&pageSize=51") {
            header(HttpHeaders.Authorization, "Bearer ${createMachineToken()}")
        }
        val invalidPaging = client.get("/api/matches/search?team=India&opponents=Pakistan&page=0&pageSize=51") {
            header(HttpHeaders.Authorization, "Bearer ${createMachineToken()}")
        }

        expectThat(anonymous.status).isEqualTo(HttpStatusCode.Unauthorized)
        expectThat(invalid.status).isEqualTo(HttpStatusCode.BadRequest)
        expectThat(invalid.bodyAsText()).contains("team name must be between 3 and 100 characters")
        expectThat(invalidPaging.status).isEqualTo(HttpStatusCode.BadRequest)
        expectThat(invalidPaging.bodyAsText()).contains("page must be between 1 and 10000")
        expectThat(invalidPaging.bodyAsText()).contains("pageSize must be between 1 and 50")
    }

    @Test
    fun `historical search returns bounded page envelope`() = testApplication {
        val page = MatchSearchPage(
            page = PageNumber.from(1),
            pageSize = PageSize.from(20),
            totalResults = 1,
            hasNext = false,
            nextPage = null,
            matches = listOf(
                MatchSearchMatch(
                    publicMatchId = PublicMatchId.from(1_000_000_123),
                    fileName = "historic-match.json",
                    matchType = MatchType.from("TEST"),
                    season = Season.from("2024"),
                    team1 = "South Africa",
                    team2 = "India",
                    ground = "Newlands"
                )
            )
        )
        application {
            moduleWithDependencies(
                FakeMatchRepository(searchPage = page),
                FakeDatabaseHealth(healthy = true),
                jwtVerifier = testVerifier
            )
        }

        val response = client.get("/api/matches/search?team=South&page=1&pageSize=20&opponents=India") {
            header(HttpHeaders.Authorization, "Bearer ${createMachineToken()}")
        }

        expectThat(response.status).isEqualTo(HttpStatusCode.OK)
        expectThat(response.bodyAsText()).contains("historic-match.json")
        expectThat(response.bodyAsText()).contains("\"pagination\": {")
        expectThat(response.bodyAsText()).contains("\"totalResults\": 1")
    }

    @Test
    fun `matches serializes repository results with machine token`() = testApplication {
        application {
            moduleWithDependencies(
                FakeMatchRepository(matches = listOf(MatchSummary.of(1_000_000_001, "match.json", "TEST", "2026"))),
                FakeDatabaseHealth(healthy = true),
                jwtVerifier = testVerifier
            )
        }

        val response = client.get("/api/matches?limit=1") {
            header(HttpHeaders.Authorization, "Bearer ${createMachineToken()}")
        }

        expectThat(response.status).isEqualTo(HttpStatusCode.OK)
        val body = response.bodyAsText()
        expectThat(body).contains("\"result\":")
        expectThat(body).contains("\"timeGenerated\":")
        expectThat(body).contains("\"matches\": [")
        expectThat(body).contains("\"fileName\": \"match.json\"")
    }


    @Test
    fun `matches serializes repository results with machine token having acs-bbb audience and bbb api read scope`() = testApplication {
        application {
            moduleWithDependencies(
                FakeMatchRepository(matches = listOf(MatchSummary.of(1_000_000_001, "match.json", "TEST", "2026"))),
                FakeDatabaseHealth(healthy = true),
                jwtVerifier = testVerifier
            )
        }

        val response = client.get("/api/matches?limit=1") {
            header(HttpHeaders.Authorization, "Bearer ${createMachineToken(audience = "acs-bbb", scope = "bbb.api.read")}")
        }

        expectThat(response.status).isEqualTo(HttpStatusCode.OK)
        expectThat(response.bodyAsText()).contains("\"matches\": [")
    }

    @Test
    fun `matches endpoint serializes full match details and supports days parameter`() = testApplication {
        val sampleMatch = MatchSummary.of(
            publicMatchId = 2_025_100_000,
            fileName = "1548895.json",
            matchType = "witt",
            season = "2026",
            competition = "Women's Asia Cup",
            date = "1 Sept 2026",
            team1 = "Pakistan",
            score1 = "119-9",
            overs1 = "(20ov)",
            isTeam1Winner = true,
            team2 = "Thailand",
            score2 = "84-8",
            overs2 = "(20ov)",
            isTeam2Winner = false,
            result = "Pakistan won by 35 runs",
            format = "women's t20i"
        )
        application {
            moduleWithDependencies(
                FakeMatchRepository(matches = listOf(sampleMatch)),
                FakeDatabaseHealth(healthy = true),
                jwtVerifier = testVerifier
            )
        }

        val response = client.get("/api/matches?days=10") {
            header(HttpHeaders.Authorization, "Bearer ${createMachineToken()}")
        }

        expectThat(response.status).isEqualTo(HttpStatusCode.OK)
        val body = response.bodyAsText()
        expectThat(body).contains("\"competition\": \"Women's Asia Cup\"")
        expectThat(body).contains("\"team1\": \"Pakistan\"")
        expectThat(body).contains("\"score1\": \"119-9\"")
        expectThat(body).contains("\"team2\": \"Thailand\"")
        expectThat(body).contains("\"score2\": \"84-8\"")
        expectThat(body).contains("\"result\": \"Pakistan won by 35 runs\"")
        expectThat(body).contains("\"format\": \"women's t20i\"")
        expectThat(body.contains("sourceMatchId")).isFalse()
        expectThat(body.contains("matchKey")).isFalse()
    }

    @Test
    fun `scoresheet rejects malformed public match ID before repository access`() = testApplication {
        application {
            moduleWithDependencies(FakeMatchRepository(), FakeDatabaseHealth(healthy = true), jwtVerifier = testVerifier)
        }

        val response = client.get("/api/matches/not-a-key/scoresheet?page=1&pageSize=20") {
            header(HttpHeaders.Authorization, "Bearer ${createMachineToken()}")
        }

        expectThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    @Test
    fun `scoresheet ignores pagination parameters and returns not found for an unknown public match ID`() = testApplication {
        application {
            moduleWithDependencies(FakeMatchRepository(), FakeDatabaseHealth(healthy = true), jwtVerifier = testVerifier)
        }

        val response = client.get("/api/matches/9999999999/scoresheet?page=0&pageSize=not-a-number") {
            header(HttpHeaders.Authorization, "Bearer ${createMachineToken()}")
        }

        expectThat(response.status).isEqualTo(HttpStatusCode.NotFound)
        expectThat(response.bodyAsText()).contains("requested match was not found")
    }


    private data class FakeMatchRepository(
        val matches: List<MatchSummary> = emptyList(),
        val searchPage: MatchSearchPage = MatchSearchPage(
            page = PageNumber.from(1),
            pageSize = PageSize.from(20),
            totalResults = 0,
            hasNext = false,
            nextPage = null,
            matches = emptyList()
        )
    ) : MatchRepository {
        override suspend fun recentMatches(limit: Limit): List<MatchSummary> = matches.take(limit.value)

        override suspend fun searchMatches(criteria: MatchSearchCriteria): MatchSearchPage = searchPage

        override suspend fun scoresheet(publicMatchId: PublicMatchId): MatchScoresheetPage? = null
    }

    private data class FakeDatabaseHealth(val healthy: Boolean) : DatabaseHealth {
        override suspend fun isHealthy(): Boolean = healthy
    }
}