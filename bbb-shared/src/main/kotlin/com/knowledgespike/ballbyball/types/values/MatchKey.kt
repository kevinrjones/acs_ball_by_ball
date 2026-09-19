package com.knowledgespike.ballbyball.types.values

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.knowledgespike.ballbyball.types.error.MatchKeyError
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class MatchKey private constructor(val value: Long) {
    init {
        require(value > 0) { "matchKey must be greater than 0" }
    }

    companion object {
        context(raise: Raise<MatchKeyError>)
        operator fun invoke(value: Long?): MatchKey {
            if (value == null || value <= 0) {
                raise.raise(MatchKeyError("matchKey must be greater than 0", value))
            }
            return MatchKey(value)
        }

        context(raise: Raise<MatchKeyError>)
        operator fun invoke(value: String?): MatchKey {
            val parsed = value?.toLongOrNull()
            if (parsed == null || parsed <= 0) {
                raise.raise(MatchKeyError("matchKey must be a valid positive number", parsed))
            }
            return MatchKey(parsed)
        }

        fun fromRaw(value: String?): Either<MatchKeyError, MatchKey> = either {
            invoke(value)
        }

        fun of(value: Long): Either<MatchKeyError, MatchKey> = either {
            invoke(value)
        }

        fun from(value: Long): MatchKey {
            require(value > 0) { "matchKey must be greater than 0" }
            return MatchKey(value)
        }
    }

    override fun toString(): String = value.toString()
}
