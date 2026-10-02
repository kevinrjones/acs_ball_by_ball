package com.knowledgespike.ballbyball.api.feature.matches.data.repository

import com.knowledgespike.ballbyball.contracts.ScoresheetCompleteness
import org.jooq.Record
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Presentation mapping for match read models. Keeps score/result/date formatting
 * out of jOOQ query construction.
 */
object MatchResponseMapper {
    private val MATCH_DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun calculateTeamScore(
        inningsList: List<Record>,
        ballsPerOver: Int
    ): Pair<String?, String?> {
        if (inningsList.isEmpty()) {
            return Pair(null, null)
        }

        val formattedInnings = inningsList.map { inn ->
            val runs = inn.get("runs") as? Number ?: 0
            val wickets = inn.get("wickets") as? Number ?: 0
            val legalBalls = inn.get("legal_balls") as? Number ?: 0

            val scorePart = if (wickets.toInt() >= 10) {
                "${runs.toInt()}ao"
            } else {
                "${runs.toInt()}-${wickets.toInt()}"
            }

            val completedOvers = legalBalls.toInt() / ballsPerOver
            val remainingBalls = legalBalls.toInt() % ballsPerOver
            val oversStr = if (remainingBalls == 0) "$completedOvers" else "$completedOvers.$remainingBalls"
            Pair(scorePart, "(${oversStr}ov)")
        }

        return if (formattedInnings.size == 1) {
            Pair(formattedInnings[0].first, formattedInnings[0].second)
        } else {
            Pair(formattedInnings.joinToString(" & ") { it.first }, null)
        }
    }

    fun formatResult(winnerName: String?, victoryType: String?, margin: Int?): String? {
        if (!winnerName.isNullOrBlank()) {
            val winner = winnerName
            val m = margin ?: 0
            return when (victoryType?.lowercase()) {
                "runs" -> "$winner won by $m ${if (m == 1) "run" else "runs"}"
                "wickets" -> "$winner won by $m ${if (m == 1) "wicket" else "wickets"}"
                "innings" -> "$winner won by an innings and $m ${if (m == 1) "run" else "runs"}"
                "tie" -> "Match tied"
                "draw" -> "Match drawn"
                "no result" -> "No result"
                else -> "$winner won"
            }
        }
        return when (victoryType?.lowercase()) {
            "tie" -> "Match tied"
            "draw" -> "Match drawn"
            "no result" -> "No result"
            else -> null
        }
    }

    fun mapFormat(rawMatchType: String): String = when (rawMatchType.lowercase()) {
        "t" -> "test"
        "tt" -> "t20"
        "itt" -> "t20i"
        "witt" -> "women's t20i"
        "wt" -> "women's test"
        "wtt" -> "women's t20"
        "a" -> "lista"
        "wa" -> "women's lista"
        "f" -> "fc"
        "odi" -> "odi"
        "wodi" -> "women's odi"
        "md" -> "multi-day"
        else -> rawMatchType
    }

    fun formatDateText(dateText: String?, calendarDate: LocalDate?): String? {
        if (calendarDate != null) {
            return calendarDate.format(MATCH_DATE_FORMATTER)
        }
        if (dateText.isNullOrBlank()) return null
        val parsed = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
        return if (parsed != null) {
            parsed.format(MATCH_DATE_FORMATTER)
        } else {
            dateText
        }
    }

    fun scoresheetCompleteness(
        inningsEmpty: Boolean,
        totalDeliveries: Int,
        missingInningsDeliveries: Boolean,
        teamsIncomplete: Boolean
    ): Pair<ScoresheetCompleteness, List<String>> {
        val missingData = buildList {
            if (inningsEmpty) add("innings")
            if (totalDeliveries == 0) add("deliveries")
            if (missingInningsDeliveries) add("deliveries for one or more innings")
            if (teamsIncomplete) add("team names")
        }.distinct()
        val completeness = when {
            inningsEmpty && totalDeliveries == 0 -> ScoresheetCompleteness.EMPTY
            missingData.isEmpty() -> ScoresheetCompleteness.COMPLETE
            else -> ScoresheetCompleteness.INCOMPLETE
        }
        return completeness to missingData
    }
}
