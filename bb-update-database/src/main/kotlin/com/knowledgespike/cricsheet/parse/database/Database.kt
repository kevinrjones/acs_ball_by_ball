package com.knowledgespike.cricsheet.parse.database

import com.knowledgespike.cricketarchive.InvalidStateException
import com.knowledgespike.cricketarchive.LoggerDelegate
import com.knowledgespike.cricsheet.parse.models.CardDirectoryData
import com.knowledgespike.cricsheet.parse.parser.structure.*
import java.sql.Connection
import java.sql.Statement
import java.sql.Types
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.stream.Stream

class Database(val connection: Connection) {
    private val log by LoggerDelegate()

    private val findFile = "select Id from Matches where filename =?"
    private val findPersonRegistry = "select PersonId from PersonRegistry where personid =?"
    private val findTeam = "select Id from Teams where name =?"
    private val findPlayer = "select PersonId from Players where PersonId =?"
    private val findUmpire = "select PersonId from Umpires where PersonId =?"
    private val findTvUmpire = "select PersonId from TvUmpires where PersonId =?"
    private val findReserveUmpire = "select PersonId from ReserveUmpires where PersonId =?"
    private val findMatchReferee = "select PersonId from MatchReferees where PersonId =?"
    private val findGround = "select Id, Name from Grounds where Name = ?"

    private val findPlayersMatches = "select personid from PlayersMatches where personid=?"
    private val findUmpiresMatches = "select personid from UmpiresMatches where personid=?"
    private val findTvUmpiresMatches = "select personid from TvUmpiresMatches where personid=?"
    private val findReserveUmpiresMatches = "select personid from ReserveUmpiresMatches where personid=?"
    private val findMatchRefereesMatches = "select personid from MatchRefereesMatches where personid=?"
    private val findBall =
        "select id from BallByBall where matchid=? and teamid=? and opponentsid=? and inningsNumber=? and BallNumber=?"

    private val insertPersonRegistry = "insert into PersonRegistry (PersonId, FullName, CaId) values (?, ?, ?)"
    private val insertTeam = "insert into Teams (Name) values (?)"
    private val insertPlayer =
        "insert into Players (PersonId, FullName, SortNamePart, OtherNamePart) values (?, ?, ?, ?)"

    private val insertUmpire =
        "insert into Umpires (PersonId, FullName, SortNamePart, OtherNamePart) values (?, ?, ?, ?)"
    private val insertTvUmpire =
        "insert into TvUmpires (PersonId, FullName, SortNamePart, OtherNamePart) values (?, ?, ?, ?)"
    private val insertReserveUmpire =
        "insert into ReserveUmpires (PersonId, FullName, SortNamePart, OtherNamePart) values (?, ?, ?, ?)"
    private val insertMatchReferee =
        "insert into MatchReferees (PersonId, FullName, SortNamePart, OtherNamePart) values (?, ?, ?, ?)"
    private val insertGround = "insert into Grounds (Name) values (?)"
    private val insertMatch =
        "insert into matches (FileName, MatchInSeries, MatchType, Event, Team1Id, Team1Name," +
                "Team2Id, Team2Name, MatchDate, Season, MatchStartYear, " +
                "MatchStartDate, Duration, BallsPerOver, AddedDate, Location, " +
                "LocationId, TossTeamId, TossDecision, VictoryType, Margin, WhoWonId, WhoLostId) " +
                "values (?,?,?,?,?,?, ?, ?,?,?,?,?,?,?,?,?,?, ?, ?,?,?,?,?)"
    private val insertBall =
        "insert into BallByBall (TeamId, OpponentsId, MatchId, InningsNumber, InningsOrder, " +
                "OverNumber, BallNumber, BallInOver, BowlerId, BatterId, " +
                "NonStrikerId, BatterRuns, ExtraRuns, TotalRuns, NoBalls, " +
                "Wides, LegByes, Byes, NonBoundary, Powerplay, Wicket) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"


    private val insertPlayersMatches = "insert into PlayersMatches (PersonId, MatchId) values (?,?)"
    private val insertUmpiresMatches = "insert into UmpiresMatches (PersonId, MatchId) values (?,?)"
    private val insertTvUmpiresMatches = "insert into TvUmpiresMatches (PersonId, MatchId) values (?,?)"
    private val insertReserveUmpiresMatches = "insert into ReserveUmpiresMatches (PersonId, MatchId) values (?,?)"
    private val insertMatchRefereesMatches = "insert into MatchRefereesMatches (PersonId, MatchId) values (?,?)"

    private val insertWicket = "insert into Wickets (kind) value (?)"
    private val insertBallWicket = "insert into BallsWickets (ballId, wicketId) value (?, ?)"


    private val insertPerson = "insert into PersonRegistry (PersonId, FullName, CaId) values (?,?,?)"


    fun writeMatch(fileName: String, cricSheet: CricSheet, cardDirectoryData: CardDirectoryData) {

        val teamsWithId: List<Team> = upsertTeams(cricSheet.info.teams)

        val players = Translate.getPlayers(cricSheet.info.players, cricSheet)
        insertPlayers(players)

        val umpires = Translate.getOfficials(cricSheet.info.officials?.umpires, cricSheet)
        val tvumpires = Translate.getOfficials(cricSheet.info.officials?.tvUmpires, cricSheet)
        val reserveumpires = Translate.getOfficials(cricSheet.info.officials?.reserveUmpires, cricSheet)
        val matchReferees = Translate.getOfficials(cricSheet.info.officials?.matchReferees, cricSheet)
        insertOfficials(umpires, tvumpires, reserveumpires, matchReferees)

        val venue = cricSheet.info.venue ?: ""
        val location = insertGround(venue)

        val key = addMatchToDatabase(fileName, teamsWithId, location, cricSheet, cardDirectoryData)

        updatePlayersMatches(key, players)
        updateAllOfficialsMatches(key, umpires, tvumpires, reserveumpires, matchReferees)

        addBallByBall(key, teamsWithId, cricSheet)

        connection.commit()
    }

    private fun addBallByBall(
        matchId: Int, teamsWithId: List<Team>, cricSheet: CricSheet
    ) {

        val players = cricSheet.info.registry.people
        var inningsOrder = 0
        val teamToInnings = mutableMapOf<Int, Int>()
        cricSheet.innings.forEach { inning ->
            val powerplays = calculatePowerplays(inning.powerplays, cricSheet.info.ballsPerOver)

            inningsOrder++
            val batterTeam = teamsWithId.find { it.name == inning.team }!!
            val bowlerTeam = teamsWithId.find { it.name != inning.team }!!
            var inningsNumber = teamToInnings.getOrDefault(batterTeam.id, 0)
            inningsNumber++
            teamToInnings.put(batterTeam.id, inningsNumber)

            var deliveryNumber = 0
            inning.overs?.forEach { over ->
                val overNumber = over.over
                var ballInOver = 0
                over.deliveries.forEach { delivery ->
                    deliveryNumber++
                    ballInOver++

                    val batterId = players[delivery.batter]!!
                    val nonStrikerId = players[delivery.nonStriker]!!
                    val bowlerId = players[delivery.bowler]!!
                    val batterRuns = delivery.runs.batter
                    val extraRuns = delivery.runs.extras
                    val totalRuns = delivery.runs.total
                    val nonBoundary = delivery.runs.nonBoundary
                    val noballs = delivery.extras?.noballs ?: 0
                    val wides = delivery.extras?.wides ?: 0
                    val byes = delivery.extras?.byes ?: 0
                    val legbyes = delivery.extras?.legbyes ?: 0

                    var powerPlayNumber = 0
                    powerplays.forEach { ppd ->
                        if (deliveryNumber in ppd.from..ppd.to) {
                            powerPlayNumber = ppd.powerplayNumber
                        }
                    }

                    val wicket: Int = if (delivery.wickets != null)
                        if (delivery.wickets.isNotEmpty()) 1 else 0
                    else
                        0

                    val findStmt = connection.prepareStatement(findBall)

                    findStmt.setInt(1, matchId)
                    findStmt.setInt(2, batterTeam.id)
                    findStmt.setInt(3, bowlerTeam.id)
                    findStmt.setInt(4, inningsNumber)
                    findStmt.setInt(5, deliveryNumber)

                    val rs = findStmt.executeQuery()
                    if (!rs.next()) {
                        val insertStatement = connection.prepareStatement(insertBall, Statement.RETURN_GENERATED_KEYS)

                        insertStatement.setInt(1, batterTeam.id)
                        insertStatement.setInt(2, bowlerTeam.id)
                        insertStatement.setInt(3, matchId)
                        insertStatement.setInt(4, inningsNumber)
                        insertStatement.setInt(5, inningsOrder)
                        insertStatement.setInt(6, overNumber)
                        insertStatement.setInt(7, deliveryNumber)
                        insertStatement.setInt(8, ballInOver)
                        insertStatement.setString(9, bowlerId)
                        insertStatement.setString(10, batterId)
                        insertStatement.setString(11, nonStrikerId)
                        insertStatement.setInt(12, batterRuns)
                        insertStatement.setInt(13, extraRuns)
                        insertStatement.setInt(14, totalRuns)
                        insertStatement.setInt(15, noballs)
                        insertStatement.setInt(16, wides)
                        insertStatement.setInt(17, legbyes)
                        insertStatement.setInt(18, byes)

                        if (nonBoundary == null) {
                            insertStatement.setNull(19, Types.INTEGER)
                        } else {
                            insertStatement.setBoolean(19, nonBoundary)
                        }
                        insertStatement.setInt(20, powerPlayNumber)
                        insertStatement.setInt(21, wicket)

                        try {
                            val count = insertStatement.executeUpdate()
                            check(count == 1)
                        } catch (e: Exception) {
//                            log.debug("Bowler is: {}", bowler)
                            log.debug("Batter is: {}", batterId)
                            throw e
                        }

                        val keys = insertStatement.generatedKeys
                        val ballId = if (keys.next()) keys.getInt(1)
                        else throw IllegalStateException()


                        if (wicket == 1) {
                            addWickets(ballId, delivery.wickets!!)
                        }
                    }

                }
            }
        }
    }

    private fun addWickets(ballId: Int, wickets: Array<Wickets>) {
        val insertWicketStmt = connection.prepareStatement(insertWicket, Statement.RETURN_GENERATED_KEYS)
        wickets.forEach { wicket ->
            insertWicketStmt.setString(1, wicket.kind)

            var count = insertWicketStmt.executeUpdate()
            check(count == 1)
            val keys = insertWicketStmt.generatedKeys
            val wicketId = if (keys.next()) keys.getInt(1)
            else throw IllegalStateException()

            val insertBallsWicketsStatement = connection.prepareStatement(insertBallWicket)
            insertBallsWicketsStatement.setInt(1, ballId)
            insertBallsWicketsStatement.setInt(2, wicketId)

            count = insertBallsWicketsStatement.executeUpdate()

            check(count == 1)
        }
    }

    private fun calculatePowerplays(powerplays: Array<PowerPlays>?, ballsPersOVer: Int): List<PowerplayDetails> {
        val powerplayDetails = mutableListOf<PowerplayDetails>()
        powerplays?.let { pps ->
            var ppNumber = 0
            pps.forEach {
                ppNumber++
                val fromBall = calculateBallFromOver(it.from, ballsPersOVer)
                val toBall = calculateBallFromOver(it.to, ballsPersOVer)
                val type = it.type
                powerplayDetails.add(PowerplayDetails(ppNumber, fromBall, toBall, type))
            }
        }
        return powerplayDetails
    }

    private fun calculateBallFromOver(over: String, ballsPersOVer: Int): Int {
        val parts = over.split(".")
        val overs = parts[0].toInt()
        val balls = parts[1].toInt()
        return (overs * ballsPersOVer) + balls // assuming 6 ball over,
    }

    private fun updateAllOfficialsMatches(
        matchKey: Int,
        umpires: List<Person>,
        tvumpires: List<Person>,
        reserveumpires: List<Person>,
        matchReferees: List<Person>
    ) {
        updateOfficialsMatches(matchKey, umpires, findUmpiresMatches, insertUmpiresMatches)
        updateOfficialsMatches(matchKey, reserveumpires, findReserveUmpiresMatches, insertReserveUmpiresMatches)
        updateOfficialsMatches(matchKey, tvumpires, findTvUmpiresMatches, insertTvUmpiresMatches)
        updateOfficialsMatches(matchKey, matchReferees, findMatchRefereesMatches, insertMatchRefereesMatches)
    }

    private fun updatePlayersMatches(matchKey: Int, playersTeams: Map<String, List<Person>>) {
        playersTeams.forEach { (team, players) ->
            players.forEach { player ->
                val findStmt = connection.prepareStatement(findPlayersMatches)
                findStmt.setString(1, player.id)

                val rs = findStmt.executeQuery()
                if (!rs.next()) {

                    val insertStatement = connection.prepareStatement(insertPlayersMatches)

                    insertStatement.setString(1, player.id)
                    insertStatement.setInt(2, matchKey)

                    val count = insertStatement.executeUpdate()

                    check(count == 1)
                }
            }
        }
    }

    private fun updateOfficialsMatches(
        matchKey: Int,
        officials: List<Person>,
        findStmt: String,
        insertStmt: String
    ) {
        officials.forEach { person ->
            val findStmt = connection.prepareStatement(findStmt)
            findStmt.setString(1, person.id)

            val rs = findStmt.executeQuery()
            if (!rs.next()) {

                val insertStatement = connection.prepareStatement(insertStmt)

                insertStatement.setString(1, person.id)
                insertStatement.setInt(2, matchKey)

                val count = insertStatement.executeUpdate()

                check(count == 1)
            }

        }
    }

    private fun addMatchToDatabase(
        fileName: String,
        teamsWithId: List<Team>,
        location: Location,
        cricSheet: CricSheet,
        cardDirectoryData: CardDirectoryData
    ): Int {
        val teamType = cricSheet.info.teamType // 'international' or 'club'
        val eventName = cricSheet.info.event?.name ?: ""
        val eventMatch = cricSheet.info.event?.matchNumber ?: 0

        val matchType = if (!cardDirectoryData.mixedGender) {
            cardDirectoryData.matchType
        } else {
            if (cricSheet.info.gender.lowercase() == "female")
                "w" + cardDirectoryData.matchType
            else
                cardDirectoryData.matchType
        }

        val teams = cricSheet.info.teams
        val homeTeam = teamsWithId.find { it.name == teams[0] }!!
        val awayTeam = teamsWithId.find { it.name == teams[1] }!!
        val matchDate = cricSheet.info.dates.joinToString(separator = ";")
        val season = cricSheet.info.season


        val sdf = SimpleDateFormat("yyyy-MM-dd")
        val matchStartDate = sdf.parse(cricSheet.info.dates[0])

        val calendar = GregorianCalendar()
        calendar.time = matchStartDate

        val duration = cricSheet.info.dates.size
        val ballsPerOver = cricSheet.info.ballsPerOver
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val addedDate = java.sql.Date.valueOf(now.toLocalDate())

        val toss = teamsWithId.find { it.name == cricSheet.info.toss.winner }!!
        val tossDecision = cricSheet.info.toss.decision
        val victoryType = getVictoryType(cricSheet.info.outcome)
        val margin = getMargin(cricSheet.info.outcome)
        var whoWonId = Team(1, "Unknown")
        var whoLostId = Team(1, "Unknown")
        if (cricSheet.info.outcome.winner != null) {
            whoWonId = teamsWithId.find { it.name == cricSheet.info.outcome.winner } ?: Team(1, "Unknown")
            whoLostId = teamsWithId.find { it.name != cricSheet.info.outcome.winner } ?: Team(1, "Unknown")
        }

        val findStmt = connection.prepareStatement(findFile)
        findStmt.setString(1, fileName)

        val insertStatement = connection.prepareStatement(insertMatch, Statement.RETURN_GENERATED_KEYS)

        insertStatement.setString(1, fileName)
        insertStatement.setInt(2, eventMatch)
        insertStatement.setString(3, matchType)
        insertStatement.setString(4, eventName)
        insertStatement.setInt(5, homeTeam.id)
        insertStatement.setString(6, homeTeam.name)
        insertStatement.setInt(7, awayTeam.id)
        insertStatement.setString(8, awayTeam.name)
        insertStatement.setString(9, matchDate)
        insertStatement.setString(10, season)
        insertStatement.setString(11, calendar.get(Calendar.YEAR).toString())
        insertStatement.setDate(12, java.sql.Date(matchStartDate.time))
        insertStatement.setInt(13, duration)
        insertStatement.setInt(14, ballsPerOver)
        insertStatement.setDate(15, addedDate)
        insertStatement.setString(16, location.name)
        insertStatement.setInt(17, location.id)
        insertStatement.setInt(18, toss.id)
        insertStatement.setString(19, tossDecision)
        insertStatement.setString(20, victoryType)
        insertStatement.setInt(21, margin)
        insertStatement.setInt(22, whoWonId.id)
        insertStatement.setInt(23, whoLostId.id)

        insertStatement.executeUpdate()
        val keys = insertStatement.generatedKeys
        if (keys.next()) return keys.getInt(1)
        else throw InvalidStateException("Expected to get primary key")

    }

    private fun getMargin(outcome: Outcome): Int {
        val by = outcome.by ?: return 0

        return by.runs ?: by.wickets ?: by.innings ?: 0
    }

    private fun getVictoryType(outcome: Outcome): String {
        val by = outcome.by

        if (outcome.result != null) return outcome.result

        return if (by == null) "unknown"
        else if (by.innings != null) "innings"
        else if (by.wickets != null) "wickets"
        else if (by.runs != null) "runs"
        else "unknown"
    }

    private fun insertGround(name: String): Location {
        val findStmt = connection.prepareStatement(findGround)
        findStmt.setString(1, name)

        val rs = findStmt.executeQuery()

        if (!rs.next()) {

            val insertStmt = connection.prepareStatement(insertGround, Statement.RETURN_GENERATED_KEYS)
            insertStmt.setString(1, name)

            val count = insertStmt.executeUpdate()

            val keys = insertStmt.generatedKeys
            if (keys.next()) return Location(keys.getInt(1), name)
            else throw InvalidStateException("Expected to get primary key")
        } else {
            return Location(rs.getInt(1), rs.getString(2))
        }
    }

    private fun insertOfficials(
        umpires: List<Person>, tvumpires: List<Person>, reserveumpires: List<Person>, matchReferees: List<Person>
    ) {
        umpires.forEach { official ->
            insertOfficial(official, findUmpire, insertUmpire)
        }
        tvumpires.forEach { official ->
            insertOfficial(official, findTvUmpire, insertTvUmpire)
        }
        reserveumpires.forEach { official ->
            insertOfficial(official, findReserveUmpire, insertReserveUmpire)
        }
        matchReferees.forEach { official ->
            insertOfficial(official, findMatchReferee, insertMatchReferee)
        }
    }

    private fun insertOfficial(official: Person, findStatement: String, insertStatement: String) {
        val findStmt = connection.prepareStatement(findStatement)
        findStmt.setString(1, official.id)

        val rs = findStmt.executeQuery()

        if (!rs.next()) {

            val (sortNamePart, otherNamePart) = getNameParts(official.name)
            val insertStmt = connection.prepareStatement(insertStatement)

            insertStmt.setString(1, official.id)
            insertStmt.setString(2, official.name)
            insertStmt.setString(3, sortNamePart)
            insertStmt.setString(4, otherNamePart)

            val count = insertStmt.executeUpdate()

            check(count == 1)

        }
    }

    private fun insertPlayers(playersTeams: Map<String, List<Person>>) {
        playersTeams.forEach { (team, players) ->
            players.forEach { player ->
                val findStmt = connection.prepareStatement(findPlayer)
                findStmt.setString(1, player.id)

                val rs = findStmt.executeQuery()
                if (!rs.next()) {
                    val (sortNamePart, otherNamePart) = getNameParts(player.name)
                    val insertStmt = connection.prepareStatement(insertPlayer)
                    insertStmt.setString(1, player.id)
                    insertStmt.setString(2, player.name)
                    insertStmt.setString(3, sortNamePart)
                    insertStmt.setString(4, otherNamePart)

                    try {
                        val count = insertStmt.executeUpdate()
                    } catch (e: Exception) {
                        log.error("Unable to insert player: {}", player)
                        throw e
                    }

                }
            }
        }
    }

    private fun upsertTeams(teams: Array<String>): List<Team> {
        return teams.map { name ->
            val findStmt = connection.prepareStatement(findTeam)
            findStmt.setString(1, name)

            val rs = findStmt.executeQuery()

            if (rs.next()) {
                return@map Team(rs.getInt(1), name)
            } else {
                val insertStmt = connection.prepareStatement(insertTeam, Statement.RETURN_GENERATED_KEYS)
                insertStmt.setString(1, name)

                insertStmt.executeUpdate()
                val keys = insertStmt.generatedKeys
                if (keys.next()) return@map Team(keys.getInt(1), name)
                else throw InvalidStateException("Expected to get primary key")

            }
        }
    }

    fun writeAllPeople(people: Stream<PersonRegistryEntity>) {
        people.forEach { pr ->
            writePersonRegistry(pr)
        }
        connection.commit()
    }

    private fun writePersonRegistry(personRegistry: PersonRegistryEntity) {

        val findStmt = connection.prepareStatement(findPersonRegistry)
        findStmt.setString(1, personRegistry.id)
        val res = findStmt.executeQuery()


        if (!res.next()) {
            log.info("Person does not exist in DB: ${personRegistry.name}")

            val insertStmt = connection.prepareStatement(insertPersonRegistry)
            insertStmt.setString(1, personRegistry.id)
            insertStmt.setString(2, personRegistry.name)
            insertStmt.setInt(3, personRegistry.caId)

            val rows = insertStmt.executeUpdate()
            check(rows == 1)
        }
    }

    fun shouldParse(fileName: String): Boolean {
        return !doesFileExistInDatabase(fileName)
    }

    private fun doesFileExistInDatabase(fileName: String): Boolean {
        log.debug("Does file exist {}", fileName)
        val findStmt = connection.prepareStatement(findFile)

        findStmt.setString(1, fileName)

        val rs = findStmt.executeQuery()

        return rs.next()
    }
}

fun getNameParts(personName: String): Pair<String, String> {
    var name: String = ""

    val sortNamePart: String
    val otherNamePart: String

    name = if (personName.contains("(")) {
        name.substringBefore("(").trim()
    } else {
        personName
    }

    val fullName = name

    if (!fullName.contains(" ")) {
        otherNamePart = ""
        sortNamePart = fullName.replace("'", "")
    } else {
        val parts = fullName.split(" ")

        // Assume initials are all upper case of name would be
        // GS Sobers for example
        when {
            parts[0].uppercase(Locale.getDefault()) == parts[0] -> {
                val others = parts.drop(1)
                otherNamePart = parts[0].trim()
                sortNamePart = others.joinToString("").replace(" ", "").replace("'", "")
            }

            parts[0] == "Lord" -> {
                // lord hawke
                // lord j graham
                val others = parts.drop(1)
                val sort = parts.last()
                otherNamePart = others.joinToString(" ")
                sortNamePart = sort.replace(" ", "").replace("'", "")
            }

            (parts[0] == "Sir" || parts[0] == "Earl" || parts[0] == "Duke" || parts[0] == "Nawab") && parts[1] == "of" -> {
                val others = parts.dropLast(1)
                val sort = parts.drop(2)
                otherNamePart = others.joinToString(" ")
                sortNamePart = sort[0].replace(" ", "").replace("'", "")
            }

            else -> {
                // full name is the sort name
                // e.g. Majid Khan
                otherNamePart = ""
                sortNamePart = fullName.replace(" ", "").replace("'", "")
            }
        }
    }
    return Pair(sortNamePart, otherNamePart)

}

data class PowerplayDetails(val powerplayNumber: Int, val from: Int, val to: Int, val type: String)