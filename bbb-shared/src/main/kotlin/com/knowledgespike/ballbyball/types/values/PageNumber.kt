package com.knowledgespike.ballbyball.types.values

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.knowledgespike.ballbyball.types.error.PageNumberError
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class PageNumber private constructor(val value: Int) {
    init {
        require(value in MIN_VALUE..MAX_VALUE) { "page must be between $MIN_VALUE and $MAX_VALUE" }
    }

    companion object {
        const val DEFAULT_VALUE = 1
        const val MIN_VALUE = 1
        const val MAX_VALUE = 10_000

        context(raise: Raise<PageNumberError>)
        operator fun invoke(value: String?): PageNumber {
            if (value.isNullOrBlank()) return PageNumber(DEFAULT_VALUE)
            val parsed = value.toIntOrNull()
            if (parsed == null || parsed !in MIN_VALUE..MAX_VALUE) {
                raise.raise(PageNumberError("page must be between $MIN_VALUE and $MAX_VALUE", value))
            }
            return PageNumber(parsed)
        }

        fun fromRaw(value: String?): Either<PageNumberError, PageNumber> = either {
            invoke(value)
        }

        fun from(value: Int): PageNumber {
            require(value in MIN_VALUE..MAX_VALUE) { "page must be between $MIN_VALUE and $MAX_VALUE" }
            return PageNumber(value)
        }
    }
}