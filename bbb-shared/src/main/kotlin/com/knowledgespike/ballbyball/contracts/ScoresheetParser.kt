package com.knowledgespike.ballbyball.contracts

import arrow.core.Either
import arrow.core.NonEmptyList
import com.knowledgespike.ballbyball.types.error.Error
import com.knowledgespike.ballbyball.types.values.PublicMatchId

data class MatchScoresheetRequest(
    val publicMatchId: PublicMatchId
)

fun parseMatchScoresheetRequest(
    parameter: (String) -> String?
): Either<NonEmptyList<Error>, MatchScoresheetRequest> {
    val errors = mutableListOf<Error>()
    var publicMatchId: PublicMatchId? = null

    PublicMatchId.fromRaw(parameter("publicMatchId")).fold({ errors += it }, { publicMatchId = it })

    return if (errors.isEmpty()) {
        Either.Right(MatchScoresheetRequest(requireNotNull(publicMatchId)))
    } else {
        Either.Left(NonEmptyList(errors.first(), errors.drop(1)))
    }
}