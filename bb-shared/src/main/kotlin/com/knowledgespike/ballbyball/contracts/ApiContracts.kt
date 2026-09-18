package com.knowledgespike.ballbyball.contracts

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
    val subject: String,
    val name: String? = null,
    val email: String? = null,
    val roles: List<String> = emptyList()
)

@Serializable
data class MatchSummary(
    val matchKey: Long,
    val sourceMatchId: Int,
    val fileName: String,
    val matchType: String,
    val season: String
)

@Serializable
data class RecentMatchesResponse(
    val matches: List<MatchSummary>
)

@Serializable
data class ApiError(
    val code: String,
    val message: String
)