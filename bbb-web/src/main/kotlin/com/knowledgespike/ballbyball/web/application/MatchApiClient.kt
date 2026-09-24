package com.knowledgespike.ballbyball.web.application

import com.knowledgespike.ballbyball.contracts.MatchSummary
import io.ktor.http.HttpStatusCode
import kotlin.time.Instant

sealed interface MatchApiResult {
    data class Success(
        val matches: List<MatchSummary>,
        val timeGenerated: Instant? = null
    ) : MatchApiResult

    data class Unavailable(val status: HttpStatusCode? = null) : MatchApiResult
}

interface MatchApiClient {
    suspend fun recentMatches(): MatchApiResult
}