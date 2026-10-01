package com.knowledgespike.ballbyball.parse.database

import com.knowledgespike.ballbyball.parse.database.adapter.sqlite.SqlOutputAdapter
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.sql.DriverManager

class SqliteOutputAdapterTest {
    @Test
    fun `given an existing match when found by a fully qualified path then its key is returned`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table dim_match (
                        id integer primary key,
                        file_name varchar(120) not null
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    "insert into dim_match (id, file_name) values (12345, '/old/root/12345.json')"
                )
            }

            SqlOutputAdapter(connection).use { adapter ->
                expectThat(adapter.findMatchKey("/new/root/12345.json")).isEqualTo(12345L)
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