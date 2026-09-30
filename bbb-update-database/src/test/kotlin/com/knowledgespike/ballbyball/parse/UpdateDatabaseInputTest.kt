package com.knowledgespike.ballbyball.parse

import org.apache.commons.cli.ParseException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.nio.file.Files
import java.nio.file.Path

class UpdateDatabaseInputTest {
    @Test
    fun `given nightly options when parsed then registry path uses the base root`() {
        val input = UpdateDatabaseArguments.parse(
            arrayOf(
                "-bd", "/tmp/cricsheet",
                "-dd", "matches",
                "-nd", "register",
                "-pr", "people.csv",
                "-n"
            )
        )

        expectThat(input).isEqualTo(
            UpdateDatabaseInput(
                baseDirectory = Path.of("/tmp/cricsheet"),
                dataDirectory = Path.of("/tmp/cricsheet/matches"),
                namesDirectory = Path.of("/tmp/cricsheet"),
                playerRegistry = Path.of("/tmp/cricsheet/people.csv"),
                nightly = true
            )
        )
    }

    @Test
    fun `given full options without names directory when parsed then base directory is used for the registry`() {
        val input = UpdateDatabaseArguments.parse(
            arrayOf(
                "--base-directory", "/tmp/cricsheet",
                "--data-directory", "matches",
                "--player-registry", "people.csv"
            )
        )

        expectThat(input.dataDirectory).isEqualTo(Path.of("/tmp/cricsheet/matches"))
        expectThat(input.namesDirectory).isEqualTo(Path.of("/tmp/cricsheet"))
        expectThat(input.playerRegistry).isEqualTo(Path.of("/tmp/cricsheet/people.csv"))
        expectThat(input.nightly).isEqualTo(false)
    }

    @Test
    fun `given unsafe data directory when parsed then parsing fails`() {
        assertThrows<ParseException> {
            UpdateDatabaseArguments.parse(
                arrayOf("-bd", "/tmp/cricsheet", "-dd", "../outside", "-pr", "people.csv")
            )
        }
    }

    @Test
    fun `given relative output paths when resolved then they are rooted at base directory`() {
        val baseDirectory = Path.of("/tmp/cricsheet")

        expectThat(
            UpdateDatabaseArguments.resolveOutputPath(
                baseDirectory,
                "sql/update.sql",
                "--outputFile"
            )
        ).isEqualTo(Path.of("/tmp/cricsheet/sql/update.sql"))
        expectThat(
            UpdateDatabaseArguments.resolveOutputPath(
                baseDirectory,
                "sql/update.sql",
                "--sqlFile"
            )
        ).isEqualTo(Path.of("/tmp/cricsheet/sql/update.sql"))
        expectThat(
            UpdateDatabaseArguments.resolveOutputPath(
                baseDirectory,
                "csv/warehouse",
                "--csvDir"
            )
        ).isEqualTo(Path.of("/tmp/cricsheet/csv/warehouse"))
    }

    @Test
    fun `given unsafe output file when resolved then parsing fails`() {
        assertThrows<ParseException> {
            UpdateDatabaseArguments.resolveOutputPath(
                Path.of("/tmp/cricsheet"),
                "../outside/update.sql",
                "--outputFile"
            )
        }

        assertThrows<ParseException> {
            UpdateDatabaseArguments.resolveOutputPath(
                Path.of("/tmp/cricsheet"),
                "../outside/csv",
                "--csvDir"
            )
        }
    }

    @Test
    fun `given data directory when selected then all json files are returned without directory metadata`() {
        val directory = Files.createTempDirectory("nightly-matches")
        Files.writeString(directory.resolve("b.json"), "b")
        Files.writeString(directory.resolve("a.json"), "a")
        Files.writeString(directory.resolve("notes.txt"), "ignore")
        Files.createDirectories(directory.resolve("archive-name"))
        Files.writeString(directory.resolve("archive-name/nested.json"), "nested")

        expectThat(matchFiles(directory).map { directory.relativize(it).toString() })
            .isEqualTo(listOf("a.json", "archive-name/nested.json", "b.json"))
    }
}