package com.knowledgespike.ballbyball.types.values

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.knowledgespike.ballbyball.types.error.MatchTypeError
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class MatchType private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "matchType must not be blank" }
    }

    companion object {
        context(raise: Raise<MatchTypeError>)
        operator fun invoke(value: String?): MatchType {
            if (value.isNullOrBlank()) {
                raise.raise(MatchTypeError("matchType must not be blank", value))
            }
            return MatchType(value.trim())
        }

        fun fromRaw(value: String?): Either<MatchTypeError, MatchType> = either {
            invoke(value)
        }

        fun of(value: String): Either<MatchTypeError, MatchType> = either {
            invoke(value)
        }

        fun from(value: String): MatchType {
            require(value.isNotBlank()) { "matchType must not be blank" }
            return MatchType(value.trim())
        }
    }

    override fun toString(): String = value
}
