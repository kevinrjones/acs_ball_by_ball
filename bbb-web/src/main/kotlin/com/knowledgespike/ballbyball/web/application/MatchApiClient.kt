package com.knowledgespike.ballbyball.web.application

import com.knowledgespike.ballbyball.contracts.MatchSearchRequest
import com.knowledgespike.ballbyball.contracts.MatchSearchResponse
import com.knowledgespike.ballbyball.contracts.MatchSummary
import io.ktor.http.HttpStatusCode
import kotlin.time.Instant

sealed interface RecentMatchesResult {
    data class Success(
        val matches: List<MatchSummary>,
        val timeGenerated: Instant? = null
    ) : RecentMatchesResult

    data class Unavailable(val status: HttpStatusCode? = null) : RecentMatchesResult
}

sealed interface SearchMatchesResult {
    data class Success(
        val response: MatchSearchResponse,
        val timeGenerated: Instant? = null
    ) : SearchMatchesResult

    data class Unavailable(val status: HttpStatusCode? = null) : SearchMatchesResult
}

interface MatchApiClient {
    suspend fun recentMatches(): RecentMatchesResult

    suspend fun searchMatches(request: MatchSearchRequest): SearchMatchesResult
}
