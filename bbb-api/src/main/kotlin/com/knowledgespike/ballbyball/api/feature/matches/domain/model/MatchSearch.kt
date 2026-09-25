package com.knowledgespike.ballbyball.api.feature.matches.domain.model

import com.knowledgespike.ballbyball.contracts.MatchSearchRequest
import com.knowledgespike.ballbyball.types.values.ExactMatch
import com.knowledgespike.ballbyball.types.values.MatchKey
import com.knowledgespike.ballbyball.types.values.MatchResultFilter
import com.knowledgespike.ballbyball.types.values.MatchType
import com.knowledgespike.ballbyball.types.values.MatchTypeFilter
import com.knowledgespike.ballbyball.types.values.PageNumber
import com.knowledgespike.ballbyball.types.values.PageSize
import com.knowledgespike.ballbyball.types.values.SearchDate
import com.knowledgespike.ballbyball.types.values.SearchTeam
import com.knowledgespike.ballbyball.types.values.Season
import com.knowledgespike.ballbyball.types.values.SourceMatchId
import com.knowledgespike.ballbyball.types.values.VenueFilter

data class MatchSearchCriteria(
    val team: SearchTeam,
    val teamExactMatch: ExactMatch,
    val opponents: SearchTeam,
    val opponentsExactMatch: ExactMatch,
    val venue: VenueFilter,
    val startDate: SearchDate?,
    val endDate: SearchDate?,
    val matchType: MatchTypeFilter,
    val matchResult: MatchResultFilter,
    val page: PageNumber,
    val pageSize: PageSize
) {
    companion object {
        fun from(request: MatchSearchRequest): MatchSearchCriteria = MatchSearchCriteria(
            team = request.team,
            teamExactMatch = request.teamExactMatch,
            opponents = request.opponents,
            opponentsExactMatch = request.opponentsExactMatch,
            venue = request.venue,
            startDate = request.startDate,
            endDate = request.endDate,
            matchType = request.matchType,
            matchResult = request.matchResult,
            page = request.page,
            pageSize = request.pageSize
        )
    }
}

data class MatchSearchMatch(
    val matchKey: MatchKey,
    val sourceMatchId: SourceMatchId,
    val fileName: String,
    val matchType: MatchType?,
    val season: Season?,
    val competition: String? = null,
    val date: String? = null,
    val team1: String?,
    val team2: String?,
    val ground: String? = null,
    val result: String? = null
)

data class MatchSearchPage(
    val page: PageNumber,
    val pageSize: PageSize,
    val totalResults: Int,
    val hasNext: Boolean,
    val nextPage: PageNumber?,
    val matches: List<MatchSearchMatch>
)