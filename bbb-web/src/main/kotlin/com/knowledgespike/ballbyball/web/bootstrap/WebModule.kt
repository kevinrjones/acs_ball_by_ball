package com.knowledgespike.ballbyball.web.bootstrap

import com.knowledgespike.ballbyball.web.adapter.`in`.http.registerWebRoutes
import com.knowledgespike.ballbyball.web.adapter.`in`.http.registerAuthenticationRoutes
import com.knowledgespike.ballbyball.web.adapter.out.api.HttpClientFactory
import com.knowledgespike.ballbyball.web.adapter.out.api.KtorMatchApiClient
import com.knowledgespike.ballbyball.web.adapter.out.service.DefaultTokenService
import com.knowledgespike.ballbyball.web.application.MatchApiClient
import com.knowledgespike.ballbyball.web.config.KbffConfigFactory
import com.knowledgespike.ballbyball.web.application.ApplicationMetadataService
import com.knowledgespike.ballbyball.web.config.apiBaseUrl
import com.knowledgespike.ballbyball.web.config.resolveRegistrationUrl
import com.knowledgespike.feature.kbff.data.repository.InMemoryKbffSessionStorage
import com.knowledgespike.feature.kbff.domain.model.KbffConfiguration
import com.knowledgespike.feature.kbff.domain.model.KbffSession
import com.knowledgespike.feature.kbff.domain.repository.KbffSessionStorage
import com.knowledgespike.feature.kbff.domain.service.OidcService
import com.knowledgespike.feature.kbff.presentation.auth.installKbffSecurityHeaders
import com.knowledgespike.feature.kbff.presentation.auth.kbffOidc
import com.knowledgespike.feature.kbff.presentation.route.kbffAuthRoutes
import com.knowledgespike.feature.kbff.presentation.route.kbffProxyRoutes
import io.ktor.client.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.DisposableHandle
import kotlinx.serialization.json.Json

fun Application.module() {
    val isDevelopment = environment.config.propertyOrNull("ktor.development")?.getString() == "true"
    val bffConfig = KbffConfigFactory.fromConfig(environment.config, isDevelopment)
    val registrationUrl = environment.config.resolveRegistrationUrl()
    val apiBaseUrl = bffConfig.apiBaseUrl

    val httpClient = HttpClientFactory.fromConfig(environment.config)
    val appInstance = this
    var stopSubscription: DisposableHandle? = null
    stopSubscription = monitor.subscribe(ApplicationStopped) { app ->
        if (app == appInstance) {
            httpClient.close()
            stopSubscription?.dispose()
        }
    }

    val tokenService = DefaultTokenService(httpClient, bffConfig)
    val matchApiClient = KtorMatchApiClient(apiBaseUrl, httpClient, tokenService)
    val oidcService = OidcService(httpClient, bffConfig)
    val sessionStorage = InMemoryKbffSessionStorage()

    moduleWithDependencies(
        matchApiClient = matchApiClient,
        bffConfig = bffConfig,
        oidcService = oidcService,
        sessionStorage = sessionStorage,
        httpClient = httpClient,
        registrationUrl = registrationUrl
    )
}

fun Application.moduleWithApiClient(
    matchApiClient: MatchApiClient,
    client: HttpClient? = null,
    applicationMetadataService: ApplicationMetadataService = ApplicationMetadataService(matchApiClient)
) {
    val defaultConfig = KbffConfigFactory.defaultConfiguration(isDevelopment = true)
    val defaultClient = client ?: HttpClientFactory.fromConfig(environment.config)
    if (client == null) {
        val appInstance = this
        var stopSubscription: DisposableHandle? = null
        stopSubscription = monitor.subscribe(ApplicationStopped) { app ->
            if (app == appInstance) {
                defaultClient.close()
                stopSubscription?.dispose()
            }
        }
    }
    val defaultOidc = OidcService(defaultClient, defaultConfig)
    moduleWithDependencies(
        matchApiClient = matchApiClient,
        bffConfig = defaultConfig,
        oidcService = defaultOidc,
        sessionStorage = InMemoryKbffSessionStorage(),
        httpClient = defaultClient,
        applicationMetadataService = applicationMetadataService,
        registrationUrl = KbffConfigFactory.DEFAULT_REGISTRATION_URL
    )
}

fun Application.moduleWithDependencies(
    matchApiClient: MatchApiClient,
    bffConfig: KbffConfiguration,
    oidcService: OidcService,
    sessionStorage: KbffSessionStorage,
    httpClient: HttpClient,
    applicationMetadataService: ApplicationMetadataService = ApplicationMetadataService(matchApiClient),
    registrationUrl: String = KbffConfigFactory.DEFAULT_REGISTRATION_URL
) {
    install(CallLogging)
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }

    install(Sessions) {
        cookie<KbffSession>("bb_session", sessionStorage) {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.secure = bffConfig.isProduction
            cookie.extensions["SameSite"] = "Lax"
        }
    }

    installKbffSecurityHeaders(bffConfig)

    install(Authentication) {
        kbffOidc("kbff-oidc") {
            redirectToLogin(
                loginPath = "/bff/login",
                callbackPath = "/signin-oidc"
            )
        }
    }

    routing {
        kbffAuthRoutes(oidcService, bffConfig, "/bff/login", "/signin-oidc")
        registerAuthenticationRoutes(registrationUrl)
        kbffProxyRoutes(bffConfig, httpClient, oidcService)
        registerWebRoutes(matchApiClient, applicationMetadataService)
    }
}
