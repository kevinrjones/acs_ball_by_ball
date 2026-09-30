package com.knowledgespike.ballbyball.parsecricsheet

import org.apache.commons.cli.ParseException
import org.apache.commons.cli.help.HelpFormatter
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

object Application {
    private const val APPLICATION_NAME = "bbb-parse-cricsheet"
    private val log = LoggerFactory.getLogger(Application::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val exitCode = execute(args)
        if (exitCode != 0) exitProcess(exitCode)
    }

    internal fun execute(args: Array<String>): Int = try {
        if (args.isEmpty() || args.any { it == "-h" || it == "--help" }) {
            printHelp()
            0
        } else {
            val input = ParseCricsheetArguments.parse(args)
            log.info("Parsing Cricsheet JSON from {} into {}", input.inputDirectory, input.outputDirectory)
            val result = CricSheetParser().parse(input.inputDirectory, input.outputDirectory)
            result.failures.forEach { failure ->
                log.error("Unable to parse {}: {}", failure.input, failure.message)
            }
            log.info("Finished {} with {} failures", APPLICATION_NAME, result.failures.size)
            if (result.succeeded) 0 else 1
        }
    } catch (exception: ParseException) {
        log.error("Unable to parse command-line arguments: {}", exception.message)
        printHelp()
        2
    } catch (exception: Exception) {
        log.error("Unable to start {}", APPLICATION_NAME, exception)
        1
    }

    private fun printHelp() {
        HelpFormatter.builder().get().printHelp(
            APPLICATION_NAME,
            "Normalize Cricsheet match data into the shared Ball By Ball schema",
            ParseCricsheetArguments.options(),
            "",
            true
        )
    }
}