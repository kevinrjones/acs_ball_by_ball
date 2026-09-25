package com.knowledgespike.ballbyball.api.feature.matches.presentation

import arrow.core.raise.fold
import com.knowledgespike.ballbyball.api.bootstrap.AUTH_JWT
import com.knowledgespike.ballbyball.api.feature.health.data.repository.JooqDatabaseHealth
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchSearchCriteria
import com.knowledgespike.ballbyball.api.feature.matches.domain.service.MatchService
import com.knowledgespike.ballbyball.api.routing.respondBadRequest
import com.knowledgespike.ballbyball.api.routing.respondOk
import com.knowledgespike.ballbyball.contracts.MatchSearchPagination
import com.knowledgespike.ballbyball.contracts.MatchSearchResponse
import com.knowledgespike.ballbyball.contracts.MatchSearchResult
import com.knowledgespike.ballbyball.contracts.parseMatchSearchRequest
import com.knowledgespike.ballbyball.contracts.RecentMatchesResponse
import com.knowledgespike.ballbyball.contracts.MatchSearchRequest
import com.knowledgespike.ballbyball.types.values.Limit
import io.ktor.server.auth.authenticate
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

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
            get("/matches/search") {
                parseMatchSearchRequest(call.request.queryParameters::get).fold(
                    ifLeft = { errors -> call.respondBadRequest(errors) },
                    ifRight = { request ->
                        val page = matchService.searchMatches(MatchSearchCriteria.from(request))
                        call.respondOk(page.toResponse())
                    }
                )
            }
        }
    }
}

private fun com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchSearchPage.toResponse() =
    MatchSearchResponse(
        matches = matches.map {
            MatchSearchResult(
                matchKey = it.matchKey,
                sourceMatchId = it.sourceMatchId,
                fileName = it.fileName,
                matchType = it.matchType,
                season = it.season,
                competition = it.competition,
                date = it.date,
                team1 = it.team1,
                team2 = it.team2,
                ground = it.ground,
                result = it.result
            )
        },
        pagination = MatchSearchPagination(page, pageSize, totalResults, hasNext, nextPage)
    )
