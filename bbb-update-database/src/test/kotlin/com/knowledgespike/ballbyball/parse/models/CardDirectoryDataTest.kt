package com.knowledgespike.ballbyball.parse.models

import com.knowledgespike.ballbyball.parse.parser.structure.CricSheet
import com.knowledgespike.ballbyball.parse.parser.structure.Event
import com.knowledgespike.ballbyball.parse.parser.structure.Info
import com.knowledgespike.ballbyball.parse.parser.structure.Meta
import com.knowledgespike.ballbyball.parse.parser.structure.Outcome
import com.knowledgespike.ballbyball.parse.parser.structure.PlayersRegistry
import com.knowledgespike.ballbyball.parse.parser.structure.Toss
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class CardDirectoryDataTest {
    @Test
    fun `given female t20 match when metadata is derived then women warehouse type is produced`() {
        val metadata = cardDirectoryDataForMatch(cricSheet("T20", "female", Event("The Hundred")))

        expectThat(metadata.name).isEqualTo("The Hundred")
        expectThat(metadata.matchType).isEqualTo("wtt")
        expectThat(metadata.mixedGender).isEqualTo(true)
    }

    @Test
    fun `given missing event and international t20 when metadata is derived then fallback and code are used`() {
        val metadata = cardDirectoryDataForMatch(cricSheet("IT20", "male", null))

        expectThat(metadata.name).isEqualTo("Unknown")
        expectThat(metadata.matchType).isEqualTo("itt")
    }

    @Test
    fun `given female one day match when metadata is derived then women one day type is produced`() {
        val metadata = cardDirectoryDataForMatch(cricSheet("ODI", "female", Event("Women's One-Day Cup")))

        expectThat(metadata.matchType).isEqualTo("wa")
    }

    @Test
    fun `given test match when metadata is derived then test warehouse type is produced`() {
        val metadata = cardDirectoryDataForMatch(cricSheet("Test", "male", Event("Test Matches")))

        expectThat(metadata.matchType).isEqualTo("t")
    }

    private fun cricSheet(matchType: String, gender: String, event: Event?): CricSheet = CricSheet(
        meta = Meta("1.0", "2024-01-01", 1),
        info = Info(
            ballsPerOver = 6,
            dates = listOf("2024-01-01"),
            event = event,
            gender = gender,
            matchType = matchType,
            outcome = Outcome(),
            players = JsonObject(emptyMap()),
            registry = PlayersRegistry(emptyMap()),
            season = "2024",
            teamType = "international",
            teams = listOf("Home", "Away"),
            toss = Toss(decision = "bat", winner = "Home")
        ),
        innings = emptyList()
    )
}