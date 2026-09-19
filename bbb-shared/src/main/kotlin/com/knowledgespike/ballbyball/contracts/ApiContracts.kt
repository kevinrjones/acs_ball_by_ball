package com.knowledgespike.ballbyball.contracts

import com.knowledgespike.ballbyball.types.values.MatchKey
import com.knowledgespike.ballbyball.types.values.MatchType
import com.knowledgespike.ballbyball.types.values.Season
import com.knowledgespike.ballbyball.types.values.SourceMatchId
import com.knowledgespike.ballbyball.types.values.UserId
import kotlinx.serialization.Serializable

@Serializable
data class ApiHealth(
    val status: String,
    val database: Boolean
)

@Serializable
data class HeartbeatResponse(
    val message: String
)

@Serializable
data class UserProfileResponse(
    val subject: UserId,
    val name: String? = null,
    val email: String? = null,
    val roles: List<String> = emptyList()
) {
    companion object {
        fun of(
            subject: String,
            name: String? = null,
            email: String? = null,
            roles: List<String> = emptyList()
        ): UserProfileResponse = UserProfileResponse(UserId.from(subject), name, email, roles)
    }
}

@Serializable
data class MatchSummary(
    val matchKey: MatchKey,
    val sourceMatchId: SourceMatchId,
    val fileName: String,
    val matchType: MatchType,
    val season: Season,
    val competition: String = "MISSING",
    val date: String = "MISSING",
    val team1: String = "MISSING",
    val score1: String = "MISSING",
    val overs1: String? = null,
    val isTeam1Winner: Boolean = false,
    val team2: String = "MISSING",
    val score2: String = "MISSING",
    val overs2: String? = null,
    val isTeam2Winner: Boolean = false,
    val result: String = "MISSING",
    val format: String = "MISSING"
) {
    companion object {
        fun of(
            matchKey: Long,
            sourceMatchId: Int,
            fileName: String,
            matchType: String,
            season: String,
            competition: String = "MISSING",
            date: String = "MISSING",
            team1: String = "MISSING",
            score1: String = "MISSING",
            overs1: String? = null,
            isTeam1Winner: Boolean = false,
            team2: String = "MISSING",
            score2: String = "MISSING",
            overs2: String? = null,
            isTeam2Winner: Boolean = false,
            result: String = "MISSING",
            format: String = "MISSING"
        ): MatchSummary = MatchSummary(
            MatchKey.from(matchKey),
            SourceMatchId.from(sourceMatchId),
            fileName,
            MatchType.from(matchType),
            Season.from(season),
            competition,
            date,
            team1,
            score1,
            overs1,
            isTeam1Winner,
            team2,
            score2,
            overs2,
            isTeam2Winner,
            result,
            format
        )
    }
}

@Serializable
data class RecentMatchesResponse(
    val matches: List<MatchSummary>
)

@Serializable
data class ApiError(
    val code: String,
    val message: String
)