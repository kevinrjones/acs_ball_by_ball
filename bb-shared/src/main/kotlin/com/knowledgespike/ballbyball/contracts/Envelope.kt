package com.knowledgespike.ballbyball.contracts

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * Generic envelope structure encapsulating API responses with payload data,
 * error message (if any), and timestamp of when the response was generated.
 */
@Serializable
data class Envelope<T>(
    val result: T,
    val errorMessage: String = "",
    val timeGenerated: Instant = Clock.System.now()
) {
    companion object {
        fun <T> success(result: T): Envelope<T> =
            Envelope(result = result, errorMessage = "", timeGenerated = Clock.System.now())

        fun failure(message: String): Envelope<String> =
            Envelope(result = "", errorMessage = message, timeGenerated = Clock.System.now())

        fun <T> failure(message: String, defaultResult: T): Envelope<T> =
            Envelope(result = defaultResult, errorMessage = message, timeGenerated = Clock.System.now())
    }
}
