package com.knowledgespike.ballbyball.api

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
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

    val repository: MatchRepository = JooqMatchRepository(dataSource, dialectFor(settings.jdbcUrl))

    override fun close() {
        dataSource.close()
    }
}

internal fun dialectFor(jdbcUrl: String) = when {
    jdbcUrl.startsWith("jdbc:mariadb:") -> org.jooq.SQLDialect.MARIADB
    jdbcUrl.startsWith("jdbc:mysql:") -> org.jooq.SQLDialect.MYSQL
    jdbcUrl.startsWith("jdbc:postgresql:") -> org.jooq.SQLDialect.POSTGRES
    jdbcUrl.startsWith("jdbc:sqlite:") -> org.jooq.SQLDialect.SQLITE
    else -> org.jooq.SQLDialect.DEFAULT
}