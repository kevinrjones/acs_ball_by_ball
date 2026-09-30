package com.knowledgespike.ballbyball.parsecricsheet

import org.apache.commons.cli.ParseException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.nio.file.Path

class ParseCricsheetArgumentsTest {
    @Test
    fun `given parser paths when parsed then directories resolve beneath base`() {
        val input = ParseCricsheetArguments.parse(
            arrayOf("-bd", "/tmp/cricsheet", "-i", "raw", "-o", "normalized")
        )

        expectThat(input).isEqualTo(
            ParseCricsheetInput(
                baseDirectory = Path.of("/tmp/cricsheet"),
                inputDirectory = Path.of("/tmp/cricsheet/raw"),
                outputDirectory = Path.of("/tmp/cricsheet/normalized")
            )
        )
    }

    @Test
    fun `given unsafe parser path when parsed then parsing fails`() {
        assertThrows<ParseException> {
            ParseCricsheetArguments.parse(
                arrayOf("--base-directory", "/tmp/cricsheet", "--input", "../raw", "--output", "normalized")
            )
        }
    }
}