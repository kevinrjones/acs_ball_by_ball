package com.knowledgespike.ballbyball.api.feature.health.data.repository

import com.knowledgespike.ballbyball.api.feature.health.domain.DatabaseHealth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class JooqDatabaseHealth(
    dataSource: DataSource,
    dialect: SQLDialect,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DatabaseHealth {
    private val log = LoggerFactory.getLogger(JooqDatabaseHealth::class.java)
    private val dsl = DSL.using(dataSource, dialect)

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
}
