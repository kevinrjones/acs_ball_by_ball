package com.knowledgespike.ballbyball.parse.database

import com.knowledgespike.ballbyball.identity.CanonicalMatchId

import com.knowledgespike.ballbyball.parse.database.adapter.sqlite.SqlOutputAdapter
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.sql.DriverManager
import java.util.UUID

class SqliteOutputAdapterTest {
    @Test
    fun `given an existing canonical match when found then its surrogate key is returned`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table matches (
                        id integer primary key,
                        canonical_match_id varchar(36) not null unique
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    "insert into matches (id, canonical_match_id) values (7, '1890a7a8-f76d-5f36-89f7-39b0319044b0')"
                )
            }

            SqlOutputAdapter(connection).use { adapter ->
                val id = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0"))
                expectThat(adapter.findMatchKey(id)).isEqualTo(7L)
            }
        }
    }

    @Test
    fun `given sqlite connection when adapter opens then foreign keys are enabled`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            SqlOutputAdapter(connection).use { }

            connection.createStatement().use { statement ->
                statement.executeQuery("pragma foreign_keys").use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(1)
                }
            }
        }
    }

    @Test
    fun `given a failed match write when rolled back then no rows remain`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table delivery_fielders (
                        delivery_id integer not null,
                        wicket_id integer not null,
                        person_id integer not null,
                        primary key (delivery_id, wicket_id, person_id)
                    )
                    """.trimIndent()
                )
            }

            SqlOutputAdapter(connection).use { adapter ->
                adapter.beginMatch()
                adapter.insertDeliveryFielder(1, 2, 3)
                adapter.rollback()
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from delivery_fielders").use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(0)
                }
            }
        }
    }

    @Test
    fun `given repeated delivery fielder when written to sqlite then one row is stored`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table delivery_fielders (
                        delivery_id integer not null,
                        wicket_id integer not null,
                        person_id integer not null,
                        primary key (delivery_id, wicket_id, person_id)
                    )
                    """.trimIndent()
                )
            }

            SqlOutputAdapter(connection).use { adapter ->
                adapter.insertDeliveryFielder(1, 2, 3)
                adapter.insertDeliveryFielder(1, 2, 3)
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from delivery_fielders").use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(1)
                }
            }
        }
    }
}