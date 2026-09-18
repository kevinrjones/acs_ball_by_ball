package com.knowledgespike.ballbyball.web

import com.knowledgespike.ballbyball.contracts.MatchSummary
import com.knowledgespike.ballbyball.contracts.RecentMatchesResponse
import com.knowledgespike.ballbyball.web.adapter.out.api.KtorMatchApiClient
import com.knowledgespike.ballbyball.web.adapter.out.service.DefaultTokenService
import com.knowledgespike.ballbyball.web.application.MatchApiClient
import com.knowledgespike.ballbyball.web.application.MatchApiResult
import com.knowledgespike.ballbyball.web.bootstrap.moduleWithApiClient
import com.knowledgespike.ballbyball.web.bootstrap.moduleWithDependencies
import com.knowledgespike.ballbyball.web.config.KbffConfigFactory
import com.knowledgespike.ballbyball.web.domain.service.TokenService
import com.knowledgespike.feature.kbff.data.repository.InMemoryKbffSessionStorage
import com.knowledgespike.feature.kbff.domain.model.KbffClaim
import com.knowledgespike.feature.kbff.domain.model.KbffConfiguration
import com.knowledgespike.feature.kbff.domain.model.KbffSession
import com.knowledgespike.feature.kbff.domain.service.OidcService
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
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
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString())
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
                        headers = headersOf(
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

    @Test
    fun `default token service fetches client credentials token and caches it`() = runBlocking {
        var callCount = 0
        var discoveryCount = 0
        val mockHttpClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/.well-known/openid-configuration" -> {
                            discoveryCount++
                            respond(
                                content = """{"token_endpoint": "https://ids.local:8443/connect/token"}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf("Content-Type", "application/json")
                            )
                        }
                        "/connect/token" -> {
                            callCount++
                            respond(
                                content = """{"access_token": "token-xyz-123", "expires_in": 3600}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf("Content-Type", "application/json")
                            )
                        }
                        else -> respond("Not found", HttpStatusCode.NotFound)
                    }
                }
            }
        }

        val config = KbffConfiguration().apply {
            environment(isProduction = false)
            oidc {
                authority = "https://ids.local:8443"
                clientId = "bbweb"
                clientSecret = "secret"
                scopes = listOf("openid", "profile", "bb.api.read")
            }
        }

        val tokenService = DefaultTokenService(mockHttpClient, config)
        val token1 = tokenService.getAccessToken()
        val token2 = tokenService.getAccessToken()

        expectThat(token1).isEqualTo("token-xyz-123")
        expectThat(token2).isEqualTo("token-xyz-123")
        expectThat(callCount).isEqualTo(1) // Token was served from in-memory cache on second call
        expectThat(discoveryCount).isEqualTo(1) // Discovery endpoint cached and called only once
        mockHttpClient.close()
    }

    @Test
    fun `ktor match api client sends bearer token from token service`() = runBlocking {
        var authHeaderValue: String? = null
        val mockHttpClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    authHeaderValue = request.headers[HttpHeaders.Authorization]
                    respond(
                        content = Json.encodeToString(RecentMatchesResponse(emptyList())),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                    )
                }
            }
            install(ContentNegotiation) { json() }
        }

        val fakeTokenService = object : TokenService {
            override suspend fun getAccessToken(): String = "test-token-456"
        }

        val client = KtorMatchApiClient("http://api", mockHttpClient, fakeTokenService)
        val result = client.recentMatches()

        expectThat(result).isA<MatchApiResult.Success>()
        expectThat(authHeaderValue).isEqualTo("Bearer test-token-456")
        mockHttpClient.close()
    }

    @Test
    fun `bff user endpoint returns 401 when anonymous`() = testApplication {
        application { moduleWithApiClient(FakeMatchApiClient()) }

        val response = client.get("/bff/user")

        expectThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `bff user endpoint returns user claims and csrf token when session exists`() = testApplication {
        val config = KbffConfiguration().apply {
            environment(isProduction = false)
            oidc {
                authority = "https://ids.local:8443"
                clientId = "bbweb"
                clientSecret = "secret"
                scopes = listOf("openid", "profile", "bb.api")
                redirectUri = "http://localhost:8080/signin-oidc"
                postLogoutRedirectUri = "http://localhost:8080/"
            }
            proxy {
                endpoint("/api", "http://localhost:8081/api")
            }
            security {
                csrfHeaderName = "X-CSRF"
            }
        }

        val sessionStorage = InMemoryKbffSessionStorage()
        val serializer = defaultSessionSerializer<KbffSession>()
        val testSession = KbffSession(
            sessionId = "session-1",
            accessToken = "user-access-token",
            csrfToken = "csrf-abc",
            claims = listOf(
                KbffClaim("name", "Kevin Jones"),
                KbffClaim("email", "kevin@knowledgespike.com")
            )
        )
        sessionStorage.write("session-1", serializer.serialize(testSession))

        val mockHttpClient = HttpClient(MockEngine) {
            engine {
                addHandler { respond("OK", HttpStatusCode.OK) }
            }
        }
        val oidcService = OidcService(mockHttpClient, config)

        application {
            moduleWithDependencies(
                matchApiClient = FakeMatchApiClient(),
                bffConfig = config,
                oidcService = oidcService,
                sessionStorage = sessionStorage,
                httpClient = mockHttpClient
            )
        }

        val response = client.get("/bff/user") {
            header(HttpHeaders.Cookie, "bb_session=session-1")
        }

        expectThat(response.status).isEqualTo(HttpStatusCode.OK)
        val body = response.bodyAsText()
        expectThat(body).contains("\"name\"")
        expectThat(body).contains("\"Kevin Jones\"")
        expectThat(body).contains("\"csrfToken\": \"csrf-abc\"")
        expectThat(body).contains("bff:logout_url")
        mockHttpClient.close()
    }

    @Test
    fun `bff login redirects to oidc authorization endpoint`() = testApplication {
        val config = KbffConfiguration().apply {
            environment(isProduction = false)
            oidc {
                authority = "https://ids.local:8443"
                clientId = "bbweb"
                clientSecret = "secret"
                scopes = listOf("openid", "profile", "bb.api")
                redirectUri = "http://localhost:8080/signin-oidc"
                postLogoutRedirectUri = "http://localhost:8080/"
            }
        }

        val mockHttpClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/.well-known/openid-configuration" -> {
                            respond(
                                content = """{
                                    "issuer": "https://ids.local:8443",
                                    "authorization_endpoint": "https://ids.local:8443/connect/authorize",
                                    "token_endpoint": "https://ids.local:8443/connect/token",
                                    "jwks_uri": "https://ids.local:8443/.well-known/openid-configuration/jwks",
                                    "response_types_supported": ["code"],
                                    "subject_types_supported": ["public"],
                                    "id_token_signing_alg_values_supported": ["RS256"]
                                }""".trimIndent(),
                                status = HttpStatusCode.OK,
                                headers = headersOf("Content-Type", "application/json")
                            )
                        }
                        else -> respond("Not found", HttpStatusCode.NotFound)
                    }
                }
            }
        }
        val oidcService = OidcService(mockHttpClient, config)

        application {
            moduleWithDependencies(
                matchApiClient = FakeMatchApiClient(),
                bffConfig = config,
                oidcService = oidcService,
                sessionStorage = InMemoryKbffSessionStorage(),
                httpClient = mockHttpClient
            )
        }

        val testClient = createClient {
            followRedirects = false
        }
        val response = testClient.get("/bff/login")

        expectThat(response.status).isEqualTo(HttpStatusCode.Found)
        val location = response.headers[HttpHeaders.Location]
        expectThat(location).isNotNull()
        expectThat(location!!).contains("https://ids.local:8443/connect/authorize")
        expectThat(location).contains("client_id=bbweb")
        expectThat(location).contains("response_type=code")
        mockHttpClient.close()
        testClient.close()
    }

    @Test
    fun `security headers include content security policy allowing styles and fonts`() = testApplication {
        val apiClient = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = Json.encodeToString(RecentMatchesResponse(emptyList())),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                    )
                }
            }
            install(ContentNegotiation) { json() }
        }
        application { moduleWithApiClient(KtorMatchApiClient("http://api", apiClient)) }

        val response = client.get("/")
        val csp = response.headers["Content-Security-Policy"]
        expectThat(csp).isNotNull()
        expectThat(csp!!).contains("style-src")
        expectThat(csp).contains("fonts.googleapis.com")
        expectThat(csp).contains("font-src")
        expectThat(csp).contains("fonts.gstatic.com")
        apiClient.close()
    }

    @Test
    fun `stopping unrelated application does not close module http client`() = testApplication {
        val config = KbffConfigFactory.defaultConfiguration(isDevelopment = true)
        val mockHttpClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/.well-known/openid-configuration" -> {
                            respond(
                                content = """{
                                    "issuer": "https://ids.local:8443",
                                    "authorization_endpoint": "https://ids.local:8443/connect/authorize",
                                    "token_endpoint": "https://ids.local:8443/connect/token",
                                    "jwks_uri": "https://ids.local:8443/.well-known/openid-configuration/jwks",
                                    "response_types_supported": ["code"],
                                    "subject_types_supported": ["public"],
                                    "id_token_signing_alg_values_supported": ["RS256"]
                                }""".trimIndent(),
                                status = HttpStatusCode.OK,
                                headers = headersOf("Content-Type", "application/json")
                            )
                        }
                        else -> respond("Not found", HttpStatusCode.NotFound)
                    }
                }
            }
        }
        val oidcService = OidcService(mockHttpClient, config)

        var appInstance: Application? = null
        application {
            appInstance = this
            moduleWithDependencies(
                matchApiClient = FakeMatchApiClient(),
                bffConfig = config,
                oidcService = oidcService,
                sessionStorage = InMemoryKbffSessionStorage(),
                httpClient = mockHttpClient
            )
        }

        startApplication()
        val app = appInstance!!
        val constructor = Application::class.java.declaredConstructors.first()
        constructor.isAccessible = true
        val otherApp = constructor.newInstance(
            app.environment,
            false,
            "",
            app.monitor,
            kotlin.coroutines.EmptyCoroutineContext,
            { error("dummy") }
        ) as Application
        // Simulate an auto-reload where an unrelated application instance is stopped
        app.monitor.raise(ApplicationStopped, otherApp)

        val testClient = createClient { followRedirects = false }
        val response = testClient.get("/bff/login")
        expectThat(response.status).isEqualTo(HttpStatusCode.Found)
        mockHttpClient.close()
        testClient.close()
    }

    private class FakeMatchApiClient : MatchApiClient {
        override suspend fun recentMatches(): MatchApiResult = MatchApiResult.Success(emptyList())
    }
}