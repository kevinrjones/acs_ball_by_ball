package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.parsecricsheet.source.CricSheet
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
import kotlin.io.path.extension
import kotlin.io.path.name

data class ParseFailure(val input: Path, val message: String)

data class ParseResult(val failures: List<ParseFailure>) {
    val succeeded: Boolean get() = failures.isEmpty()
}

class CricSheetParser(
    private val sourceJson: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val outputJson: Json = Json { encodeDefaults = false },
    private val converter: CricSheetConverter = CricSheetConverter(sourceJson)
) {
    private val log = LoggerFactory.getLogger(CricSheetParser::class.java)

    fun parse(inputDirectory: Path, outputDirectory: Path): ParseResult {
        log.info("Starting Cricsheet parsing from {} into {}", inputDirectory, outputDirectory)
        require(Files.isDirectory(inputDirectory)) { "Input directory does not exist: $inputDirectory" }
        Files.createDirectories(outputDirectory)

        val inputs = jsonFiles(inputDirectory)
        log.info("Found {} Cricsheet JSON files in {}", inputs.size, inputDirectory)
        if (inputs.isEmpty()) {
            log.warn("No Cricsheet JSON files found in {}", inputDirectory)
        }

        val failures = mutableListOf<ParseFailure>()
        inputs.forEach { input ->
            val output = outputDirectory.resolve(inputDirectory.relativize(input))
            try {
                log.debug("Parsing {} into {}", input, output)
                val matchData = converter.convert(sourceJson.decodeFromString<CricSheet>(Files.readString(input)))
                writeAtomically(
                    output,
                    outputJson.encodeToString(matchData)
                )
                log.debug("Wrote normalized match data to {}", output)
            } catch (exception: Exception) {
                log.warn("Unable to parse {}", input, exception)
                failures += ParseFailure(input, exception.message ?: exception::class.simpleName.orEmpty())
            }
        }
        log.info(
            "Completed Cricsheet parsing of {} files with {} failures",
            inputs.size,
            failures.size
        )
        return ParseResult(failures)
    }

    private fun jsonFiles(inputDirectory: Path): List<Path> = Files.walk(inputDirectory).use { files ->
        files.filter { Files.isRegularFile(it) && it.extension.equals("json", ignoreCase = true) }
            .sorted()
            .toList()
    }

    private fun writeAtomically(path: Path, content: String) {
        Files.createDirectories(path.parent)
        val temporary = Files.createTempFile(path.parent, ".${path.name}.", ".tmp")
        try {
            Files.writeString(temporary, content)
            try {
                Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}