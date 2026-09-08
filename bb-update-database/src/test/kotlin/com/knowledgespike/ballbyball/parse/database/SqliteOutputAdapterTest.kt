package com.knowledgespike.ballbyball.parse.database

import com.knowledgespike.ballbyball.parse.database.adapter.sqlite.SqlOutputAdapter
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.sql.DriverManager

class SqliteOutputAdapterTest {
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
    fun `given repeated delivery fielder when written to sqlite then one row is stored`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table bridge_delivery_fielder (
                        delivery_key integer not null,
                        wicket_key integer not null,
                        person_key integer not null,
                        primary key (delivery_key, wicket_key, person_key)
                    )
                    """.trimIndent()
                )
            }

            SqlOutputAdapter(connection).use { adapter ->
                adapter.insertDeliveryFielder(1, 2, 3)
                adapter.insertDeliveryFielder(1, 2, 3)
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from bridge_delivery_fielder").use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(1)
                }
            }
        }
    }
}