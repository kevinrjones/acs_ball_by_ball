package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.clishared.identity.CanonicalMatchEnvelope
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEqualTo
import java.nio.file.Files

class CricSheetParserTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun `given an input directory without json files then parsing fails`() {
        val root = Files.createTempDirectory("parse-cricsheet-empty")
        Files.createDirectories(root.resolve("input"))

        val result = CricSheetParser().parse(root.resolve("input"), root.resolve("output"))

        expectThat(result.succeeded).isEqualTo(false)
        expectThat(result.failures.single().message).isEqualTo("No Cricsheet JSON files found")
    }

    @Test
    fun `given cricsheet input when parsed then output is mirrored with normalized names and match type`() {
        val root = Files.createTempDirectory("parse-cricsheet")
        val input = root.resolve("raw/archive")
        val output = root.resolve("normalized")
        Files.createDirectories(input)
        Files.writeString(input.resolve("12345.json"), sourceDocument())

        val result = CricSheetParser().parse(root.resolve("raw"), output)
        val normalized = Files.readString(output.resolve("archive/12345.json"))
        val envelope = json.decodeFromString<CanonicalMatchEnvelope>(normalized)

        expectThat(result.succeeded).isEqualTo(true)
        expectThat(normalized).contains("\"matchType\":\"wtt\"")
        expectThat(normalized).contains("\"match\"")
        expectThat(normalized.contains("\"meta\"")).isEqualTo(false)
        expectThat(normalized.contains("\"info\"")).isEqualTo(false)
        expectThat(normalized).contains("\"nonStriker\":\"Batter Two\"")
        expectThat(normalized).contains("\"cameIn\":\"Substitute\"")
        expectThat(normalized).contains("\"superSubs\":{\"Team A\":\"Substitute\"}")
        expectThat(envelope.sources.single().providerRecordKey).isEqualTo("12345")
        expectThat(envelope.sources.single().sourceRecordId.value.toString())
            .isEqualTo("c61f62bd-7b02-5c3f-ae44-572d533b5877")
    }

    @Test
    fun `given unsupported match type when parsed then failure is reported and other files continue`() {
        val root = Files.createTempDirectory("parse-cricsheet-failure")
        Files.createDirectories(root.resolve("input"))
        Files.writeString(root.resolve("input/100.json"), sourceDocument(matchType = "Unknown"))
        Files.writeString(root.resolve("input/101.json"), sourceDocument(matchType = "T20"))

        val result = CricSheetParser().parse(root.resolve("input"), root.resolve("output"))

        expectThat(result.failures.map { it.input.fileName.toString() }).isEqualTo(listOf("100.json"))
        expectThat(Files.exists(root.resolve("output/101.json"))).isEqualTo(true)
        expectThat(Files.exists(root.resolve("output/100.json"))).isEqualTo(false)
    }

    @Test
    fun `moving a source file between parent directories does not change identity`() {
        val first = parseSingle("first/archive", "12345.json", sourceDocument())
        val moved = parseSingle("moved/elsewhere", "12345.json", sourceDocument())

        expectThat(first.sources.single().sourceRecordId).isEqualTo(moved.sources.single().sourceRecordId)
        expectThat(first.sources.single().rawContentDigest).isEqualTo(moved.sources.single().rawContentDigest)
        expectThat(first.canonicalMatchId).isEqualTo(moved.canonicalMatchId)
    }

    @Test
    fun `correcting source bytes changes digest but preserves source and canonical identities`() {
        val original = parseSingle("original", "12345.json", sourceDocument())
        val corrected = parseSingle("corrected", "12345.json", sourceDocument().replace("The Hundred", "Corrected Event"))

        expectThat(original.sources.single().sourceRecordId).isEqualTo(corrected.sources.single().sourceRecordId)
        expectThat(original.canonicalMatchId).isEqualTo(corrected.canonicalMatchId)
        expectThat(original.sources.single().rawContentDigest).isNotEqualTo(corrected.sources.single().rawContentDigest)
    }

    @Test
    fun `current Cricsheet provider key with letters and underscore is accepted`() {
        val first = parseSingle("archive/first", "wi_201706.json", sourceDocument())
        val moved = parseSingle("archive/moved", "wi_201706.json", sourceDocument())

        expectThat(first.sources.single().providerRecordKey).isEqualTo("wi_201706")
        expectThat(first.sources.single().sourceRecordId).isEqualTo(moved.sources.single().sourceRecordId)
        expectThat(first.canonicalMatchId).isEqualTo(moved.canonicalMatchId)
    }

    @Test
    fun `unsafe Cricsheet provider key is rejected while valid files continue`() {
        val root = Files.createTempDirectory("parse-cricsheet-key")
        Files.createDirectories(root.resolve("input"))
        Files.writeString(root.resolve("input/not a provider key.json"), sourceDocument())
        Files.writeString(root.resolve("input/12345.json"), sourceDocument())

        val result = CricSheetParser().parse(root.resolve("input"), root.resolve("output"))

        expectThat(result.failures.map { it.input.fileName.toString() }).isEqualTo(listOf("not a provider key.json"))
        expectThat(Files.exists(root.resolve("output/12345.json"))).isEqualTo(true)
    }

    private fun parseSingle(parent: String, fileName: String, content: String): CanonicalMatchEnvelope {
        val root = Files.createTempDirectory("parse-cricsheet-identity")
        val input = root.resolve("input/$parent")
        Files.createDirectories(input)
        Files.writeString(input.resolve(fileName), content)

        val result = CricSheetParser().parse(root.resolve("input"), root.resolve("output"))

        expectThat(result.succeeded).isEqualTo(true)
        return json.decodeFromString(Files.readString(root.resolve("output/$parent/$fileName")))
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