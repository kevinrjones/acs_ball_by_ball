package com.knowledgespike.ballbyball.parse.database.adapter.mariadb

import com.knowledgespike.ballbyball.parse.database.adapter.SqlDialect
import java.nio.file.Path

class SqlScriptOutputAdapter(output: Path) :
    com.knowledgespike.ballbyball.parse.database.adapter.SqlScriptOutputAdapter(output, SqlDialect.MARIADB)