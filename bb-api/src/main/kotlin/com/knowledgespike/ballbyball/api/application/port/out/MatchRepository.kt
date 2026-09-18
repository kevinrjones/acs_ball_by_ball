package com.knowledgespike.ballbyball.api.application.port.out

import com.knowledgespike.ballbyball.contracts.MatchSummary

interface MatchRepository {
    suspend fun recentMatches(limit: Int): List<MatchSummary>
}