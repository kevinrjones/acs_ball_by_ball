package com.knowledgespike.ballbyball.parsecricsheet

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import java.nio.file.Files

class CricSheetParserTest {
    @Test
    fun `given cricsheet input when parsed then output is mirrored with normalized names and match type`() {
        val root = Files.createTempDirectory("parse-cricsheet")
        val input = root.resolve("raw/archive")
        val output = root.resolve("normalized")
        Files.createDirectories(input)
        Files.writeString(input.resolve("match.json"), sourceDocument())

        val result = CricSheetParser().parse(root.resolve("raw"), output)
        val normalized = Files.readString(output.resolve("archive/match.json"))

        expectThat(result.succeeded).isEqualTo(true)
        expectThat(normalized).contains("\"matchType\":\"wtt\"")
        expectThat(normalized).contains("\"match\"")
        expectThat(normalized.contains("\"meta\"")).isEqualTo(false)
        expectThat(normalized.contains("\"info\"")).isEqualTo(false)
        expectThat(normalized).contains("\"nonStriker\":\"Batter Two\"")
        expectThat(normalized).contains("\"cameIn\":\"Substitute\"")
        expectThat(normalized).contains("\"superSubs\":{\"Team A\":\"Substitute\"}")
    }

    @Test
    fun `given unsupported match type when parsed then failure is reported and other files continue`() {
        val root = Files.createTempDirectory("parse-cricsheet-failure")
        Files.createDirectories(root.resolve("input"))
        Files.writeString(root.resolve("input/bad.json"), sourceDocument(matchType = "Unknown"))
        Files.writeString(root.resolve("input/good.json"), sourceDocument(matchType = "T20"))

        val result = CricSheetParser().parse(root.resolve("input"), root.resolve("output"))

        expectThat(result.failures.map { it.input.fileName.toString() }).isEqualTo(listOf("bad.json"))
        expectThat(Files.exists(root.resolve("output/good.json"))).isEqualTo(true)
        expectThat(Files.exists(root.resolve("output/bad.json"))).isEqualTo(false)
    }

    private fun sourceDocument(matchType: String = "T20") = """
        {
          "meta": {"data_version": "1.0", "created": "2024-01-01", "revision": 1},
          "info": {
            "balls_per_over": 6,
            "dates": ["2024-01-01"],
            "event": {"name": "The Hundred", "match_number": 1},
            "gender": "female",
            "match_type": "$matchType",
            "outcome": {"result": "draw"},
            "players": {"Team A": ["Batter One", "Batter Two"]},
            "registry": {"people": {"Batter One": "person-1"}},
            "season": "2024",
            "supersubs": {"Team A": "Substitute"},
            "team_type": "international",
            "teams": ["Team A", "Team B"],
            "toss": {"decision": "bat", "winner": "Team A"}
          },
          "innings": [{
            "team": "Team A",
            "overs": [{
              "over": 0,
              "deliveries": [{
                "batter": "Batter One",
                "bowler": "Bowler One",
                "non_striker": "Batter Two",
                "replacements": {"match": [{"in": "Substitute", "out": "Batter One", "reason": "tactical", "team": "Team A"}]},
                "runs": {"batter": 1, "extras": 0, "total": 1}
              }]
            }]
          }]
        }
    """.trimIndent()
}