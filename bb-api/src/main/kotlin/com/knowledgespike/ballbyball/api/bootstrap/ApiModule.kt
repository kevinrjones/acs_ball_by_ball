package com.knowledgespike.ballbyball.api.bootstrap

import com.auth0.jwt.interfaces.JWTVerifier
import com.knowledgespike.ballbyball.api.adapter.`in`.http.registerApiRoutes
import com.knowledgespike.ballbyball.api.application.port.out.DatabaseHealth
import com.knowledgespike.ballbyball.api.application.port.out.MatchRepository
import com.knowledgespike.ballbyball.api.application.service.DefaultMatchService
import com.knowledgespike.ballbyball.api.application.service.MatchService
import com.knowledgespike.ballbyball.api.config.DatabaseResources
import com.knowledgespike.ballbyball.api.config.DatabaseSettings
import com.knowledgespike.ballbyball.api.config.JwtSettings
import com.knowledgespike.ballbyball.contracts.ApiError
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.DisposableHandle
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

fun Application.module() {
    val resources = DatabaseResources(DatabaseSettings.from(environment.config))
    val appInstance = this
    var stopSubscription: DisposableHandle? = null
    stopSubscription = monitor.subscribe(ApplicationStopped) { app ->
        if (app == appInstance) {
            resources.close()
            stopSubscription?.dispose()
        }
    }
    val jwtSettings = JwtSettings.from(environment.config)
    moduleWithDependencies(resources.matchRepository, resources.databaseHealth, jwtSettings)
}

fun Application.moduleWithDependencies(
    matchRepository: MatchRepository,
    databaseHealth: DatabaseHealth,
    jwtSettings: JwtSettings? = null,
    jwtVerifier: JWTVerifier? = null
) {
    val settings = jwtSettings
        ?: if (runCatching { environment.config.propertyOrNull("jwt.jwksUrl") }.getOrNull() != null) {
            JwtSettings.from(environment.config)
        } else {
            JwtSettings(
                jwksUrl = "https://ids.local:8443/.well-known/openid-configuration/jwks",
                issuer = "https://ids.local:8443",
                realm = "Access to BallByBall API",
                audiences = listOf("acs-bbb", "bbb.api")
            )
        }
    moduleWithServices(DefaultMatchService(matchRepository), databaseHealth, settings, jwtVerifier)
}

fun Application.moduleWithServices(
    matchService: MatchService,
    databaseHealth: DatabaseHealth,
    jwtSettings: JwtSettings,
    jwtVerifier: JWTVerifier? = null
) {
    val applicationLog = LoggerFactory.getLogger("com.knowledgespike.ballbyball.api")
    install(CallLogging)
    install(ContentNegotiation) {
        json(Json { prettyPrint = true })
    }
    configureSecurity(jwtSettings, customVerifier = jwtVerifier)
    install(StatusPages) {
        exception<Exception> { call, cause ->
            if (cause is CancellationException) {
                throw cause
            }
            applicationLog.error("Unhandled API request failure", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = "internal_error", message = "The request could not be completed")
            )
        }
    }

    routing {
        registerApiRoutes(matchService, databaseHealth)
    }
}