package com.knowledgespike.ballbyball.parse.database

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

class SqliteMatchIdentityMigrationTest {
    @Test
    fun `populated legacy schema migrates without inferring canonical identity`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
                statement.execute("CREATE TABLE dim_match (id INTEGER NOT NULL PRIMARY KEY, file_name VARCHAR(120) NOT NULL)")
                statement.execute(
                    "CREATE TABLE dim_innings (innings_key INTEGER PRIMARY KEY, match_key INTEGER NOT NULL, " +
                        "FOREIGN KEY (match_key) REFERENCES dim_match (id))"
                )
                statement.execute("INSERT INTO dim_match (id, file_name) VALUES (12345, '/legacy/12345.json')")
                statement.execute("INSERT INTO dim_innings (innings_key, match_key) VALUES (1, 12345)")

                migrationStatements("3__deterministic_match_identity.sql", "4__public_match_id.sql")
                    .forEach(statement::execute)

                statement.execute(
                    "INSERT INTO dim_match (match_key, canonical_match_id, public_match_id, file_name) " +
                        "VALUES (12344, '1890a7a8-f76d-5f36-89f7-39b0319044b4', 2450146, '/legacy/canonical.json')"
                )
                migrationStatements("5__widen_public_match_id.sql").forEach(statement::execute)
                statement.executeQuery(
                    "SELECT public_match_id FROM dim_match WHERE match_key = 12344"
                ).use { result ->
                    result.next()
                    expectThat(result.getObject("public_match_id")).isEqualTo(null)
                }

                statement.executeQuery(
                    "SELECT match_key, canonical_match_id FROM dim_match WHERE match_key = 12345"
                ).use { result ->
                    result.next()
                    expectThat(result.getLong("match_key")).isEqualTo(12345L)
                    expectThat(result.getString("canonical_match_id")).isEqualTo(null)
                }
                statement.executeQuery("PRAGMA foreign_key_check").use { result ->
                    expectThat(result.next()).isEqualTo(false)
                }

                statement.execute(
                    "INSERT INTO dim_match (match_key, canonical_match_id, public_match_id, file_name) " +
                        "VALUES (12346, '1890a7a8-f76d-5f36-89f7-39b0319044b1', 7922450146, '/canonical/one.json')"
                )
                statement.execute(
                    "INSERT INTO dim_match (match_key, canonical_match_id, file_name) " +
                        "VALUES (12347, '1890a7a8-f76d-5f36-89f7-39b0319044b2', '/canonical/two.json')"
                )
                expectThat(runCatching {
                    statement.execute(
                        "INSERT INTO dim_match (match_key, canonical_match_id, public_match_id, file_name) " +
                            "VALUES (12348, '1890a7a8-f76d-5f36-89f7-39b0319044b3', 7922450146, '/canonical/three.json')"
                    )
                }.isFailure).isEqualTo(true)
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