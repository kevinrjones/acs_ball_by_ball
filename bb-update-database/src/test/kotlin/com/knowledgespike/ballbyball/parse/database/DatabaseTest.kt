package com.knowledgespike.ballbyball.parse.database

import com.knowledgespike.ballbyball.parse.database.adapter.sqlite.SqlScriptOutputAdapter
import com.knowledgespike.ballbyball.parse.models.CardDirectoryData
import com.knowledgespike.ballbyball.parse.parser.structure.By
import com.knowledgespike.ballbyball.parse.parser.structure.CricSheet
import com.knowledgespike.ballbyball.parse.parser.structure.Delivery
import com.knowledgespike.ballbyball.parse.parser.structure.Info
import com.knowledgespike.ballbyball.parse.parser.structure.Innings
import com.knowledgespike.ballbyball.parse.parser.structure.Meta
import com.knowledgespike.ballbyball.parse.parser.structure.Over
import com.knowledgespike.ballbyball.parse.parser.structure.Outcome
import com.knowledgespike.ballbyball.parse.parser.structure.PlayersRegistry
import com.knowledgespike.ballbyball.parse.parser.structure.Runs
import com.knowledgespike.ballbyball.parse.parser.structure.Toss
import com.knowledgespike.ballbyball.parse.parser.structure.Wickets
import com.knowledgespike.ballbyball.parse.parser.structure.Player
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.knowledgespike.cricketarchive.InvalidStateException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import java.nio.file.Files

class DatabaseTest {
    @Test
    fun `given wicket fielder when match is written then fielder bridge row is emitted`() {
        val output = Files.createTempFile("warehouse", ".sql")

        SqlScriptOutputAdapter(output).use { adapter ->
            Database(adapter).writeMatch(
                fileName = "match.json",
                cricSheet = cricSheetWithFielder(Player(name = "Fielder")),
                cardDirectoryData = CardDirectoryData("matches", "match", "t20")
            )
        }

        val sql = Files.readString(output)
        expectThat(sql).contains(
            "INSERT INTO bridge_delivery_fielder (delivery_key, wicket_key, person_key) VALUES (1, 1, 4);"
        )
        expectThat(sql).contains(
            "INSERT INTO fact_match (match_key, match_date_key, ground_key, duration_days, margin, match_count) " +
                "VALUES (1, 20240101, 1, 1, 1, 1);"
        )
    }

    @Test
    fun `given substitute wicket fielder without name when match is written then unknown substitute person and bridge row are emitted`() {
        val output = Files.createTempFile("warehouse", ".sql")

        SqlScriptOutputAdapter(output).use { adapter ->
            Database(adapter).writeMatch(
                fileName = "match.json",
                cricSheet = cricSheetWithFielder(Player(name = null, substitute = true)),
                cardDirectoryData = CardDirectoryData("matches", "match", "t20")
            )
        }

        val sql = Files.readString(output)
        expectThat(sql).contains(
            "INSERT INTO dim_person (person_key, source_person_id, full_name, sort_name_part, other_name_part, ca_id) VALUES (5, 'unknown', '[substitute]', '[substitute]', '', 0);"
        )
        expectThat(sql).contains(
            "INSERT INTO bridge_delivery_fielder (delivery_key, wicket_key, person_key) VALUES (1, 1, 5);"
        )
    }

    @Test
    fun `given wicket fielder with no name and not substitute when match is written then InvalidStateException is thrown`() {
        val output = Files.createTempFile("warehouse", ".sql")

        assertThrows<InvalidStateException> {
            SqlScriptOutputAdapter(output).use { adapter ->
                Database(adapter).writeMatch(
                    fileName = "match.json",
                    cricSheet = cricSheetWithFielder(Player(name = null, substitute = null)),
                    cardDirectoryData = CardDirectoryData("matches", "match", "t20")
                )
            }
        }
    }

    @Test
    fun `given player with substitute boolean in json when deserialized then returns correct player`() {
        val json = """{"name": null, "substitute": true}"""
        val player = Json.decodeFromString<Player>(json)
        expectThat(player.substitute).isEqualTo(true)
        expectThat(player.name).isEqualTo(null)
    }

    private fun cricSheetWithFielder(fielder: Player = Player(name = "Fielder")): CricSheet = CricSheet(
        meta = Meta("1.0", "2024-01-01", 1),
        info = Info(
            ballsPerOver = 6,
            dates = listOf("2024-01-01"),
            gender = "male",
            matchType = "T20",
            outcome = Outcome(winner = "Home", by = By(runs = 1)),
            players = JsonObject(
                mapOf(
                    "Home" to JsonArray(listOf(JsonPrimitive("Batter"))),
                    "Away" to JsonArray(listOf(JsonPrimitive("Bowler")))
                )
            ),
            registry = PlayersRegistry(
                mapOf(
                    "Batter" to "batter-id",
                    "Bowler" to "bowler-id",
                    "NonStriker" to "non-striker-id",
                    "Fielder" to "fielder-id"
                )
            ),
            season = "2024",
            teamType = "international",
            teams = listOf("Home", "Away"),
            toss = Toss(decision = "bat", winner = "Home")
        ),
        innings = listOf(
            Innings(
                team = "Home",
                overs = listOf(
                    Over(
                        over = 0,
                        deliveries = listOf(
                            Delivery(
                                batter = "Batter",
                                bowler = "Bowler",
                                nonStriker = "NonStriker",
                                runs = Runs(batter = 0, extras = 0, total = 0),
                                wickets = listOf(
                                    Wickets(
                                        fielders = listOf(fielder),
                                        kind = "caught",
                                        playerOut = "Batter"
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}