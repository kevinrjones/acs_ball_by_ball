package com.knowledgespike.cricsheet.parse.database

import com.knowledgespike.cricketarchive.InvalidStateException
import com.knowledgespike.cricketarchive.LoggerDelegate
import com.knowledgespike.cricsheet.parse.models.CardDirectoryData
import com.knowledgespike.cricsheet.parse.parser.structure.*
import java.sql.Connection
import java.sql.Statement
import java.sql.Types
import java.sql.Timestamp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import java.util.*
import java.util.stream.Stream

class Database(val connection: Connection) {
    private val log by LoggerDelegate()

    private val findFile = "select match_key from dim_match where file_name = ?"
    private val findPerson = "select person_key from dim_person where source_person_id = ?"
    private val findTeam = "select team_key from dim_team where team_name = ?"
    private val findGround = "select ground_key, ground_name from dim_ground where ground_name = ?"
    private val findDate = "select date_key from dim_date where calendar_date = ?"
    private val findInnings = "select innings_key from dim_innings where match_key = ? and innings_number = ?"
    private val findDelivery =
        "select delivery_key from fact_delivery where match_key = ? and innings_key = ? and innings_order = ?"

    private val insertPerson =
        "insert into dim_person (source_person_id, full_name, sort_name_part, other_name_part, ca_id) values (?, ?, ?, ?, ?)"
    private val insertTeam = "insert into dim_team (source_team_id, team_name) values (?, ?)"
    private val insertGround = "insert into dim_ground (source_ground_id, ground_name) values (?, ?)"
    private val insertDate =
        "insert into dim_date (date_key, calendar_date, calendar_year, calendar_quarter, calendar_month, " +
                "month_name, week_of_year, day_of_month, day_of_week, day_name, is_weekend) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    private val insertMatch =
        "insert into dim_match (source_match_id, source_ca_id, file_name, match_in_series, match_type, event_name, " +
                "match_date_text, season, match_start_year, match_start_date_key, balls_per_over, added_timestamp, " +
                "team1_key, team2_key, ground_key, toss_team_key, toss_decision, victory_type, winner_team_key, loser_team_key) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    private val insertMatchFact =
        "insert into fact_match (match_key, match_date_key, ground_key, duration_days, margin, match_count) values (?, ?, ?, ?, ?, 1)"
    private val insertInnings =
        "insert into dim_innings (match_key, innings_number, batting_team_key, bowling_team_key) values (?, ?, ?, ?)"
    private val insertDelivery =
        "insert into fact_delivery (source_ball_id, match_key, match_date_key, innings_key, batting_team_key, " +
                "bowling_team_key, batter_key, non_striker_key, bowler_key, over_number, ball_number, ball_in_over, " +
                "innings_order, batter_runs, extra_runs, total_runs, no_balls, wides, byes, leg_byes, non_boundary, " +
                "powerplay, wicket_count) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    private val insertMatchPerson =
        "insert into bridge_match_person (match_key, person_key, role_code) values (?, ?, ?) " +
                "on duplicate key update match_person_key = match_person_key"
    private val insertWicket = "insert into dim_wicket (source_wicket_id, wicket_kind) values (?, ?)"
    private val insertDeliveryWicket =
        "insert into bridge_delivery_wicket (delivery_key, wicket_key) values (?, ?)"
    private val nextTeamId = "select coalesce(max(source_team_id), 0) + 1 from dim_team"
    private val nextGroundId = "select coalesce(max(source_ground_id), 0) + 1 from dim_ground"
    private val nextMatchId = "select coalesce(max(source_match_id), 0) + 1 from dim_match"
    private val nextBallId = "select coalesce(max(source_ball_id), 0) + 1 from fact_delivery"
    private val nextWicketId = "select coalesce(max(source_wicket_id), 0) + 1 from dim_wicket"


    fun writeMatch(fileName: String, cricSheet: CricSheet, cardDirectoryData: CardDirectoryData) {
        if (!shouldParse(fileName)) {
            log.info("Match already exists: {}", fileName)
            return
        }

        val teams = upsertTeams(cricSheet.info.teams)
        Translate.getPeople(cricSheet).forEach { person ->
            upsertPerson(person.id, person.name, 0)
        }

        val players = Translate.getPlayers(cricSheet.info.players, cricSheet)
        val umpires = Translate.getOfficials(cricSheet.info.officials?.umpires, cricSheet)
        val tvUmpires = Translate.getOfficials(cricSheet.info.officials?.tvUmpires, cricSheet)
        val reserveUmpires = Translate.getOfficials(cricSheet.info.officials?.reserveUmpires, cricSheet)
        val matchReferees = Translate.getOfficials(cricSheet.info.officials?.matchReferees, cricSheet)
        val ground = upsertGround(cricSheet.info.venue ?: "")
        val match = addMatchToDatabase(fileName, teams, ground, cricSheet, cardDirectoryData)

        addMatchPeople(match, players.values.flatten(), "PLAYER")
        addMatchPeople(match, umpires, "UMPIRE")
        addMatchPeople(match, tvUmpires, "TV_UMPIRE")
        addMatchPeople(match, reserveUmpires, "RESERVE_UMPIRE")
        addMatchPeople(match, matchReferees, "MATCH_REFEREE")
        addBallByBall(match, teams, cricSheet)

        connection.commit()
    }

    private fun addBallByBall(match: WarehouseMatch, teams: List<Team>, cricSheet: CricSheet) {
        val players = cricSheet.info.registry.people
        val matchDateKey = cricSheet.info.dates.firstOrNull()?.let(::parseDate)?.let(::upsertDate)
        var inningsOrder = 0

        cricSheet.innings.forEachIndexed { inningsIndex, inning ->
            val battingTeam = teams.find { it.name == inning.team }
                ?: throw InvalidStateException("Unknown batting team: ${inning.team}")
            val bowlingTeam = teams.firstOrNull { it.id != battingTeam.id }
                ?: throw InvalidStateException("Could not determine bowling team for ${inning.team}")
            val innings = upsertInnings(match.key, inningsIndex + 1, battingTeam.id, bowlingTeam.id)
            val powerplays = calculatePowerplays(inning.powerplays, cricSheet.info.ballsPerOver)
            var deliveryNumber = 0

            inning.overs.orEmpty().forEach { over ->
                over.deliveries.forEachIndexed { ballIndex, delivery ->
                    deliveryNumber++
                    inningsOrder++
                    val batterKey = requirePersonKey(players[delivery.batter], delivery.batter)
                    val nonStrikerKey = requirePersonKey(players[delivery.nonStriker], delivery.nonStriker)
                    val bowlerKey = requirePersonKey(players[delivery.bowler], delivery.bowler)
                    val powerplay = powerplays.firstOrNull { deliveryNumber in it.from..it.to }?.powerplayNumber ?: 0
                    val deliveryKey = insertDeliveryIfAbsent(
                        match = match,
                        matchDateKey = matchDateKey,
                        innings = innings,
                        battingTeam = battingTeam,
                        bowlingTeam = bowlingTeam,
                        batterKey = batterKey,
                        nonStrikerKey = nonStrikerKey,
                        bowlerKey = bowlerKey,
                        overNumber = over.over,
                        ballNumber = deliveryNumber,
                        ballInOver = ballIndex + 1,
                        inningsOrder = inningsOrder,
                        delivery = delivery,
                        powerplay = powerplay
                    )
                    if (deliveryKey != null) {
                        addWickets(deliveryKey, delivery.wickets.orEmpty())
                    }
                }
            }
        }
    }

    private fun insertDeliveryIfAbsent(
        match: WarehouseMatch,
        matchDateKey: Int?,
        innings: WarehouseInnings,
        battingTeam: Team,
        bowlingTeam: Team,
        batterKey: Long,
        nonStrikerKey: Long,
        bowlerKey: Long,
        overNumber: Int,
        ballNumber: Int,
        ballInOver: Int,
        inningsOrder: Int,
        delivery: Delivery,
        powerplay: Int
    ): Long? {
        connection.prepareStatement(findDelivery).use { statement ->
            statement.setLong(1, match.key)
            statement.setLong(2, innings.key)
            statement.setInt(3, inningsOrder)
            statement.executeQuery().use { results ->
                if (results.next()) return null
            }
        }

        val wickets = delivery.wickets.orEmpty()
        connection.prepareStatement(insertDelivery, Statement.RETURN_GENERATED_KEYS).use { statement ->
            statement.setLong(1, nextId(nextBallId))
            statement.setLong(2, match.key)
            setNullableInt(statement, 3, matchDateKey)
            statement.setLong(4, innings.key)
            statement.setLong(5, battingTeam.id)
            statement.setLong(6, bowlingTeam.id)
            statement.setLong(7, batterKey)
            statement.setLong(8, nonStrikerKey)
            statement.setLong(9, bowlerKey)
            statement.setInt(10, overNumber)
            statement.setInt(11, ballNumber)
            statement.setInt(12, ballInOver)
            statement.setInt(13, inningsOrder)
            statement.setInt(14, delivery.runs.batter)
            statement.setInt(15, delivery.runs.extras)
            statement.setInt(16, delivery.runs.total)
            statement.setInt(17, delivery.extras?.noballs ?: 0)
            statement.setInt(18, delivery.extras?.wides ?: 0)
            statement.setInt(19, delivery.extras?.byes ?: 0)
            statement.setInt(20, delivery.extras?.legbyes ?: 0)
            if (delivery.runs.nonBoundary == null) statement.setNull(21, Types.INTEGER)
            else statement.setInt(21, if (delivery.runs.nonBoundary) 1 else 0)
            statement.setInt(22, powerplay)
            statement.setInt(23, wickets.size)
            check(statement.executeUpdate() == 1)
            statement.generatedKeys.use { keys ->
                if (!keys.next()) throw InvalidStateException("Expected delivery key")
                return keys.getLong(1)
            }
        }
    }

    private fun addWickets(deliveryKey: Long, wickets: List<Wickets>) {
        wickets.forEach { wicket ->
            val wicketKey = connection.prepareStatement(insertWicket, Statement.RETURN_GENERATED_KEYS).use { statement ->
                statement.setLong(1, nextId(nextWicketId))
                statement.setString(2, wicket.kind)
                check(statement.executeUpdate() == 1)
                statement.generatedKeys.use { keys ->
                    if (!keys.next()) throw InvalidStateException("Expected wicket key")
                    keys.getLong(1)
                }
            }
            connection.prepareStatement(insertDeliveryWicket).use { statement ->
                statement.setLong(1, deliveryKey)
                statement.setLong(2, wicketKey)
                check(statement.executeUpdate() == 1)
            }
        }
    }

    private fun calculatePowerplays(powerplays: List<PowerPlays>?, ballsPersOVer: Int): List<PowerplayDetails> {
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

    private fun addMatchPeople(match: WarehouseMatch, people: List<Person>, roleCode: String) {
        people.forEach { person ->
            val personKey = requirePersonKey(person.id, person.name)
            connection.prepareStatement(insertMatchPerson).use { statement ->
                statement.setLong(1, match.key)
                statement.setLong(2, personKey)
                statement.setString(3, roleCode)
                statement.executeUpdate()
            }
        }
    }

    private fun addMatchToDatabase(
        fileName: String,
        teamsWithId: List<Team>,
        location: Location,
        cricSheet: CricSheet,
        cardDirectoryData: CardDirectoryData
    ): WarehouseMatch {
        val eventName = cricSheet.info.event?.name ?: ""
        val eventMatch = cricSheet.info.event?.matchNumber ?: 0
        val matchType = if (!cardDirectoryData.mixedGender) {
            cardDirectoryData.matchType
        } else {
            if (cricSheet.info.gender.lowercase() == "female") "w${cardDirectoryData.matchType}"
            else cardDirectoryData.matchType
        }
        val teams = cricSheet.info.teams
        val homeTeam = teamsWithId.find { it.name == teams[0] }
            ?: throw InvalidStateException("Unknown home team: ${teams[0]}")
        val awayTeam = teamsWithId.find { it.name == teams[1] }
            ?: throw InvalidStateException("Unknown away team: ${teams[1]}")
        val matchDate = cricSheet.info.dates.joinToString(separator = ";")
        val matchStartDate = cricSheet.info.dates.firstOrNull()?.let(::parseDate)
        val matchStartDateKey = matchStartDate?.let(::upsertDate)
        val duration = cricSheet.info.dates.size
        val ballsPerOver = cricSheet.info.ballsPerOver
        val toss = teamsWithId.find { it.name == cricSheet.info.toss.winner }
            ?: throw InvalidStateException("Unknown toss winner: ${cricSheet.info.toss.winner}")
        val tossDecision = cricSheet.info.toss.decision
        val victoryType = getVictoryType(cricSheet.info.outcome)
        val margin = getMargin(cricSheet.info.outcome)
        val winner = cricSheet.info.outcome.winner?.let { winnerName ->
            teamsWithId.find { it.name == winnerName }
        }
        val loser = winner?.let { winningTeam -> teamsWithId.firstOrNull { it.id != winningTeam.id } }

        val matchKey = connection.prepareStatement(insertMatch, Statement.RETURN_GENERATED_KEYS).use { statement ->
            statement.setLong(1, nextId(nextMatchId))
            statement.setNull(2, Types.VARCHAR)
            statement.setString(3, fileName)
            statement.setInt(4, eventMatch)
            statement.setString(5, matchType)
            statement.setString(6, eventName)
            statement.setString(7, matchDate)
            statement.setString(8, cricSheet.info.season)
            statement.setString(9, matchStartDate?.year?.toString() ?: "")
            setNullableInt(statement, 10, matchStartDateKey)
            statement.setInt(11, ballsPerOver)
            statement.setTimestamp(12, Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)))
            statement.setLong(13, homeTeam.id)
            statement.setLong(14, awayTeam.id)
            statement.setLong(15, location.id)
            statement.setLong(16, toss.id)
            statement.setString(17, tossDecision)
            statement.setString(18, victoryType)
            setNullableLong(statement, 19, winner?.id)
            setNullableLong(statement, 20, loser?.id)
            check(statement.executeUpdate() == 1)
            statement.generatedKeys.use { keys ->
                if (!keys.next()) throw InvalidStateException("Expected match key")
                keys.getLong(1)
            }
        }

        connection.prepareStatement(insertMatchFact).use { statement ->
            statement.setLong(1, matchKey)
            setNullableInt(statement, 2, matchStartDateKey)
            statement.setLong(3, location.id)
            statement.setInt(4, duration)
            statement.setInt(5, margin)
            check(statement.executeUpdate() == 1)
        }
        return WarehouseMatch(matchKey)

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

    private fun upsertGround(name: String): Location {
        connection.prepareStatement(findGround).use { statement ->
            statement.setString(1, name)
            statement.executeQuery().use { results ->
                if (results.next()) return Location(results.getLong(1), results.getString(2))
            }
        }

        return connection.prepareStatement(insertGround, Statement.RETURN_GENERATED_KEYS).use { statement ->
            statement.setLong(1, nextId(nextGroundId))
            statement.setString(2, name)
            check(statement.executeUpdate() == 1)
            statement.generatedKeys.use { keys ->
                if (!keys.next()) throw InvalidStateException("Expected ground key")
                Location(keys.getLong(1), name)
            }
        }
    }

    private fun upsertPerson(sourceId: String, fullName: String, caId: Int): Long {
        connection.prepareStatement(findPerson).use { statement ->
            statement.setString(1, sourceId)
            statement.executeQuery().use { results ->
                if (results.next()) return results.getLong(1)
            }
        }

        val (sortNamePart, otherNamePart) = getNameParts(fullName)
        return connection.prepareStatement(insertPerson, Statement.RETURN_GENERATED_KEYS).use { statement ->
            statement.setString(1, sourceId)
            statement.setString(2, fullName)
            statement.setString(3, sortNamePart)
            statement.setString(4, otherNamePart)
            statement.setInt(5, caId)
            check(statement.executeUpdate() == 1)
            statement.generatedKeys.use { keys ->
                if (!keys.next()) throw InvalidStateException("Expected person key")
                keys.getLong(1)
            }
        }
    }


    private fun upsertTeams(teams: List<String>): List<Team> {
        return teams.map { name ->
            connection.prepareStatement(findTeam).use { statement ->
                statement.setString(1, name)
                statement.executeQuery().use { results ->
                    if (results.next()) return@map Team(results.getLong(1), name)
                }
            }

            connection.prepareStatement(insertTeam, Statement.RETURN_GENERATED_KEYS).use { statement ->
                statement.setLong(1, nextId(nextTeamId))
                statement.setString(2, name)
                check(statement.executeUpdate() == 1)
                statement.generatedKeys.use { keys ->
                    if (keys.next()) return@map Team(keys.getLong(1), name)
                    throw InvalidStateException("Expected team key")
                }
            }
        }
    }

    fun writeAllPeople(people: Stream<PersonRegistryEntity>) {
        people.forEach(::writePersonRegistry)
        connection.commit()
    }

    private fun writePersonRegistry(personRegistry: PersonRegistryEntity) {
        upsertPerson(personRegistry.id, personRegistry.name, personRegistry.caId)
    }

    private fun upsertDate(date: LocalDate): Int {
        connection.prepareStatement(findDate).use { statement ->
            statement.setDate(1, java.sql.Date.valueOf(date))
            statement.executeQuery().use { results ->
                if (results.next()) return results.getInt(1)
            }
        }

        val dateKey = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
        val weekFields = WeekFields.ISO
        connection.prepareStatement(insertDate).use { statement ->
            statement.setInt(1, dateKey)
            statement.setDate(2, java.sql.Date.valueOf(date))
            statement.setInt(3, date.year)
            statement.setInt(4, (date.monthValue - 1) / 3 + 1)
            statement.setInt(5, date.monthValue)
            statement.setString(6, date.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH))
            statement.setInt(7, date.get(weekFields.weekOfYear()))
            statement.setInt(8, date.dayOfMonth)
            statement.setInt(9, date.dayOfWeek.value)
            statement.setString(10, date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH))
            statement.setBoolean(11, date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY)
            check(statement.executeUpdate() == 1)
        }
        return dateKey
    }

    private fun upsertInnings(
        matchKey: Long,
        inningsNumber: Int,
        battingTeamKey: Long,
        bowlingTeamKey: Long
    ): WarehouseInnings {
        connection.prepareStatement(findInnings).use { statement ->
            statement.setLong(1, matchKey)
            statement.setInt(2, inningsNumber)
            statement.executeQuery().use { results ->
                if (results.next()) return WarehouseInnings(results.getLong(1))
            }
        }

        return connection.prepareStatement(insertInnings, Statement.RETURN_GENERATED_KEYS).use { statement ->
            statement.setLong(1, matchKey)
            statement.setInt(2, inningsNumber)
            statement.setLong(3, battingTeamKey)
            statement.setLong(4, bowlingTeamKey)
            check(statement.executeUpdate() == 1)
            statement.generatedKeys.use { keys ->
                if (!keys.next()) throw InvalidStateException("Expected innings key")
                WarehouseInnings(keys.getLong(1))
            }
        }
    }

    private fun requirePersonKey(sourceId: String?, displayName: String): Long {
        if (sourceId == null) throw InvalidStateException("Person is not registered: $displayName")
        return upsertPerson(sourceId, displayName, 0)
    }

    private fun nextId(statementText: String): Long {
        connection.prepareStatement(statementText).use { statement ->
            statement.executeQuery().use { results ->
                if (results.next()) return results.getLong(1)
                throw InvalidStateException("Expected next source identifier")
            }
        }
    }

    private fun parseDate(value: String): LocalDate = LocalDate.parse(value)


    private fun setNullableInt(statement: java.sql.PreparedStatement, index: Int, value: Int?) {
        if (value == null) statement.setNull(index, Types.INTEGER) else statement.setInt(index, value)
    }

    private fun setNullableLong(statement: java.sql.PreparedStatement, index: Int, value: Long?) {
        if (value == null) statement.setNull(index, Types.BIGINT) else statement.setLong(index, value)
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