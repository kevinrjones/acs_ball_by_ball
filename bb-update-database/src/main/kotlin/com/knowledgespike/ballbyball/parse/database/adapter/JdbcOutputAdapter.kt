package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.cricketarchive.InvalidStateException
import com.knowledgespike.ballbyball.parse.database.Location
import com.knowledgespike.ballbyball.parse.database.PersonRegistryEntity
import com.knowledgespike.ballbyball.parse.database.Team
import com.knowledgespike.ballbyball.parse.database.WarehouseInnings
import com.knowledgespike.ballbyball.parse.database.WarehouseMatch
import com.knowledgespike.ballbyball.parse.database.getNameParts
import java.sql.Connection
import java.sql.Statement
import java.sql.Types
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import java.util.Locale

/** Writes warehouse rows directly to the configured database. */
abstract class JdbcOutputAdapter(protected val connection: Connection) : OutputAdapter {
    override fun findMatchKey(fileName: String): Long? = queryKey(
        "select match_key from dim_match where file_name = ?",
        fileName
    )

    override fun upsertPerson(sourceId: String, fullName: String, caId: Int): Long {
        queryKey("select person_key from dim_person where source_person_id = ?", sourceId)?.let { return it }
        val (sortNamePart, otherNamePart) = getNameParts(fullName)
        return insertWithKey(
            sql = "insert into dim_person (source_person_id, full_name, sort_name_part, other_name_part, ca_id) values (?, ?, ?, ?, ?)",
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
        val key = insertWithKey("insert into dim_team (source_team_id, team_name) values (?, ?)", sourceId, name)
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
        val key = insertWithKey("insert into dim_ground (source_ground_id, ground_name) values (?, ?)", sourceId, name)
        return Location(key, name)
    }

    override fun upsertDate(date: LocalDate): Int {
        connection.prepareStatement("select date_key from dim_date where calendar_date = ?").use { statement ->
            statement.setDate(1, java.sql.Date.valueOf(date))
            statement.executeQuery().use { results ->
                if (results.next()) return results.getInt(1)
            }
        }

        val dateKey = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
        val weekFields = WeekFields.ISO
        connection.prepareStatement(
            "insert into dim_date (date_key, calendar_date, calendar_year, calendar_quarter, calendar_month, " +
                    "month_name, week_of_year, day_of_month, day_of_week, day_name, is_weekend) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        ).use { statement ->
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

    override fun insertMatch(match: MatchRecord): WarehouseMatch {
        val sourceId = nextSourceId("source_match_id", "dim_match")
        val key = insertWithKey(
            sql = "insert into dim_match (source_match_id, source_ca_id, file_name, match_in_series, match_type, event_name, " +
                    "match_date_text, season, match_start_year, match_start_date_key, balls_per_over, added_timestamp, " +
                    "team1_key, team2_key, ground_key, toss_team_key, toss_decision, victory_type, winner_team_key, loser_team_key) " +
                    "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            sourceId,
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
            java.sql.Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)),
            match.team1Key,
            match.team2Key,
            match.groundKey,
            match.tossTeamKey,
            match.tossDecision,
            match.victoryType,
            match.winnerTeamKey,
            match.loserTeamKey
        )
        return WarehouseMatch(key)
    }

    override fun insertMatchFact(
        matchKey: Long,
        matchDateKey: Int?,
        groundKey: Long,
        durationDays: Int,
        margin: Int
    ) {
        execute(
            "insert into fact_match (match_key, match_date_key, ground_key, duration_days, margin, match_count) values (?, ?, ?, ?, ?, 1)",
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
            "insert into dim_innings (match_key, innings_number, batting_team_key, bowling_team_key) values (?, ?, ?, ?)",
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
            "insert into fact_delivery (source_ball_id, match_key, match_date_key, innings_key, batting_team_key, " +
                    "bowling_team_key, batter_key, non_striker_key, bowler_key, over_number, ball_number, ball_in_over, " +
                    "innings_order, batter_runs, extra_runs, total_runs, no_balls, wides, byes, leg_byes, non_boundary, " +
                    "powerplay, wicket_count) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            sourceId,
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
    }

    override fun insertWicket(kind: String): Long {
        val sourceId = nextSourceId("source_wicket_id", "dim_wicket")
        return insertWithKey("insert into dim_wicket (source_wicket_id, wicket_kind) values (?, ?)", sourceId, kind)
    }

    override fun insertDeliveryWicket(deliveryKey: Long, wicketKey: Long) {
        execute("insert into bridge_delivery_wicket (delivery_key, wicket_key) values (?, ?)", deliveryKey, wicketKey)
    }

    override fun insertDeliveryFielder(deliveryKey: Long, wicketKey: Long, personKey: Long) {
        execute(
            "insert into bridge_delivery_fielder (delivery_key, wicket_key, person_key) values (?, ?, ?)",
            deliveryKey,
            wicketKey,
            personKey
        )
    }

    override fun insertMatchPerson(matchKey: Long, personKey: Long, roleCode: String) {
        executeAllowingNoOp(
            "insert into bridge_match_person (match_key, person_key, role_code) values (?, ?, ?) " +
                    duplicateMatchPersonClause(),
            matchKey,
            personKey,
            roleCode
        )
    }

    protected abstract fun duplicateMatchPersonClause(): String

    override fun writeAllPeople(people: Sequence<PersonRegistryEntity>) = people.forEach { person ->
        upsertPerson(person.id, person.name, person.caId)
    }

    override fun commit() = connection.commit()

    override fun close() = Unit

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

    private fun executeAllowingNoOp(sql: String, vararg values: Any?) {
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> setValue(statement, index + 1, value) }
            check(statement.executeUpdate() >= 0)
        }
    }

    private fun setValue(statement: java.sql.PreparedStatement, index: Int, value: Any?) {
        when (value) {
            null -> statement.setNull(index, Types.NULL)
            is Int -> statement.setInt(index, value)
            is Long -> statement.setLong(index, value)
            is Boolean -> statement.setBoolean(index, value)
            is String -> statement.setString(index, value)
            is java.sql.Timestamp -> statement.setTimestamp(index, value)
            else -> statement.setObject(index, value)
        }
    }
}