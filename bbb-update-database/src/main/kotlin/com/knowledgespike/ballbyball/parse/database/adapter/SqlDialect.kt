package com.knowledgespike.ballbyball.parse.database.adapter

enum class SqlDialect(
    val duplicateMatchPersonClause: String,
    val duplicateDeliveryFielderClause: String,
    val booleanLiteral: (Boolean) -> String,
    val transactionStart: String
) {
    MARIADB(
        duplicateMatchPersonClause = "ON DUPLICATE KEY UPDATE id = id",
        duplicateDeliveryFielderClause = "ON DUPLICATE KEY UPDATE delivery_id = delivery_id",
        booleanLiteral = { if (it) "1" else "0" },
        transactionStart = "START TRANSACTION;"
    ),
    POSTGRES(
        duplicateMatchPersonClause = "ON CONFLICT (match_id, person_id, role_code) DO NOTHING",
        duplicateDeliveryFielderClause = "ON CONFLICT (delivery_id, wicket_id, person_id) DO NOTHING",
        booleanLiteral = { if (it) "TRUE" else "FALSE" },
        transactionStart = "START TRANSACTION;"
    ),
    SQLITE(
        duplicateMatchPersonClause = "ON CONFLICT (match_id, person_id, role_code) DO NOTHING",
        duplicateDeliveryFielderClause = "ON CONFLICT (delivery_id, wicket_id, person_id) DO NOTHING",
        booleanLiteral = { if (it) "1" else "0" },
        transactionStart = "BEGIN TRANSACTION;"
    )
}