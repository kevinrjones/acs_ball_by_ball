package com.knowledgespike.ballbyball.api

import com.knowledgespike.ballbyball.contracts.MatchSummary

interface MatchRepository {
    fun isHealthy(): Boolean

    fun recentMatches(limit: Int): List<MatchSummary>
}