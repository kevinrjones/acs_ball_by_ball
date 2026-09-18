package com.knowledgespike.ballbyball.api

import com.knowledgespike.ballbyball.api.config.DatabaseResources
import com.knowledgespike.ballbyball.api.config.DatabaseSettings
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isFalse

class DatabaseResourcesTest {
    @Test
    fun `database resources start when the database is unavailable`(): Unit = runBlocking {
        DatabaseResources(
            DatabaseSettings(
                jdbcUrl = "jdbc:mariadb://127.0.0.1:1/unavailable",
                user = "unavailable",
                password = "unavailable",
                maximumPoolSize = 1
            )
        ).use { resources ->
            expectThat(resources.databaseHealth.isHealthy()).isFalse()
        }
    }
}