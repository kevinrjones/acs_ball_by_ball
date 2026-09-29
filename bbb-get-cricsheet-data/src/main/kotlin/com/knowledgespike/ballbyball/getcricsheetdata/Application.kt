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
                log.info(
                    "Starting {} with base directory {}, data directory {}, names directory {}, force {}",
                    APPLICATION_NAME,
                    command.baseDirectory,
                    command.dataDirectory,
                    command.namesDirectory,
                    command.force
                )
                val result = CricsheetDataRetriever().retrieve(
                    RetrievalConfiguration(
                        baseDirectory = command.baseDirectory,
                        dataDirectory = command.dataDirectory,
                        namesDirectory = command.namesDirectory,
                        force = command.force
                    )
                )
                if (result.succeeded) 0 else 1
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
            "Download Cricsheet JSON match data and register CSV files",
            CommandLineArguments.options(),
            "",
            true
        )
    }
}