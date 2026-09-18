package com.knowledgespike.ballbyball.types.values

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.knowledgespike.ballbyball.types.error.UserIdError
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class UserId private constructor(val value: String) {
    init {
        require(value.isNotBlank()) { "userId must not be blank" }
    }

    companion object {
        context(raise: Raise<UserIdError>)
        operator fun invoke(value: String?): UserId {
            if (value.isNullOrBlank()) {
                raise.raise(UserIdError("userId must not be blank", value))
            }
            return UserId(value.trim())
        }

        fun fromRaw(value: String?): Either<UserIdError, UserId> = either {
            invoke(value)
        }

        fun of(value: String): Either<UserIdError, UserId> = either {
            invoke(value)
        }

        fun from(value: String): UserId {
            require(value.isNotBlank()) { "userId must not be blank" }
            return UserId(value.trim())
        }
    }

    override fun toString(): String = value
}
