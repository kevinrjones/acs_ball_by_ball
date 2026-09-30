package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.clishared.schema.BbbMatchData
import com.knowledgespike.ballbyball.parsecricsheet.source.CricSheet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

class CricSheetConverter(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }
) {
    fun convert(cricSheet: CricSheet): BbbMatchData {
        val source = json.encodeToJsonElement(CricSheet.serializer(), cricSheet).jsonObject
        val normalized = normalizeKeys(source).jsonObject.toMutableMap()
        val match = normalized.remove("info")?.jsonObject?.toMutableMap()
            ?: error("Cricsheet document is missing info")
        match["matchType"] = JsonPrimitive(warehouseMatchType(cricSheet.info.matchType, cricSheet.info.gender))
        normalized.remove("meta")
        normalized["match"] = JsonObject(match)
        return json.decodeFromJsonElement(BbbMatchData.serializer(), JsonObject(normalized))
    }

    private fun warehouseMatchType(sourceMatchType: String, gender: String): String {
        val matchType = when (sourceMatchType.lowercase()) {
            "test" -> "t"
            "t20" -> "tt"
            "it20" -> "itt"
            "odi" -> "o"
            "odm" -> "a"
            "mdm" -> "f"
            else -> throw IllegalArgumentException("Unknown match type $sourceMatchType")
        }
        return if (gender.equals("female", ignoreCase = true)) "w$matchType" else matchType
    }

    private fun normalizeKeys(element: JsonElement, renameReplacementKeys: Boolean = false): JsonElement =
        when (element) {
            is JsonObject -> buildJsonObject {
                element.forEach { (key, value) ->
                    val normalizedKey = when {
                        renameReplacementKeys && key == "in" -> "cameIn"
                        renameReplacementKeys && key == "out" -> "wentOut"
                        else -> SOURCE_KEYS[key] ?: key
                    }
                    put(
                        normalizedKey,
                        normalizeKeys(value, key == "match" || key == "role")
                    )
                }
            }

            is JsonArray -> JsonArray(element.map { normalizeKeys(it, renameReplacementKeys) })

            else -> element
        }

    private companion object {
        val SOURCE_KEYS = mapOf(
            "data_version" to "dataVersion",
            "balls_per_over" to "ballsPerOver",
            "bowl_out" to "bowlOut",
            "match_number" to "matchNumber",
            "match_type" to "matchType",
            "match_type_number" to "matchTypeNumber",
            "player_of_match" to "playerOfMatch",
            "super_subs" to "superSubs",
            "supersubs" to "superSubs",
            "team_type" to "teamType",
            "match_referees" to "matchReferees",
            "reserve_umpires" to "reserveUmpires",
            "tv_umpires" to "tvUmpires",
            "absent_hurt" to "absentHurt",
            "penalty_runs" to "penaltyRuns",
            "miscounted_overs" to "miscountedOvers",
            "super_over" to "superOver",
            "non_striker" to "nonStriker",
            "umpires_call" to "umpiresCall",
            "non_boundary" to "nonBoundary",
            "player_out" to "playerOut"
        )
    }
}