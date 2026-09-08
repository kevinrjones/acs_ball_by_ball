package com.knowledgespike.cricsheet.parse.database

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isGreaterThan
import strikt.assertions.isEqualTo
import java.nio.file.Files
import java.time.LocalDate

class SqlScriptOutputAdapterTest {
    @Test
    fun `given a new script when opened then warehouse tables are reset before data`() {
        val output = Files.createTempFile("warehouse", ".sql")
        val adapter = SqlScriptOutputAdapter(output)
        adapter.close()

        val sql = Files.readString(output)
        val statements = sql.lines()
        val dropBridgeDeliveryWicket = statements.indexOf("DROP TABLE IF EXISTS bridge_delivery_wicket;")
        val dropDimDate = statements.indexOf("DROP TABLE IF EXISTS dim_date;")
        val createDimDate = statements.indexOfFirst { it.startsWith("CREATE TABLE dim_date") }
        val createBridgeDeliveryWicket = statements.indexOfFirst { it.startsWith("CREATE TABLE bridge_delivery_wicket") }
        val startTransaction = statements.indexOf("START TRANSACTION;")
        val commit = statements.indexOf("COMMIT;")

        expectThat(dropBridgeDeliveryWicket).isEqualTo(2)
        expectThat(dropDimDate).isEqualTo(12)
        expectThat(createDimDate).isGreaterThan(dropDimDate)
        expectThat(createBridgeDeliveryWicket).isGreaterThan(createDimDate)
        expectThat(startTransaction).isGreaterThan(createBridgeDeliveryWicket)
        expectThat(commit).isGreaterThan(createBridgeDeliveryWicket)
    }

    @Test
    fun `given repeated person when written then one escaped insert is emitted`() {
        val output = Files.createTempFile("warehouse", ".sql")
        val adapter = SqlScriptOutputAdapter(output)

        val firstKey = adapter.upsertPerson("person-1", "O'Brien", 7)
        val secondKey = adapter.upsertPerson("person-1", "O'Brien", 7)
        adapter.close()

        val sql = Files.readString(output)
        expectThat(firstKey).isEqualTo(secondKey)
        expectThat(sql).contains("'O''Brien'")
        expectThat(sql.lines().count { it.startsWith("INSERT INTO dim_person") }).isEqualTo(1)
    }

    @Test
    fun `given warehouse entities when written then generated script preserves foreign keys`() {
        val output = Files.createTempFile("warehouse", ".sql")
        val adapter = SqlScriptOutputAdapter(output)
        val team1 = adapter.upsertTeam("Home")
        val team2 = adapter.upsertTeam("Away")
        val ground = adapter.upsertGround("The Oval")
        val dateKey = adapter.upsertDate(LocalDate.parse("2024-01-02"))
        val match = adapter.insertMatch(
            MatchRecord(
                fileName = "match.json",
                matchInSeries = 1,
                matchType = "tt",
                eventName = "Series",
                matchDateText = "2024-01-02",
                season = "2024",
                matchStartYear = "2024",
                matchStartDateKey = dateKey,
                ballsPerOver = 6,
                team1Key = team1.id,
                team2Key = team2.id,
                groundKey = ground.id,
                tossTeamKey = team1.id,
                tossDecision = "bat",
                victoryType = "runs",
                winnerTeamKey = team1.id,
                loserTeamKey = team2.id
            )
        )
        adapter.insertMatchFact(match.key, dateKey, ground.id, 1, 10)
        adapter.close()

        val sql = Files.readString(output)
        expectThat(sql).contains("INSERT INTO dim_match (match_key, source_match_id")
        expectThat(sql).contains("VALUES (1, 1, NULL, 'match.json'")
        expectThat(sql).contains("INSERT INTO fact_match (match_key, match_date_key")
            .and { contains("VALUES (1, 20240102, 1, 1, 10, 1)") }
    }
}