package com.knowledgespike.ballbyball.types.values

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.knowledgespike.ballbyball.types.error.PageSizeError
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class PageSize private constructor(val value: Int) {
    init {
        require(value in MIN_VALUE..MAX_VALUE) { "pageSize must be between $MIN_VALUE and $MAX_VALUE" }
    }

    companion object {
        const val DEFAULT_VALUE = 20
        const val MIN_VALUE = 1
        const val MAX_VALUE = 50

        context(raise: Raise<PageSizeError>)
        operator fun invoke(value: String?): PageSize {
            if (value.isNullOrBlank()) return PageSize(DEFAULT_VALUE)
            val parsed = value.toIntOrNull()
            if (parsed == null || parsed !in MIN_VALUE..MAX_VALUE) {
                raise.raise(PageSizeError("pageSize must be between $MIN_VALUE and $MAX_VALUE", value))
            }
            return PageSize(parsed)
        }

        fun fromRaw(value: String?): Either<PageSizeError, PageSize> = either {
            invoke(value)
        }

        fun from(value: Int): PageSize {
            require(value in MIN_VALUE..MAX_VALUE) { "pageSize must be between $MIN_VALUE and $MAX_VALUE" }
            return PageSize(value)
        }
    }
}