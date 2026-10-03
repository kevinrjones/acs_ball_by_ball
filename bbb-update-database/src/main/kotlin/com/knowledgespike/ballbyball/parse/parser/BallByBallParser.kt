package com.knowledgespike.ballbyball.parse.parser

import com.knowledgespike.ballbyball.clishared.identity.CanonicalMatchEnvelope
import com.knowledgespike.ballbyball.clishared.identity.CURRENT_ENVELOPE_VERSION
import kotlinx.serialization.json.Json
import java.io.File

class BallByBallParser {

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(file: File): CanonicalMatchEnvelope {

        val jsonData: String
        jsonData = file.readText()

        return buildCricSheet(jsonData)

    }

    fun buildCricSheet(data: String): CanonicalMatchEnvelope {
        val envelope = json.decodeFromString<CanonicalMatchEnvelope>(data)
        require(envelope.version == CURRENT_ENVELOPE_VERSION) {
            "Unsupported canonical match envelope version: ${envelope.version}"
        }
        return envelope
    }
}