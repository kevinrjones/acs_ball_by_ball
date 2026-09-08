package com.knowledgespike.ballbyball.parse.database.adapter.postgres

import com.knowledgespike.ballbyball.parse.database.adapter.JdbcOutputAdapter
import java.sql.Connection

class SqlOutputAdapter(connection: Connection) : JdbcOutputAdapter(connection) {
    init {
        connection.createStatement().use { statement ->
            statement.execute("set search_path to acs_ball_by_ball")
        }
    }

    override fun duplicateMatchPersonClause(): String =
        "on conflict (match_key, person_key, role_code) do nothing"

    override fun duplicateDeliveryFielderClause(): String =
        "on conflict (delivery_key, wicket_key, person_key) do nothing"
}