package com.knowledgespike.ballbyball.parsecricsheet

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import java.nio.file.Path

data class ParseCricsheetInput(
    val baseDirectory: Path,
    val inputDirectory: Path,
    val outputDirectory: Path
)

object ParseCricsheetArguments {
    fun options(): Options = Options().apply {
        addOption("h", "help", false, "print this message")
        addOption(
            Option.builder("bd")
                .longOpt("base-directory")
                .hasArg()
                .argName("directory")
                .desc("absolute root directory")
                .get()
        )
        addOption(
            Option.builder("i")
                .longOpt("input")
                .hasArg()
                .argName("directory")
                .desc("relative Cricsheet input directory")
                .get()
        )
        addOption(
            Option.builder("o")
                .longOpt("output")
                .hasArg()
                .argName("directory")
                .desc("relative normalized output directory")
                .get()
        )
    }

    fun parse(args: Array<String>): ParseCricsheetInput {
        val commandLine = DefaultParser().parse(options(), args)
        val baseDirectory = absoluteBaseDirectory(commandLine.requiredValue("bd", "base-directory"))
        return ParseCricsheetInput(
            baseDirectory = baseDirectory,
            inputDirectory = childDirectory(
                baseDirectory,
                commandLine.requiredValue("i", "input"),
                "--input"
            ),
            outputDirectory = childDirectory(
                baseDirectory,
                commandLine.requiredValue("o", "output"),
                "--output"
            )
        )
    }

    private fun absoluteBaseDirectory(value: String): Path {
        val path = pathValue(value, "--base-directory")
        if (!path.isAbsolute) {
            throw ParseException("--base-directory must be an absolute path")
        }
        return path.normalize()
    }

    private fun childDirectory(baseDirectory: Path, value: String, option: String): Path {
        val path = pathValue(value, option)
        if (path.isAbsolute) {
            throw ParseException("$option must be relative to --base-directory")
        }
        val resolved = baseDirectory.resolve(path).normalize()
        if (!resolved.startsWith(baseDirectory)) {
            throw ParseException("$option must resolve within --base-directory")
        }
        return resolved
    }

    private fun pathValue(value: String?, option: String): Path {
        if (value.isNullOrBlank()) {
            throw ParseException("$option must not be blank")
        }
        return try {
            Path.of(value)
        } catch (exception: RuntimeException) {
            throw ParseException("$option is not a valid path: ${exception.message}")
        }
    }

    private fun CommandLine.requiredValue(shortName: String, longName: String): String =
        getOptionValue(shortName) ?: getOptionValue(longName)
        ?: throw ParseException("--$longName is required")
}