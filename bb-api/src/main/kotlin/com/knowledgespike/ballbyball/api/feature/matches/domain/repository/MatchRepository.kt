package com.knowledgespike.ballbyball.api.feature.matches.domain.repository

import com.knowledgespike.ballbyball.contracts.MatchSummary

interface MatchRepository {
    suspend fun recentMatches(limit: Int): List<MatchSummary>
}
