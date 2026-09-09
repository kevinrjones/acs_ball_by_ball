package com.knowledgespike.ballbyball.api

import com.knowledgespike.ballbyball.contracts.ApiHealth
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
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

fun Application.module() {
    val resources = DatabaseResources(DatabaseSettings.from(environment.config))
    monitor.subscribe(ApplicationStopped) {
        resources.close()
    }
    module(resources.repository)
}

fun Application.module(repository: MatchRepository) {
    val applicationLog = LoggerFactory.getLogger("com.knowledgespike.ballbyball.api")
    install(CallLogging)
    install(ContentNegotiation) {
        json(Json { prettyPrint = true })
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            applicationLog.error("Unhandled API request failure", cause)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }

    routing {
        get("/health") {
            val healthy = repository.isHealthy()
            call.respond(
                if (healthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                ApiHealth(status = if (healthy) "ok" else "unavailable", database = healthy)
            )
        }
        route("/api") {
            get("/matches") {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 25
                if (limit !in 1..100) {
                    call.respond(HttpStatusCode.BadRequest, "limit must be between 1 and 100")
                    return@get
                }
                call.respond(repository.recentMatches(limit))
            }
        }
    }
}