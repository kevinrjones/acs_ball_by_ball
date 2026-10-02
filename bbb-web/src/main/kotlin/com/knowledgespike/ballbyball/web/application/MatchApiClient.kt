package com.knowledgespike.ballbyball.web.application

import com.knowledgespike.ballbyball.contracts.MatchSearchRequest
import com.knowledgespike.ballbyball.contracts.MatchSearchResponse
import com.knowledgespike.ballbyball.contracts.MatchScoresheetResponse
import com.knowledgespike.ballbyball.contracts.MatchSummary
import com.knowledgespike.ballbyball.types.values.PublicMatchId
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

sealed interface MatchScoresheetResult {
    data class Success(
        val response: MatchScoresheetResponse,
        val timeGenerated: Instant? = null
    ) : MatchScoresheetResult

    data class Unavailable(val status: HttpStatusCode? = null) : MatchScoresheetResult
}

interface MatchApiClient {
    suspend fun recentMatches(): RecentMatchesResult

    suspend fun searchMatches(request: MatchSearchRequest): SearchMatchesResult

    suspend fun scoresheet(publicMatchId: PublicMatchId): MatchScoresheetResult
}
