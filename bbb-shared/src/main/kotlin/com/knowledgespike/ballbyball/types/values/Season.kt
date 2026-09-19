package com.knowledgespike.ballbyball.types.values

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.knowledgespike.ballbyball.types.error.SeasonError
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Season private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "season must not be blank" }
    }

    companion object {
        context(raise: Raise<SeasonError>)
        operator fun invoke(value: String?): Season {
            if (value.isNullOrBlank()) {
                raise.raise(SeasonError("season must not be blank", value))
            }
            return Season(value.trim())
        }

        fun fromRaw(value: String?): Either<SeasonError, Season> = either {
            invoke(value)
        }

        fun of(value: String): Either<SeasonError, Season> = either {
            invoke(value)
        }

        fun from(value: String): Season {
            require(value.isNotBlank()) { "season must not be blank" }
            return Season(value.trim())
        }
    }

    override fun toString(): String = value
}
