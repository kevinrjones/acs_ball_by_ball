package com.knowledgespike.ballbyball.parse.parser

import com.knowledgespike.ballbyball.clishared.identity.CanonicalMatchEnvelope
import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.clishared.identity.MergeEvidence
import com.knowledgespike.ballbyball.identity.ProviderId
import com.knowledgespike.ballbyball.identity.Sha256Digest
import com.knowledgespike.ballbyball.identity.SourceRecordId
import com.knowledgespike.ballbyball.clishared.identity.SourceReference

import com.knowledgespike.ballbyball.clishared.schema.BbbMatchData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.nio.file.Files
import java.util.UUID

class BallByBallParserTest {
    @Test
    fun `given shared schema json when parsed then normalized match data is returned`() {
        val file = Files.createTempFile("shared-match", ".json")
        Files.writeString(file, Json.encodeToString(sampleEnvelope()))

        val parsed = BallByBallParser().parse(file.toFile())

        expectThat(parsed.match.match.matchType).isEqualTo("wtt")
        expectThat(parsed.match.match.event?.name).isEqualTo("The Hundred")
        expectThat(parsed.match.innings.first().overs?.first()?.deliveries?.first()?.nonStriker)
            .isEqualTo("Batter Two")
    }

    private fun sampleEnvelope(): CanonicalMatchEnvelope {
        val sourceRecordId = SourceRecordId.from(UUID.fromString("c61f62bd-7b02-5c3f-ae44-572d533b5877"))
        return CanonicalMatchEnvelope(
            canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0")),
            sources = listOf(
                SourceReference(
                    provider = ProviderId.from("cricsheet"),
                    providerRecordKey = "12345",
                    sourceRecordId = sourceRecordId,
                    rawContentDigest = Sha256Digest.from("0".repeat(64))
                )
            ),
            mergeEvidence = MergeEvidence.SingleSource(sourceRecordId),
            match = sampleMatchData()
        )
    }

    private fun sampleMatchData() = BbbMatchData(
        match = com.knowledgespike.ballbyball.clishared.schema.Info(
            ballsPerOver = 6,
            dates = listOf("2024-01-01"),
            event = com.knowledgespike.ballbyball.clishared.schema.Event("The Hundred"),
            gender = "female",
            matchType = "wtt",
            outcome = com.knowledgespike.ballbyball.clishared.schema.Outcome(result = "draw"),
            players = JsonObject(emptyMap()),
            registry = com.knowledgespike.ballbyball.clishared.schema.PlayersRegistry(emptyMap()),
            season = "2024",
            teamType = "international",
            teams = listOf("Team A", "Team B"),
            toss = com.knowledgespike.ballbyball.clishared.schema.Toss(decision = "bat", winner = "Team A")
        ),
        innings = listOf(
            com.knowledgespike.ballbyball.clishared.schema.Innings(
                team = "Team A",
                overs = listOf(
                    com.knowledgespike.ballbyball.clishared.schema.Over(
                        over = 0,
                        deliveries = listOf(
                            com.knowledgespike.ballbyball.clishared.schema.Delivery(
                                batter = "Batter One",
                                bowler = "Bowler One",
                                nonStriker = "Batter Two",
                                runs = com.knowledgespike.ballbyball.clishared.schema.Runs(1, 0, total = 1)
                            )
                        )
                    )
                )
            )
        )
    )
}