package com.knowledgespike.ballbyball.types.values

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.knowledgespike.ballbyball.types.error.LimitError
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Limit private constructor(val value: Int) {
    init {
        require(value in MIN_LIMIT..MAX_LIMIT) { "limit must be between $MIN_LIMIT and $MAX_LIMIT" }
    }

    companion object {
        const val DEFAULT_LIMIT = 10
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 100

        fun default(): Limit = Limit(DEFAULT_LIMIT)

        context(raise: Raise<LimitError>)
        operator fun invoke(value: String?): Limit {
            if (value.isNullOrBlank()) return default()
            val parsed = value.toIntOrNull()
            if (parsed == null || parsed !in MIN_LIMIT..MAX_LIMIT) {
                raise.raise(LimitError("limit must be between $MIN_LIMIT and $MAX_LIMIT", value))
            }
            return Limit(parsed)
        }

        context(raise: Raise<LimitError>)
        operator fun invoke(value: Int): Limit {
            if (value !in MIN_LIMIT..MAX_LIMIT) {
                raise.raise(LimitError("limit must be between $MIN_LIMIT and $MAX_LIMIT", value.toString()))
            }
            return Limit(value)
        }

        fun fromRaw(value: String?): Either<LimitError, Limit> = either {
            invoke(value)
        }

        fun of(value: Int): Either<LimitError, Limit> = either {
            invoke(value)
        }

        fun from(value: Int): Limit {
            require(value in MIN_LIMIT..MAX_LIMIT) { "limit must be between $MIN_LIMIT and $MAX_LIMIT" }
            return Limit(value)
        }
    }

    override fun toString(): String = value.toString()
}
