package com.knowledgespike.ballbyball.api.config

import com.knowledgespike.ballbyball.api.feature.health.data.repository.JooqDatabaseHealth
import com.knowledgespike.ballbyball.api.feature.health.domain.DatabaseHealth
import com.knowledgespike.ballbyball.api.feature.matches.data.repository.JooqMatchRepository
import com.knowledgespike.ballbyball.api.feature.matches.domain.repository.MatchRepository
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jooq.SQLDialect
import java.io.Closeable

class DatabaseResources(settings: DatabaseSettings) : Closeable {
    private val dataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = settings.jdbcUrl
            username = settings.user
            password = settings.password
            maximumPoolSize = settings.maximumPoolSize
            initializationFailTimeout = -1L
            connectionTimeout = 1_000L
            poolName = "bbb-api-pool"
        }
    )

    private val dialect = dialectFor(settings.jdbcUrl)

    val matchRepository: MatchRepository = JooqMatchRepository(dataSource, dialect)
    val databaseHealth: DatabaseHealth = JooqDatabaseHealth(dataSource, dialect)

    override fun close() {
        dataSource.close()
    }
}

internal fun dialectFor(jdbcUrl: String): SQLDialect = when {
    jdbcUrl.startsWith("jdbc:mariadb:") -> SQLDialect.MARIADB
    jdbcUrl.startsWith("jdbc:mysql:") -> SQLDialect.MYSQL
    jdbcUrl.startsWith("jdbc:postgresql:") -> SQLDialect.POSTGRES
    jdbcUrl.startsWith("jdbc:sqlite:") -> SQLDialect.SQLITE
    else -> SQLDialect.DEFAULT
}