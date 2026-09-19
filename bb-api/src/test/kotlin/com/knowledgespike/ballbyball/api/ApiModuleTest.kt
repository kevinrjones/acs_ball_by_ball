package com.knowledgespike.ballbyball.api

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import com.knowledgespike.ballbyball.api.bootstrap.moduleWithDependencies
import com.knowledgespike.ballbyball.api.feature.health.domain.DatabaseHealth
import com.knowledgespike.ballbyball.api.feature.matches.domain.repository.MatchRepository
import com.knowledgespike.ballbyball.contracts.MatchSummary
import com.knowledgespike.ballbyball.types.values.Limit
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo

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

    private fun createUserToken(audience: String = "bb.api", scope: String = "bb.api.read"): String = JWT.create()
        .withIssuer("https://ids.local:8443")
        .withAudience(audience)
        .withSubject("user-sub-123")
        .withClaim("client_id", "bbweb")
        .withClaim("scope", listOf(scope))
        .withClaim("role", listOf("BB.User"))
        .withClaim("name", "Kevin Jones")
        .withClaim("email", "kevin@knowledgespike.com")
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
    fun `matches serializes repository results with machine token`() = testApplication {
        application {
            moduleWithDependencies(
                FakeMatchRepository(matches = listOf(MatchSummary.of(1, 10, "match.json", "TEST", "2026"))),
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
    fun `user profile rejects unauthenticated requests with 401`() = testApplication {
        application { moduleWithDependencies(FakeMatchRepository(), FakeDatabaseHealth(healthy = true), jwtVerifier = testVerifier) }

        val response = client.get("/api/user/profile")

        expectThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `user profile rejects machine token with 403 Forbidden`() = testApplication {
        application { moduleWithDependencies(FakeMatchRepository(), FakeDatabaseHealth(healthy = true), jwtVerifier = testVerifier) }

        val response = client.get("/api/user/profile") {
            header(HttpHeaders.Authorization, "Bearer ${createMachineToken()}")
        }

        expectThat(response.status).isEqualTo(HttpStatusCode.Forbidden)
        expectThat(response.bodyAsText()).contains("User authorization required")
    }

    @Test
    fun `user profile succeeds with user token returning user details`() = testApplication {
        application { moduleWithDependencies(FakeMatchRepository(), FakeDatabaseHealth(healthy = true), jwtVerifier = testVerifier) }

        val response = client.get("/api/user/profile") {
            header(HttpHeaders.Authorization, "Bearer ${createUserToken()}")
        }

        expectThat(response.status).isEqualTo(HttpStatusCode.OK)
        val body = response.bodyAsText()
        expectThat(body).contains("\"result\":")
        expectThat(body).contains("\"timeGenerated\":")
        expectThat(body).contains("\"subject\": \"user-sub-123\"")
        expectThat(body).contains("\"name\": \"Kevin Jones\"")
        expectThat(body).contains("\"email\": \"kevin@knowledgespike.com\"")
        expectThat(body).contains("\"roles\": [")
        expectThat(body).contains("\"BB.User\"")
    }

    @Test
    fun `user profile succeeds with user token without explicit roles`() = testApplication {
        val userWithoutRolesToken = JWT.create()
            .withIssuer("https://ids.local:8443")
            .withAudience("bb.api")
            .withSubject("user-sub-no-roles")
            .withClaim("client_id", "bbweb")
            .withClaim("scope", listOf("bb.api.read"))
            .withClaim("name", "Role-less User")
            .sign(algorithm)

        application { moduleWithDependencies(FakeMatchRepository(), FakeDatabaseHealth(healthy = true), jwtVerifier = testVerifier) }

        val response = client.get("/api/user/profile") {
            header(HttpHeaders.Authorization, "Bearer $userWithoutRolesToken")
        }

        expectThat(response.status).isEqualTo(HttpStatusCode.OK)
        val body = response.bodyAsText()
        expectThat(body).contains("\"subject\": \"user-sub-no-roles\"")
        expectThat(body).contains("\"name\": \"Role-less User\"")
    }

    @Test
    fun `matches serializes repository results with machine token having acs-bbb audience and bbb api read scope`() = testApplication {
        application {
            moduleWithDependencies(
                FakeMatchRepository(matches = listOf(MatchSummary.of(1, 10, "match.json", "TEST", "2026"))),
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
            matchKey = 20251,
            sourceMatchId = 20251,
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
    }

    @Test
    fun `user profile succeeds with user token having acs-bbb audience and bbb api read scope`() = testApplication {
        application { moduleWithDependencies(FakeMatchRepository(), FakeDatabaseHealth(healthy = true), jwtVerifier = testVerifier) }

        val response = client.get("/api/user/profile") {
            header(HttpHeaders.Authorization, "Bearer ${createUserToken(audience = "acs-bbb", scope = "bbb.api.read")}")
        }

        expectThat(response.status).isEqualTo(HttpStatusCode.OK)
        val body = response.bodyAsText()
        expectThat(body).contains("\"subject\": \"user-sub-123\"")
        expectThat(body).contains("\"name\": \"Kevin Jones\"")
    }

    private data class FakeMatchRepository(
        val matches: List<MatchSummary> = emptyList()
    ) : MatchRepository {
        override suspend fun recentMatches(limit: Limit): List<MatchSummary> = matches.take(limit.value)
    }

    private data class FakeDatabaseHealth(val healthy: Boolean) : DatabaseHealth {
        override suspend fun isHealthy(): Boolean = healthy
    }
}