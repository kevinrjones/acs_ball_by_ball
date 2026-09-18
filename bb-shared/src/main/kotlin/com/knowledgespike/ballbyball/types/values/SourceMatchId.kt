package com.knowledgespike.ballbyball.types.values

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.knowledgespike.ballbyball.types.error.SourceMatchIdError
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class SourceMatchId private constructor(val value: Int) {
    init {
        require(value >= 0) { "sourceMatchId must not be negative" }
    }

    companion object {
        context(raise: Raise<SourceMatchIdError>)
        operator fun invoke(value: Int?): SourceMatchId {
            if (value == null || value < 0) {
                raise.raise(SourceMatchIdError("sourceMatchId must not be negative", value))
            }
            return SourceMatchId(value)
        }

        context(raise: Raise<SourceMatchIdError>)
        operator fun invoke(value: String?): SourceMatchId {
            val parsed = value?.toIntOrNull()
            if (parsed == null || parsed < 0) {
                raise.raise(SourceMatchIdError("sourceMatchId must be a non-negative integer", parsed))
            }
            return SourceMatchId(parsed)
        }

        fun fromRaw(value: String?): Either<SourceMatchIdError, SourceMatchId> = either {
            invoke(value)
        }

        fun of(value: Int): Either<SourceMatchIdError, SourceMatchId> = either {
            invoke(value)
        }

        fun from(value: Int): SourceMatchId {
            require(value >= 0) { "sourceMatchId must not be negative" }
            return SourceMatchId(value)
        }
    }

    override fun toString(): String = value.toString()
}
