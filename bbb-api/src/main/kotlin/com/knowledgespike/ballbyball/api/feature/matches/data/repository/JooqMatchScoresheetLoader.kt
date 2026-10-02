package com.knowledgespike.ballbyball.api.feature.matches.data.repository

import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchScoresheetContext
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchScoresheetDelivery
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchScoresheetInnings
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchScoresheetPage
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchScoresheetWicket
import com.knowledgespike.ballbyball.api.generated.jooq.tables.BridgeDeliveryFielder
import com.knowledgespike.ballbyball.api.generated.jooq.tables.BridgeDeliveryWicket
import com.knowledgespike.ballbyball.api.generated.jooq.tables.DimDate
import com.knowledgespike.ballbyball.api.generated.jooq.tables.DimGround
import com.knowledgespike.ballbyball.api.generated.jooq.tables.DimInnings
import com.knowledgespike.ballbyball.api.generated.jooq.tables.DimMatch
import com.knowledgespike.ballbyball.api.generated.jooq.tables.DimPerson
import com.knowledgespike.ballbyball.api.generated.jooq.tables.DimTeam
import com.knowledgespike.ballbyball.api.generated.jooq.tables.DimWicket
import com.knowledgespike.ballbyball.api.generated.jooq.tables.FactDelivery
import com.knowledgespike.ballbyball.api.generated.jooq.tables.FactMatch
import com.knowledgespike.ballbyball.types.values.MatchType
import com.knowledgespike.ballbyball.types.values.PublicMatchId
import com.knowledgespike.ballbyball.types.values.Season
import kotlinx.coroutines.CoroutineDispatcher
import org.jooq.DSLContext
import org.jooq.impl.DSL.count
import org.slf4j.LoggerFactory

/** Loads scoresheet context, innings, deliveries, and wicket graph for one public match id. */
internal class JooqMatchScoresheetLoader(
    private val dsl: DSLContext,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val log = LoggerFactory.getLogger(JooqMatchScoresheetLoader::class.java)

    private val dimMatch = DimMatch.DIM_MATCH
    private val dimDate = DimDate.DIM_DATE
    private val dimTeam1 = DimTeam.DIM_TEAM.`as`("t1")
    private val dimTeam2 = DimTeam.DIM_TEAM.`as`("t2")
    private val dimGround = DimGround.DIM_GROUND
    private val dimTeamWinner = DimTeam.DIM_TEAM.`as`("tw")
    private val factMatch = FactMatch.FACT_MATCH
    private val dimInnings = DimInnings.DIM_INNINGS
    private val factDelivery = FactDelivery.FACT_DELIVERY
    private val dimInningsBattingTeam = DimTeam.DIM_TEAM.`as`("ib")
    private val dimInningsBowlingTeam = DimTeam.DIM_TEAM.`as`("io")
    private val dimPersonBatter = DimPerson.DIM_PERSON.`as`("pb")
    private val dimPersonNonStriker = DimPerson.DIM_PERSON.`as`("pns")
    private val dimPersonBowler = DimPerson.DIM_PERSON.`as`("pbo")
    private val dimPersonFielder = DimPerson.DIM_PERSON.`as`("pf")
    private val dimWicket = DimWicket.DIM_WICKET
    private val bridgeDeliveryWicket = BridgeDeliveryWicket.BRIDGE_DELIVERY_WICKET
    private val bridgeDeliveryFielder = BridgeDeliveryFielder.BRIDGE_DELIVERY_FIELDER

    private val mMatchKey = dimMatch.MATCH_KEY
    private val mPublicMatchId = dimMatch.PUBLIC_MATCH_ID
    private val mFileName = dimMatch.FILE_NAME
    private val mMatchType = dimMatch.MATCH_TYPE
    private val mEventName = dimMatch.EVENT_NAME
    private val mMatchDateText = dimMatch.MATCH_DATE_TEXT
    private val mSeason = dimMatch.SEASON
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
    private val iMatchKey = dimInnings.MATCH_KEY
    private val iBattingTeamKey = dimInnings.BATTING_TEAM_KEY
    private val iBowlingTeamKey = dimInnings.BOWLING_TEAM_KEY
    private val fdMatchKey = factDelivery.MATCH_KEY
    private val fdInningsKey = factDelivery.INNINGS_KEY
    private val fdDeliveryKey = factDelivery.DELIVERY_KEY
    private val fdSourceBallId = factDelivery.SOURCE_BALL_ID
    private val fdInningsOrder = factDelivery.INNINGS_ORDER
    private val fdOverNumber = factDelivery.OVER_NUMBER
    private val fdBallNumber = factDelivery.BALL_NUMBER
    private val fdBallInOver = factDelivery.BALL_IN_OVER
    private val fdBatterKey = factDelivery.BATTER_KEY
    private val fdNonStrikerKey = factDelivery.NON_STRIKER_KEY
    private val fdBowlerKey = factDelivery.BOWLER_KEY
    private val fdBatterRuns = factDelivery.BATTER_RUNS
    private val fdExtraRuns = factDelivery.EXTRA_RUNS
    private val fdTotalRuns = factDelivery.TOTAL_RUNS
    private val fdNoBalls = factDelivery.NO_BALLS
    private val fdWides = factDelivery.WIDES
    private val fdByes = factDelivery.BYES
    private val fdLegByes = factDelivery.LEG_BYES
    private val fdNonBoundary = factDelivery.NON_BOUNDARY
    private val fdPowerplay = factDelivery.POWERPLAY
    private val fdWicketCount = factDelivery.WICKET_COUNT
    private val pbPersonKey = dimPersonBatter.PERSON_KEY
    private val pbFullName = dimPersonBatter.FULL_NAME
    private val pnsPersonKey = dimPersonNonStriker.PERSON_KEY
    private val pnsFullName = dimPersonNonStriker.FULL_NAME
    private val pboPersonKey = dimPersonBowler.PERSON_KEY
    private val pboFullName = dimPersonBowler.FULL_NAME
    private val pfPersonKey = dimPersonFielder.PERSON_KEY
    private val pfFullName = dimPersonFielder.FULL_NAME
    private val ibTeamKey = dimInningsBattingTeam.TEAM_KEY
    private val ibTeamName = dimInningsBattingTeam.TEAM_NAME
    private val ioTeamKey = dimInningsBowlingTeam.TEAM_KEY
    private val ioTeamName = dimInningsBowlingTeam.TEAM_NAME
    private val dwDeliveryKey = bridgeDeliveryWicket.DELIVERY_KEY
    private val dwWicketKey = bridgeDeliveryWicket.WICKET_KEY
    private val dfDeliveryKey = bridgeDeliveryFielder.DELIVERY_KEY
    private val dfWicketKey = bridgeDeliveryFielder.WICKET_KEY
    private val dfPersonKey = bridgeDeliveryFielder.PERSON_KEY
    private val wWicketKey = dimWicket.WICKET_KEY
    private val wKind = dimWicket.WICKET_KIND

    suspend fun load(
        publicMatchId: PublicMatchId
    ): MatchScoresheetPage? =
        withMatchQuery(log, ioDispatcher, "Historical match scoresheet query") {
            val match = dsl.select(
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
                fmMargin
            )
                .from(dimMatch)
                .leftJoin(dimDate).on(mMatchStartDateKey.eq(dDateKey))
                .leftJoin(dimTeam1).on(mTeam1Key.eq(t1TeamKey))
                .leftJoin(dimTeam2).on(mTeam2Key.eq(t2TeamKey))
                .leftJoin(dimGround).on(mGroundKey.eq(gGroundKey))
                .leftJoin(factMatch).on(fmMatchKey.eq(mMatchKey))
                .where(mPublicMatchId.eq(publicMatchId.value))
                .fetchOne() ?: return@withMatchQuery null

            val matchKey = requireNotNull(match.get(mMatchKey))

            val team1Key = match.get(mTeam1Key)
            val team2Key = match.get(mTeam2Key)
            val winnerKey = match.get(mWinnerTeamKey)
            val winnerName = when (winnerKey) {
                team1Key -> match.get(t1TeamName)
                team2Key -> match.get(t2TeamName)
                else -> null
            }
            val context = MatchScoresheetContext(
                publicMatchId = PublicMatchId.from(requireNotNull(match.get(mPublicMatchId))),
                fileName = requireNotNull(match.get(mFileName)),
                matchType = match.get(mMatchType)?.takeIf { it.isNotBlank() }?.let(MatchType::from),
                season = match.get(mSeason)?.takeIf { it.isNotBlank() }?.let(Season::from),
                competition = match.get(mEventName)?.takeIf { it.isNotBlank() },
                date = MatchResponseMapper.formatDateText(match.get(mMatchDateText), match.get(dCalendarDate)),
                team1 = match.get(t1TeamName)?.takeIf { it.isNotBlank() },
                team2 = match.get(t2TeamName)?.takeIf { it.isNotBlank() },
                ground = match.get(gGroundName)?.takeIf { it.isNotBlank() },
                result = MatchResponseMapper.formatResult(winnerName, match.get(mVictoryType), match.get(fmMargin))
            )

            val inningsRows = dsl.select(iInningsKey, iInningsNumber, ibTeamName, ioTeamName)
                .from(dimInnings)
                .leftJoin(dimInningsBattingTeam).on(iBattingTeamKey.eq(ibTeamKey))
                .leftJoin(dimInningsBowlingTeam).on(iBowlingTeamKey.eq(ioTeamKey))
                .where(iMatchKey.eq(matchKey))
                .orderBy(iInningsNumber.asc(), iInningsKey.asc())
                .fetch()

            val totalDeliveries = dsl.selectCount()
                .from(factDelivery)
                .where(fdMatchKey.eq(matchKey))
                .fetchOne(count()) ?: 0
            val deliveryInnings = dsl.selectDistinct(fdInningsKey)
                .from(factDelivery)
                .where(fdMatchKey.eq(matchKey))
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
                .where(fdMatchKey.eq(matchKey))
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
            val (completeness, missingData) = MatchResponseMapper.scoresheetCompleteness(
                inningsEmpty = inningsRows.isEmpty(),
                totalDeliveries = totalDeliveries,
                missingInningsDeliveries = inningsRows.any { !deliveryInnings.contains(it.get(iInningsKey)) },
                teamsIncomplete = context.team1 == null || context.team2 == null
            )
            MatchScoresheetPage(
                context = context,
                completeness = completeness,
                missingData = missingData,
                innings = innings
            )
        
        }

}
