package com.knowledgespike.ballbyball.api.feature.matches.data.repository

import com.knowledgespike.ballbyball.api.config.DatabaseResources
import com.knowledgespike.ballbyball.api.config.DatabaseSettings
import com.knowledgespike.ballbyball.types.values.Limit
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isNotBlank
import strikt.assertions.isNotEmpty

class JooqMatchRepositoryIntegrationTest {

    @Test
    fun `recentMatches fetches matches from live database when available`(): Unit = runBlocking {
        val settings = DatabaseSettings(
            jdbcUrl = "jdbc:mariadb://localhost:3306/acs_ball_by_ball",
            user = "ballbyball",
            password = "p4ssw0rd",
            maximumPoolSize = 2
        )

        DatabaseResources(settings).use { resources ->
            assumeTrue(resources.databaseHealth.isHealthy(), "Database is not reachable on localhost:3306")

            val repository = resources.matchRepository
            val matches = repository.recentMatches(Limit.from(10))

            expectThat(matches).isNotEmpty()
            val first = matches.first()
            expectThat(first.competition).isNotBlank()
            expectThat(first.team1).isNotBlank()
            expectThat(first.team2).isNotBlank()
            expectThat(first.score1).isNotBlank()
            expectThat(first.score2).isNotBlank()
        }
    }
}
