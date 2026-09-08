package com.knowledgespike.cricsheet.parse.database

import com.knowledgespike.cricsheet.parse.database.adapter.sqlite.SqlOutputAdapter
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
}