package com.knowledgespike.ballbyball.api.adapter.`in`.http

import com.knowledgespike.ballbyball.api.application.port.out.DatabaseHealth
import com.knowledgespike.ballbyball.api.application.service.MatchService
import com.knowledgespike.ballbyball.api.application.service.RecentMatchesRequest
import com.knowledgespike.ballbyball.api.application.service.RecentMatchesResult
import com.knowledgespike.ballbyball.api.bootstrap.AUTH_JWT
import com.knowledgespike.ballbyball.api.bootstrap.extractRoles
import com.knowledgespike.ballbyball.api.bootstrap.requireUserPrincipal
import com.knowledgespike.ballbyball.contracts.ApiError
import com.knowledgespike.ballbyball.contracts.ApiHealth
import com.knowledgespike.ballbyball.contracts.HeartbeatResponse
import com.knowledgespike.ballbyball.contracts.RecentMatchesResponse
import com.knowledgespike.ballbyball.contracts.UserProfileResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.registerApiRoutes(
    matchService: MatchService,
    databaseHealth: DatabaseHealth
) {
    get("/health") {
        val healthy = databaseHealth.isHealthy()
        call.respond(
            if (healthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
            ApiHealth(status = if (healthy) "ok" else "unavailable", database = healthy)
        )
    }

    route("/api") {
        // Public unauthenticated heartbeat endpoint
        get("/heartbeat/alive") {
            call.respond(HttpStatusCode.OK, HeartbeatResponse(message = "Heartbeat: Alive"))
        }

        // Machine & User authenticated routes
        authenticate(AUTH_JWT) {
            get("/matches") {
                when (val result = matchService.recentMatches(RecentMatchesRequest(call.request.queryParameters["limit"]))) {
                    is RecentMatchesResult.InvalidLimit -> call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(code = "invalid_limit", message = result.message)
                    )

                    is RecentMatchesResult.Success -> call.respond(
                        RecentMatchesResponse(matches = result.matches)
                    )
                }
            }

            // User-only authenticated route
            get("/user/profile") {
                val principal = call.requireUserPrincipal() ?: return@get
                val sub = principal.payload.subject ?: principal.payload.getClaim("sub")?.asString() ?: ""
                val name = principal.payload.getClaim("name")?.asString()
                val email = principal.payload.getClaim("email")?.asString()
                val roles = extractRoles(principal)

                call.respond(
                    HttpStatusCode.OK,
                    UserProfileResponse(
                        subject = sub,
                        name = name,
                        email = email,
                        roles = roles
                    )
                )
            }
        }
    }
}