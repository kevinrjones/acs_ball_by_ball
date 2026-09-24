package com.knowledgespike.ballbyball.types.values

import arrow.core.Either
import arrow.core.raise.fold
import arrow.core.raise.zipOrAccumulate
import com.knowledgespike.ballbyball.contracts.MatchSummary
import com.knowledgespike.ballbyball.types.error.LimitError
import com.knowledgespike.ballbyball.types.error.MatchTypeError
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class ValueClassesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `Limit defaults to 10 on null or empty input`() {
        val defaultLimit = Limit.fromRaw(null)
        expectThat(defaultLimit.isRight()).isTrue()
        expectThat((defaultLimit as Either.Right).value.value).isEqualTo(10)

        val blankLimit = Limit.fromRaw("   ")
        expectThat(blankLimit.isRight()).isTrue()
        expectThat((blankLimit as Either.Right).value.value).isEqualTo(10)
    }

    @Test
    fun `Limit parses valid integers within 1 to 100`() {
        val minLimit = Limit.fromRaw("1")
        expectThat((minLimit as Either.Right).value.value).isEqualTo(1)

        val maxLimit = Limit.fromRaw("100")
        expectThat((maxLimit as Either.Right).value.value).isEqualTo(100)

        val midLimit = Limit.of(50)
        expectThat((midLimit as Either.Right).value.value).isEqualTo(50)
    }

    @Test
    fun `Limit fails on values out of range or non integers`() {
        val zero = Limit.fromRaw("0")
        expectThat(zero.isLeft()).isTrue()
        expectThat((zero as Either.Left).value.message).isEqualTo("limit must be between 1 and 100")

        val negative = Limit.of(-5)
        expectThat(negative.isLeft()).isTrue()

        val tooLarge = Limit.fromRaw("101")
        expectThat(tooLarge.isLeft()).isTrue()

        val notAnInt = Limit.fromRaw("abc")
        expectThat(notAnInt.isLeft()).isTrue()
    }

    @Test
    fun `MatchKey validates positive long values`() {
        val valid = MatchKey.of(12345L)
        expectThat((valid as Either.Right).value.value).isEqualTo(12345L)

        val fromRaw = MatchKey.fromRaw("98765")
        expectThat((fromRaw as Either.Right).value.value).isEqualTo(98765L)

        val invalid = MatchKey.fromRaw("-1")
        expectThat(invalid.isLeft()).isTrue()

        val zero = MatchKey.of(0L)
        expectThat(zero.isLeft()).isTrue()
    }

    @Test
    fun `SourceMatchId validates non negative integers`() {
        val valid = SourceMatchId.of(0)
        expectThat((valid as Either.Right).value.value).isEqualTo(0)

        val invalid = SourceMatchId.fromRaw("-1")
        expectThat(invalid.isLeft()).isTrue()

        val negative = SourceMatchId.of(-1)
        expectThat(negative.isLeft()).isTrue()
    }

    @Test
    fun `MatchType and Season reject blank strings`() {
        val matchType = MatchType.of("Test")
        expectThat((matchType as Either.Right).value.value).isEqualTo("Test")

        val blankType = MatchType.fromRaw("  ")
        expectThat(blankType.isLeft()).isTrue()

        val season = Season.of("2023/24")
        expectThat((season as Either.Right).value.value).isEqualTo("2023/24")

        val blankSeason = Season.fromRaw("")
        expectThat(blankSeason.isLeft()).isTrue()
    }


    @Test
    fun `MatchSummary serializes with value classes as primitive JSON values`() {
        val summary = MatchSummary.of(
            matchKey = 1001L,
            sourceMatchId = 42,
            fileName = "match_1001.json",
            matchType = "ODI",
            season = "2024"
        )

        val jsonString = json.encodeToString(summary)
        expectThat(jsonString).isEqualTo(
            """{"matchKey":1001,"sourceMatchId":42,"fileName":"match_1001.json","matchType":"ODI","season":"2024"}"""
        )

        val deserialized = json.decodeFromString<MatchSummary>(jsonString)
        expectThat(deserialized.matchKey.value).isEqualTo(1001L)
        expectThat(deserialized.sourceMatchId.value).isEqualTo(42)
        expectThat(deserialized.matchType.value).isEqualTo("ODI")
        expectThat(deserialized.season.value).isEqualTo("2024")
    }

    @Test
    fun `Arrow zipOrAccumulate accumulates validation errors across value classes`() {
        fold(
            block = {
                zipOrAccumulate<com.knowledgespike.ballbyball.types.error.Error, Limit, MatchType, Pair<Limit, MatchType>>(
                    { Limit("0") },
                    { MatchType("") }
                ) { limit, matchType ->
                    Pair(limit, matchType)
                }
            },
            recover = { errors ->
                expectThat(errors.size).isEqualTo(2)
                expectThat(errors[0] is LimitError).isTrue()
                expectThat(errors[1] is MatchTypeError).isTrue()
            },
            transform = {
                error("Should not succeed")
            }
        )
    }
}
