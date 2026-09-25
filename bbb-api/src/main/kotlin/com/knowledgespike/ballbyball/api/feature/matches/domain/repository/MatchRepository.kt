package com.knowledgespike.ballbyball.api.feature.matches.domain.repository

import com.knowledgespike.ballbyball.contracts.MatchSummary
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchSearchCriteria
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchSearchPage
import com.knowledgespike.ballbyball.types.values.Limit

interface MatchRepository {
    suspend fun recentMatches(limit: Limit): List<MatchSummary>

    suspend fun searchMatches(criteria: MatchSearchCriteria): MatchSearchPage
}
