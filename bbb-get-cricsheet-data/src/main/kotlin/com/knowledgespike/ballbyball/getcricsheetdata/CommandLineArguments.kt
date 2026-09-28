package com.knowledgespike.ballbyball.getcricsheetdata

import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import java.nio.file.Path

sealed interface ApplicationCommand {
    data object Help : ApplicationCommand

    data object Version : ApplicationCommand

    data class Run(val outputDirectory: Path) : ApplicationCommand
}

object CommandLineArguments {
    const val DEFAULT_OUTPUT_DIRECTORY = "data/cricsheet"

    fun parse(args: Array<String>): ApplicationCommand {
        if (args.isEmpty() || args.any { it == "-h" || it == "--help" }) {
            return ApplicationCommand.Help
        }

        val commandLine = DefaultParser().parse(options(), args)
        if (commandLine.hasOption("version")) {
            return ApplicationCommand.Version
        }
        if (!commandLine.hasOption("run") && !commandLine.hasOption("output-directory")) {
            return ApplicationCommand.Help
        }

        val outputDirectory = commandLine.getOptionValue(
            "output-directory",
            DEFAULT_OUTPUT_DIRECTORY
        )
        require(outputDirectory.isNotBlank()) { "--output-directory must not be blank" }

        return ApplicationCommand.Run(Path.of(outputDirectory))
    }

    fun options(): Options = Options().apply {
        addOption("h", "help", false, "print this message")
        addOption(Option.builder().longOpt("version").desc("print the application version").get())
        addOption(Option.builder().longOpt("run").desc("start the retrieval workflow").get())
        addOption(
            Option.builder("o")
                .longOpt("output-directory")
                .hasArg()
                .argName("directory")
                .desc("directory where Cricsheet data will be stored (default: $DEFAULT_OUTPUT_DIRECTORY)")
                .get()
        )
    }
}