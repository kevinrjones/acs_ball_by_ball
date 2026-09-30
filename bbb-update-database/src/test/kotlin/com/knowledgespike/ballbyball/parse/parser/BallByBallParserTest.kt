package com.knowledgespike.ballbyball.parse.parser

import com.knowledgespike.ballbyball.clishared.schema.BbbMatchData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.nio.file.Files

class BallByBallParserTest {
    @Test
    fun `given shared schema json when parsed then normalized match data is returned`() {
        val file = Files.createTempFile("shared-match", ".json")
        Files.writeString(file, Json.encodeToString(sampleMatchData()))

        val parsed = BallByBallParser().parse(file.toFile())

        expectThat(parsed.match.matchType).isEqualTo("wtt")
        expectThat(parsed.match.event?.name).isEqualTo("The Hundred")
        expectThat(parsed.innings.first().overs?.first()?.deliveries?.first()?.nonStriker)
            .isEqualTo("Batter Two")
    }

    private fun sampleMatchData() = BbbMatchData(
        match = com.knowledgespike.ballbyball.clishared.schema.Info(
            ballsPerOver = 6,
            dates = listOf("2024-01-01"),
            event = com.knowledgespike.ballbyball.clishared.schema.Event("The Hundred"),
            gender = "female",
            matchType = "wtt",
            outcome = com.knowledgespike.ballbyball.clishared.schema.Outcome(result = "draw"),
            players = JsonObject(emptyMap()),
            registry = com.knowledgespike.ballbyball.clishared.schema.PlayersRegistry(emptyMap()),
            season = "2024",
            teamType = "international",
            teams = listOf("Team A", "Team B"),
            toss = com.knowledgespike.ballbyball.clishared.schema.Toss(decision = "bat", winner = "Team A")
        ),
        innings = listOf(
            com.knowledgespike.ballbyball.clishared.schema.Innings(
                team = "Team A",
                overs = listOf(
                    com.knowledgespike.ballbyball.clishared.schema.Over(
                        over = 0,
                        deliveries = listOf(
                            com.knowledgespike.ballbyball.clishared.schema.Delivery(
                                batter = "Batter One",
                                bowler = "Bowler One",
                                nonStriker = "Batter Two",
                                runs = com.knowledgespike.ballbyball.clishared.schema.Runs(1, 0, total = 1)
                            )
                        )
                    )
                )
            )
        )
    )
}