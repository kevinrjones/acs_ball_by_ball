package com.knowledgespike.ballbyball.api.feature.matches.domain.service

import com.knowledgespike.ballbyball.api.feature.matches.domain.repository.MatchRepository
import com.knowledgespike.ballbyball.contracts.MatchSummary
import com.knowledgespike.ballbyball.types.values.Limit

interface MatchService {
    suspend fun recentMatches(limit: Limit): List<MatchSummary>
}

class DefaultMatchService(
    private val matchRepository: MatchRepository
) : MatchService {
    override suspend fun recentMatches(limit: Limit): List<MatchSummary> =
        matchRepository.recentMatches(limit)
}
