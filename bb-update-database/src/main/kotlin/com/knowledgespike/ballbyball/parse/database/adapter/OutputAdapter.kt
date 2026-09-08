package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.parse.database.PersonRegistryEntity
import com.knowledgespike.ballbyball.parse.database.Team
import com.knowledgespike.ballbyball.parse.database.Location
import com.knowledgespike.ballbyball.parse.database.WarehouseInnings
import com.knowledgespike.ballbyball.parse.database.WarehouseMatch
import com.knowledgespike.ballbyball.parse.parser.structure.Delivery

/**
 * Persists warehouse rows without coupling the parser to a particular output.
 */
interface OutputAdapter : AutoCloseable {
    fun findMatchKey(fileName: String): Long?

    fun upsertPerson(sourceId: String, fullName: String, caId: Int): Long

    fun upsertTeam(name: String): Team

    fun upsertGround(name: String): Location

    fun upsertDate(date: java.time.LocalDate): Int

    fun insertMatch(match: MatchRecord): WarehouseMatch

    fun insertMatchFact(
        matchKey: Long,
        fileName: String,
        matchDateKey: Int?,
        groundKey: Long,
        durationDays: Int,
        margin: Int
    )

    fun upsertInnings(matchKey: Long, inningsNumber: Int, battingTeamKey: Long, bowlingTeamKey: Long): WarehouseInnings

    fun findDeliveryKey(matchKey: Long, inningsKey: Long, inningsOrder: Int): Long?

    fun insertDelivery(delivery: DeliveryRecord): Long

    fun insertWicket(kind: String): Long

    fun insertDeliveryWicket(deliveryKey: Long, wicketKey: Long)

    fun insertDeliveryFielder(deliveryKey: Long, wicketKey: Long, personKey: Long)

    fun insertMatchPerson(matchKey: Long, personKey: Long, roleCode: String)

    fun writeAllPeople(people: Sequence<PersonRegistryEntity>)

    fun commit()

    override fun close()
}

data class MatchRecord(
    val fileName: String,
    val matchInSeries: Int,
    val matchType: String,
    val eventName: String,
    val matchDateText: String,
    val season: String,
    val matchStartYear: String,
    val matchStartDateKey: Int?,
    val ballsPerOver: Int,
    val team1Key: Long,
    val team2Key: Long,
    val groundKey: Long,
    val tossTeamKey: Long,
    val tossDecision: String?,
    val victoryType: String,
    val winnerTeamKey: Long?,
    val loserTeamKey: Long?
)

data class DeliveryRecord(
    val matchKey: Long,
    val matchDateKey: Int?,
    val inningsKey: Long,
    val battingTeamKey: Long,
    val bowlingTeamKey: Long,
    val batterKey: Long,
    val nonStrikerKey: Long,
    val bowlerKey: Long,
    val overNumber: Int,
    val ballNumber: Int,
    val ballInOver: Int,
    val inningsOrder: Int,
    val delivery: Delivery,
    val powerplay: Int
)