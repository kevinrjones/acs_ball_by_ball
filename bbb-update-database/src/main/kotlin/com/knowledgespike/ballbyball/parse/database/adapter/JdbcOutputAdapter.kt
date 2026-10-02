package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.clishared.identity.SourceReference
import com.knowledgespike.ballbyball.types.values.PublicMatchId

import com.knowledgespike.cricketarchive.InvalidStateException
import com.knowledgespike.cricketarchive.LoggerDelegate
import com.knowledgespike.ballbyball.parse.database.Location
import com.knowledgespike.ballbyball.parse.database.PersonRegistryEntity
import com.knowledgespike.ballbyball.parse.database.Team
import com.knowledgespike.ballbyball.parse.database.WarehouseInnings
import com.knowledgespike.ballbyball.parse.database.WarehouseMatch
import com.knowledgespike.ballbyball.parse.database.getNameParts
import java.sql.Connection
import java.sql.Statement
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Writes warehouse rows directly to the configured database. */
abstract class JdbcOutputAdapter(protected val connection: Connection) : OutputAdapter {
    private val log by LoggerDelegate()
    private var autoCommitBeforeMatch: Boolean? = null
    override fun findMatchKey(canonicalMatchId: CanonicalMatchId): Long? = queryKey(
        "select match_key from dim_match where canonical_match_id = ?",
        canonicalMatchId.value.toString()
    )

    override fun ensurePublicMatchId(canonicalMatchId: CanonicalMatchId, publicMatchId: PublicMatchId) {
        when (matchWriteDecision(canonicalMatchId, publicMatchId)) {
            is MatchWriteDecision.Reuse -> backfillPublicMatchId(canonicalMatchId, publicMatchId)
            MatchWriteDecision.Insert -> Unit
        }
    }

    override fun upsertPerson(sourceId: String, fullName: String, caId: Int): Long {
        queryKey("select person_key from dim_person where source_person_id = ?", sourceId)?.let { return it }
        val (sortNamePart, otherNamePart) = getNameParts(fullName)
        return insertWithKey(
            sql = WarehouseWriteSupport.insertSql("dim_person", WarehouseWriteSupport.PERSON_JDBC_COLUMNS, "insert into"),
            sourceId,
            fullName,
            sortNamePart,
            otherNamePart,
            caId
        )
    }

    override fun upsertTeam(name: String): Team {
        findTeam(name)?.let { return it }
        val sourceId = nextSourceId("source_team_id", "dim_team")
        val key = insertWithKey(
            WarehouseWriteSupport.insertSql("dim_team", WarehouseWriteSupport.TEAM_JDBC_COLUMNS, "insert into"),
            sourceId,
            name
        )
        return Team(key, name)
    }

    override fun upsertGround(name: String): Location {
        connection.prepareStatement("select ground_key from dim_ground where ground_name = ?").use { statement ->
            statement.setString(1, name)
            statement.executeQuery().use { results ->
                if (results.next()) return Location(results.getLong(1), name)
            }
        }
        val sourceId = nextSourceId("source_ground_id", "dim_ground")
        val key = insertWithKey(
            WarehouseWriteSupport.insertSql("dim_ground", WarehouseWriteSupport.GROUND_JDBC_COLUMNS, "insert into"),
            sourceId,
            name
        )
        return Location(key, name)
    }

    override fun upsertDate(date: LocalDate): Int {
        connection.prepareStatement("select date_key from dim_date where calendar_date = ?").use { statement ->
            statement.setDate(1, java.sql.Date.valueOf(date))
            statement.executeQuery().use { results ->
                if (results.next()) return results.getInt(1)
            }
        }

        val dimensions = WarehouseWriteSupport.dateDimensions(date)
        connection.prepareStatement(
            WarehouseWriteSupport.insertSql("dim_date", WarehouseWriteSupport.DATE_COLUMNS, "insert into")
        ).use { statement ->
            dimensions.values(java.sql.Date.valueOf(date)).forEachIndexed { index, value ->
                setValue(statement, index + 1, value)
            }
            check(statement.executeUpdate() == 1)
        }
        return dimensions.dateKey
    }

    override fun insertMatch(match: MatchRecord): WarehouseMatch {
        return when (val decision = matchWriteDecision(match.canonicalMatchId, match.publicMatchId)) {
            is MatchWriteDecision.Reuse -> {
                backfillPublicMatchId(match.canonicalMatchId, match.publicMatchId)
                WarehouseMatch(decision.matchKey, match.publicMatchId)
            }

            MatchWriteDecision.Insert -> {
                val key = insertWithKey(
                    WarehouseWriteSupport.insertSql(
                        "dim_match",
                        WarehouseWriteSupport.MATCH_JDBC_COLUMNS,
                        "insert into"
                    ),
                    *WarehouseWriteSupport.matchInsertValues(
                        match,
                        java.sql.Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC))
                    ).toTypedArray()
                )
                WarehouseMatch(key, match.publicMatchId)
            }
        }
    }

    override fun insertSourceReferences(matchKey: Long, sources: List<SourceReference>) {
        sources.forEach { source ->
            execute(
                WarehouseWriteSupport.insertSql(
                    "match_source_reference",
                    WarehouseWriteSupport.SOURCE_REFERENCE_COLUMNS,
                    "insert into"
                ),
                *WarehouseWriteSupport.sourceReferenceValues(matchKey, source).toTypedArray()
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
        execute(
            WarehouseWriteSupport.insertSql(
                "fact_match",
                WarehouseWriteSupport.MATCH_FACT_COLUMNS,
                "insert into",
                listOf("?", "?", "?", "?", "?", "1")
            ),
            matchKey,
            matchDateKey,
            groundKey,
            durationDays,
            margin
        )
    }

    override fun upsertInnings(
        matchKey: Long,
        inningsNumber: Int,
        battingTeamKey: Long,
        bowlingTeamKey: Long
    ): WarehouseInnings {
        connection.prepareStatement("select innings_key from dim_innings where match_key = ? and innings_number = ?").use { statement ->
            statement.setLong(1, matchKey)
            statement.setInt(2, inningsNumber)
            statement.executeQuery().use { results ->
                if (results.next()) return WarehouseInnings(results.getLong(1))
            }
        }
        val key = insertWithKey(
            WarehouseWriteSupport.insertSql(
                "dim_innings",
                WarehouseWriteSupport.INNINGS_JDBC_COLUMNS,
                "insert into"
            ),
            matchKey,
            inningsNumber,
            battingTeamKey,
            bowlingTeamKey
        )
        return WarehouseInnings(key)
    }

    override fun findDeliveryKey(matchKey: Long, inningsKey: Long, inningsOrder: Int): Long? = queryKey(
        "select delivery_key from fact_delivery where match_key = ? and innings_key = ? and innings_order = ?",
        matchKey,
        inningsKey,
        inningsOrder
    )

    override fun insertDelivery(delivery: DeliveryRecord): Long {
        val sourceId = nextSourceId("source_ball_id", "fact_delivery")
        return insertWithKey(
            WarehouseWriteSupport.insertSql(
                "fact_delivery",
                WarehouseWriteSupport.DELIVERY_JDBC_COLUMNS,
                "insert into"
            ),
            *WarehouseWriteSupport.deliveryInsertValues(sourceId, delivery).toTypedArray()
        )
    }

    override fun insertWicket(kind: String): Long {
        val sourceId = nextSourceId("source_wicket_id", "dim_wicket")
        return insertWithKey(
            WarehouseWriteSupport.insertSql("dim_wicket", WarehouseWriteSupport.WICKET_JDBC_COLUMNS, "insert into"),
            sourceId,
            kind
        )
    }

    override fun insertDeliveryWicket(deliveryKey: Long, wicketKey: Long) {
        execute(
            WarehouseWriteSupport.insertSql(
                "bridge_delivery_wicket",
                WarehouseWriteSupport.DELIVERY_WICKET_COLUMNS,
                "insert into"
            ),
            deliveryKey,
            wicketKey
        )
    }

    override fun insertDeliveryFielder(deliveryKey: Long, wicketKey: Long, personKey: Long) {
        val affectedRows = executeAllowingNoOp(
            WarehouseWriteSupport.insertSql(
                "bridge_delivery_fielder",
                WarehouseWriteSupport.DELIVERY_FIELDER_COLUMNS,
                "insert into"
            ) + " " + duplicateDeliveryFielderClause(),
            deliveryKey,
            wicketKey,
            personKey
        )
        if (affectedRows == 0) {
            log.warn(
                "Duplicate bridge_delivery_fielder suppressed: deliveryKey={}, wicketKey={}, personKey={}",
                deliveryKey,
                wicketKey,
                personKey
            )
        }
    }

    override fun insertMatchPerson(matchKey: Long, personKey: Long, roleCode: String) {
        executeAllowingNoOp(
            WarehouseWriteSupport.insertSql(
                "bridge_match_person",
                WarehouseWriteSupport.MATCH_PERSON_INSERT_COLUMNS,
                "insert into"
            ) + " " + duplicateMatchPersonClause(),
            matchKey,
            personKey,
            roleCode
        )
    }

    protected abstract fun duplicateMatchPersonClause(): String

    protected abstract fun duplicateDeliveryFielderClause(): String

    override fun writeAllPeople(people: Sequence<PersonRegistryEntity>) = people.forEach { person ->
        upsertPerson(person.id, person.name, person.caId)
    }

    override fun beginMatch() {
        check(autoCommitBeforeMatch == null) { "A match transaction is already active" }
        val previousAutoCommit = connection.autoCommit
        if (previousAutoCommit) connection.autoCommit = false
        autoCommitBeforeMatch = previousAutoCommit
    }

    override fun commit() {
        connection.commit()
        restoreAutoCommit()
    }

    override fun rollback() {
        try {
            connection.rollback()
        } finally {
            restoreAutoCommit()
        }
    }

    override fun close() = Unit

    private fun matchWriteDecision(
        canonicalMatchId: CanonicalMatchId,
        publicMatchId: PublicMatchId
    ): MatchWriteDecision {
        val existingMatchKey = findMatchKey(canonicalMatchId)
        val existingPublicMatchId = existingMatchKey?.let {
            queryNullableKey(
                "select public_match_id from dim_match where canonical_match_id = ?",
                canonicalMatchId.value.toString()
            )
        }
        val publicMatchOwner = queryNullableKey(
            "select match_key from dim_match where public_match_id = ?",
            publicMatchId.value
        )
        return WarehouseWriteSupport.matchWriteDecision(
            canonicalMatchId = canonicalMatchId,
            publicMatchId = publicMatchId,
            existingMatchKey = existingMatchKey,
            existingPublicMatchId = existingPublicMatchId,
            publicIdOwnedByAnotherMatch = publicMatchOwner != null && publicMatchOwner != existingMatchKey
        )
    }

    private fun backfillPublicMatchId(canonicalMatchId: CanonicalMatchId, publicMatchId: PublicMatchId) {
        executeAllowingNoOp(
            "update dim_match set public_match_id = ? " +
                "where canonical_match_id = ? and public_match_id is null",
            publicMatchId.value,
            canonicalMatchId.value.toString()
        )
    }

    private fun findTeam(name: String): Team? {
        connection.prepareStatement("select team_key from dim_team where team_name = ?").use { statement ->
            statement.setString(1, name)
            statement.executeQuery().use { results ->
                if (results.next()) return Team(results.getLong(1), name)
            }
        }
        return null
    }

    private fun queryKey(sql: String, vararg values: Any?): Long? {
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> setValue(statement, index + 1, value) }
            statement.executeQuery().use { results ->
                if (results.next()) return results.getLong(1)
            }
        }
        return null
    }

    private fun queryNullableKey(sql: String, vararg values: Any?): Long? {
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> setValue(statement, index + 1, value) }
            statement.executeQuery().use { results ->
                if (results.next()) {
                    val value = results.getLong(1)
                    return if (results.wasNull()) null else value
                }
            }
        }
        return null
    }

    private fun nextSourceId(column: String, table: String): Long = queryKey(
        "select coalesce(max($column), 0) + 1 from $table"
    ) ?: throw InvalidStateException("Expected next source identifier")

    private fun insertWithKey(sql: String, vararg values: Any?): Long {
        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { statement ->
            values.forEachIndexed { index, value -> setValue(statement, index + 1, value) }
            check(statement.executeUpdate() == 1)
            statement.generatedKeys.use { keys ->
                if (!keys.next()) throw InvalidStateException("Expected generated key")
                return keys.getLong(1)
            }
        }
    }

    private fun execute(sql: String, vararg values: Any?) {
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> setValue(statement, index + 1, value) }
            check(statement.executeUpdate() == 1)
        }
    }

    private fun executeAllowingNoOp(sql: String, vararg values: Any?): Int {
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> setValue(statement, index + 1, value) }
            return statement.executeUpdate().also { check(it >= 0) }
        }
    }

    private fun restoreAutoCommit() {
        val previousAutoCommit = autoCommitBeforeMatch ?: return
        try {
            connection.autoCommit = previousAutoCommit
        } finally {
            autoCommitBeforeMatch = null
        }
    }

    private fun setValue(statement: java.sql.PreparedStatement, index: Int, value: Any?) {
        when (value) {
            null -> statement.setNull(index, Types.NULL)
            is Int -> statement.setInt(index, value)
            is Long -> statement.setLong(index, value)
            is Boolean -> statement.setBoolean(index, value)
            is String -> statement.setString(index, value)
            is java.sql.Date -> statement.setDate(index, value)
            is java.sql.Timestamp -> statement.setTimestamp(index, value)
            else -> statement.setObject(index, value)
        }
    }
}