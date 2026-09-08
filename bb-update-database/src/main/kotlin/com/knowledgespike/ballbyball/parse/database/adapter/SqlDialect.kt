package com.knowledgespike.ballbyball.parse.database.adapter

enum class SqlDialect(
    val duplicateMatchPersonClause: String,
    val booleanLiteral: (Boolean) -> String,
    val transactionStart: String
) {
    MARIADB(
        duplicateMatchPersonClause = "ON DUPLICATE KEY UPDATE match_person_key = match_person_key",
        booleanLiteral = { if (it) "1" else "0" },
        transactionStart = "START TRANSACTION;"
    ),
    POSTGRES(
        duplicateMatchPersonClause = "ON CONFLICT (match_key, person_key, role_code) DO NOTHING",
        booleanLiteral = { if (it) "TRUE" else "FALSE" },
        transactionStart = "START TRANSACTION;"
    ),
    SQLITE(
        duplicateMatchPersonClause = "ON CONFLICT (match_key, person_key, role_code) DO NOTHING",
        booleanLiteral = { if (it) "1" else "0" },
        transactionStart = "BEGIN TRANSACTION;"
    )
}