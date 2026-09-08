package com.knowledgespike.cricsheet.parse.database.adapter.sqlite

import com.knowledgespike.cricsheet.parse.database.adapter.JdbcOutputAdapter
import java.sql.Connection

class SqlOutputAdapter(connection: Connection) : JdbcOutputAdapter(connection) {
    init {
        connection.createStatement().use { statement ->
            statement.execute("pragma foreign_keys = on")
        }
    }

    override fun duplicateMatchPersonClause(): String =
        "on conflict (match_key, person_key, role_code) do nothing"
}