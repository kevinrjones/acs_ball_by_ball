package com.knowledgespike.ballbyball.parse.models

import com.knowledgespike.ballbyball.parse.parser.structure.CricSheet

data class CardDirectoryData(val directoryName: String, val name: String,  val matchType: String, val mixedGender: Boolean = false)

fun cardDirectoryDataForMatch(cricSheet: CricSheet): CardDirectoryData = CardDirectoryData(
    directoryName = "",
    name = cricSheet.info.event?.name ?: "Unknown",
    matchType = warehouseMatchType(cricSheet.info.matchType, cricSheet.info.gender),
    mixedGender = true
)

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
