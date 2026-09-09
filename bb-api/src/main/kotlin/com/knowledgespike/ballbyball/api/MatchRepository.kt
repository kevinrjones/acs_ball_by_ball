package com.knowledgespike.ballbyball.api

import com.knowledgespike.ballbyball.contracts.MatchSummary

interface MatchRepository {
    suspend fun isHealthy(): Boolean

    suspend fun recentMatches(limit: Int): List<MatchSummary>
}