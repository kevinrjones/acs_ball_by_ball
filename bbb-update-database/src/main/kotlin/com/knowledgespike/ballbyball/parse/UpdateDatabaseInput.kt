package com.knowledgespike.ballbyball.parse

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

data class UpdateDatabaseInput(
    val baseDirectory: Path,
    val dataDirectory: Path,
    val namesDirectory: Path,
    val playerRegistry: Path,
    val nightly: Boolean
)

object UpdateDatabaseArguments {
    fun options(): Options = Options().apply {
        addOption(
            Option.builder("bd")
                .longOpt("base-directory")
                .argName("baseDirectory")
                .hasArg()
                .desc("absolute root directory for Cricsheet data")
                .get()
        )
        addOption(
            Option.builder("dd")
                .longOpt("data-directory")
                .argName("dataDirectory")
                .hasArg()
                .desc("relative match-data directory below the base directory")
                .get()
        )
        addOption(
            Option.builder("nd")
                .longOpt("names-directory")
                .argName("namesDirectory")
                .hasArg()
                .desc("deprecated compatibility option; the player registry is read from the base directory")
                .get()
        )
        addOption(
            Option.builder("pr")
                .longOpt("player-registry")
                .argName("playerRegistry")
                .hasArg()
                .desc("player-registry filename")
                .get()
        )
        addOption(
            Option.builder("n")
                .longOpt("nightly")
                .desc("read match data from the configured data directory")
                .get()
        )
    }

    fun parse(args: Array<String>): UpdateDatabaseInput {
        val commandLine = try {
            DefaultParser().parse(options(), args)
        } catch (exception: ParseException) {
            throw exception
        }
        return from(commandLine)
    }

    fun from(commandLine: CommandLine): UpdateDatabaseInput {
        val baseDirectory = absoluteBaseDirectory(requiredValue(commandLine, "bd", "base-directory"))
        val dataDirectoryName = requiredValue(commandLine, "dd", "data-directory")
        val dataDirectory = childDirectory(baseDirectory, dataDirectoryName, "--data-directory")
        commandLine.value("nd", "names-directory")?.let { namesDirectory ->
            childDirectory(baseDirectory, namesDirectory, "--names-directory")
        }
        val namesDirectory = baseDirectory
        val playerRegistry = childFile(
            baseDirectory,
            requiredValue(commandLine, "pr", "player-registry"),
            "--player-registry",
            "base directory"
        )

        return UpdateDatabaseInput(
            baseDirectory = baseDirectory,
            dataDirectory = dataDirectory,
            namesDirectory = namesDirectory,
            playerRegistry = playerRegistry,
            nightly = commandLine.hasOption("n")
        )
    }

    fun resolveOutputPath(baseDirectory: Path, value: String, option: String): Path =
        childPath(baseDirectory, value, option, "base directory")

    fun resolveCsvOutputPath(input: UpdateDatabaseInput, value: String): Path {
        val output = resolveOutputPath(input.baseDirectory, value, "--csvDir")
        val dataDirectory = input.dataDirectory.normalize()
        val baseDirectory = input.baseDirectory.normalize()
        if (output == baseDirectory || output.startsWith(dataDirectory) || dataDirectory.startsWith(output)) {
            throw ParseException("--csvDir must not overlap the base or match-data directory: $value")
        }
        if (output == input.playerRegistry.normalize()) {
            throw ParseException("--csvDir must not target the player registry: $value")
        }
        return output
    }

    private fun requiredValue(commandLine: CommandLine, vararg names: String): String =
        names.firstNotNullOfOrNull { commandLine.getOptionValue(it) }
            ?: throw ParseException("Missing required option: --${names.last()}")

    private fun CommandLine.value(vararg names: String): String? =
        names.firstNotNullOfOrNull { getOptionValue(it) }

    private fun absoluteBaseDirectory(value: String): Path {
        val path = Path.of(value)
        if (!path.isAbsolute) {
            throw ParseException("--base-directory must be an absolute path: $value")
        }
        return path.normalize()
    }

    private fun childDirectory(root: Path, value: String, option: String): Path {
        return childPath(root, value, option, "base directory")
    }

    private fun childPath(root: Path, value: String, option: String, rootDescription: String): Path {
        val path = Path.of(value)
        if (path.isAbsolute) {
            throw ParseException("$option must be relative: $value")
        }
        val resolved = root.resolve(path).normalize()
        if (!resolved.startsWith(root.normalize())) {
            throw ParseException("$option must remain below the $rootDescription: $value")
        }
        return resolved
    }

    private fun childFile(root: Path, value: String, option: String, rootDescription: String): Path {
        return childPath(root, value, option, rootDescription)
    }
}

internal fun matchFiles(dataDirectory: Path): List<Path> = Files.walk(dataDirectory).use { files ->
    files.filter { it != dataDirectory && Files.isRegularFile(it) && it.name.endsWith(".json", ignoreCase = true) }
        .sorted()
        .toList()
}