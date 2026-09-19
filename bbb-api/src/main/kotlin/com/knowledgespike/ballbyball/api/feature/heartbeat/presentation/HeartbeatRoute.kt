package com.knowledgespike.ballbyball.api.feature.heartbeat.presentation

import com.knowledgespike.ballbyball.contracts.Envelope
import com.knowledgespike.ballbyball.contracts.HeartbeatResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.routeHeartbeat() {
    route("/api") {
        get("/heartbeat/alive") {
            call.respond(HttpStatusCode.OK, Envelope.success(HeartbeatResponse(message = "Heartbeat: Alive")))
        }
    }
}
