package com.knowledgespike.ballbyball.api.feature.matches.data.repository

import com.knowledgespike.ballbyball.api.config.DatabaseResources
import com.knowledgespike.ballbyball.api.config.DatabaseSettings
import com.knowledgespike.ballbyball.types.values.Limit
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotBlank
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull

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

    @Test
    fun `scoresheet maps selected match context innings and all ordered deliveries when available`(): Unit = runBlocking {
        val settings = DatabaseSettings(
            jdbcUrl = "jdbc:mariadb://localhost:3306/acs_ball_by_ball",
            user = "ballbyball",
            password = "p4ssw0rd",
            maximumPoolSize = 2
        )

        DatabaseResources(settings).use { resources ->
            assumeTrue(resources.databaseHealth.isHealthy(), "Database is not reachable on localhost:3306")

            val repository = resources.matchRepository
            val match = repository.recentMatches(Limit.from(10)).firstOrNull()
            assumeTrue(match != null, "No match is available in the warehouse")
            val scoresheet = repository.scoresheet(match!!.publicMatchId)

            expectThat(scoresheet).isNotNull()
            val mapped = requireNotNull(scoresheet)
            expectThat(mapped.context.publicMatchId).isEqualTo(match.publicMatchId)
            expectThat(mapped.innings.map { it.inningsNumber }).isEqualTo(
                mapped.innings.map { it.inningsNumber }.sorted()
            )
            val deliveries = mapped.innings.flatMap { it.deliveries }
            if (deliveries.isNotEmpty()) {
                expectThat(deliveries.map { it.deliveryKey }).isEqualTo(
                    deliveries.map { it.deliveryKey }.sorted()
                )
            }
        }
    }
}
