package com.knowledgespike.ballbyball.contracts

import kotlinx.serialization.Serializable

@Serializable
data class ApiHealth(
    val status: String,
    val database: Boolean
)

@Serializable
data class MatchSummary(
    val matchKey: Long,
    val sourceMatchId: Int,
    val fileName: String,
    val matchType: String,
    val season: String
)