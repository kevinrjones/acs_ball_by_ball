package com.knowledgespike.ballbyball.parse.database.adapter.mariadb

import com.knowledgespike.ballbyball.parse.database.adapter.JdbcOutputAdapter
import java.sql.Connection

class SqlOutputAdapter(connection: Connection) : JdbcOutputAdapter(connection) {
    override fun duplicateMatchPersonClause(): String =
        "on duplicate key update id = id"

    override fun duplicateDeliveryFielderClause(): String =
        "on duplicate key update delivery_id = delivery_id"
}