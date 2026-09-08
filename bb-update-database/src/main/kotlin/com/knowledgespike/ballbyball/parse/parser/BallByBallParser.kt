package com.knowledgespike.ballbyball.parse.parser

import com.knowledgespike.ballbyball.parse.parser.structure.CricSheet
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
        return json.decodeFromString(data)
    }
}