package com.knowledgespike.ballbyball.web.adapter.`in`.http

import com.knowledgespike.ballbyball.contracts.Envelope
import com.knowledgespike.ballbyball.contracts.MatchSearchRequest
import com.knowledgespike.ballbyball.contracts.parseMatchSearchRequest
import com.knowledgespike.ballbyball.contracts.RecentMatchesResponse
import com.knowledgespike.ballbyball.web.application.MatchApiClient
import com.knowledgespike.ballbyball.web.application.ApplicationMetadataService
import com.knowledgespike.ballbyball.web.application.RecentMatchesResult
import com.knowledgespike.ballbyball.web.application.SearchMatchesResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.registerWebRoutes(
    matchApiClient: MatchApiClient,
    applicationMetadataService: ApplicationMetadataService = ApplicationMetadataService(matchApiClient)
) {
    suspend fun io.ktor.server.application.ApplicationCall.respondMatches() {
        when (val result = matchApiClient.recentMatches()) {
            is RecentMatchesResult.Success -> respond(
                HttpStatusCode.OK,
                Envelope.success(RecentMatchesResponse(result.matches))
            )

            is RecentMatchesResult.Unavailable -> respond(
                HttpStatusCode.BadGateway,
                Envelope.failure(
                    result.status?.let { "The API is unavailable (${it.value})." }
                        ?: "The API is unavailable."
                )
            )

        }
    }

    route("/api") {
        get("/matches") { call.respondMatches() }
        authenticate("kbff-oidc") {
            get("/matches/search") {
                parseMatchSearchRequest(call.request.queryParameters::get).fold(
                    ifLeft = { errors ->
                        call.respond(HttpStatusCode.BadRequest, Envelope.failure(errors))
                    },
                    ifRight = { request ->
                        when (val result = matchApiClient.searchMatches(request)) {
                            is SearchMatchesResult.Success -> call.respond(
                                HttpStatusCode.OK,
                                Envelope.success(result.response)
                            )

                            is SearchMatchesResult.Unavailable -> call.respond(
                                HttpStatusCode.BadGateway,
                                Envelope.failure(
                                    result.status?.let { "The API is unavailable (${it.value})." }
                                        ?: "The API is unavailable."
                                )
                            )
                        }
                    }
                )
            }
        }
        get("/metadata") {
            call.respond(HttpStatusCode.OK, Envelope.success(applicationMetadataService.metadata()))
        }
    }

    get("/matches") { call.respondMatches() }

    singlePageApplication {
        useResources = true
        filesPath = "static/browser"
        defaultPage = "index.html"
    }
}
