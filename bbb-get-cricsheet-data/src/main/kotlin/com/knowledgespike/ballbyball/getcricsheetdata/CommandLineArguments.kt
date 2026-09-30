package com.knowledgespike.ballbyball.getcricsheetdata

import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import java.nio.file.Path

sealed interface ApplicationCommand {
    data object Help : ApplicationCommand

    data object Version : ApplicationCommand

    data class Run(
        val baseDirectory: Path,
        val dataDirectory: Path,
        val namesDirectory: Path,
        val force: Boolean,
        val nightly: Boolean = false
    ) : ApplicationCommand
}

object CommandLineArguments {
    fun parse(args: Array<String>): ApplicationCommand {
        if (args.isEmpty() || args.any { it == "-h" || it == "--help" }) {
            return ApplicationCommand.Help
        }

        val commandLine = DefaultParser().parse(options(), args)
        if (commandLine.hasOption("version")) {
            return ApplicationCommand.Version
        }

        val baseDirectory = absoluteBaseDirectory(commandLine.requiredValue("base-directory"))
        val dataDirectoryName = commandLine.requiredValue("data-directory")
        val nightly = commandLine.hasOption("nightly")
        val dataDirectory = childDirectory(baseDirectory, dataDirectoryName, "--data-directory")
        commandLine.getOptionValue("names-directory")?.let { namesDirectory ->
            childDirectory(baseDirectory, namesDirectory, "--names-directory")
        }

        return ApplicationCommand.Run(
            baseDirectory = baseDirectory,
            dataDirectory = dataDirectory,
            namesDirectory = baseDirectory,
            force = commandLine.hasOption("force"),
            nightly = nightly
        )
    }

    fun options(): Options = Options().apply {
        addOption("h", "help", false, "print this message")
        addOption(Option.builder().longOpt("version").desc("print the application version").get())
        addOption(
            Option.builder("bd")
                .longOpt("base-directory")
                .hasArg()
                .argName("directory")
                .desc("absolute root directory for the download")
                .get()
        )
        addOption(
            Option.builder("dd")
                .longOpt("data-directory")
                .hasArg()
                .argName("directory")
                .desc("relative directory for match data")
                .get()
        )
        addOption(
            Option.builder("nd")
                .longOpt("names-directory")
                .hasArg()
                .argName("directory")
                .desc("deprecated compatibility option; register CSVs are stored in the base directory")
                .get()
        )
        addOption(
            Option.builder("f")
                .longOpt("force")
                .desc("allow existing data and names directories")
                .get()
        )
        addOption(
            Option.builder("n")
                .longOpt("nightly")
                .desc("retrieve the fixed nightly archive set")
                .get()
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

    private fun org.apache.commons.cli.CommandLine.requiredValue(option: String): String =
        getOptionValue(option) ?: throw ParseException("--$option is required")
}