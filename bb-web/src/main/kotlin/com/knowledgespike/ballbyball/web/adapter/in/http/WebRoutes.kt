package com.knowledgespike.ballbyball.web.adapter.`in`.http

import com.knowledgespike.ballbyball.contracts.Envelope
import com.knowledgespike.ballbyball.contracts.RecentMatchesResponse
import com.knowledgespike.ballbyball.web.application.MatchApiClient
import com.knowledgespike.ballbyball.web.application.MatchApiResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.registerWebRoutes(matchApiClient: MatchApiClient) {
    route("/api") {
        get("/matches") {
            when (val result = matchApiClient.recentMatches()) {
                is MatchApiResult.Success -> call.respond(
                    HttpStatusCode.OK,
                    Envelope.success(RecentMatchesResponse(result.matches))
                )

                is MatchApiResult.Unavailable -> call.respond(
                    HttpStatusCode.BadGateway,
                    Envelope.failure(
                        result.status?.let { "The API is unavailable (${it.value})." }
                            ?: "The API is unavailable."
                    )
                )
            }
        }
    }

    get("/matches") {
        when (val result = matchApiClient.recentMatches()) {
            is MatchApiResult.Success -> call.respond(
                HttpStatusCode.OK,
                Envelope.success(RecentMatchesResponse(result.matches))
            )

            is MatchApiResult.Unavailable -> call.respond(
                HttpStatusCode.BadGateway,
                Envelope.failure(
                    result.status?.let { "The API is unavailable (${it.value})." }
                        ?: "The API is unavailable."
                )
            )
        }
    }

    singlePageApplication {
        useResources = true
        filesPath = "static/browser"
        defaultPage = "index.html"
    }
}
