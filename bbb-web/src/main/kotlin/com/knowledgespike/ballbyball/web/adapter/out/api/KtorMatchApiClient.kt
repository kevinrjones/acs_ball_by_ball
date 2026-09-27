package com.knowledgespike.ballbyball.web.adapter.out.api

import com.knowledgespike.ballbyball.contracts.Envelope
import com.knowledgespike.ballbyball.contracts.MatchSearchRequest
import com.knowledgespike.ballbyball.contracts.MatchSearchResponse
import com.knowledgespike.ballbyball.contracts.MatchScoresheetResponse
import com.knowledgespike.ballbyball.contracts.RecentMatchesResponse
import com.knowledgespike.ballbyball.web.application.MatchApiClient
import com.knowledgespike.ballbyball.web.application.MatchScoresheetResult
import com.knowledgespike.ballbyball.web.application.RecentMatchesResult
import com.knowledgespike.ballbyball.web.application.SearchMatchesResult
import com.knowledgespike.ballbyball.web.domain.service.TokenService
import com.knowledgespike.ballbyball.types.values.MatchKey
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

class KtorMatchApiClient(
    apiBaseUrl: String,
    private val httpClient: HttpClient,
    private val tokenService: TokenService? = null
) : MatchApiClient {
    private val baseUrl = apiBaseUrl.trimEnd('/')
    private val log = LoggerFactory.getLogger(KtorMatchApiClient::class.java)

    override suspend fun recentMatches(): RecentMatchesResult = try {
        val token = tokenService?.getAccessToken()
        val response = httpClient.get("$baseUrl/api/matches") {
            if (!token.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
        if (!response.status.isSuccess()) {
            RecentMatchesResult.Unavailable(response.status)
        } else {
            val envelope = response.body<Envelope<RecentMatchesResponse>>()
            RecentMatchesResult.Success(envelope.result.matches, envelope.timeGenerated)
        }
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        log.warn("API request failed", cause)
        RecentMatchesResult.Unavailable()
    }

    override suspend fun searchMatches(request: MatchSearchRequest): SearchMatchesResult = try {
        val token = tokenService?.getAccessToken()
        val response = httpClient.get("$baseUrl/api/matches/search") {
            parameter("team", request.team.value)
            parameter("teamExactMatch", request.teamExactMatch.value)
            parameter("opponents", request.opponents.value)
            parameter("opponentsExactMatch", request.opponentsExactMatch.value)
            parameter("venue", request.venue.value)
            request.startDate?.let { parameter("startDate", it.value) }
            request.endDate?.let { parameter("endDate", it.value) }
            parameter("matchType", request.matchType.value)
            parameter("matchResult", request.matchResult.value)
            parameter("page", request.page.value)
            parameter("pageSize", request.pageSize.value)
            if (!token.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
        if (!response.status.isSuccess()) {
            SearchMatchesResult.Unavailable(response.status)
        } else {
            val envelope = response.body<Envelope<MatchSearchResponse>>()
            SearchMatchesResult.Success(envelope.result, envelope.timeGenerated)
        }
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        log.warn("Historical match search request failed", cause)
        SearchMatchesResult.Unavailable()
    }

    override suspend fun scoresheet(
        matchKey: MatchKey
    ): MatchScoresheetResult = try {
        val token = tokenService?.getAccessToken()
        val response = httpClient.get("$baseUrl/api/matches/${matchKey.value}/scoresheet") {
            if (!token.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
        if (!response.status.isSuccess()) {
            MatchScoresheetResult.Unavailable(response.status)
        } else {
            val envelope = response.body<Envelope<MatchScoresheetResponse>>()
            MatchScoresheetResult.Success(envelope.result, envelope.timeGenerated)
        }
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        log.warn("Historical match scoresheet request failed", cause)
        MatchScoresheetResult.Unavailable()
    }
}