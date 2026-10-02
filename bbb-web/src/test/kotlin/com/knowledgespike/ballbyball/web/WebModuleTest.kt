package com.knowledgespike.ballbyball.web

import com.knowledgespike.ballbyball.contracts.Envelope
import com.knowledgespike.ballbyball.contracts.MatchSummary
import com.knowledgespike.ballbyball.contracts.MatchSearchRequest
import com.knowledgespike.ballbyball.contracts.MatchSearchPagination
import com.knowledgespike.ballbyball.contracts.MatchSearchResult
import com.knowledgespike.ballbyball.contracts.MatchSearchResponse
import com.knowledgespike.ballbyball.contracts.RecentMatchesResponse
import com.knowledgespike.ballbyball.web.adapter.out.api.HttpClientFactory
import com.knowledgespike.ballbyball.web.adapter.out.api.KtorMatchApiClient
import com.knowledgespike.ballbyball.web.adapter.out.service.DefaultTokenService
import com.knowledgespike.ballbyball.web.application.MatchApiClient
import com.knowledgespike.ballbyball.web.application.ApplicationMetadataService
import com.knowledgespike.ballbyball.web.application.RecentMatchesResult
import com.knowledgespike.ballbyball.web.application.SearchMatchesResult
import com.knowledgespike.ballbyball.web.application.MatchScoresheetResult
import com.knowledgespike.ballbyball.web.bootstrap.moduleWithApiClient
import com.knowledgespike.ballbyball.web.bootstrap.moduleWithDependencies
import com.knowledgespike.ballbyball.web.config.KbffConfigFactory
import com.knowledgespike.ballbyball.web.config.resolveRegistrationUrl
import com.knowledgespike.ballbyball.web.domain.service.TokenService
import com.knowledgespike.ballbyball.types.values.ExactMatch
import com.knowledgespike.ballbyball.types.values.PublicMatchId
import com.knowledgespike.ballbyball.types.values.MatchResultFilter
import com.knowledgespike.ballbyball.types.values.MatchTypeFilter
import com.knowledgespike.ballbyball.types.values.PageNumber
import com.knowledgespike.ballbyball.types.values.PageSize
import com.knowledgespike.ballbyball.types.values.SearchTeam
import com.knowledgespike.ballbyball.types.values.SourceMatchId
import com.knowledgespike.ballbyball.types.values.VenueFilter
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
import io.ktor.server.config.*
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
import kotlin.time.Instant

class WebModuleTest {
    @Test
    fun `metadata endpoint returns dynamic data timestamp and application version`() = testApplication {
        val apiClient = FakeMatchApiClient(
            RecentMatchesResult.Success(
                emptyList(),
                Instant.parse("2026-09-24T06:29:00Z")
            )
        )
        val metadataService = ApplicationMetadataService(apiClient, "Application version: v0.1.117. Built on 23rd of September 2026 at 06:59 GMT+00:00")
        application {
            moduleWithApiClient(
                matchApiClient = apiClient,
                applicationMetadataService = metadataService
            )
        }

        val response = client.get("/api/metadata")

        expectThat(response.status).isEqualTo(HttpStatusCode.OK)
        expectThat(response.bodyAsText()).contains("24 September 2026 at 07:29 BST")
        expectThat(response.bodyAsText()).contains("Application version: v0.1.117")
    }
    @Test
    fun `matches renders data returned by the API`() = testApplication {
        val apiClient = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = Json.encodeToString(
                            Envelope.success(
                                RecentMatchesResponse(listOf(MatchSummary.of(1_000_000_001, 10, "match.json", "TEST", "2026")))
                            )
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
                        content = Json.encodeToString(Envelope.success(RecentMatchesResponse(emptyList()))),
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

        expectThat(result).isA<RecentMatchesResult.Success>()
        expectThat(authHeaderValue).isEqualTo("Bearer test-token-456")
        mockHttpClient.close()
    }

    @Test
    fun `ktor match api client forwards typed search request and decodes search response`() = runBlocking {
        var requestedPath: String? = null
        var authHeaderValue: String? = null
        val searchResponse = MatchSearchResponse(
            matches = listOf(
                MatchSearchResult(
                    publicMatchId = PublicMatchId.from(1_000_000_100),
                    sourceMatchId = SourceMatchId.from(200),
                    fileName = "historic.json",
                    matchType = null,
                    season = null
                )
            ),
            pagination = MatchSearchPagination(
                page = PageNumber.from(2),
                pageSize = PageSize.from(10),
                totalResults = 1,
                hasNext = false,
                nextPage = null
            )
        )
        val mockHttpClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requestedPath = request.url.encodedPath + "?" + request.url.encodedQuery
                    authHeaderValue = request.headers[HttpHeaders.Authorization]
                    respond(
                        content = Json.encodeToString(
                            Envelope.success(
                                searchResponse
                            )
                        ),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString())
                    )
                }
            }
            install(ContentNegotiation) { json() }
        }
        val tokenService = object : TokenService {
            override suspend fun getAccessToken(): String = "search-token"
        }
        val client = KtorMatchApiClient("http://api", mockHttpClient, tokenService)

        val result = client.searchMatches(
            MatchSearchRequest(
                team = SearchTeam.from("South Africa"),
                teamExactMatch = ExactMatch.from(true),
                opponents = SearchTeam.from("India"),
                opponentsExactMatch = ExactMatch.from(false),
                venue = VenueFilter.from(VenueFilter.ALL),
                startDate = null,
                endDate = null,
                matchType = MatchTypeFilter.from(MatchTypeFilter.ALL),
                matchResult = MatchResultFilter.from(MatchResultFilter.ALL),
                page = PageNumber.from(2),
                pageSize = PageSize.from(10)
            )
        )

        expectThat(result).isA<SearchMatchesResult.Success>()
        expectThat((result as SearchMatchesResult.Success).response.pagination.totalResults).isEqualTo(1)
        expectThat(requestedPath).isEqualTo(
            "/api/matches/search?team=South+Africa&teamExactMatch=true&opponents=India&opponentsExactMatch=false&venue=0&matchType=all&matchResult=0&page=2&pageSize=10"
        )
        expectThat(authHeaderValue).isEqualTo("Bearer search-token")
        mockHttpClient.close()
    }

    @Test
    fun `ktor match api client preserves scoresheet path and not found status`() = runBlocking {
        var requestedPath: String? = null
        var authHeaderValue: String? = null
        val mockHttpClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requestedPath = request.url.encodedPath + "?" + request.url.encodedQuery
                    authHeaderValue = request.headers[HttpHeaders.Authorization]
                    respond("not found", HttpStatusCode.NotFound)
                }
            }
        }
        val tokenService = object : TokenService {
            override suspend fun getAccessToken(): String = "scoresheet-token"
        }
        val client = KtorMatchApiClient("http://api", mockHttpClient, tokenService)

        val result = client.scoresheet(PublicMatchId.from(1_000_000_042))

        expectThat(result).isEqualTo(MatchScoresheetResult.Unavailable(HttpStatusCode.NotFound))
        expectThat(requestedPath).isEqualTo("/api/matches/1000000042/scoresheet?")
        expectThat(authHeaderValue).isEqualTo("Bearer scoresheet-token")
        mockHttpClient.close()
    }

    @Test
    fun `bff user endpoint returns 401 when anonymous`() = testApplication {
        application { moduleWithApiClient(FakeMatchApiClient()) }

        val response = client.get("/bff/user")

        expectThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `bff card search redirects anonymous users to login`() = testApplication {
        application { moduleWithApiClient(FakeMatchApiClient()) }

        val testClient = createClient { followRedirects = false }
        val response = testClient.get("/api/matches/search?team=India&opponents=Pakistan")

        expectThat(response.status).isEqualTo(HttpStatusCode.Found)
        testClient.close()
    }

    @Test
    fun `authenticated bff card search invokes the match api client`() = testApplication {
        var searchInvoked = false
        val searchResponse = MatchSearchResponse(
            matches = emptyList(),
            pagination = MatchSearchPagination(
                page = PageNumber.from(1),
                pageSize = PageSize.from(20),
                totalResults = 0,
                hasNext = false,
                nextPage = null
            )
        )
        val apiClient = FakeMatchApiClient(
            searchResult = SearchMatchesResult.Success(searchResponse),
            onSearch = { searchInvoked = true }
        )
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
            proxy { endpoint("/api", "http://api/api") }
            security { csrfHeaderName = "X-CSRF" }
        }
        val sessionStorage = InMemoryKbffSessionStorage()
        val serializer = defaultSessionSerializer<KbffSession>()
        sessionStorage.write(
            "session-1",
            serializer.serialize(
                KbffSession(
                    sessionId = "session-1",
                    accessToken = "user-access-token",
                    csrfToken = "csrf-abc",
                    claims = listOf(KbffClaim("name", "Kevin Jones"))
                )
            )
        )
        val mockHttpClient = HttpClient(MockEngine) {
            engine { addHandler { respond("proxy route was selected", HttpStatusCode.BadGateway) } }
        }

        application {
            moduleWithDependencies(
                matchApiClient = apiClient,
                bffConfig = config,
                oidcService = OidcService(mockHttpClient, config),
                sessionStorage = sessionStorage,
                httpClient = mockHttpClient
            )
        }

        val response = client.get("/api/matches/search?team=India&opponents=Pakistan") {
            header(HttpHeaders.Cookie, "bb_session=session-1")
        }

        expectThat(response.status).isEqualTo(HttpStatusCode.OK)
        expectThat(searchInvoked).isEqualTo(true)
        mockHttpClient.close()
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
    fun `bff signup redirects to the configured oidc registration endpoint`() = testApplication {
        val config = KbffConfiguration().apply {
            environment(isProduction = false)
            oidc {
                authority = "https://identity.example.com"
                clientId = "bbweb"
                clientSecret = "secret"
                scopes = listOf("openid", "profile", "bb.api")
                redirectUri = "http://localhost:8080/signin-oidc"
                postLogoutRedirectUri = "http://localhost:8080/"
            }
        }
        val mockHttpClient = HttpClient(MockEngine) {
            engine {
                addHandler { respond("unused", HttpStatusCode.OK) }
            }
        }
        val oidcService = OidcService(mockHttpClient, config)

        application {
            moduleWithDependencies(
                matchApiClient = FakeMatchApiClient(),
                bffConfig = config,
                oidcService = oidcService,
                sessionStorage = InMemoryKbffSessionStorage(),
                httpClient = mockHttpClient,
                registrationUrl = "https://identity.example.com/account/create"
            )
        }

        val testClient = createClient {
            followRedirects = false
        }
        val response = testClient.get("/bff/signup")

        expectThat(response.status).isEqualTo(HttpStatusCode.Found)
        expectThat(response.headers[HttpHeaders.Location])
            .isEqualTo("https://identity.example.com/account/create")
        mockHttpClient.close()
        testClient.close()
    }

    @Test
    fun `registration URL is read from explicit oidc configuration`() {
        val config = MapApplicationConfig(
            "kbff.oidc.registrationUrl" to "https://identity.example.com/account/create"
        )

        expectThat(config.resolveRegistrationUrl())
            .isEqualTo("https://identity.example.com/account/create")
    }

    @Test
    fun `security headers include content security policy allowing styles and fonts`() = testApplication {
        val apiClient = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = Json.encodeToString(Envelope.success(RecentMatchesResponse(emptyList()))),
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

    @Test
    fun `http client factory creates client with custom timeouts from application config`() {
        val config = MapApplicationConfig(
            "httpClient.requestTimeoutMillis" to "45000",
            "httpClient.connectTimeoutMillis" to "15000",
            "httpClient.socketTimeoutMillis" to "45000"
        )

        val client = HttpClientFactory.fromConfig(config)
        expectThat(client).isA<HttpClient>()
        client.close()
    }

    private class FakeMatchApiClient(
        private val result: RecentMatchesResult = RecentMatchesResult.Success(emptyList()),
        private val searchResult: SearchMatchesResult = SearchMatchesResult.Unavailable(),
        private val onSearch: () -> Unit = {}
    ) : MatchApiClient {
        override suspend fun recentMatches(): RecentMatchesResult = result

        override suspend fun searchMatches(request: MatchSearchRequest): SearchMatchesResult {
            onSearch()
            return searchResult
        }

        override suspend fun scoresheet(publicMatchId: PublicMatchId): MatchScoresheetResult = MatchScoresheetResult.Unavailable()
    }
}