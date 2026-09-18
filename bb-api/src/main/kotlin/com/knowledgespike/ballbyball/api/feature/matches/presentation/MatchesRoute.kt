package com.knowledgespike.ballbyball.api.feature.matches.presentation

import com.knowledgespike.ballbyball.api.bootstrap.AUTH_JWT
import com.knowledgespike.ballbyball.api.feature.matches.domain.service.MatchService
import com.knowledgespike.ballbyball.api.feature.matches.domain.service.RecentMatchesRequest
import com.knowledgespike.ballbyball.api.feature.matches.domain.service.RecentMatchesResult
import com.knowledgespike.ballbyball.contracts.Envelope
import com.knowledgespike.ballbyball.contracts.RecentMatchesResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.routeMatches(matchService: MatchService) {
    authenticate(AUTH_JWT) {
        route("/api") {
            get("/matches") {
                when (val result = matchService.recentMatches(RecentMatchesRequest(call.request.queryParameters["limit"]))) {
                    is RecentMatchesResult.InvalidLimit -> call.respond(
                        HttpStatusCode.BadRequest,
                        Envelope.failure(result.message)
                    )

                    is RecentMatchesResult.Success -> call.respond(
                        HttpStatusCode.OK,
                        Envelope.success(RecentMatchesResponse(matches = result.matches))
                    )
                }
            }
        }
    }
}
