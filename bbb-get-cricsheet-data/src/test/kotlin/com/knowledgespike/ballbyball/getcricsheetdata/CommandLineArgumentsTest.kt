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
    fun `given output directory when parsed then run command contains the directory`() {
        expectThat(CommandLineArguments.parse(arrayOf("--output-directory", "/tmp/cricsheet")))
            .isEqualTo(ApplicationCommand.Run(Path.of("/tmp/cricsheet")))
    }

    @Test
    fun `given run without output directory when parsed then default directory is used`() {
        expectThat(CommandLineArguments.parse(arrayOf("--run")))
            .isEqualTo(ApplicationCommand.Run(Path.of(CommandLineArguments.DEFAULT_OUTPUT_DIRECTORY)))
    }

    @Test
    fun `given unknown argument when parsed then parsing fails`() {
        assertThrows<ParseException> {
            CommandLineArguments.parse(arrayOf("--unknown"))
        }
    }
}