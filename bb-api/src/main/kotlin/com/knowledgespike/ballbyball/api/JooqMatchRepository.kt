package com.knowledgespike.ballbyball.api

import com.knowledgespike.ballbyball.contracts.MatchSummary
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class JooqMatchRepository(dataSource: DataSource) : MatchRepository {
    private val log = LoggerFactory.getLogger(JooqMatchRepository::class.java)
    private val dsl = DSL.using(dataSource, SQLDialect.DEFAULT)
    private val dimMatch = table(name("dim_match"))
    private val matchKey = field(name("match_key"), Long::class.javaObjectType)
    private val sourceMatchId = field(name("source_match_id"), Int::class.javaObjectType)
    private val fileName = field(name("file_name"), String::class.java)
    private val matchType = field(name("match_type"), String::class.java)
    private val season = field(name("season"), String::class.java)

    override fun isHealthy(): Boolean = runCatching {
        dsl.selectOne().fetch()
        true
    }.onFailure { exception ->
        log.warn("Database health query failed", exception)
    }.getOrDefault(false)

    override fun recentMatches(limit: Int): List<MatchSummary> = dsl
        .select(matchKey, sourceMatchId, fileName, matchType, season)
        .from(dimMatch)
        .orderBy(matchKey.desc())
        .limit(limit)
        .fetch { record ->
            MatchSummary(
                matchKey = record.get(matchKey)!!,
                sourceMatchId = record.get(sourceMatchId)!!,
                fileName = record.get(fileName)!!,
                matchType = record.get(matchType)!!,
                season = record.get(season)!!
            )
        }
}