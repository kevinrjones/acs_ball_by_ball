package com.knowledgespike.ballbyball.parse.parser

import com.knowledgespike.ballbyball.clishared.schema.BbbMatchData
import kotlinx.serialization.json.Json
import java.io.File

class BallByBallParser {

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(file: File): BbbMatchData {

        val jsonData: String
        jsonData = file.readText()

        return buildCricSheet(jsonData)

    }

    fun buildCricSheet(data: String): BbbMatchData {
        return json.decodeFromString(data)
    }
}