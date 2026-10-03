package com.knowledgespike.ballbyball.parse.database

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

class SqliteMatchIdentityMigrationTest {
    @Test
    fun `warehouse cutover creates normalized schema and removes warehouse tables`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
                statement.execute("CREATE TABLE dim_match (id INTEGER PRIMARY KEY, file_name VARCHAR(120) NOT NULL)")
                statement.execute(
                    "CREATE TABLE dim_innings (innings_key INTEGER PRIMARY KEY, match_key INTEGER NOT NULL, " +
                        "FOREIGN KEY (match_key) REFERENCES dim_match (id))"
                )
                migrationStatements("1__initial_tables.sql").forEach(statement::execute)

                statement.executeQuery(
                    "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name LIKE 'dim_%'"
                ).use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(0)
                }
                statement.executeQuery(
                    "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name IN " +
                        "('dates', 'teams', 'people', 'grounds', 'matches', 'innings', 'deliveries', " +
                        "'wickets', 'match_people', 'delivery_wickets', 'delivery_fielders', 'match_source_reference')"
                ).use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(12)
                }
                statement.executeQuery("PRAGMA foreign_key_check").use { result ->
                    expectThat(result.next()).isEqualTo(false)
                }
                statement.executeQuery("PRAGMA table_info(matches)").use { result ->
                    val columns = buildList {
                        while (result.next()) add(result.getString("name"))
                    }
                    expectThat(columns).isEqualTo(
                        listOf(
                            "id", "canonical_match_id", "public_match_id", "source_ca_id", "file_name",
                            "match_in_series", "match_type", "event_name", "match_date_text", "season",
                            "match_start_year", "match_start_date_id", "balls_per_over", "added_timestamp",
                            "team1_id", "team2_id", "ground_id", "toss_team_id", "toss_decision", "victory_type",
                            "winner_team_id", "loser_team_id", "duration_days", "margin", "match_count"
                        )
                    )
                }
            }
        }
    }

    private fun migrationStatements(vararg fileNames: String): List<String> = fileNames.flatMap(::readMigration)

    private fun readMigration(fileName: String): List<String> = Files.readString(
        Path.of("migrations/sqlite/$fileName")
    ).lines()
        .filterNot { it.trimStart().startsWith("--") }
        .joinToString("\n")
        .split(';')
        .map(String::trim)
        .filter(String::isNotBlank)
}