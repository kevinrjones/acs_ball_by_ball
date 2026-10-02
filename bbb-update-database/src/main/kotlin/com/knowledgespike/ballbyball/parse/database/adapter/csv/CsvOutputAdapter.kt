package com.knowledgespike.ballbyball.parse.database.adapter.csv

import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.clishared.identity.SourceReference
import com.knowledgespike.ballbyball.types.values.PublicMatchId

import com.knowledgespike.cricketarchive.LoggerDelegate
import com.knowledgespike.ballbyball.parse.database.Location
import com.knowledgespike.ballbyball.parse.database.PersonRegistryEntity
import com.knowledgespike.ballbyball.parse.database.Team
import com.knowledgespike.ballbyball.parse.database.WarehouseInnings
import com.knowledgespike.ballbyball.parse.database.WarehouseMatch
import com.knowledgespike.ballbyball.parse.database.adapter.OutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.DeliveryRecord
import com.knowledgespike.ballbyball.parse.database.adapter.MatchWriteDecision
import com.knowledgespike.ballbyball.parse.database.adapter.MatchRecord
import com.knowledgespike.ballbyball.parse.database.adapter.WarehouseWriteSupport
import com.knowledgespike.ballbyball.parse.database.getNameParts
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Writes warehouse tables as UTF-8 CSV files suitable for bulk loading into MariaDB. */
class CsvOutputAdapter(output: Path) : OutputAdapter {
    private val log by LoggerDelegate()
    private val writers = mutableMapOf<String, BufferedWriter>()
    private val people = mutableMapOf<String, Long>()
    private val teams = mutableMapOf<String, Team>()
    private val grounds = mutableMapOf<String, Location>()
    private val dates = mutableMapOf<LocalDate, Int>()
    private val matches = mutableMapOf<CanonicalMatchId, Long>()
    private val matchPublicIds = mutableMapOf<CanonicalMatchId, PublicMatchId>()
    private val publicMatches = mutableMapOf<PublicMatchId, CanonicalMatchId>()
    private val innings = mutableMapOf<Pair<Long, Int>, WarehouseInnings>()
    private val deliveries = mutableMapOf<Triple<Long, Long, Int>, Long>()
    private val matchPeople = mutableSetOf<Triple<Long, Long, String>>()
    private val deliveryFielders = mutableSetOf<Triple<Long, Long, Long>>()
    private var nextTeamSourceId = 1L
    private var nextGroundSourceId = 1L
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

    override fun findMatchKey(canonicalMatchId: CanonicalMatchId): Long? = matches[canonicalMatchId]

    override fun ensurePublicMatchId(canonicalMatchId: CanonicalMatchId, publicMatchId: PublicMatchId) {
        decideMatchWrite(canonicalMatchId, publicMatchId)
    }

    override fun upsertPerson(sourceId: String, fullName: String, caId: Int): Long {
        people[sourceId]?.let { return it }
        val key = nextPersonKey++
        val (sortNamePart, otherNamePart) = getNameParts(fullName)
        writeRow(
            "dim_person",
            WarehouseWriteSupport.PERSON_COLUMNS,
            listOf(key, sourceId, fullName, sortNamePart, otherNamePart, caId)
        )
        people[sourceId] = key
        return key
    }

    override fun upsertTeam(name: String): Team {
        teams[name]?.let { return it }
        val team = Team(nextTeamKey++, name)
        writeRow("dim_team", WarehouseWriteSupport.TEAM_COLUMNS, listOf(team.id, nextTeamSourceId++, team.name))
        teams[name] = team
        return team
    }

    override fun upsertGround(name: String): Location {
        grounds[name]?.let { return it }
        val ground = Location(nextGroundKey++, name)
        writeRow("dim_ground", WarehouseWriteSupport.GROUND_COLUMNS, listOf(ground.id, nextGroundSourceId++, ground.name))
        grounds[name] = ground
        return ground
    }

    override fun upsertDate(date: LocalDate): Int {
        dates[date]?.let { return it }
        val dimensions = WarehouseWriteSupport.dateDimensions(date)
        writeRow(
            "dim_date",
            WarehouseWriteSupport.DATE_COLUMNS,
            dimensions.values()
        )
        dates[date] = dimensions.dateKey
        return dimensions.dateKey
    }

    override fun insertMatch(match: MatchRecord): WarehouseMatch {
        val key = when (val decision = decideMatchWrite(match.canonicalMatchId, match.publicMatchId)) {
            is MatchWriteDecision.Reuse -> return WarehouseMatch(decision.matchKey, match.publicMatchId)
            MatchWriteDecision.Insert -> nextMatchKey++
        }
        writeRow(
            "dim_match",
            WarehouseWriteSupport.MATCH_COLUMNS,
            WarehouseWriteSupport.matchValues(
                key,
                match,
                LocalDateTime.now(java.time.ZoneOffset.UTC)
            )
        )
        matches[match.canonicalMatchId] = key
        matchPublicIds[match.canonicalMatchId] = match.publicMatchId
        publicMatches[match.publicMatchId] = match.canonicalMatchId
        return WarehouseMatch(key, match.publicMatchId)
    }

    override fun insertSourceReferences(matchKey: Long, sources: List<SourceReference>) {
        sources.forEach { source ->
            writeRow(
                "match_source_reference",
                WarehouseWriteSupport.SOURCE_REFERENCE_COLUMNS,
                WarehouseWriteSupport.sourceReferenceValues(matchKey, source)
            )
        }
    }

    override fun insertMatchFact(
        matchKey: Long,
        matchDateKey: Int?,
        groundKey: Long,
        durationDays: Int,
        margin: Int
    ) {
        writeRow(
            "fact_match",
            WarehouseWriteSupport.MATCH_FACT_COLUMNS,
            listOf(matchKey, matchDateKey, groundKey, durationDays, margin, 1)
        )
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
            WarehouseWriteSupport.INNINGS_COLUMNS,
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
            WarehouseWriteSupport.DELIVERY_COLUMNS,
            WarehouseWriteSupport.deliveryValues(key, nextBallSourceId++, delivery)
        )
        deliveries[Triple(delivery.matchKey, delivery.inningsKey, delivery.inningsOrder)] = key
        return key
    }

    override fun insertWicket(kind: String): Long {
        val key = nextWicketKey++
        writeRow("dim_wicket", WarehouseWriteSupport.WICKET_COLUMNS, listOf(key, nextWicketSourceId++, kind))
        return key
    }

    override fun insertDeliveryWicket(deliveryKey: Long, wicketKey: Long) {
        writeRow(
            "bridge_delivery_wicket",
            WarehouseWriteSupport.DELIVERY_WICKET_COLUMNS,
            listOf(deliveryKey, wicketKey)
        )
    }

    override fun insertDeliveryFielder(deliveryKey: Long, wicketKey: Long, personKey: Long) {
        val bridgeKey = Triple(deliveryKey, wicketKey, personKey)
        if (!deliveryFielders.add(bridgeKey)) {
            log.warn(
                "Duplicate bridge_delivery_fielder suppressed from CSV output: deliveryKey={}, wicketKey={}, personKey={}",
                deliveryKey,
                wicketKey,
                personKey
            )
            return
        }
        writeRow(
            "bridge_delivery_fielder",
            WarehouseWriteSupport.DELIVERY_FIELDER_COLUMNS,
            listOf(deliveryKey, wicketKey, personKey)
        )
    }

    override fun insertMatchPerson(matchKey: Long, personKey: Long, roleCode: String) {
        if (matchPeople.add(Triple(matchKey, personKey, roleCode))) {
            writeRow("bridge_match_person", WarehouseWriteSupport.MATCH_PERSON_COLUMNS, listOf(null, matchKey, personKey, roleCode))
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

    private fun decideMatchWrite(canonicalMatchId: CanonicalMatchId, publicMatchId: PublicMatchId) =
        WarehouseWriteSupport.matchWriteDecision(
            canonicalMatchId = canonicalMatchId,
            publicMatchId = publicMatchId,
            existingMatchKey = matches[canonicalMatchId],
            existingPublicMatchId = matchPublicIds[canonicalMatchId]?.value,
            publicIdOwnedByAnotherMatch = publicMatches[publicMatchId]?.let { it != canonicalMatchId } ?: false
        )

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

    }
}