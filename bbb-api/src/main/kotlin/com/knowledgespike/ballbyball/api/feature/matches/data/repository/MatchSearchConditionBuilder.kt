package com.knowledgespike.ballbyball.api.feature.matches.data.repository

import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchSearchCriteria
import com.knowledgespike.ballbyball.types.values.MatchResultFilter
import com.knowledgespike.ballbyball.types.values.MatchTypeFilter
import com.knowledgespike.ballbyball.types.values.SearchTeam
import com.knowledgespike.ballbyball.types.values.VenueFilter
import org.jooq.Condition
import org.jooq.Field
import org.jooq.impl.DSL
import org.jooq.impl.DSL.trueCondition
import java.time.LocalDate

data class MatchSearchFields(
    val matchType: Field<String?>,
    val winnerTeamKey: Field<Long?>,
    val team1Key: Field<Long?>,
    val team2Key: Field<Long?>,
    val team1Name: Field<String?>,
    val team2Name: Field<String?>,
    val calendarDate: Field<LocalDate?>,
    val victoryType: Field<String?>
)

class MatchSearchConditionBuilder(private val fields: MatchSearchFields) {
    fun build(criteria: MatchSearchCriteria): Condition {
        val teamOnTeam1 = teamCondition(fields.team1Name, criteria.team, criteria.teamExactMatch.value)
        val teamOnTeam2 = teamCondition(fields.team2Name, criteria.team, criteria.teamExactMatch.value)
        val opponentOnTeam1 = teamCondition(fields.team1Name, criteria.opponents, criteria.opponentsExactMatch.value)
        val opponentOnTeam2 = teamCondition(fields.team2Name, criteria.opponents, criteria.opponentsExactMatch.value)
        val homeOrder = teamOnTeam1.and(opponentOnTeam2)
        val awayOrder = teamOnTeam2.and(opponentOnTeam1)

        return listOf(
            when (criteria.venue.value) {
                VenueFilter.HOME -> homeOrder
                VenueFilter.AWAY -> awayOrder
                VenueFilter.ALL -> homeOrder.or(awayOrder)
                else -> DSL.falseCondition()
            },
            matchTypeCondition(criteria),
            resultCondition(criteria, teamOnTeam1, teamOnTeam2),
            criteria.startDate?.let { fields.calendarDate.ge(it.localDate) } ?: trueCondition(),
            criteria.endDate?.let { fields.calendarDate.le(it.localDate) } ?: trueCondition()
        ).reduce { left, right -> left.and(right) }
    }

    private fun teamCondition(field: Field<String?>, team: SearchTeam, exact: Boolean): Condition =
        if (exact) field.equalIgnoreCase(team.value) else field.containsIgnoreCase(team.value)

    private fun matchTypeCondition(criteria: MatchSearchCriteria): Condition = when (criteria.matchType.value) {
        MatchTypeFilter.ALL -> trueCondition()
        MatchTypeFilter.ODI -> fields.matchType.`in`(MatchTypeFilter.ODI, MatchTypeFilter.ODI_STANDARD)
        MatchTypeFilter.WOMENS_ODI -> fields.matchType.`in`(MatchTypeFilter.WOMENS_ODI, MatchTypeFilter.ODI_WOMENS)
        else -> fields.matchType.equalIgnoreCase(criteria.matchType.value)
    }

    private fun resultCondition(
        criteria: MatchSearchCriteria,
        teamOnTeam1: Condition,
        teamOnTeam2: Condition
    ): Condition {
        val selectedTeamWon = (teamOnTeam1.and(fields.winnerTeamKey.eq(fields.team1Key)))
            .or(teamOnTeam2.and(fields.winnerTeamKey.eq(fields.team2Key)))
        val selectedTeamLost = (teamOnTeam1.and(fields.winnerTeamKey.eq(fields.team2Key)))
            .or(teamOnTeam2.and(fields.winnerTeamKey.eq(fields.team1Key)))
        return when (criteria.matchResult.value) {
            MatchResultFilter.ALL -> trueCondition()
            MatchResultFilter.WON -> selectedTeamWon
            MatchResultFilter.WON_BY_INNINGS -> selectedTeamWon.and(fields.victoryType.equalIgnoreCase("innings"))
            MatchResultFilter.WON_BY_RUNS -> selectedTeamWon.and(fields.victoryType.equalIgnoreCase("runs"))
            MatchResultFilter.WON_BY_WICKETS -> selectedTeamWon.and(fields.victoryType.equalIgnoreCase("wickets"))
            MatchResultFilter.LOST -> selectedTeamLost
            MatchResultFilter.LOST_BY_INNINGS -> selectedTeamLost.and(fields.victoryType.equalIgnoreCase("innings"))
            MatchResultFilter.LOST_BY_RUNS -> selectedTeamLost.and(fields.victoryType.equalIgnoreCase("runs"))
            MatchResultFilter.LOST_BY_WICKETS -> selectedTeamLost.and(fields.victoryType.equalIgnoreCase("wickets"))
            MatchResultFilter.DRAWN -> fields.victoryType.equalIgnoreCase("draw")
            MatchResultFilter.TIED -> fields.victoryType.equalIgnoreCase("tie")
            MatchResultFilter.NO_RESULT -> fields.victoryType.equalIgnoreCase("no result")
            else -> DSL.falseCondition()
        }
    }
}