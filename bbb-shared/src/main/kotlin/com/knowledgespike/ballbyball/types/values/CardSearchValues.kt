package com.knowledgespike.ballbyball.types.values

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.knowledgespike.ballbyball.types.error.Error
import com.knowledgespike.ballbyball.types.error.ExactMatchError
import com.knowledgespike.ballbyball.types.error.MatchResultFilterError
import com.knowledgespike.ballbyball.types.error.MatchTypeFilterError
import com.knowledgespike.ballbyball.types.error.SearchDateError
import com.knowledgespike.ballbyball.types.error.SearchDateRangeError
import com.knowledgespike.ballbyball.types.error.SearchTeamError
import com.knowledgespike.ballbyball.types.error.VenueFilterError
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Serializable
@JvmInline
value class SearchTeam private constructor(val value: String) {
    init {
        require(value.length in MIN_LENGTH..MAX_LENGTH) {
            "team name must be between $MIN_LENGTH and $MAX_LENGTH characters"
        }
    }

    companion object {
        const val MIN_LENGTH = 3
        const val MAX_LENGTH = 100

        context(raise: Raise<SearchTeamError>)
        operator fun invoke(value: String?): SearchTeam {
            val normalized = value?.trim().orEmpty()
            if (normalized.length !in MIN_LENGTH..MAX_LENGTH) {
                raise.raise(
                    SearchTeamError(
                        "team name must be between $MIN_LENGTH and $MAX_LENGTH characters",
                        value
                    )
                )
            }
            return SearchTeam(normalized)
        }

        fun fromRaw(value: String?): Either<SearchTeamError, SearchTeam> = either { invoke(value) }

        fun from(value: String): SearchTeam = SearchTeam(value.trim())
    }
}

@Serializable
@JvmInline
value class ExactMatch private constructor(val value: Boolean) {
    companion object {
        context(raise: Raise<ExactMatchError>)
        operator fun invoke(value: String?): ExactMatch {
            return when (value?.trim()?.lowercase()) {
                null, "" -> ExactMatch(false)
                "true" -> ExactMatch(true)
                "false" -> ExactMatch(false)
                else -> raise.raise(ExactMatchError("exact-match flag must be true or false", value))
            }
        }

        fun fromRaw(value: String?): Either<ExactMatchError, ExactMatch> = either { invoke(value) }

        fun from(value: Boolean): ExactMatch = ExactMatch(value)
    }
}

@Serializable
@JvmInline
value class SearchDate private constructor(val value: String) {
    init {
        require(isValidDate(value)) { "date must use YYYY-MM-DD format" }
    }

    val localDate: LocalDate
        get() = LocalDate.parse(value)

    companion object {
        context(raise: Raise<SearchDateError>)
        operator fun invoke(value: String?): SearchDate {
            val normalized = value?.trim().orEmpty()
            if (!isValidDate(normalized)) {
                raise.raise(SearchDateError("date must use YYYY-MM-DD format", value))
            }
            return SearchDate(normalized)
        }

        fun fromRaw(value: String?): Either<SearchDateError, SearchDate?> = either {
            val normalized = value?.trim()
            if (normalized.isNullOrEmpty()) null else invoke(normalized)
        }

        fun from(value: String): SearchDate = SearchDate(value.trim())

        private fun isValidDate(value: String): Boolean = try {
            LocalDate.parse(value)
            true
        } catch (_: DateTimeParseException) {
            false
        }
    }
}

data class SearchDateRange(
    val startDate: SearchDate?,
    val endDate: SearchDate?
) {
    companion object {
        fun fromRaw(startDate: String?, endDate: String?): Either<Error, SearchDateRange> = either {
            invoke(startDate, endDate)
        }

        context(raise: Raise<Error>)
        operator fun invoke(startDate: String?, endDate: String?): SearchDateRange {
            val start = SearchDate.fromRaw(startDate).fold(
                ifLeft = { raise.raise(it) },
                ifRight = { it }
            )
            val end = SearchDate.fromRaw(endDate).fold(
                ifLeft = { raise.raise(it) },
                ifRight = { it }
            )
            if (start != null && end != null && start.value > end.value) {
                raise.raise(SearchDateRangeError("start date must not be after end date", startDate, endDate))
            }
            return SearchDateRange(start, end)
        }
    }
}

@Serializable
@JvmInline
value class VenueFilter private constructor(val value: Int) {
    init {
        require(value in ALLOWED_VALUES) { "venue must be one of 0, 1, or 2" }
    }

    companion object {
        const val ALL = 0
        const val HOME = 1
        const val AWAY = 2
        val ALLOWED_VALUES = setOf(ALL, HOME, AWAY)

        context(raise: Raise<VenueFilterError>)
        operator fun invoke(value: String?): VenueFilter {
            val parsed = value?.trim()?.toIntOrNull() ?: ALL
            if (parsed !in ALLOWED_VALUES) {
                raise.raise(VenueFilterError("venue must be one of 0, 1, or 2; neutral venue is not available", value))
            }
            return VenueFilter(parsed)
        }

        fun fromRaw(value: String?): Either<VenueFilterError, VenueFilter> = either { invoke(value) }

        fun from(value: Int): VenueFilter = VenueFilter(value)
    }
}

@Serializable
@JvmInline
value class MatchTypeFilter private constructor(val value: String) {
    init {
        require(value in ALLOWED_VALUES) { "matchType is not supported" }
    }

    companion object {
        const val ALL = "all"
        const val ODI = "o"
        const val ODI_STANDARD = "odi"
        const val WOMENS_ODI = "wo"
        const val ODI_WOMENS = "wodi"
        val ALLOWED_VALUES = setOf("all", "t", "o", "itt", "f", "a", "tt", "wt", "wo", "witt", "wf", "wa", "wtt", "sec")

        context(raise: Raise<MatchTypeFilterError>)
        operator fun invoke(value: String?): MatchTypeFilter {
            val normalized = value?.trim()?.lowercase() ?: ALL
            if (normalized !in ALLOWED_VALUES) {
                raise.raise(MatchTypeFilterError("matchType is not supported", value))
            }
            return MatchTypeFilter(normalized)
        }

        fun fromRaw(value: String?): Either<MatchTypeFilterError, MatchTypeFilter> = either { invoke(value) }

        fun from(value: String): MatchTypeFilter = MatchTypeFilter(value)
    }
}

@Serializable
@JvmInline
value class MatchResultFilter private constructor(val value: Int) {
    init {
        require(value in MIN_VALUE..MAX_VALUE) { "matchResult must be between $MIN_VALUE and $MAX_VALUE" }
    }

    companion object {
        const val ALL = 0
        const val WON = 1
        const val WON_BY_INNINGS = 2
        const val WON_BY_RUNS = 3
        const val WON_BY_WICKETS = 4
        const val LOST = 5
        const val LOST_BY_INNINGS = 6
        const val LOST_BY_RUNS = 7
        const val LOST_BY_WICKETS = 8
        const val DRAWN = 9
        const val TIED = 10
        const val NO_RESULT = 11
        const val MIN_VALUE = ALL
        const val MAX_VALUE = NO_RESULT

        context(raise: Raise<MatchResultFilterError>)
        operator fun invoke(value: String?): MatchResultFilter {
            val parsed = value?.trim()?.toIntOrNull() ?: ALL
            if (parsed !in MIN_VALUE..MAX_VALUE) {
                raise.raise(MatchResultFilterError("matchResult must be between $MIN_VALUE and $MAX_VALUE", value))
            }
            return MatchResultFilter(parsed)
        }

        fun fromRaw(value: String?): Either<MatchResultFilterError, MatchResultFilter> = either { invoke(value) }

        fun from(value: Int): MatchResultFilter = MatchResultFilter(value)
    }
}