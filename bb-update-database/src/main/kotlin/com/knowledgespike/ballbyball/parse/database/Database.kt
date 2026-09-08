package com.knowledgespike.ballbyball.parse.database

import com.knowledgespike.cricketarchive.InvalidStateException
import com.knowledgespike.cricketarchive.LoggerDelegate
import com.knowledgespike.ballbyball.parse.models.CardDirectoryData
import com.knowledgespike.ballbyball.parse.parser.structure.*
import com.knowledgespike.ballbyball.parse.database.adapter.*
import java.time.LocalDate
import java.util.*
import java.util.stream.Stream

class Database(private val outputAdapter: OutputAdapter) {
    private val log by LoggerDelegate()

    companion object {
        const val UNKNOWN_PLAYER_ID = "unknown"
        const val SUBSTITUTE_PLAYER_NAME = "[substitute]"
    }


    fun writeMatch(fileName: String, cricSheet: CricSheet, cardDirectoryData: CardDirectoryData) {
        log.debug("Parsing match: {}", fileName)
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

        outputAdapter.commit()
    }

    private fun addBallByBall(match: WarehouseMatch, teams: List<Team>, cricSheet: CricSheet) {
        val players = cricSheet.info.registry.people
        val matchDateKey = cricSheet.info.dates.firstOrNull()?.let(::parseDate)?.let(::upsertDate)
        var inningsOrder = 0

        cricSheet.innings.forEachIndexed { inningsIndex, inning ->
            log.debug("Parsing innings: {}, {}", inning.team, inningsIndex)
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
                        addWickets(deliveryKey, delivery.wickets.orEmpty(), players)
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
        val existingKey = outputAdapter.findDeliveryKey(match.key, innings.key, inningsOrder)
        if (existingKey != null) return null

        return outputAdapter.insertDelivery(
            DeliveryRecord(
                matchKey = match.key,
                matchDateKey = matchDateKey,
                inningsKey = innings.key,
                battingTeamKey = battingTeam.id,
                bowlingTeamKey = bowlingTeam.id,
                batterKey = batterKey,
                nonStrikerKey = nonStrikerKey,
                bowlerKey = bowlerKey,
                overNumber = overNumber,
                ballNumber = ballNumber,
                ballInOver = ballInOver,
                inningsOrder = inningsOrder,
                delivery = delivery,
                powerplay = powerplay
            )
        )
    }

    private fun addWickets(deliveryKey: Long, wickets: List<Wickets>, people: Map<String, String>) {
        wickets.forEach { wicket ->
            val wicketKey = outputAdapter.insertWicket(wicket.kind)
            outputAdapter.insertDeliveryWicket(deliveryKey, wicketKey)
            wicket.fielders.orEmpty().forEach { fielder ->
                val fielderKey = if (fielder.name != null) {
                    val fielderName = fielder.name
                    requirePersonKey(people[fielderName], fielderName)
                } else if (fielder.substitute == true) {
                    upsertPerson(UNKNOWN_PLAYER_ID, SUBSTITUTE_PLAYER_NAME, 0)
                } else {
                    throw InvalidStateException("Fielder has no name for wicket ${wicket.kind}")
                }
                outputAdapter.insertDeliveryFielder(deliveryKey, wicketKey, fielderKey)
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
            outputAdapter.insertMatchPerson(match.key, personKey, roleCode)
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

        val match = outputAdapter.insertMatch(
            MatchRecord(
                fileName = fileName,
                matchInSeries = eventMatch,
                matchType = matchType,
                eventName = eventName,
                matchDateText = matchDate,
                season = cricSheet.info.season,
                matchStartYear = matchStartDate?.year?.toString() ?: "",
                matchStartDateKey = matchStartDateKey,
                ballsPerOver = ballsPerOver,
                team1Key = homeTeam.id,
                team2Key = awayTeam.id,
                groundKey = location.id,
                tossTeamKey = toss.id,
                tossDecision = tossDecision,
                victoryType = victoryType,
                winnerTeamKey = winner?.id,
                loserTeamKey = loser?.id
            )
        )
        outputAdapter.insertMatchFact(match.key, matchStartDateKey, location.id, duration, margin)
        return match

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
        return outputAdapter.upsertGround(name)
    }

    private fun upsertPerson(sourceId: String, fullName: String, caId: Int): Long {
        return outputAdapter.upsertPerson(sourceId, fullName, caId)
    }


    private fun upsertTeams(teams: List<String>): List<Team> {
        return teams.map(outputAdapter::upsertTeam)
    }

    fun writeAllPeople(people: Stream<PersonRegistryEntity>) {
        outputAdapter.writeAllPeople(people.iterator().asSequence())
        outputAdapter.commit()
    }

    private fun upsertDate(date: LocalDate): Int {
        return outputAdapter.upsertDate(date)
    }

    private fun upsertInnings(
        matchKey: Long,
        inningsNumber: Int,
        battingTeamKey: Long,
        bowlingTeamKey: Long
    ): WarehouseInnings {
        return outputAdapter.upsertInnings(matchKey, inningsNumber, battingTeamKey, bowlingTeamKey)
    }

    private fun requirePersonKey(sourceId: String?, displayName: String): Long {
        if (sourceId == null)
            throw InvalidStateException("Person is not registered: $displayName")
        return upsertPerson(sourceId, displayName, 0)
    }

    private fun parseDate(value: String): LocalDate = LocalDate.parse(value)

    fun shouldParse(fileName: String): Boolean {
        return outputAdapter.findMatchKey(fileName) == null
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