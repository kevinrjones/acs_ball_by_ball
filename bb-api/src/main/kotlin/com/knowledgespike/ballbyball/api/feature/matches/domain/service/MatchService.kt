package com.knowledgespike.ballbyball.api.feature.matches.domain.service

import com.knowledgespike.ballbyball.api.feature.matches.domain.repository.MatchRepository
import com.knowledgespike.ballbyball.contracts.MatchSummary

data class RecentMatchesRequest(
    val rawLimit: String?
)

sealed interface RecentMatchesResult {
    data class Success(val matches: List<MatchSummary>) : RecentMatchesResult
    data class InvalidLimit(val message: String) : RecentMatchesResult
}

interface MatchService {
    suspend fun recentMatches(request: RecentMatchesRequest): RecentMatchesResult
}

class DefaultMatchService(
    private val matchRepository: MatchRepository
) : MatchService {
    override suspend fun recentMatches(request: RecentMatchesRequest): RecentMatchesResult {
        val limit = when {
            request.rawLimit.isNullOrBlank() -> 10
            else -> {
                val parsed = request.rawLimit.toIntOrNull()
                if (parsed == null || parsed !in 1..100) {
                    return RecentMatchesResult.InvalidLimit("limit must be between 1 and 100")
                }
                parsed
            }
        }
        return RecentMatchesResult.Success(matchRepository.recentMatches(limit))
    }
}
