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
            poolName = "bb-api-pool"
        }
    )

    val repository: MatchRepository = JooqMatchRepository(dataSource)

    override fun close() {
        dataSource.close()
    }
}