package com.knowledgespike.ballbyball.api.adapter.out.jooq

import com.knowledgespike.ballbyball.api.application.port.out.DatabaseHealth
import com.knowledgespike.ballbyball.api.application.port.out.MatchRepository
import com.knowledgespike.ballbyball.contracts.MatchSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class JooqMatchRepository(
    dataSource: DataSource,
    dialect: SQLDialect,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) : MatchRepository, DatabaseHealth {
    private val log = LoggerFactory.getLogger(JooqMatchRepository::class.java)
    private val dsl = DSL.using(dataSource, dialect)
    private val dimMatch = table(name("dim_match"))
    private val matchKey = field(name("match_key"), Long::class.javaObjectType)
    private val sourceMatchId = field(name("source_match_id"), Int::class.javaObjectType)
    private val fileName = field(name("file_name"), String::class.java)
    private val matchType = field(name("match_type"), String::class.java)
    private val season = field(name("season"), String::class.java)
    private val matchStartDateKey = field(name("match_start_date_key"), Int::class.javaObjectType)

    override suspend fun isHealthy(): Boolean = withContext(ioDispatcher) {
        try {
            dsl.selectOne().fetch()
            true
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            log.warn("Database health query failed", cause)
            false
        }
    }

    override suspend fun recentMatches(limit: Int): List<MatchSummary> = withContext(ioDispatcher) {
        try {
            dsl.select(matchKey, sourceMatchId, fileName, matchType, season)
                .from(dimMatch)
                .orderBy(matchStartDateKey.desc().nullsLast(), matchKey.desc())
                .limit(limit)
                .fetch { record ->
                    MatchSummary(
                        matchKey = requireNotNull(record.get(matchKey)) { "dim_match.match_key must not be null" },
                        sourceMatchId = requireNotNull(record.get(sourceMatchId)) {
                            "dim_match.source_match_id must not be null"
                        },
                        fileName = requireNotNull(record.get(fileName)) { "dim_match.file_name must not be null" },
                        matchType = requireNotNull(record.get(matchType)) { "dim_match.match_type must not be null" },
                        season = requireNotNull(record.get(season)) { "dim_match.season must not be null" }
                    )
                }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            log.error("Recent match query failed", cause)
            throw cause
        }
    }
}