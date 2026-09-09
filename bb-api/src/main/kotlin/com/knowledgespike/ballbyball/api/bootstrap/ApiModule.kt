package com.knowledgespike.ballbyball.api.bootstrap

import com.knowledgespike.ballbyball.api.adapter.`in`.http.registerApiRoutes
import com.knowledgespike.ballbyball.api.application.port.out.DatabaseHealth
import com.knowledgespike.ballbyball.api.application.port.out.MatchRepository
import com.knowledgespike.ballbyball.api.application.service.DefaultMatchService
import com.knowledgespike.ballbyball.api.application.service.MatchService
import com.knowledgespike.ballbyball.api.config.DatabaseResources
import com.knowledgespike.ballbyball.api.config.DatabaseSettings
import com.knowledgespike.ballbyball.contracts.ApiError
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

fun Application.module() {
    val resources = DatabaseResources(DatabaseSettings.from(environment.config))
    monitor.subscribe(ApplicationStopped) {
        resources.close()
    }
    moduleWithDependencies(resources.matchRepository, resources.databaseHealth)
}

fun Application.moduleWithDependencies(
    matchRepository: MatchRepository,
    databaseHealth: DatabaseHealth
) {
    moduleWithServices(DefaultMatchService(matchRepository), databaseHealth)
}

fun Application.moduleWithServices(
    matchService: MatchService,
    databaseHealth: DatabaseHealth
) {
    val applicationLog = LoggerFactory.getLogger("com.knowledgespike.ballbyball.api")
    install(CallLogging)
    install(ContentNegotiation) {
        json(Json { prettyPrint = true })
    }
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