package com.knowledgespike.ballbyball.api.feature.health.presentation

import com.knowledgespike.ballbyball.api.feature.health.domain.DatabaseHealth
import com.knowledgespike.ballbyball.contracts.ApiHealth
import com.knowledgespike.ballbyball.contracts.Envelope
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.routeHealth(databaseHealth: DatabaseHealth) {
    get("/health") {
        val healthy = databaseHealth.isHealthy()
        val status = if (healthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        val health = ApiHealth(status = if (healthy) "ok" else "unavailable", database = healthy)
        if (healthy) {
            call.respond(status, Envelope.success(health))
        } else {
            call.respond(status, Envelope.failure("Database is unhealthy", health))
        }
    }
}
