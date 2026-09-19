package com.knowledgespike.ballbyball.contracts

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isTrue
import kotlin.time.Clock
import kotlin.time.Instant

class EnvelopeTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `success creates envelope with empty error message and current timestamp`() {
        val payload = HeartbeatResponse("Heartbeat: Alive")
        val before = Clock.System.now()
        val envelope = Envelope.success(payload)
        val after = Clock.System.now()

        expectThat(envelope.result).isEqualTo(payload)
        expectThat(envelope.errorMessage).isEqualTo("")
        expectThat(envelope.timeGenerated >= before).isTrue()
        expectThat(envelope.timeGenerated <= after).isTrue()
    }

    @Test
    fun `failure creates envelope with string message and empty string result`() {
        val before = Clock.System.now()
        val envelope = Envelope.failure("Something went wrong")
        val after = Clock.System.now()

        expectThat(envelope.result).isEqualTo("")
        expectThat(envelope.errorMessage).isEqualTo("Something went wrong")
        expectThat(envelope.timeGenerated >= before).isTrue()
        expectThat(envelope.timeGenerated <= after).isTrue()
    }

    @Test
    fun `failure with default result preserves result and sets error message`() {
        val envelope = Envelope.failure("Limit exceeded", RecentMatchesResponse(emptyList()))

        expectThat(envelope.result.matches).isEqualTo(emptyList())
        expectThat(envelope.errorMessage).isEqualTo("Limit exceeded")
    }

    @Test
    fun `envelope serializes to and from json preserving instant and payload`() {
        val payload = UserProfileResponse.of(
            subject = "user-123",
            name = "Kevin",
            email = "kevin@test.com",
            roles = listOf("BB.User")
        )
        val original = Envelope.success(payload)

        val serialized = json.encodeToString(original)
        expectThat(serialized).isNotEmpty()

        val deserialized = json.decodeFromString<Envelope<UserProfileResponse>>(serialized)
        expectThat(deserialized.result).isEqualTo(payload)
        expectThat(deserialized.errorMessage).isEqualTo("")
        expectThat(deserialized.timeGenerated).isEqualTo(original.timeGenerated)
    }

    @Test
    fun `failure envelope serializes and deserializes as Envelope of String`() {
        val original = Envelope.failure("Not authorized")

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<Envelope<String>>(serialized)

        expectThat(deserialized.result).isEqualTo("")
        expectThat(deserialized.errorMessage).isEqualTo("Not authorized")
        expectThat(deserialized.timeGenerated).isEqualTo(original.timeGenerated)
    }

    @Test
    fun `failure with non-empty list of domain errors formats comma separated message`() {
        val errors = arrow.core.nonEmptyListOf(
            com.knowledgespike.ballbyball.types.error.LimitError("limit must be between 1 and 100"),
            com.knowledgespike.ballbyball.types.error.MatchTypeError("matchType must not be blank")
        )
        val envelope = Envelope.failure(errors)

        expectThat(envelope.result).isEqualTo("")
        expectThat(envelope.errorMessage).isEqualTo("limit must be between 1 and 100, matchType must not be blank")
    }
}
