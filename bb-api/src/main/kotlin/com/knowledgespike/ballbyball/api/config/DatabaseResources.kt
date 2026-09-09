package com.knowledgespike.ballbyball.api.config

import com.knowledgespike.ballbyball.api.adapter.out.jooq.JooqMatchRepository
import com.knowledgespike.ballbyball.api.application.port.out.DatabaseHealth
import com.knowledgespike.ballbyball.api.application.port.out.MatchRepository
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
            poolName = "bb-api-pool"
        }
    )

    private val jooqRepository = JooqMatchRepository(dataSource, dialectFor(settings.jdbcUrl))

    val matchRepository: MatchRepository = jooqRepository
    val databaseHealth: DatabaseHealth = jooqRepository

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