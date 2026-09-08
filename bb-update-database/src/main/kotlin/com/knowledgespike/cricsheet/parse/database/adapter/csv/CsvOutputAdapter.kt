package com.knowledgespike.cricsheet.parse.database.adapter.csv

import com.knowledgespike.cricsheet.parse.database.Location
import com.knowledgespike.cricsheet.parse.database.PersonRegistryEntity
import com.knowledgespike.cricsheet.parse.database.Team
import com.knowledgespike.cricsheet.parse.database.WarehouseInnings
import com.knowledgespike.cricsheet.parse.database.WarehouseMatch
import com.knowledgespike.cricsheet.parse.database.adapter.OutputAdapter
import com.knowledgespike.cricsheet.parse.database.adapter.DeliveryRecord
import com.knowledgespike.cricsheet.parse.database.adapter.MatchRecord
import com.knowledgespike.cricsheet.parse.database.getNameParts
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/** Writes warehouse tables as UTF-8 CSV files suitable for bulk loading into MariaDB. */
class CsvOutputAdapter(output: Path) : OutputAdapter {
    private val writers = mutableMapOf<String, BufferedWriter>()
    private val people = mutableMapOf<String, Long>()
    private val teams = mutableMapOf<String, Team>()
    private val grounds = mutableMapOf<String, Location>()
    private val dates = mutableMapOf<LocalDate, Int>()
    private val matches = mutableMapOf<String, Long>()
    private val innings = mutableMapOf<Pair<Long, Int>, WarehouseInnings>()
    private val deliveries = mutableMapOf<Triple<Long, Long, Int>, Long>()
    private val matchPeople = mutableSetOf<Triple<Long, Long, String>>()
    private var nextTeamSourceId = 1L
    private var nextGroundSourceId = 1L
    private var nextMatchSourceId = 1L
    private var nextBallSourceId = 1L
    private var nextWicketSourceId = 1L
    private var nextPersonKey = 1L
    private var nextTeamKey = 1L
    private var nextGroundKey = 1L
    private var nextMatchKey = 1L
    private var nextInningsKey = 1L
    private var nextDeliveryKey = 1L
    private var nextWicketKey = 1L
    private var closed = false
    private val path: Path = prepareOutput(output)

    override fun findMatchKey(fileName: String): Long? = matches[fileName]

    override fun upsertPerson(sourceId: String, fullName: String, caId: Int): Long {
        people[sourceId]?.let { return it }
        val key = nextPersonKey++
        val (sortNamePart, otherNamePart) = getNameParts(fullName)
        writeRow(
            "dim_person",
            PERSON_HEADERS,
            listOf(key, sourceId, fullName, sortNamePart, otherNamePart, caId)
        )
        people[sourceId] = key
        return key
    }

    override fun upsertTeam(name: String): Team {
        teams[name]?.let { return it }
        val team = Team(nextTeamKey++, name)
        writeRow("dim_team", TEAM_HEADERS, listOf(team.id, nextTeamSourceId++, team.name))
        teams[name] = team
        return team
    }

    override fun upsertGround(name: String): Location {
        grounds[name]?.let { return it }
        val ground = Location(nextGroundKey++, name)
        writeRow("dim_ground", GROUND_HEADERS, listOf(ground.id, nextGroundSourceId++, ground.name))
        grounds[name] = ground
        return ground
    }

    override fun upsertDate(date: LocalDate): Int {
        dates[date]?.let { return it }
        val dateKey = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
        val weekFields = WeekFields.ISO
        writeRow(
            "dim_date",
            DATE_HEADERS,
            listOf(
                dateKey,
                date,
                date.year,
                (date.monthValue - 1) / 3 + 1,
                date.monthValue,
                date.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH),
                date.get(weekFields.weekOfYear()),
                date.dayOfMonth,
                date.dayOfWeek.value,
                date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH),
                date.dayOfWeek.value >= 6
            )
        )
        dates[date] = dateKey
        return dateKey
    }

    override fun insertMatch(match: MatchRecord): WarehouseMatch {
        matches[match.fileName]?.let { return WarehouseMatch(it) }
        val key = nextMatchKey++
        writeRow(
            "dim_match",
            MATCH_HEADERS,
            listOf(
                key,
                nextMatchSourceId++,
                null,
                match.fileName,
                match.matchInSeries,
                match.matchType,
                match.eventName,
                match.matchDateText,
                match.season,
                match.matchStartYear,
                match.matchStartDateKey,
                match.ballsPerOver,
                LocalDateTime.now(java.time.ZoneOffset.UTC),
                match.team1Key,
                match.team2Key,
                match.groundKey,
                match.tossTeamKey,
                match.tossDecision,
                match.victoryType,
                match.winnerTeamKey,
                match.loserTeamKey
            )
        )
        matches[match.fileName] = key
        return WarehouseMatch(key)
    }

    override fun insertMatchFact(matchKey: Long, matchDateKey: Int?, groundKey: Long, durationDays: Int, margin: Int) {
        writeRow("fact_match", MATCH_FACT_HEADERS, listOf(matchKey, matchDateKey, groundKey, durationDays, margin, 1))
    }

    override fun upsertInnings(
        matchKey: Long,
        inningsNumber: Int,
        battingTeamKey: Long,
        bowlingTeamKey: Long
    ): WarehouseInnings {
        val lookup = matchKey to inningsNumber
        innings[lookup]?.let { return it }
        val result = WarehouseInnings(nextInningsKey++)
        writeRow(
            "dim_innings",
            INNINGS_HEADERS,
            listOf(result.key, matchKey, inningsNumber, battingTeamKey, bowlingTeamKey)
        )
        innings[lookup] = result
        return result
    }

    override fun findDeliveryKey(matchKey: Long, inningsKey: Long, inningsOrder: Int): Long? =
        deliveries[Triple(matchKey, inningsKey, inningsOrder)]

    override fun insertDelivery(delivery: DeliveryRecord): Long {
        val key = nextDeliveryKey++
        writeRow(
            "fact_delivery",
            DELIVERY_HEADERS,
            listOf(
                key,
                nextBallSourceId++,
                delivery.matchKey,
                delivery.matchDateKey,
                delivery.inningsKey,
                delivery.battingTeamKey,
                delivery.bowlingTeamKey,
                delivery.batterKey,
                delivery.nonStrikerKey,
                delivery.bowlerKey,
                delivery.overNumber,
                delivery.ballNumber,
                delivery.ballInOver,
                delivery.inningsOrder,
                delivery.delivery.runs.batter,
                delivery.delivery.runs.extras,
                delivery.delivery.runs.total,
                delivery.delivery.extras?.noballs ?: 0,
                delivery.delivery.extras?.wides ?: 0,
                delivery.delivery.extras?.byes ?: 0,
                delivery.delivery.extras?.legbyes ?: 0,
                delivery.delivery.runs.nonBoundary?.let { if (it) 1 else 0 },
                delivery.powerplay,
                delivery.delivery.wickets.orEmpty().size
            )
        )
        deliveries[Triple(delivery.matchKey, delivery.inningsKey, delivery.inningsOrder)] = key
        return key
    }

    override fun insertWicket(kind: String): Long {
        val key = nextWicketKey++
        writeRow("dim_wicket", WICKET_HEADERS, listOf(key, nextWicketSourceId++, kind))
        return key
    }

    override fun insertDeliveryWicket(deliveryKey: Long, wicketKey: Long) {
        writeRow("bridge_delivery_wicket", DELIVERY_WICKET_HEADERS, listOf(deliveryKey, wicketKey))
    }

    override fun insertMatchPerson(matchKey: Long, personKey: Long, roleCode: String) {
        if (matchPeople.add(Triple(matchKey, personKey, roleCode))) {
            writeRow("bridge_match_person", MATCH_PERSON_HEADERS, listOf(null, matchKey, personKey, roleCode))
        }
    }

    override fun writeAllPeople(people: Sequence<PersonRegistryEntity>) {
        people.forEach { person -> upsertPerson(person.id, person.name, person.caId) }
    }

    override fun commit() {
        writers.values.forEach(BufferedWriter::flush)
    }

    override fun close() {
        if (closed) return
        closed = true
        commit()
        writers.values.forEach(BufferedWriter::close)
        writers.clear()
    }

    private fun writeRow(tableName: String, headers: List<String>, values: List<Any?>) {
        check(!closed) { "Cannot write to a closed CSV output adapter" }
        val writer = writers.getOrPut(tableName) {
            val file = path.resolve("$tableName.csv")
            val newFile = !Files.exists(file) || Files.size(file) == 0L
            Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            ).also { if (newFile) it.appendLine(headers.joinToString(",")) }
        }
        writer.appendLine(values.joinToString(",", transform = ::toCsvValue))
    }

    private fun toCsvValue(value: Any?): String = when (value) {
        null -> "\\N"
        is Boolean -> if (value) "1" else "0"
        is LocalDateTime -> value.format(DATETIME_FORMATTER)
        is String -> escapeCsv(value)
        else -> value.toString()
    }

    private fun escapeCsv(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private companion object {
        val DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        fun prepareOutput(output: Path): Path {
            require(!Files.exists(output) || Files.isDirectory(output)) {
                "CSV output path must be a directory: $output"
            }
            if (Files.exists(output)) output.toFile().deleteRecursively()
            Files.createDirectories(output)
            return output
        }

        val DATE_HEADERS = listOf(
            "date_key", "calendar_date", "calendar_year", "calendar_quarter", "calendar_month",
            "month_name", "week_of_year", "day_of_month", "day_of_week", "day_name", "is_weekend"
        )
        val TEAM_HEADERS = listOf("team_key", "source_team_id", "team_name")
        val PERSON_HEADERS = listOf("person_key", "source_person_id", "full_name", "sort_name_part", "other_name_part", "ca_id")
        val GROUND_HEADERS = listOf("ground_key", "source_ground_id", "ground_name")
        val MATCH_HEADERS = listOf(
            "match_key", "source_match_id", "source_ca_id", "file_name", "match_in_series", "match_type", "event_name",
            "match_date_text", "season", "match_start_year", "match_start_date_key", "balls_per_over", "added_timestamp",
            "team1_key", "team2_key", "ground_key", "toss_team_key", "toss_decision", "victory_type", "winner_team_key", "loser_team_key"
        )
        val INNINGS_HEADERS = listOf("innings_key", "match_key", "innings_number", "batting_team_key", "bowling_team_key")
        val WICKET_HEADERS = listOf("wicket_key", "source_wicket_id", "wicket_kind")
        val MATCH_FACT_HEADERS = listOf("match_key", "match_date_key", "ground_key", "duration_days", "margin", "match_count")
        val DELIVERY_HEADERS = listOf(
            "delivery_key", "source_ball_id", "match_key", "match_date_key", "innings_key", "batting_team_key", "bowling_team_key",
            "batter_key", "non_striker_key", "bowler_key", "over_number", "ball_number", "ball_in_over", "innings_order",
            "batter_runs", "extra_runs", "total_runs", "no_balls", "wides", "byes", "leg_byes", "non_boundary", "powerplay", "wicket_count"
        )
        val MATCH_PERSON_HEADERS = listOf("match_person_key", "match_key", "person_key", "role_code")
        val DELIVERY_WICKET_HEADERS = listOf("delivery_key", "wicket_key")
    }
}