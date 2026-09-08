package com.knowledgespike.cricsheet.parse

import com.knowledgespike.cricketarchive.LoggerDelegate
import com.knowledgespike.cricketarchive.shared.DatabaseConnection
import com.knowledgespike.cricsheet.parse.parser.BallByBallParser
import com.knowledgespike.cricsheet.parse.parser.PlayerRegistryParser
import com.knowledgespike.cricsheet.parse.database.Database
import com.knowledgespike.cricsheet.parse.database.CsvOutputAdapter
import com.knowledgespike.cricsheet.parse.database.OutputAdapter
import com.knowledgespike.cricsheet.parse.database.PersonRegistryEntity
import com.knowledgespike.cricsheet.parse.database.SqlOutputAdapter
import com.knowledgespike.cricsheet.parse.database.SqlScriptOutputAdapter
import com.knowledgespike.cricsheet.parse.models.CardDirectoryData
import org.apache.commons.cli.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name


class Application {

    companion object {
        private val log by LoggerDelegate()

        @JvmStatic
        fun main(args: Array<String>) {
            try {

                // run --args="-bd /Users/kevinjones/Dropbox/projects/cricket/ballbyball/Archive/cricsheet
                // -pr people.csv -c jdbc:mysql://localhost:3306/cricsheet?useSSL=true&requireSSL=true
                // -u cricsheet -p p4ssw0rd"
                val cardDirectories = listOf(
                    CardDirectoryData("apl_json", "Afghanistan Premier League", "tt"),
                    CardDirectoryData("bbl_json", "Big Bash", "tt"),
                    CardDirectoryData("blz_json", "Blaze", "wtt"),
                    CardDirectoryData("bpl_json", "Bangladesh Premier League", "tt"),
                    CardDirectoryData("bwt_json", "Bob Willis Trophy", "f"),
                    CardDirectoryData("cch_json", "County Championship", "f"),
                    CardDirectoryData("cec_json", "Charlotte Edwards Cup", "wtt"),
                    CardDirectoryData("cpl_json", "Caribbean Premier League", "tt"),
                    CardDirectoryData("ctc_json", "CSA T20 Challenge", "tt"),
                    CardDirectoryData("hnd_json", "The Hundred", "tt", true),
                    CardDirectoryData("frb_json", "Fairbreak", "wmisc"),
                    CardDirectoryData("ilt_json", "International League T20", "mmisc"),
                    CardDirectoryData("ipl_json", "Indian Premier League", "tt"),
                    CardDirectoryData("ipo_json", "Cricket Ireland Inter-Provincial Limited Over Cup", "a"),
                    CardDirectoryData("ipt_json", "Cricket Ireland Inter-Provincial Twenty20 Trophy", "tt"),
                    CardDirectoryData("it20s_json", "International T20", "itt", true),
                    CardDirectoryData("lpl_json", "Lanka Premier League", "tt"),
                    CardDirectoryData("mcl_json", "Major Clubs Limited Over Tournament", "tt"),
                    CardDirectoryData("mct_json", "Major Clubs T20 Tournament", "tt"),
                    CardDirectoryData("mdms_json", "Multiday Matches", "f"), // duplicates of ccg, ssh, bwt
                    CardDirectoryData("mlc_json", "Major League Cricket", "tt"),
                    CardDirectoryData("mlt_json", "Major League Tournament", "f"),
                    CardDirectoryData("msl_json", "Mzansi Super League", "tt"),
                    CardDirectoryData("npl_json", "Nepal Premier League", "tt"),
                    CardDirectoryData("ntb_json", "T20 Blast matches", "tt"),
                    CardDirectoryData("odis_json", "One-Day Internationals", "a", true),
                    CardDirectoryData("odms_json", "ICC World Cricket League Americas Region Division One", "a"),
                    CardDirectoryData("pks_json", "Plunket Shield", "f"),
                    CardDirectoryData("psl_json", "Pakistan Super League", "tt"),
                    CardDirectoryData("rhf_json", "Rachael Heyhoe-Flint Trophy", "wa"),
                    CardDirectoryData("rlc_json", "Royal London Cup", "a"),
                    CardDirectoryData("sat_json", "SA T20", "tt"),
                    CardDirectoryData("ssh_json", "Sheffield Shield", "f"), // add sheffied shield before mdm
                    CardDirectoryData("sft_json", "West Indies Super 50", "wa"),
                    CardDirectoryData("sma_json", "Syed Mushtaq Ali Trophy", "tt"),
                    CardDirectoryData("ssm_json", "Super Smash", "tt", true),
                    CardDirectoryData("t20s_json", "International T20s", "itt", true),
                    CardDirectoryData("tests_json", "Test Matches", "t", true),
                    CardDirectoryData("wbb_json", "Women's Big Bash", "wtt"),
                    CardDirectoryData("wcl_json", "Women's Caribbean Premier League", "wtt"),
                    CardDirectoryData("wod_json", "ECB Women's One-Day Cup", "wa"),
                    CardDirectoryData("wpl_json", "Women's Premier League", "wtt"),
                    CardDirectoryData("wsl_json", "Women's Cricket Super League", "wtt"),
                    CardDirectoryData("wtb_json", "Women's Blast", "wtt"),
                    CardDirectoryData("wtc_json", "Women's T20 Challenge", "wtt"),
                )

//                val exceptions = listOf("804779.json", "1146789.json", "1002157.json")
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

                var baseDirectory = cmd.getOptionValue("bd")
                val playerRegistry = cmd.getOptionValue("pr")
                val outputType = cmd.getOptionValue("ot", "SQL").uppercase()
                val outputFile = cmd.getOptionValue("o") ?: cmd.getOptionValue("sf")
                val csvDirectory = cmd.getOptionValue("cd")


                if (!baseDirectory.endsWith('/'))
                    baseDirectory += "/"

                val playerRegistryParser = PlayerRegistryParser()
                val players = playerRegistryParser.parse(File(baseDirectory + playerRegistry))
                val people = players.map {
                    PersonRegistryEntity(it.id, it.name, it.caId.toIntOrNull() ?: 0)
                }.toList()

                when (outputType) {
                    "SQL_FILE" -> {
                        require(!outputFile.isNullOrBlank()) { "--outputFile is required when --outputType SQL_FILE is selected" }
                        SqlScriptOutputAdapter(Path.of(outputFile)).use { adapter ->
                            runImport(adapter, baseDirectory, people, cardDirectories, exceptions)
                        }
                    }

                    "SQL", "DATABASE" -> {
                        if (outputType == "SQL" && !outputFile.isNullOrBlank()) {
                            SqlScriptOutputAdapter(Path.of(outputFile)).use { adapter ->
                                runImport(adapter, baseDirectory, people, cardDirectories, exceptions)
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
                            SqlOutputAdapter(db.connection).use { adapter ->
                                runImport(adapter, baseDirectory, people, cardDirectories, exceptions)
                            }
                        }
                    }

                    "CSV" -> {
                        require(!csvDirectory.isNullOrBlank()) {
                            "--csvDir is required when --outputType CSV is selected"
                        }
                        CsvOutputAdapter(Path.of(csvDirectory)).use { adapter ->
                            runImport(adapter, baseDirectory, people, cardDirectories, exceptions)
                        }
                    }

                    else -> throw IllegalArgumentException("Unsupported output type: $outputType")
                }
            } catch (e: Exception) {
                log.error("Unable to parse cricsheet data", e)
            }
            log.info("finished")
        }

        private fun createCommandLineOptions(): Options {
            val options = Options()
            options.addOption("h", "help", false, "print this message")
            options.addOption("c", "connectionString", true, "database connection string")
            options.addOption("u", "userName", true, "database user name")
            options.addOption("p", "password", true, "database password")
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
            options.addOption(
                Option
                    .builder("bd")
                    .longOpt("baseDirectory")
                    .argName("baseDirectory")
                    .hasArg()
                    .required()
                    .desc("the base directory for scorecards and data")
                    .get()
            )
            options.addOption(
                Option
                    .builder("pr")
                    .longOpt("playerRegistry")
                    .argName("playerRegistry")
                    .hasArg()
                    .required()
                    .desc("the name of the file containing the player registry")
                    .get()
            )
            return options
        }

        private fun runImport(
            adapter: OutputAdapter,
            baseDirectory: String,
            people: List<PersonRegistryEntity>,
            cardDirectories: List<CardDirectoryData>,
            exceptions: List<String>
        ) {
            val database = Database(adapter)
            database.writeAllPeople(people.stream())
            val ballByBallParser = BallByBallParser()

            cardDirectories.forEach { cardDirectoryData ->
                log.info("Inserting {} {}", cardDirectoryData.name, cardDirectoryData.matchType)
                val directory = Paths.get("${baseDirectory}${cardDirectoryData.directoryName}/")
                Files.list(directory).use { files ->
                    files.filter { it.name.endsWith("json") }.forEach { file ->
                        if (!exceptions.contains(file.fileName.toString()) && database.shouldParse(file.fileName.name)) {
                            log.debug(
                                "Parsing : {}, {}, {}",
                                file.fileName,
                                cardDirectoryData.name,
                                cardDirectoryData.matchType
                            )
                            database.writeMatch(file.fileName.name, ballByBallParser.parse(file.toFile()), cardDirectoryData)
                        }
                    }
                }
            }
        }
    }
}