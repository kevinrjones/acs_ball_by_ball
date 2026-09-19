package com.knowledgespike.ballbyball.api.feature.matches.data.repository

import com.knowledgespike.ballbyball.api.feature.matches.domain.repository.MatchRepository
import com.knowledgespike.ballbyball.contracts.MatchSummary
import com.knowledgespike.ballbyball.types.values.Limit
import com.knowledgespike.ballbyball.types.values.MatchKey
import com.knowledgespike.ballbyball.types.values.MatchType
import com.knowledgespike.ballbyball.types.values.Season
import com.knowledgespike.ballbyball.types.values.SourceMatchId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DSL.case_
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.sum
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.sql.DataSource

class JooqMatchRepository(
    dataSource: DataSource,
    dialect: SQLDialect,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MatchRepository {
    private val log = LoggerFactory.getLogger(JooqMatchRepository::class.java)
    private val dsl = DSL.using(dataSource, dialect)

    private val dimMatch = table(name("dim_match"))
    private val dimDate = table(name("dim_date"))
    private val dimTeam1 = table(name("dim_team")).`as`("t1")
    private val dimTeam2 = table(name("dim_team")).`as`("t2")
    private val dimTeamWinner = table(name("dim_team")).`as`("tw")
    private val factMatch = table(name("fact_match"))
    private val dimInnings = table(name("dim_innings"))
    private val factDelivery = table(name("fact_delivery"))

    private val mMatchKey = field(name("dim_match", "match_key"), Long::class.javaObjectType)
    private val mSourceMatchId = field(name("dim_match", "source_match_id"), Int::class.javaObjectType)
    private val mFileName = field(name("dim_match", "file_name"), String::class.java)
    private val mMatchType = field(name("dim_match", "match_type"), String::class.java)
    private val mEventName = field(name("dim_match", "event_name"), String::class.java)
    private val mMatchDateText = field(name("dim_match", "match_date_text"), String::class.java)
    private val mSeason = field(name("dim_match", "season"), String::class.java)
    private val mBallsPerOver = field(name("dim_match", "balls_per_over"), Int::class.javaObjectType)
    private val mTeam1Key = field(name("dim_match", "team1_key"), Long::class.javaObjectType)
    private val mTeam2Key = field(name("dim_match", "team2_key"), Long::class.javaObjectType)
    private val mWinnerTeamKey = field(name("dim_match", "winner_team_key"), Long::class.javaObjectType)
    private val mVictoryType = field(name("dim_match", "victory_type"), String::class.java)
    private val mMatchStartDateKey = field(name("dim_match", "match_start_date_key"), Int::class.javaObjectType)

    private val dDateKey = field(name("dim_date", "date_key"), Int::class.javaObjectType)
    private val dCalendarDate = field(name("dim_date", "calendar_date"), LocalDate::class.java)

    private val t1TeamKey = field(name("t1", "team_key"), Long::class.javaObjectType)
    private val t1TeamName = field(name("t1", "team_name"), String::class.java)

    private val t2TeamKey = field(name("t2", "team_key"), Long::class.javaObjectType)
    private val t2TeamName = field(name("t2", "team_name"), String::class.java)

    private val twTeamKey = field(name("tw", "team_key"), Long::class.javaObjectType)
    private val twTeamName = field(name("tw", "team_name"), String::class.java)

    private val fmMatchKey = field(name("fact_match", "match_key"), Long::class.javaObjectType)
    private val fmMargin = field(name("fact_match", "margin"), Int::class.javaObjectType)

    private val iInningsKey = field(name("dim_innings", "innings_key"), Long::class.javaObjectType)
    private val iInningsNumber = field(name("dim_innings", "innings_number"), Int::class.javaObjectType)

    private val fdMatchKey = field(name("fact_delivery", "match_key"), Long::class.javaObjectType)
    private val fdInningsKey = field(name("fact_delivery", "innings_key"), Long::class.javaObjectType)
    private val fdBattingTeamKey = field(name("fact_delivery", "batting_team_key"), Long::class.javaObjectType)
    private val fdTotalRuns = field(name("fact_delivery", "total_runs"), Int::class.javaObjectType)
    private val fdWicketCount = field(name("fact_delivery", "wicket_count"), Int::class.javaObjectType)
    private val fdWides = field(name("fact_delivery", "wides"), Int::class.javaObjectType)
    private val fdNoBalls = field(name("fact_delivery", "no_balls"), Int::class.javaObjectType)

    override suspend fun recentMatches(limit: Limit): List<MatchSummary> = withContext(ioDispatcher) {
        try {
            // Select the distinct match_start_date_keys for the last `limit.value` days
            val recentDateKeys = dsl.selectDistinct(mMatchStartDateKey)
                .from(dimMatch)
                .where(mMatchStartDateKey.isNotNull)
                .orderBy(mMatchStartDateKey.desc())
                .limit(limit.value)
                .fetch(mMatchStartDateKey)
                .filterNotNull()

            if (recentDateKeys.isEmpty()) {
                return@withContext emptyList()
            }

            // Fetch match descriptors on those dates
            val matchRows = dsl.select(
                mMatchKey,
                mSourceMatchId,
                mFileName,
                mMatchType,
                mEventName,
                mMatchDateText,
                mSeason,
                mBallsPerOver,
                mTeam1Key,
                t1TeamName,
                mTeam2Key,
                t2TeamName,
                mWinnerTeamKey,
                twTeamName,
                mVictoryType,
                fmMargin,
                dCalendarDate
            )
                .from(dimMatch)
                .join(dimDate).on(mMatchStartDateKey.eq(dDateKey))
                .leftJoin(dimTeam1).on(mTeam1Key.eq(t1TeamKey))
                .leftJoin(dimTeam2).on(mTeam2Key.eq(t2TeamKey))
                .leftJoin(dimTeamWinner).on(mWinnerTeamKey.eq(twTeamKey))
                .leftJoin(factMatch).on(mMatchKey.eq(fmMatchKey))
                .where(mMatchStartDateKey.`in`(recentDateKeys))
                .orderBy(dCalendarDate.desc(), mMatchKey.desc())
                .fetch()

            if (matchRows.isEmpty()) {
                return@withContext emptyList()
            }

            val matchKeys = matchRows.mapNotNull { it.get(mMatchKey) }

            // Aggregate innings scores for these matches
            val inningsRows = dsl.select(
                fdMatchKey,
                fdBattingTeamKey,
                iInningsNumber,
                sum(fdTotalRuns).`as`("runs"),
                sum(fdWicketCount).`as`("wickets"),
                sum(case_().`when`(fdWides.eq(0).and(fdNoBalls.eq(0)), 1).otherwise(0)).`as`("legal_balls")
            )
                .from(factDelivery)
                .join(dimInnings).on(fdInningsKey.eq(iInningsKey))
                .where(fdMatchKey.`in`(matchKeys))
                .groupBy(fdMatchKey, fdBattingTeamKey, iInningsNumber)
                .orderBy(fdMatchKey.asc(), iInningsNumber.asc())
                .fetch()

            val inningsByMatchAndTeam = inningsRows.groupBy {
                Pair(
                    it.get(fdMatchKey) ?: 0L,
                    it.get(fdBattingTeamKey) ?: 0L
                )
            }

            matchRows.map { record ->
                val matchId = record.get(mMatchKey) ?: 0L
                val ballsPerOver = (record.get(mBallsPerOver) ?: 6).let { if (it <= 0) 6 else it }
                val team1Id = record.get(mTeam1Key) ?: 0L
                val team2Id = record.get(mTeam2Key) ?: 0L
                val winnerId = record.get(mWinnerTeamKey)

                val team1Innings = inningsByMatchAndTeam[Pair(matchId, team1Id)].orEmpty()
                val team2Innings = inningsByMatchAndTeam[Pair(matchId, team2Id)].orEmpty()

                val (team1Score, team1Overs) = calculateTeamScore(team1Innings, ballsPerOver)
                val (team2Score, team2Overs) = calculateTeamScore(team2Innings, ballsPerOver)

                val winnerName = record.get(twTeamName)
                val victoryType = record.get(mVictoryType)
                val margin = record.get(fmMargin)
                val rawMatchType = record.get(mMatchType).orEmpty()

                MatchSummary(
                    matchKey = MatchKey.from(matchId),
                    sourceMatchId = SourceMatchId.from(record.get(mSourceMatchId) ?: 0),
                    fileName = record.get(mFileName).orEmpty(),
                    matchType = MatchType.from(rawMatchType),
                    season = Season.from(record.get(mSeason).orEmpty()),
                    competition = record.get(mEventName)?.takeIf { it.isNotBlank() } ?: "MISSING",
                    date = formatDateText(record.get(mMatchDateText), record.get(dCalendarDate)),
                    team1 = record.get(t1TeamName)?.takeIf { it.isNotBlank() } ?: "MISSING",
                    score1 = team1Score,
                    overs1 = team1Overs,
                    isTeam1Winner = (winnerId != null && winnerId == team1Id),
                    team2 = record.get(t2TeamName)?.takeIf { it.isNotBlank() } ?: "MISSING",
                    score2 = team2Score,
                    overs2 = team2Overs,
                    isTeam2Winner = (winnerId != null && winnerId == team2Id),
                    result = formatResult(winnerName, victoryType, margin),
                    format = mapFormat(rawMatchType)
                )
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            log.error("Recent match query failed", cause)
            throw cause
        }
    }

    companion object {
        private val MATCH_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

        fun calculateTeamScore(
            inningsList: List<org.jooq.Record>,
            ballsPerOver: Int
        ): Pair<String, String?> {
            if (inningsList.isEmpty()) {
                return Pair("MISSING", null)
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

        fun formatResult(winnerName: String?, victoryType: String?, margin: Int?): String {
            if (winnerName != null) {
                val winner = winnerName.ifBlank { "MISSING" }
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
                else -> "MISSING"
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
            else -> rawMatchType.ifBlank { "MISSING" }
        }

        fun formatDateText(dateText: String?, calendarDate: LocalDate?): String {
            if (calendarDate != null) {
                return calendarDate.format(MATCH_DATE_FORMATTER)
            }
            if (dateText.isNullOrBlank()) return "MISSING"
            val parsed = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
            return if (parsed != null) {
                parsed.format(MATCH_DATE_FORMATTER)
            } else {
                dateText
            }
        }
    }
}
