package com.knowledgespike.ballbyball.api.feature.matches.presentation

import arrow.core.raise.fold
import com.knowledgespike.ballbyball.api.bootstrap.AUTH_JWT
import com.knowledgespike.ballbyball.api.feature.matches.domain.service.MatchService
import com.knowledgespike.ballbyball.api.routing.respondBadRequest
import com.knowledgespike.ballbyball.api.routing.respondOk
import com.knowledgespike.ballbyball.contracts.RecentMatchesResponse
import com.knowledgespike.ballbyball.types.values.Limit
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.routeMatches(matchService: MatchService) {
    authenticate(AUTH_JWT) {
        route("/api") {
            get("/matches") {
                fold(
                    block = { Limit(call.request.queryParameters["days"] ?: call.request.queryParameters["limit"]) },
                    recover = { error -> call.respondBadRequest(error.message) },
                    transform = { limit ->
                        val matches = matchService.recentMatches(limit)
                        call.respondOk(RecentMatchesResponse(matches = matches))
                    }
                )
            }
        }
    }
}
