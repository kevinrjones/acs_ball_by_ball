package com.knowledgespike.ballbyball.api.application.service

import com.knowledgespike.ballbyball.api.application.port.out.MatchRepository
import com.knowledgespike.ballbyball.contracts.MatchSummary

data class RecentMatchesRequest(val rawLimit: String?)

sealed interface RecentMatchesResult {
    data class Success(val matches: List<MatchSummary>) : RecentMatchesResult

    data class InvalidLimit(val message: String) : RecentMatchesResult
}

interface MatchService {
    suspend fun recentMatches(request: RecentMatchesRequest): RecentMatchesResult
}

class DefaultMatchService(
    private val matchRepository: MatchRepository
) : MatchService {
    override suspend fun recentMatches(request: RecentMatchesRequest): RecentMatchesResult = when (
        val limit = MatchLimit.from(request.rawLimit)
    ) {
        is MatchLimitResult.Invalid -> RecentMatchesResult.InvalidLimit(limit.message)
        is MatchLimitResult.Valid -> RecentMatchesResult.Success(matchRepository.recentMatches(limit.value.value))
    }
}

@JvmInline
value class MatchLimit private constructor(val value: Int) {
    companion object {
        const val DEFAULT = 25
        private const val MINIMUM = 1
        private const val MAXIMUM = 100

        fun from(rawValue: String?): MatchLimitResult {
            if (rawValue == null) return MatchLimitResult.Valid(MatchLimit(DEFAULT))

            val value = rawValue.toIntOrNull()
                ?: return MatchLimitResult.Invalid("limit must be a number")
            return if (value in MINIMUM..MAXIMUM) {
                MatchLimitResult.Valid(MatchLimit(value))
            } else {
                MatchLimitResult.Invalid("limit must be between $MINIMUM and $MAXIMUM")
            }
        }
    }
}

sealed interface MatchLimitResult {
    data class Valid(val value: MatchLimit) : MatchLimitResult

    data class Invalid(val message: String) : MatchLimitResult
}