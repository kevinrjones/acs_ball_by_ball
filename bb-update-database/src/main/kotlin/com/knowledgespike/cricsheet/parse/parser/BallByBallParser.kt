package com.knowledgespike.cricsheet.parse.parser

import com.knowledgespike.cricsheet.parse.parser.structure.CricSheet
import kotlinx.serialization.json.Json
import java.io.File

class BallByBallParser {

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(file: File): CricSheet {

        val jsonData: String
        jsonData = file.readText()

        return buildCricSheet(jsonData)

    }

    fun buildCricSheet(data: String): CricSheet {
        val sheet: CricSheet
        return json.decodeFromString(data)
    }
}