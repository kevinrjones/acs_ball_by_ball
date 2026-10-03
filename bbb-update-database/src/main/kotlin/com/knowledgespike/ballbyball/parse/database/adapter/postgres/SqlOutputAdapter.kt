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
        "on conflict (match_id, person_id, role_code) do nothing"

    override fun duplicateDeliveryFielderClause(): String =
        "on conflict (delivery_id, wicket_id, person_id) do nothing"
}