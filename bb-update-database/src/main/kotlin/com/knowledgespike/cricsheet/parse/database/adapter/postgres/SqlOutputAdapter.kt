package com.knowledgespike.cricsheet.parse.database.adapter.postgres

import com.knowledgespike.cricsheet.parse.database.adapter.JdbcOutputAdapter
import java.sql.Connection

class SqlOutputAdapter(connection: Connection) : JdbcOutputAdapter(connection) {
    init {
        connection.createStatement().use { statement ->
            statement.execute("set search_path to cricsheet")
        }
    }

    override fun duplicateMatchPersonClause(): String =
        "on conflict (match_key, person_key, role_code) do nothing"
}