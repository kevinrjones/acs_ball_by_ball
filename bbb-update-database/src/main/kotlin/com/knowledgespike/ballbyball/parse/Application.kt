package com.knowledgespike.ballbyball.parse

import com.knowledgespike.cricketarchive.LoggerDelegate
import com.knowledgespike.cricketarchive.shared.DatabaseConnection
import com.knowledgespike.ballbyball.parse.parser.BallByBallParser
import com.knowledgespike.ballbyball.parse.parser.PlayerRegistryParser
import com.knowledgespike.ballbyball.parse.database.Database
import com.knowledgespike.ballbyball.parse.database.PersonRegistryEntity
import com.knowledgespike.ballbyball.parse.database.adapter.OutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.csv.CsvOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.mariadb.SqlOutputAdapter as MariaDbSqlOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.mariadb.SqlScriptOutputAdapter as MariaDbSqlScriptOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.postgres.SqlOutputAdapter as PostgresSqlOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.postgres.SqlScriptOutputAdapter as PostgresSqlScriptOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.sqlite.SqlOutputAdapter as SqliteSqlOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.sqlite.SqlScriptOutputAdapter as SqliteSqlScriptOutputAdapter
import com.knowledgespike.ballbyball.parse.models.cardDirectoryDataForMatch
import org.apache.commons.cli.*
import java.nio.file.Path


class Application {

    companion object {
        private val log by LoggerDelegate()

        @JvmStatic
        fun main(args: Array<String>) {
            try {

                val exceptions = emptyList<String>()

                val options = createCommandLineOptions()

                val formatter = org.apache.commons.cli.help.HelpFormatter.builder().get()
                val header = "Parse cricsheet data into database"
                if (args.isEmpty() || args.any { it == "-h" || it == "--help" }) {
                    formatter.printHelp(
                        "java parsecard",
                        header,
                        options,
                        "",
                        true
                    )
                    return
                }

                val cmd: CommandLine
                val cmdLineParser: CommandLineParser = DefaultParser()
                try {
                    cmd = cmdLineParser.parse(options, args)
                } catch (e: Exception) {
                    println(e.message)
                    formatter.printHelp(
                        "java parsecard",
                        header,
                        options,
                        "",
                        true
                    )
                    System.exit(2)
                    return
                }

                val outputType = cmd.getOptionValue("ot", "SQL").uppercase()
                val outputFile = cmd.getOptionValue("o") ?: cmd.getOptionValue("sf")
                val csvDirectory = cmd.getOptionValue("cd")
                val databaseType = cmd.getOptionValue("db", "mariadb").lowercase()
                val input = UpdateDatabaseArguments.from(cmd)
                val outputPath = outputFile?.let {
                    UpdateDatabaseArguments.resolveOutputPath(input.baseDirectory, it, "--outputFile")
                }
                val csvPath = csvDirectory?.let {
                    UpdateDatabaseArguments.resolveOutputPath(input.baseDirectory, it, "--csvDir")
                }

                val playerRegistryParser = PlayerRegistryParser()
                val players = playerRegistryParser.parse(input.playerRegistry.toFile())
                val people = players.map {
                    PersonRegistryEntity(it.id, it.name, it.caId.toIntOrNull() ?: 0)
                }.toList()

                when (outputType) {
                    "SQL_FILE" -> {
                        require(!outputFile.isNullOrBlank()) { "--outputFile is required when --outputType SQL_FILE is selected" }
                        createScriptOutputAdapter(databaseType, requireNotNull(outputPath)).use { adapter ->
                            runImport(adapter, input, people, exceptions)
                        }
                    }

                    "SQL", "DATABASE" -> {
                        if (outputType == "SQL" && !outputFile.isNullOrBlank()) {
                            createScriptOutputAdapter(databaseType, requireNotNull(outputPath)).use { adapter ->
                                runImport(adapter, input, people, exceptions)
                            }
                            return
                        }
                        val connectionString = cmd.getOptionValue("c")
                            ?: throw IllegalArgumentException("--connectionString is required for DATABASE output")
                        val dbConnection = DatabaseConnection(
                            connectionString,
                            cmd.getOptionValue("u"),
                            cmd.getOptionValue("p")
                        )
                        dbConnection.connect.use { db ->
                            createSqlOutputAdapter(connectionString, db.connection).use { adapter ->
                                runImport(adapter, input, people, exceptions)
                            }
                        }
                    }

                    "CSV" -> {
                        require(!csvDirectory.isNullOrBlank()) {
                            "--csvDir is required when --outputType CSV is selected"
                        }
                        CsvOutputAdapter(requireNotNull(csvPath)).use { adapter ->
                            runImport(adapter, input, people, exceptions)
                        }
                    }

                    else -> throw IllegalArgumentException("Unsupported output type: $outputType")
                }
            } catch (e: Exception) {
                log.error("Unable to parse cricsheet data", e)
            }
            log.info("finished")
        }

        private fun createScriptOutputAdapter(databaseType: String, output: Path): OutputAdapter = when (databaseType) {
            "mariadb", "mysql" -> MariaDbSqlScriptOutputAdapter(output)
            "postgres", "postgresql" -> PostgresSqlScriptOutputAdapter(output)
            "sqlite" -> SqliteSqlScriptOutputAdapter(output)
            else -> throw IllegalArgumentException("Unsupported database type: $databaseType")
        }

        private fun createSqlOutputAdapter(connectionString: String, connection: java.sql.Connection): OutputAdapter =
            when (databaseType(connectionString)) {
                "mariadb" -> MariaDbSqlOutputAdapter(connection)
                "postgres" -> PostgresSqlOutputAdapter(connection)
                "sqlite" -> SqliteSqlOutputAdapter(connection)
                else -> throw IllegalArgumentException("Unsupported database connection: $connectionString")
            }

        private fun databaseType(connectionString: String): String = when {
            connectionString.startsWith("jdbc:mariadb:") || connectionString.startsWith("jdbc:mysql:") -> "mariadb"
            connectionString.startsWith("jdbc:postgresql:") -> "postgres"
            connectionString.startsWith("jdbc:sqlite:") -> "sqlite"
            else -> "unknown"
        }

        private fun createCommandLineOptions(): Options {
            val options = Options()
            options.addOption("h", "help", false, "print this message")
            options.addOption("c", "connectionString", true, "database connection string")
            options.addOption("u", "userName", true, "database user name")
            options.addOption("p", "password", true, "database password")
            options.addOption(
                Option.builder("db").longOpt("database").hasArg().argName("database")
                    .desc("SQL-file database dialect: mariadb, postgres, or sqlite (default: mariadb)").get()
            )
            options.addOption(
                Option.builder("ot").longOpt("outputType").hasArg().argName("outputType")
                    .desc("output destination: SQL, DATABASE, SQL_FILE, or CSV (default: SQL)").get()
            )
            options.addOption(
                Option.builder("o").longOpt("outputFile").hasArg().argName("outputFile")
                    .desc("SQL script output file; with SQL this selects file output").get()
            )
            options.addOption("sf", "sqlFile", true, "alias for outputFile")
            options.addOption("cd", "csvDir", true, "CSV output directory; required for CSV output")
            UpdateDatabaseArguments.options().options.forEach(options::addOption)
            return options
        }

        private fun runImport(
            adapter: OutputAdapter,
            input: UpdateDatabaseInput,
            people: List<PersonRegistryEntity>,
            exceptions: List<String>
        ) {
            val database = Database(adapter)
            database.writeAllPeople(people.stream())
            val ballByBallParser = BallByBallParser()
            importMatches(database, ballByBallParser, input.dataDirectory, exceptions)
        }

        private fun importMatches(
            database: Database,
            parser: BallByBallParser,
            dataDirectory: Path,
            exceptions: List<String>
        ) {
            log.info("Inserting matches from {}", dataDirectory)
            matchFiles(dataDirectory).forEach { file ->
                val sourceFile = file.toAbsolutePath().normalize()
                if (!exceptions.contains(file.fileName.toString()) && database.shouldParse(sourceFile.toString())) {
                    val cricSheet = parser.parse(file.toFile())
                    val cardDirectoryData = cardDirectoryDataForMatch(cricSheet)
                    log.debug(
                        "Parsing match: {}, {}, {}",
                        file.fileName,
                        cardDirectoryData.name,
                        cardDirectoryData.matchType
                    )
                    database.writeMatch(sourceFile.toString(), cricSheet, cardDirectoryData)
                }
            }
        }

    }
}