package com.knowledgespike.ballbyball.api.bootstrap

import com.auth0.jwt.interfaces.JWTVerifier
import com.knowledgespike.ballbyball.api.config.DatabaseResources
import com.knowledgespike.ballbyball.api.config.DatabaseSettings
import com.knowledgespike.ballbyball.api.config.JwtSettings
import com.knowledgespike.ballbyball.api.feature.health.domain.DatabaseHealth
import com.knowledgespike.ballbyball.api.feature.health.presentation.routeHealth
import com.knowledgespike.ballbyball.api.feature.heartbeat.presentation.routeHeartbeat
import com.knowledgespike.ballbyball.api.feature.matches.domain.repository.MatchRepository
import com.knowledgespike.ballbyball.api.feature.matches.domain.service.DefaultMatchService
import com.knowledgespike.ballbyball.api.feature.matches.domain.service.MatchService
import com.knowledgespike.ballbyball.api.feature.matches.presentation.routeMatches
import com.knowledgespike.ballbyball.contracts.Envelope
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
                realm = "Access to BallByBall API"
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
                Envelope.failure("The request could not be completed")
            )
        }
    }

    routing {
        routeHeartbeat()
        routeHealth(databaseHealth)
        routeMatches(matchService)
    }
}