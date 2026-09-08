package com.knowledgespike.cricsheet.parse.database.adapter.mariadb

import com.knowledgespike.cricsheet.parse.database.adapter.SqlDialect
import java.nio.file.Path

class SqlScriptOutputAdapter(output: Path) :
    com.knowledgespike.cricsheet.parse.database.adapter.SqlScriptOutputAdapter(output, SqlDialect.MARIADB)