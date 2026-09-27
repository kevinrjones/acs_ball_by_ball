package com.knowledgespike.ballbyball.contracts

import arrow.core.Either
import arrow.core.NonEmptyList
import com.knowledgespike.ballbyball.types.error.Error
import com.knowledgespike.ballbyball.types.values.MatchKey

data class MatchScoresheetRequest(
    val matchKey: MatchKey
)

fun parseMatchScoresheetRequest(
    parameter: (String) -> String?
): Either<NonEmptyList<Error>, MatchScoresheetRequest> {
    val errors = mutableListOf<Error>()
    var matchKey: MatchKey? = null

    MatchKey.fromRaw(parameter("matchKey")).fold({ errors += it }, { matchKey = it })

    return if (errors.isEmpty()) {
        Either.Right(MatchScoresheetRequest(requireNotNull(matchKey)))
    } else {
        Either.Left(NonEmptyList(errors.first(), errors.drop(1)))
    }
}