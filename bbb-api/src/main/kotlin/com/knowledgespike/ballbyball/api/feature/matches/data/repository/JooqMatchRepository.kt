package com.knowledgespike.ballbyball.api.feature.matches.data.repository

import com.knowledgespike.ballbyball.api.feature.matches.domain.repository.MatchRepository
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchSearchCriteria
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchSearchMatch
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchSearchPage
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchScoresheetPage
import com.knowledgespike.ballbyball.contracts.MatchSummary
import com.knowledgespike.ballbyball.api.generated.jooq.tables.DimDate
import com.knowledgespike.ballbyball.api.generated.jooq.tables.DimGround
import com.knowledgespike.ballbyball.api.generated.jooq.tables.DimInnings
import com.knowledgespike.ballbyball.api.generated.jooq.tables.DimMatch
import com.knowledgespike.ballbyball.api.generated.jooq.tables.DimTeam
import com.knowledgespike.ballbyball.api.generated.jooq.tables.FactDelivery
import com.knowledgespike.ballbyball.api.generated.jooq.tables.FactMatch
import com.knowledgespike.ballbyball.types.values.Limit
import com.knowledgespike.ballbyball.types.values.MatchType
import com.knowledgespike.ballbyball.types.values.PageNumber
import com.knowledgespike.ballbyball.types.values.PublicMatchId
import com.knowledgespike.ballbyball.types.values.Season
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DSL.case_
import org.jooq.impl.DSL.count
import org.jooq.impl.DSL.sum
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class JooqMatchRepository(
    dataSource: DataSource,
    dialect: SQLDialect,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MatchRepository {
    private val log = LoggerFactory.getLogger(JooqMatchRepository::class.java)
    private val dsl = DSL.using(dataSource, dialect)

    private val dimMatch = DimMatch.DIM_MATCH
    private val dimDate = DimDate.DIM_DATE
    private val dimTeam1 = DimTeam.DIM_TEAM.`as`("t1")
    private val dimTeam2 = DimTeam.DIM_TEAM.`as`("t2")
    private val dimGround = DimGround.DIM_GROUND
    private val dimTeamWinner = DimTeam.DIM_TEAM.`as`("tw")
    private val factMatch = FactMatch.FACT_MATCH
    private val dimInnings = DimInnings.DIM_INNINGS
    private val factDelivery = FactDelivery.FACT_DELIVERY

    private val mMatchKey = dimMatch.MATCH_KEY
    private val mPublicMatchId = dimMatch.PUBLIC_MATCH_ID
    private val mFileName = dimMatch.FILE_NAME
    private val mMatchType = dimMatch.MATCH_TYPE
    private val mEventName = dimMatch.EVENT_NAME
    private val mMatchDateText = dimMatch.MATCH_DATE_TEXT
    private val mSeason = dimMatch.SEASON
    private val mBallsPerOver = dimMatch.BALLS_PER_OVER
    private val mTeam1Key = dimMatch.TEAM1_KEY
    private val mTeam2Key = dimMatch.TEAM2_KEY
    private val mGroundKey = dimMatch.GROUND_KEY
    private val mWinnerTeamKey = dimMatch.WINNER_TEAM_KEY
    private val mVictoryType = dimMatch.VICTORY_TYPE
    private val mMatchStartDateKey = dimMatch.MATCH_START_DATE_KEY

    private val dDateKey = dimDate.DATE_KEY
    private val dCalendarDate = dimDate.CALENDAR_DATE

    private val t1TeamKey = dimTeam1.TEAM_KEY
    private val t1TeamName = dimTeam1.TEAM_NAME

    private val t2TeamKey = dimTeam2.TEAM_KEY
    private val t2TeamName = dimTeam2.TEAM_NAME

    private val gGroundKey = dimGround.GROUND_KEY
    private val gGroundName = dimGround.GROUND_NAME

    private val twTeamKey = dimTeamWinner.TEAM_KEY
    private val twTeamName = dimTeamWinner.TEAM_NAME

    private val fmMatchKey = factMatch.MATCH_KEY
    private val fmMargin = factMatch.MARGIN

    private val iInningsKey = dimInnings.INNINGS_KEY
    private val iInningsNumber = dimInnings.INNINGS_NUMBER

    private val fdMatchKey = factDelivery.MATCH_KEY
    private val fdInningsKey = factDelivery.INNINGS_KEY
    private val fdBattingTeamKey = factDelivery.BATTING_TEAM_KEY
    private val fdTotalRuns = factDelivery.TOTAL_RUNS
    private val fdWicketCount = factDelivery.WICKET_COUNT
    private val fdWides = factDelivery.WIDES
    private val fdNoBalls = factDelivery.NO_BALLS

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

    override suspend fun recentMatches(limit: Limit): List<MatchSummary> =
        withMatchQuery(log, ioDispatcher, "Recent match query") {
// Select the distinct match_start_date_keys for the last `limit.value` days
            val recentDateKeys = dsl.selectDistinct(mMatchStartDateKey)
                .from(dimMatch)
                .where(mMatchStartDateKey.isNotNull.and(mPublicMatchId.isNotNull))
                .orderBy(mMatchStartDateKey.desc())
                .limit(limit.value)
                .fetch(mMatchStartDateKey)
                .filterNotNull()

            if (recentDateKeys.isEmpty()) {
                return@withMatchQuery emptyList()
            }

            // Fetch match descriptors on those dates
            val matchRows = dsl.select(
                mPublicMatchId,
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
                .where(mPublicMatchId.isNotNull.and(mMatchStartDateKey.`in`(recentDateKeys)))
                .orderBy(dCalendarDate.desc(), mMatchKey.desc())
                .fetch()

            if (matchRows.isEmpty()) {
                return@withMatchQuery emptyList()
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
                    it.get(fdMatchKey) ?: 0,
                    it.get(fdBattingTeamKey) ?: 0L
                )
            }

            matchRows.map { record ->
                val matchId = record.get(mMatchKey) ?: 0
                val ballsPerOver = (record.get(mBallsPerOver) ?: 6).let { if (it <= 0) 6 else it }
                val team1Id = record.get(mTeam1Key) ?: 0L
                val team2Id = record.get(mTeam2Key) ?: 0L
                val winnerId = record.get(mWinnerTeamKey)

                val team1Innings = inningsByMatchAndTeam[Pair(matchId, team1Id)].orEmpty()
                val team2Innings = inningsByMatchAndTeam[Pair(matchId, team2Id)].orEmpty()

                val (team1Score, team1Overs) = MatchResponseMapper.calculateTeamScore(team1Innings, ballsPerOver)
                val (team2Score, team2Overs) = MatchResponseMapper.calculateTeamScore(team2Innings, ballsPerOver)

                val winnerName = record.get(twTeamName)
                val victoryType = record.get(mVictoryType)
                val margin = record.get(fmMargin)
                val rawMatchType = record.get(mMatchType).orEmpty()

                MatchSummary(
                    publicMatchId = PublicMatchId.from(requireNotNull(record.get(mPublicMatchId))),
                    fileName = record.get(mFileName).orEmpty(),
                    matchType = MatchType.from(rawMatchType),
                    season = Season.from(record.get(mSeason).orEmpty()),
                    competition = record.get(mEventName)?.takeIf { it.isNotBlank() },
                    date = MatchResponseMapper.formatDateText(record.get(mMatchDateText), record.get(dCalendarDate)),
                    team1 = record.get(t1TeamName)?.takeIf { it.isNotBlank() },
                    score1 = team1Score,
                    overs1 = team1Overs,
                    isTeam1Winner = (winnerId != null && winnerId == team1Id),
                    team2 = record.get(t2TeamName)?.takeIf { it.isNotBlank() },
                    score2 = team2Score,
                    overs2 = team2Overs,
                    isTeam2Winner = (winnerId != null && winnerId == team2Id),
                    result = MatchResponseMapper.formatResult(winnerName, victoryType, margin),
                    format = MatchResponseMapper.mapFormat(rawMatchType)
                )
            }
        }

    override suspend fun searchMatches(
        criteria: MatchSearchCriteria
    ): MatchSearchPage =
        withMatchQuery(log, ioDispatcher, "Historical match search") {
            val searchCondition = searchConditionBuilder.build(criteria)
            val totalResults = dsl.selectCount()
                .from(dimMatch)
                .leftJoin(dimDate).on(mMatchStartDateKey.eq(dDateKey))
                .leftJoin(dimTeam1).on(mTeam1Key.eq(t1TeamKey))
                .leftJoin(dimTeam2).on(mTeam2Key.eq(t2TeamKey))
                .leftJoin(dimGround).on(mGroundKey.eq(gGroundKey))
                .where(mPublicMatchId.isNotNull.and(searchCondition))
                .fetchOne(count()) ?: 0

            val rows = dsl.select(
                mPublicMatchId,
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
                .where(mPublicMatchId.isNotNull.and(searchCondition))
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
                        publicMatchId = PublicMatchId.from(requireNotNull(record.get(mPublicMatchId))),
                        fileName = requireNotNull(record.get(mFileName)),
                        matchType = record.get(mMatchType)?.takeIf { it.isNotBlank() }?.let(MatchType::from),
                        season = record.get(mSeason)?.takeIf { it.isNotBlank() }?.let(Season::from),
                        competition = record.get(mEventName)?.takeIf { it.isNotBlank() },
                        date = MatchResponseMapper.formatDateText(record.get(mMatchDateText), record.get(dCalendarDate)),
                        team1 = record.get(t1TeamName)?.takeIf { it.isNotBlank() },
                        team2 = record.get(t2TeamName)?.takeIf { it.isNotBlank() },
                        ground = record.get(gGroundName)?.takeIf { it.isNotBlank() },
                        result = MatchResponseMapper.formatResult(winnerName, record.get(mVictoryType), record.get(fmMargin))
                    )
                }
            )
        
        }

    private val scoresheetLoader = JooqMatchScoresheetLoader(dsl, ioDispatcher)

    override suspend fun scoresheet(
        publicMatchId: PublicMatchId
    ): MatchScoresheetPage? = scoresheetLoader.load(publicMatchId)
}
