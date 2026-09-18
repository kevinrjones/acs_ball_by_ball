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
    val season: Season
) {
    companion object {
        fun of(
            matchKey: Long,
            sourceMatchId: Int,
            fileName: String,
            matchType: String,
            season: String
        ): MatchSummary = MatchSummary(
            MatchKey.from(matchKey),
            SourceMatchId.from(sourceMatchId),
            fileName,
            MatchType.from(matchType),
            Season.from(season)
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