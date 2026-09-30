package com.knowledgespike.ballbyball.getcricsheetdata

import org.apache.commons.cli.ParseException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.nio.file.Path

class CommandLineArgumentsTest {
    @Test
    fun `given no arguments when parsed then help is requested`() {
        expectThat(CommandLineArguments.parse(emptyArray())).isEqualTo(ApplicationCommand.Help)
    }

    @Test
    fun `given help argument when parsed then help is requested`() {
        expectThat(CommandLineArguments.parse(arrayOf("--help"))).isEqualTo(ApplicationCommand.Help)
    }

    @Test
    fun `given version argument when parsed then version is requested`() {
        expectThat(CommandLineArguments.parse(arrayOf("--version"))).isEqualTo(ApplicationCommand.Version)
    }

    @Test
    fun `given required directories when parsed then run command contains resolved directories`() {
        expectThat(
            CommandLineArguments.parse(
                arrayOf(
                    "--base-directory", "/tmp/cricsheet-root",
                    "--data-directory", "matches",
                    "--names-directory", "register"
                )
            )
        ).isEqualTo(
            ApplicationCommand.Run(
                baseDirectory = Path.of("/tmp/cricsheet-root"),
                dataDirectory = Path.of("/tmp/cricsheet-root/matches"),
                namesDirectory = Path.of("/tmp/cricsheet-root/register"),
                force = false
            )
        )
    }

    @Test
    fun `given names directory omitted when parsed then data directory is used`() {
        expectThat(
            CommandLineArguments.parse(
                arrayOf("-bd", "/tmp/cricsheet-root", "-dd", "matches")
            )
        ).isEqualTo(
            ApplicationCommand.Run(
                baseDirectory = Path.of("/tmp/cricsheet-root"),
                dataDirectory = Path.of("/tmp/cricsheet-root/matches"),
                namesDirectory = Path.of("/tmp/cricsheet-root/matches"),
                force = false
            )
        )
    }

    @Test
    fun `given force flag when parsed then force is enabled`() {
        expectThat(
            CommandLineArguments.parse(
                arrayOf("-bd", "/tmp/cricsheet-root", "-dd", "matches", "-f")
            )
        ).isEqualTo(
            ApplicationCommand.Run(
                baseDirectory = Path.of("/tmp/cricsheet-root"),
                dataDirectory = Path.of("/tmp/cricsheet-root/matches"),
                namesDirectory = Path.of("/tmp/cricsheet-root/matches"),
                force = true
            )
        )
    }

    @Test
    fun `given short nightly flag when parsed then nightly uses the configured data directory`() {
        expectThat(
            CommandLineArguments.parse(
                arrayOf("-bd", "/tmp/cricsheet-root", "-dd", "matches", "-n", "-f")
            )
        ).isEqualTo(
            ApplicationCommand.Run(
                baseDirectory = Path.of("/tmp/cricsheet-root"),
                dataDirectory = Path.of("/tmp/cricsheet-root/matches"),
                namesDirectory = Path.of("/tmp/cricsheet-root/matches"),
                force = true,
                nightly = true
            )
        )
    }

    @Test
    fun `given long nightly flag when parsed then nightly mode is enabled`() {
        val command = CommandLineArguments.parse(
            arrayOf(
                "--base-directory", "/tmp/cricsheet-root",
                "--data-directory", "nested/matches",
                "--nightly"
            )
        )

        expectThat(command).isEqualTo(
            ApplicationCommand.Run(
                baseDirectory = Path.of("/tmp/cricsheet-root"),
                dataDirectory = Path.of("/tmp/cricsheet-root/nested/matches"),
                namesDirectory = Path.of("/tmp/cricsheet-root/nested/matches"),
                force = false,
                nightly = true
            )
        )
    }

    @Test
    fun `given nightly names directory when parsed then registers remain beside nightly data`() {
        expectThat(
            CommandLineArguments.parse(
                arrayOf(
                    "-bd", "/tmp/cricsheet-root",
                    "-dd", "matches",
                    "-nd", "register",
                    "-n"
                )
            )
        ).isEqualTo(
            ApplicationCommand.Run(
                baseDirectory = Path.of("/tmp/cricsheet-root"),
                dataDirectory = Path.of("/tmp/cricsheet-root/matches"),
                namesDirectory = Path.of("/tmp/cricsheet-root/matches"),
                force = false,
                nightly = true
            )
        )
    }

    @Test
    fun `given absolute nightly data directory when parsed then parsing fails`() {
        assertThrows<ParseException> {
            CommandLineArguments.parse(
                arrayOf("-bd", "/tmp/cricsheet-root", "-dd", "/tmp/matches", "-n")
            )
        }
    }

    @Test
    fun `given traversal directory when parsed then parsing fails`() {
        assertThrows<ParseException> {
            CommandLineArguments.parse(
                arrayOf("-bd", "/tmp/cricsheet-root", "-dd", "../outside")
            )
        }
    }

    @Test
    fun `given relative base directory when parsed then parsing fails`() {
        assertThrows<ParseException> {
            CommandLineArguments.parse(arrayOf("-bd", "cricsheet-root", "-dd", "matches"))
        }
    }

    @Test
    fun `given missing required directory when parsed then parsing fails`() {
        assertThrows<ParseException> {
            CommandLineArguments.parse(arrayOf("-bd", "/tmp/cricsheet-root"))
        }
    }

    @Test
    fun `given unknown argument when parsed then parsing fails`() {
        assertThrows<ParseException> {
            CommandLineArguments.parse(arrayOf("--unknown"))
        }
    }
}