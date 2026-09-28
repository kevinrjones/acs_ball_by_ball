package com.knowledgespike.ballbyball.getcricsheetdata

import com.knowledgespike.cricketarchive.LoggerDelegate
import org.apache.commons.cli.ParseException
import org.apache.commons.cli.help.HelpFormatter
import kotlin.system.exitProcess

object Application {
    private const val APPLICATION_NAME = "bbb-get-cricsheet-data"
    private const val APPLICATION_VERSION = "0.1.0"
    private val log by LoggerDelegate()

    @JvmStatic
    fun main(args: Array<String>) {
        val exitCode = execute(args)
        if (exitCode != 0) {
            exitProcess(exitCode)
        }
    }

    private fun execute(args: Array<String>): Int = try {
        when (val command = CommandLineArguments.parse(args)) {
            ApplicationCommand.Help -> {
                printHelp()
                0
            }

            ApplicationCommand.Version -> {
                println(APPLICATION_VERSION)
                0
            }

            is ApplicationCommand.Run -> {
                log.info("Starting {} with output directory {}", APPLICATION_NAME, command.outputDirectory)
                log.info("Cricsheet data retrieval is not implemented yet")
                0
            }
        }
    } catch (exception: ParseException) {
        log.error("Unable to parse command-line arguments", exception)
        printHelp()
        2
    } catch (exception: Exception) {
        log.error("Unable to start {}", APPLICATION_NAME, exception)
        1
    }

    private fun printHelp() {
        HelpFormatter.builder().get().printHelp(
            APPLICATION_NAME,
            "Prepare to retrieve Cricsheet data",
            CommandLineArguments.options(),
            "",
            true
        )
    }
}