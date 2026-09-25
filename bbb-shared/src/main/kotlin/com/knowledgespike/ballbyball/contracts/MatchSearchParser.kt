package com.knowledgespike.ballbyball.contracts

import arrow.core.NonEmptyList
import arrow.core.Either
import com.knowledgespike.ballbyball.types.error.Error
import com.knowledgespike.ballbyball.types.values.ExactMatch
import com.knowledgespike.ballbyball.types.values.MatchResultFilter
import com.knowledgespike.ballbyball.types.values.MatchTypeFilter
import com.knowledgespike.ballbyball.types.values.PageNumber
import com.knowledgespike.ballbyball.types.values.PageSize
import com.knowledgespike.ballbyball.types.values.SearchDateRange
import com.knowledgespike.ballbyball.types.values.SearchTeam
import com.knowledgespike.ballbyball.types.values.VenueFilter

fun parseMatchSearchRequest(
    parameter: (String) -> String?
): Either<NonEmptyList<Error>, MatchSearchRequest> {
    val accumulator = ValidationAccumulator()
    val team = accumulator.read(SearchTeam.fromRaw(parameter("team")))
    val teamExactMatch = accumulator.read(ExactMatch.fromRaw(parameter("teamExactMatch")))
    val opponents = accumulator.read(SearchTeam.fromRaw(parameter("opponents")))
    val opponentsExactMatch = accumulator.read(ExactMatch.fromRaw(parameter("opponentsExactMatch")))
    val venue = accumulator.read(VenueFilter.fromRaw(parameter("venue")))
    val dates = accumulator.read(
        SearchDateRange.fromRaw(parameter("startDate"), parameter("endDate"))
    )
    val matchType = accumulator.read(MatchTypeFilter.fromRaw(parameter("matchType")))
    val matchResult = accumulator.read(MatchResultFilter.fromRaw(parameter("matchResult")))
    val page = accumulator.read(PageNumber.fromRaw(parameter("page")))
    val pageSize = accumulator.read(PageSize.fromRaw(parameter("pageSize")))

    return accumulator.finish {
        MatchSearchRequest(
            team = team!!,
            teamExactMatch = teamExactMatch!!,
            opponents = opponents!!,
            opponentsExactMatch = opponentsExactMatch!!,
            venue = venue!!,
            startDate = dates!!.startDate,
            endDate = dates.endDate,
            matchType = matchType!!,
            matchResult = matchResult!!,
            page = page!!,
            pageSize = pageSize!!
        )
    }
}

private class ValidationAccumulator {
    private val errors = mutableListOf<Error>()

    @Suppress("UNCHECKED_CAST")
    fun <A> read(value: Either<*, A>): A? = value.fold(
        ifLeft = { errors += it as Error; null },
        ifRight = { it }
    )

    fun <A> finish(build: () -> A): Either<NonEmptyList<Error>, A> =
        if (errors.isEmpty()) {
            Either.Right(build())
        } else {
            Either.Left(NonEmptyList(errors.first(), errors.drop(1)))
        }
}