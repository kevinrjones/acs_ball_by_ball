package com.knowledgespike.ballbyball.parse.database

import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.types.values.PublicMatchId

import com.knowledgespike.cricketarchive.InvalidStateException

import com.knowledgespike.ballbyball.parse.database.adapter.mariadb.SqlScriptOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.MatchRecord
import com.knowledgespike.ballbyball.parse.database.adapter.postgres.SqlScriptOutputAdapter as PostgresSqlScriptOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.sqlite.SqlScriptOutputAdapter as SqliteSqlScriptOutputAdapter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isGreaterThan
import strikt.assertions.isEqualTo
import java.nio.file.Files
import java.sql.DriverManager
import java.time.LocalDate
import java.util.UUID

class SqlScriptOutputAdapterTest {
    @Test
    fun `given a forced public ID collision then the second canonical match is rejected`() {
        val output = Files.createTempFile("warehouse-collision", ".sql")
        val first = testMatchRecord("1890a7a8-f76d-5f36-89f7-39b0319044b0")
        val second = testMatchRecord("1890a7a8-f76d-5f36-89f7-39b0319044b1")

        assertThrows<InvalidStateException> {
            SqlScriptOutputAdapter(output).use { adapter ->
                adapter.insertMatch(first)
                adapter.insertMatch(second)
            }
        }
    }
    @Test
    fun `given a new script when opened then relational tables are reset before data`() {
        val output = Files.createTempFile("warehouse", ".sql")
        val adapter = SqlScriptOutputAdapter(output)
        adapter.close()

        val sql = Files.readString(output)
        val statements = sql.lines()
        val dropDeliveryWicket = statements.indexOf("DROP TABLE IF EXISTS delivery_wickets;")
        val dropDates = statements.indexOf("DROP TABLE IF EXISTS dates;")
        val createDates = statements.indexOfFirst { it.startsWith("CREATE TABLE dates") }
        val createDeliveryWicket = statements.indexOfFirst { it.startsWith("CREATE TABLE delivery_wickets") }
        val startTransaction = statements.indexOf("START TRANSACTION;")
        val commit = statements.indexOf("COMMIT;")

        expectThat(dropDeliveryWicket).isGreaterThan(-1)
        expectThat(dropDates).isGreaterThan(dropDeliveryWicket)
        expectThat(createDates).isGreaterThan(dropDates)
        expectThat(createDeliveryWicket).isGreaterThan(createDates)
        expectThat(startTransaction).isGreaterThan(createDeliveryWicket)
        expectThat(commit).isGreaterThan(createDeliveryWicket)
    }

    @Test
    fun `given mariadb output when opened then every relational table uses innodb`() {
        val output = Files.createTempFile("warehouse", ".sql")
        SqlScriptOutputAdapter(output).use { }

        val sql = Files.readString(output)
        expectThat(Regex("CREATE TABLE ").findAll(sql).count()).isEqualTo(12)
        expectThat(Regex("\\) ENGINE = InnoDB;").findAll(sql).count()).isEqualTo(12)
    }

    @Test
    fun `given mariadb output then relational foreign keys use the match key type`() {
        val output = Files.createTempFile("warehouse", ".sql")
        SqlScriptOutputAdapter(output).use { }

        val sql = Files.readString(output)
        expectThat(sql).contains("match_id BIGINT NOT NULL")
        expectThat(sql).contains("id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY")
        expectThat(sql).contains("canonical_match_id CHAR(36) NULL")
        expectThat(sql).contains("public_match_id BIGINT NULL")
        expectThat(sql).contains("duration_days INT NOT NULL")
        expectThat(sql).contains("margin INT NOT NULL")
        expectThat(sql).contains("CREATE TABLE match_source_reference")
        expectThat(sql).contains("source_record_id CHAR(36) NOT NULL")
        expectThat(sql).contains("added_timestamp DATETIME NOT NULL")
        expectThat(sql).contains("match_count TINYINT UNSIGNED NOT NULL DEFAULT 1")
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
        expectThat(sql.lines().count { it.startsWith("INSERT INTO people") }).isEqualTo(1)
    }

    @Test
    fun `given repeated delivery fielder when written then one bridge insert is emitted`() {
        val output = Files.createTempFile("warehouse", ".sql")
        SqlScriptOutputAdapter(output).use { adapter ->
            adapter.insertDeliveryFielder(1, 2, 3)
            adapter.insertDeliveryFielder(1, 2, 3)
        }

        val sql = Files.readString(output)
        expectThat(sql.lines().count { it.startsWith("INSERT INTO delivery_fielders") }).isEqualTo(1)
    }

    @Test
    fun `given warehouse entities when written then generated script preserves foreign keys`() {
        val output = Files.createTempFile("warehouse", ".sql")
        val sourceFile = output.parent.resolve("12345.json").toAbsolutePath().normalize()
        val adapter = SqlScriptOutputAdapter(output)
        val team1 = adapter.upsertTeam("Home")
        val team2 = adapter.upsertTeam("Away")
        val ground = adapter.upsertGround("The Oval")
        val dateKey = adapter.upsertDate(LocalDate.parse("2024-01-02"))
        val match = adapter.insertMatch(
            MatchRecord(
                canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0")),
                fileName = sourceFile.toString(),
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
        adapter.close()

        val sql = Files.readString(output)
        expectThat(sql).contains("INSERT INTO matches (id, canonical_match_id, public_match_id, source_ca_id, file_name")
        expectThat(sql).contains(
            "VALUES (1, '1890a7a8-f76d-5f36-89f7-39b0319044b0', 7922450146, NULL, '${sourceFile.toString().replace("'", "''")}'"
        )
        val matchInsert = sql.lineSequence().single { it.startsWith("INSERT INTO matches") }
        expectThat(matchInsert.count { it == '?' }).isEqualTo(0)
        expectThat(sql).contains("duration_days, margin, match_count")
    }

    @Test
    fun `given postgres output when opened then postgres syntax is emitted`() {
        val output = Files.createTempFile("warehouse-postgres", ".sql")
        PostgresSqlScriptOutputAdapter(output).use { adapter ->
            adapter.insertMatchPerson(1, 1, "player")
            adapter.insertDeliveryFielder(1, 2, 3)
        }

        val sql = Files.readString(output)
        expectThat(sql).contains("CREATE SCHEMA IF NOT EXISTS acs_ball_by_ball;")
        expectThat(sql).contains("BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY")
        expectThat(sql).contains("ON CONFLICT (match_id, person_id, role_code) DO NOTHING")
        expectThat(sql).contains(
            "INSERT INTO delivery_fielders (delivery_id, wicket_id, person_id) VALUES (1, 2, 3) " +
                    "ON CONFLICT (delivery_id, wicket_id, person_id) DO NOTHING;"
        )
        expectThat(sql).contains("START TRANSACTION;")
    }

    @Test
    fun `given sqlite output when opened then sqlite syntax is emitted`() {
        val output = Files.createTempFile("warehouse-sqlite", ".sql")
        SqliteSqlScriptOutputAdapter(output).use { adapter ->
            adapter.insertMatchPerson(1, 1, "player")
        }

        val sql = Files.readString(output)
        expectThat(sql).contains("PRAGMA foreign_keys = ON;")
        expectThat(sql).contains("INTEGER PRIMARY KEY AUTOINCREMENT")
        expectThat(sql).contains("ON CONFLICT (match_id, person_id, role_code) DO NOTHING")
        expectThat(sql).contains("BEGIN TRANSACTION;")
    }

    @Test
    fun `given sqlite output when executed then relational schema is created`() {
        val output = Files.createTempFile("warehouse-sqlite", ".sql")
        SqliteSqlScriptOutputAdapter(output).use { }
        val statements = Files.readString(output)
            .split(';')
            .map { statement ->
                statement.lines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n").trim()
            }
            .filter(String::isNotBlank)

        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statements.forEach(statement::execute)
                statement.executeQuery(
                    "select count(*) from sqlite_master where type = 'table' and name not like 'sqlite_%'"
                ).use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(12)
                }
                statement.executeQuery(
                    "select count(*) from sqlite_master where type = 'index' and name = 'idx_deliveries_match_order'"
                ).use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(1)
                }
                listOf(
                    "idx_deliveries_match_sequence",
                    "idx_matches_file_name",
                    "idx_matches_type_year",
                    "idx_matches_teams_type",
                    "idx_people_full_name",
                    "idx_delivery_wickets_wicket",
                    "idx_delivery_fielders_wicket"
                ).forEach { indexName ->
                    statement.executeQuery(
                        "select count(*) from sqlite_master where type = 'index' and name = '$indexName'"
                    ).use { result ->
                        result.next()
                        expectThat(result.getInt(1)).isEqualTo(1)
                    }
                }
                statement.executeQuery(
                    "select count(*) from pragma_table_info('matches') where name = 'duration_days'"
                ).use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(1)
                }
            }
        }
    }

    private fun testMatchRecord(canonicalMatchId: String): MatchRecord = MatchRecord(
        canonicalMatchId = CanonicalMatchId.from(UUID.fromString(canonicalMatchId)),
        publicMatchId = PublicMatchId.from(7_922_450_146L),
        fileName = "match.json",
        matchInSeries = 1,
        matchType = "tt",
        eventName = "Series",
        matchDateText = "2024-01-02",
        season = "2024",
        matchStartYear = "2024",
        matchStartDateKey = null,
        ballsPerOver = 6,
        team1Key = 1,
        team2Key = 2,
        groundKey = 1,
        tossTeamKey = 1,
        tossDecision = null,
        victoryType = "runs",
        winnerTeamKey = 1,
        loserTeamKey = 2
    )
}