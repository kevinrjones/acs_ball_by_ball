package com.knowledgespike.ballbyball.api.feature.matches.data.repository

import com.knowledgespike.ballbyball.api.feature.matches.domain.repository.MatchRepository
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchSearchCriteria
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchSearchMatch
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchSearchPage
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchScoresheetContext
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchScoresheetDelivery
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchScoresheetInnings
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchScoresheetPage
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchScoresheetWicket
import com.knowledgespike.ballbyball.contracts.MatchSummary
import com.knowledgespike.ballbyball.types.values.Limit
import com.knowledgespike.ballbyball.types.values.MatchKey
import com.knowledgespike.ballbyball.types.values.MatchType
import com.knowledgespike.ballbyball.types.values.PageNumber
import com.knowledgespike.ballbyball.types.values.Season
import com.knowledgespike.ballbyball.types.values.SourceMatchId
import com.knowledgespike.ballbyball.contracts.ScoresheetCompleteness
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DSL.case_
import org.jooq.impl.DSL.count
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
    private val dimGround = table(name("dim_ground"))
    private val dimTeamWinner = table(name("dim_team")).`as`("tw")
    private val factMatch = table(name("fact_match"))
    private val dimInnings = table(name("dim_innings"))
    private val factDelivery = table(name("fact_delivery"))
    private val dimInningsBattingTeam = table(name("dim_team")).`as`("ib")
    private val dimInningsBowlingTeam = table(name("dim_team")).`as`("io")
    private val dimPersonBatter = table(name("dim_person")).`as`("pb")
    private val dimPersonNonStriker = table(name("dim_person")).`as`("pns")
    private val dimPersonBowler = table(name("dim_person")).`as`("pbo")
    private val dimPersonFielder = table(name("dim_person")).`as`("pf")
    private val dimWicket = table(name("dim_wicket"))
    private val bridgeDeliveryWicket = table(name("bridge_delivery_wicket"))
    private val bridgeDeliveryFielder = table(name("bridge_delivery_fielder"))

    private val mMatchKey = field(name("dim_match", "id"), Long::class.javaObjectType)
    private val mFileName = field(name("dim_match", "file_name"), String::class.java)
    private val mMatchType = field(name("dim_match", "match_type"), String::class.java)
    private val mEventName = field(name("dim_match", "event_name"), String::class.java)
    private val mMatchDateText = field(name("dim_match", "match_date_text"), String::class.java)
    private val mSeason = field(name("dim_match", "season"), String::class.java)
    private val mBallsPerOver = field(name("dim_match", "balls_per_over"), Int::class.javaObjectType)
    private val mTeam1Key = field(name("dim_match", "team1_key"), Long::class.javaObjectType)
    private val mTeam2Key = field(name("dim_match", "team2_key"), Long::class.javaObjectType)
    private val mGroundKey = field(name("dim_match", "ground_key"), Long::class.javaObjectType)
    private val mWinnerTeamKey = field(name("dim_match", "winner_team_key"), Long::class.javaObjectType)
    private val mVictoryType = field(name("dim_match", "victory_type"), String::class.java)
    private val mMatchStartDateKey = field(name("dim_match", "match_start_date_key"), Int::class.javaObjectType)

    private val dDateKey = field(name("dim_date", "date_key"), Int::class.javaObjectType)
    private val dCalendarDate = field(name("dim_date", "calendar_date"), LocalDate::class.java)

    private val t1TeamKey = field(name("t1", "team_key"), Long::class.javaObjectType)
    private val t1TeamName = field(name("t1", "team_name"), String::class.java)

    private val t2TeamKey = field(name("t2", "team_key"), Long::class.javaObjectType)
    private val t2TeamName = field(name("t2", "team_name"), String::class.java)

    private val gGroundKey = field(name("dim_ground", "ground_key"), Long::class.javaObjectType)
    private val gGroundName = field(name("dim_ground", "ground_name"), String::class.java)

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
    private val fdDeliveryKey = field(name("fact_delivery", "delivery_key"), Long::class.javaObjectType)
    private val fdSourceBallId = field(name("fact_delivery", "source_ball_id"), Int::class.javaObjectType)
    private val fdOverNumber = field(name("fact_delivery", "over_number"), Int::class.javaObjectType)
    private val fdBallNumber = field(name("fact_delivery", "ball_number"), Int::class.javaObjectType)
    private val fdBallInOver = field(name("fact_delivery", "ball_in_over"), Int::class.javaObjectType)
    private val fdInningsOrder = field(name("fact_delivery", "innings_order"), Int::class.javaObjectType)
    private val fdBatterKey = field(name("fact_delivery", "batter_key"), Long::class.javaObjectType)
    private val fdNonStrikerKey = field(name("fact_delivery", "non_striker_key"), Long::class.javaObjectType)
    private val fdBowlerKey = field(name("fact_delivery", "bowler_key"), Long::class.javaObjectType)
    private val fdBatterRuns = field(name("fact_delivery", "batter_runs"), Int::class.javaObjectType)
    private val fdExtraRuns = field(name("fact_delivery", "extra_runs"), Int::class.javaObjectType)
    private val fdByes = field(name("fact_delivery", "byes"), Int::class.javaObjectType)
    private val fdLegByes = field(name("fact_delivery", "leg_byes"), Int::class.javaObjectType)
    private val fdNonBoundary = field(name("fact_delivery", "non_boundary"), Int::class.javaObjectType)
    private val fdPowerplay = field(name("fact_delivery", "powerplay"), Int::class.javaObjectType)
    private val pbPersonKey = field(name("pb", "person_key"), Long::class.javaObjectType)
    private val pbFullName = field(name("pb", "full_name"), String::class.java)
    private val pnsPersonKey = field(name("pns", "person_key"), Long::class.javaObjectType)
    private val pnsFullName = field(name("pns", "full_name"), String::class.java)
    private val pboPersonKey = field(name("pbo", "person_key"), Long::class.javaObjectType)
    private val pboFullName = field(name("pbo", "full_name"), String::class.java)
    private val pfPersonKey = field(name("pf", "person_key"), Long::class.javaObjectType)
    private val pfFullName = field(name("pf", "full_name"), String::class.java)
    private val ibTeamKey = field(name("ib", "team_key"), Long::class.javaObjectType)
    private val ibTeamName = field(name("ib", "team_name"), String::class.java)
    private val ioTeamKey = field(name("io", "team_key"), Long::class.javaObjectType)
    private val ioTeamName = field(name("io", "team_name"), String::class.java)
    private val iMatchKey = field(name("dim_innings", "match_key"), Long::class.javaObjectType)
    private val iBattingTeamKey = field(name("dim_innings", "batting_team_key"), Long::class.javaObjectType)
    private val iBowlingTeamKey = field(name("dim_innings", "bowling_team_key"), Long::class.javaObjectType)
    private val dwDeliveryKey = field(name("bridge_delivery_wicket", "delivery_key"), Long::class.javaObjectType)
    private val dwWicketKey = field(name("bridge_delivery_wicket", "wicket_key"), Long::class.javaObjectType)
    private val dfDeliveryKey = field(name("bridge_delivery_fielder", "delivery_key"), Long::class.javaObjectType)
    private val dfWicketKey = field(name("bridge_delivery_fielder", "wicket_key"), Long::class.javaObjectType)
    private val dfPersonKey = field(name("bridge_delivery_fielder", "person_key"), Long::class.javaObjectType)
    private val wWicketKey = field(name("dim_wicket", "wicket_key"), Long::class.javaObjectType)
    private val wKind = field(name("dim_wicket", "wicket_kind"), String::class.java)

    private val searchConditionBuilder = MatchSearchConditionBuilder(
        MatchSearchFields(
            matchType = mMatchType,
            winnerTeamKey = mWinnerTeamKey,
            team1Key = mTeam1Key,
            team2Key = mTeam2Key,
            team1Name = t1TeamName,
            team2Name = t2TeamName,
            calendarDate = dCalendarDate,
            victoryType = mVictoryType
        )
    )

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
                    sourceMatchId = SourceMatchId.from((record.get(mMatchKey) ?: 0L).toInt()),
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

    override suspend fun searchMatches(
        criteria: MatchSearchCriteria
    ): MatchSearchPage = withContext(ioDispatcher) {
        try {
            val searchCondition = searchConditionBuilder.build(criteria)
            val totalResults = dsl.selectCount()
                .from(dimMatch)
                .leftJoin(dimDate).on(mMatchStartDateKey.eq(dDateKey))
                .leftJoin(dimTeam1).on(mTeam1Key.eq(t1TeamKey))
                .leftJoin(dimTeam2).on(mTeam2Key.eq(t2TeamKey))
                .leftJoin(dimGround).on(mGroundKey.eq(gGroundKey))
                .where(searchCondition)
                .fetchOne(count()) ?: 0

            val rows = dsl.select(
                mMatchKey,
                mFileName,
                mMatchType,
                mSeason,
                mEventName,
                mMatchDateText,
                dCalendarDate,
                mTeam1Key,
                mTeam2Key,
                mWinnerTeamKey,
                mVictoryType,
                t1TeamName,
                t2TeamName,
                gGroundName,
                mGroundKey,
                fmMargin
            )
                .from(dimMatch)
                .leftJoin(dimDate).on(mMatchStartDateKey.eq(dDateKey))
                .leftJoin(dimTeam1).on(mTeam1Key.eq(t1TeamKey))
                .leftJoin(dimTeam2).on(mTeam2Key.eq(t2TeamKey))
                .leftJoin(dimGround).on(mGroundKey.eq(gGroundKey))
                .leftJoin(factMatch).on(fmMatchKey.eq(mMatchKey))
                .where(searchCondition)
                .orderBy(dCalendarDate.desc().nullsLast(), mMatchKey.desc())
                .limit(criteria.pageSize.value)
                .offset((criteria.page.value - 1) * criteria.pageSize.value)
                .fetch()

            val hasNext = criteria.page.value < PageNumber.MAX_VALUE &&
                criteria.page.value.toLong() * criteria.pageSize.value < totalResults
            MatchSearchPage(
                page = criteria.page,
                pageSize = criteria.pageSize,
                totalResults = totalResults,
                hasNext = hasNext,
                nextPage = if (hasNext) PageNumber.from(criteria.page.value + 1) else null,
                matches = rows.map { record ->
                    val team1Key = record.get(mTeam1Key)
                    val team2Key = record.get(mTeam2Key)
                    val winnerKey = record.get(mWinnerTeamKey)
                    val winnerName = when (winnerKey) {
                        team1Key -> record.get(t1TeamName)
                        team2Key -> record.get(t2TeamName)
                        else -> null
                    }
                    MatchSearchMatch(
                        matchKey = MatchKey.from(requireNotNull(record.get(mMatchKey))),
                        sourceMatchId = SourceMatchId.from(requireNotNull(record.get(mMatchKey)).toInt()),
                        fileName = requireNotNull(record.get(mFileName)),
                        matchType = record.get(mMatchType)?.takeIf { it.isNotBlank() }?.let(MatchType::from),
                        season = record.get(mSeason)?.takeIf { it.isNotBlank() }?.let(Season::from),
                        competition = record.get(mEventName)?.takeIf { it.isNotBlank() },
                        date = formatDateText(record.get(mMatchDateText), record.get(dCalendarDate))
                            .takeUnless { it == "MISSING" },
                        team1 = record.get(t1TeamName)?.takeIf { it.isNotBlank() },
                        team2 = record.get(t2TeamName)?.takeIf { it.isNotBlank() },
                        ground = record.get(gGroundName)?.takeIf { it.isNotBlank() },
                        result = formatResult(winnerName, record.get(mVictoryType), record.get(fmMargin))
                            .takeUnless { it == "MISSING" }
                    )
                }
            )
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            log.error("Historical match search failed", cause)
            throw cause
        }
    }

    override suspend fun scoresheet(
        matchKey: MatchKey
    ): MatchScoresheetPage? = withContext(ioDispatcher) {
        try {
            val match = dsl.select(
                mMatchKey,
                mFileName,
                mMatchType,
                mSeason,
                mEventName,
                mMatchDateText,
                dCalendarDate,
                mTeam1Key,
                mTeam2Key,
                mWinnerTeamKey,
                mVictoryType,
                t1TeamName,
                t2TeamName,
                gGroundName,
                fmMargin
            )
                .from(dimMatch)
                .leftJoin(dimDate).on(mMatchStartDateKey.eq(dDateKey))
                .leftJoin(dimTeam1).on(mTeam1Key.eq(t1TeamKey))
                .leftJoin(dimTeam2).on(mTeam2Key.eq(t2TeamKey))
                .leftJoin(dimGround).on(mGroundKey.eq(gGroundKey))
                .leftJoin(factMatch).on(fmMatchKey.eq(mMatchKey))
                .where(mMatchKey.eq(matchKey.value))
                .fetchOne() ?: return@withContext null

            val team1Key = match.get(mTeam1Key)
            val team2Key = match.get(mTeam2Key)
            val winnerKey = match.get(mWinnerTeamKey)
            val winnerName = when (winnerKey) {
                team1Key -> match.get(t1TeamName)
                team2Key -> match.get(t2TeamName)
                else -> null
            }
            val context = MatchScoresheetContext(
                matchKey = MatchKey.from(requireNotNull(match.get(mMatchKey))),
                sourceMatchId = SourceMatchId.from(requireNotNull(match.get(mMatchKey)).toInt()),
                fileName = requireNotNull(match.get(mFileName)),
                matchType = match.get(mMatchType)?.takeIf { it.isNotBlank() }?.let(MatchType::from),
                season = match.get(mSeason)?.takeIf { it.isNotBlank() }?.let(Season::from),
                competition = match.get(mEventName)?.takeIf { it.isNotBlank() },
                date = formatDateText(match.get(mMatchDateText), match.get(dCalendarDate))
                    .takeUnless { it == "MISSING" },
                team1 = match.get(t1TeamName)?.takeIf { it.isNotBlank() },
                team2 = match.get(t2TeamName)?.takeIf { it.isNotBlank() },
                ground = match.get(gGroundName)?.takeIf { it.isNotBlank() },
                result = formatResult(winnerName, match.get(mVictoryType), match.get(fmMargin))
                    .takeUnless { it == "MISSING" }
            )

            val inningsRows = dsl.select(iInningsKey, iInningsNumber, ibTeamName, ioTeamName)
                .from(dimInnings)
                .leftJoin(dimInningsBattingTeam).on(iBattingTeamKey.eq(ibTeamKey))
                .leftJoin(dimInningsBowlingTeam).on(iBowlingTeamKey.eq(ioTeamKey))
                .where(iMatchKey.eq(matchKey.value))
                .orderBy(iInningsNumber.asc(), iInningsKey.asc())
                .fetch()

            val totalDeliveries = dsl.selectCount()
                .from(factDelivery)
                .where(fdMatchKey.eq(matchKey.value))
                .fetchOne(count()) ?: 0
            val deliveryInnings = dsl.selectDistinct(fdInningsKey)
                .from(factDelivery)
                .where(fdMatchKey.eq(matchKey.value))
                .fetch(fdInningsKey)
                .filterNotNull()

            val deliveryRows = dsl.select(
                fdDeliveryKey,
                fdSourceBallId,
                fdInningsKey,
                fdInningsOrder,
                fdOverNumber,
                fdBallNumber,
                fdBallInOver,
                pbFullName,
                pnsFullName,
                pboFullName,
                fdBatterRuns,
                fdExtraRuns,
                fdTotalRuns,
                fdNoBalls,
                fdWides,
                fdByes,
                fdLegByes,
                fdNonBoundary,
                fdPowerplay,
                fdWicketCount
            )
                .from(factDelivery)
                .leftJoin(dimPersonBatter).on(fdBatterKey.eq(pbPersonKey))
                .leftJoin(dimPersonNonStriker).on(fdNonStrikerKey.eq(pnsPersonKey))
                .leftJoin(dimPersonBowler).on(fdBowlerKey.eq(pboPersonKey))
                .where(fdMatchKey.eq(matchKey.value))
                .orderBy(
                    fdInningsOrder.asc(),
                    fdOverNumber.asc(),
                    fdBallInOver.asc(),
                    fdBallNumber.asc(),
                    fdDeliveryKey.asc()
                )
                .fetch()

            val deliveryKeys = deliveryRows.mapNotNull { it.get(fdDeliveryKey) }
            val wicketRows = if (deliveryKeys.isEmpty()) {
                emptyList()
            } else {
                dsl.select(dwDeliveryKey, dwWicketKey, wKind)
                    .from(bridgeDeliveryWicket)
                    .join(dimWicket).on(dwWicketKey.eq(wWicketKey))
                    .where(dwDeliveryKey.`in`(deliveryKeys))
                    .fetch()
            }
            val fielderRows = if (deliveryKeys.isEmpty()) {
                emptyList()
            } else {
                dsl.select(dfDeliveryKey, dfWicketKey, pfFullName)
                    .from(bridgeDeliveryFielder)
                    .leftJoin(dimPersonFielder).on(dfPersonKey.eq(pfPersonKey))
                    .where(dfDeliveryKey.`in`(deliveryKeys))
                    .fetch()
            }
            val fieldersByWicket = fielderRows.groupBy {
                Pair(it.get(dfDeliveryKey), it.get(dfWicketKey))
            }.mapValues { (_, rows) -> rows.mapNotNull { it.get(pfFullName) } }
            val wicketsByDelivery = wicketRows.groupBy { it.get(dwDeliveryKey) }.mapValues { (_, rows) ->
                rows.mapNotNull { row ->
                    val deliveryId = row.get(dwDeliveryKey)
                    val wicketId = row.get(dwWicketKey)
                    if (deliveryId == null || wicketId == null) {
                        null
                    } else {
                        MatchScoresheetWicket(
                            wicketKey = wicketId,
                            kind = row.get(wKind),
                            fielders = fieldersByWicket[Pair(deliveryId, wicketId)].orEmpty()
                        )
                    }
                }
            }

            val deliveriesByInnings = deliveryRows.groupBy { it.get(fdInningsKey) }.mapValues { (_, rows) ->
                rows.mapNotNull { row ->
                    val deliveryId = row.get(fdDeliveryKey) ?: return@mapNotNull null
                    MatchScoresheetDelivery(
                        deliveryKey = deliveryId,
                        sourceBallId = row.get(fdSourceBallId) ?: 0,
                        inningsOrder = row.get(fdInningsOrder) ?: 0,
                        overNumber = row.get(fdOverNumber) ?: 0,
                        ballNumber = row.get(fdBallNumber) ?: 0,
                        ballInOver = row.get(fdBallInOver) ?: 0,
                        batter = row.get(pbFullName),
                        nonStriker = row.get(pnsFullName),
                        bowler = row.get(pboFullName),
                        batterRuns = row.get(fdBatterRuns) ?: 0,
                        extraRuns = row.get(fdExtraRuns) ?: 0,
                        totalRuns = row.get(fdTotalRuns) ?: 0,
                        noBalls = row.get(fdNoBalls) ?: 0,
                        wides = row.get(fdWides) ?: 0,
                        byes = row.get(fdByes) ?: 0,
                        legByes = row.get(fdLegByes) ?: 0,
                        nonBoundary = row.get(fdNonBoundary),
                        powerplay = row.get(fdPowerplay) ?: 0,
                        wicketCount = row.get(fdWicketCount) ?: 0,
                        wickets = wicketsByDelivery[deliveryId].orEmpty()
                    )
                }
            }
            val innings = inningsRows.map { row ->
                val inningsKey = row.get(iInningsKey)
                MatchScoresheetInnings(
                    inningsNumber = row.get(iInningsNumber) ?: 0,
                    battingTeam = row.get(ibTeamName),
                    bowlingTeam = row.get(ioTeamName),
                    deliveries = deliveriesByInnings[inningsKey].orEmpty()
                )
            }
            val missingData = buildList {
                if (inningsRows.isEmpty()) add("innings")
                if (totalDeliveries == 0) add("deliveries")
                if (inningsRows.any { !deliveryInnings.contains(it.get(iInningsKey)) }) {
                    add("deliveries for one or more innings")
                }
                if (context.team1 == null || context.team2 == null) add("team names")
            }.distinct()
            val completeness = when {
                inningsRows.isEmpty() && totalDeliveries == 0 -> ScoresheetCompleteness.EMPTY
                missingData.isEmpty() -> ScoresheetCompleteness.COMPLETE
                else -> ScoresheetCompleteness.INCOMPLETE
            }
            MatchScoresheetPage(
                context = context,
                completeness = completeness,
                missingData = missingData,
                innings = innings
            )
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            log.error("Historical match scoresheet query failed", cause)
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
