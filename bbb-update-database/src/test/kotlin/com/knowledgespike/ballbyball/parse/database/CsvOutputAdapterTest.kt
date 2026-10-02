package com.knowledgespike.ballbyball.parse.database

import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.identity.ProviderId
import com.knowledgespike.ballbyball.identity.Sha256Digest
import com.knowledgespike.ballbyball.identity.SourceRecordId
import com.knowledgespike.ballbyball.clishared.identity.SourceReference

import com.knowledgespike.ballbyball.parse.database.adapter.csv.CsvOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.MatchRecord
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import java.nio.file.Files
import java.time.LocalDate
import java.util.UUID

class CsvOutputAdapterTest {
    @Test
    fun `given repeated person when written then one escaped csv row is emitted`() {
        val output = Files.createTempDirectory("warehouse-csv")
        val adapter = CsvOutputAdapter(output)

        adapter.upsertPerson("person-1", "O'Brien, Jr.", 7)
        adapter.upsertPerson("person-1", "O'Brien, Jr.", 7)
        adapter.insertDeliveryFielder(1, 2, 3)
        adapter.close()

        val csv = Files.readString(output.resolve("dim_person.csv"))
        expectThat(csv).contains("source_person_id,full_name,sort_name_part,other_name_part,ca_id")
        expectThat(csv.lines().count { it.startsWith("1,person-1,") }).isEqualTo(1)
        expectThat(csv).contains("\"O'Brien, Jr.\"")
        expectThat(Files.readString(output.resolve("bridge_delivery_fielder.csv")))
            .isEqualTo("delivery_key,wicket_key,person_key\n1,2,3\n")
    }

    @Test
    fun `given warehouse rows when written then csv preserves nulls and foreign keys`() {
        val output = Files.createTempDirectory("warehouse-csv")
        val sourceFile = output.resolve("12345.json").toAbsolutePath().normalize()
        val adapter = CsvOutputAdapter(output)
        val home = adapter.upsertTeam("Home")
        val away = adapter.upsertTeam("Away")
        val ground = adapter.upsertGround("The Oval")
        val dateKey = adapter.upsertDate(LocalDate.parse("2024-01-02"))
        val match = adapter.insertMatch(
            MatchRecord(
                canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0")),
                fileName = sourceFile.toString(),
                matchInSeries = 1,
                matchType = "tt",
                eventName = "Series",
                matchDateText = "2024-01-02",
                season = "2024",
                matchStartYear = "2024",
                matchStartDateKey = dateKey,
                ballsPerOver = 6,
                team1Key = home.id,
                team2Key = away.id,
                groundKey = ground.id,
                tossTeamKey = home.id,
                tossDecision = null,
                victoryType = "runs",
                winnerTeamKey = home.id,
                loserTeamKey = away.id
            )
        )
        val duplicate = adapter.insertMatch(
            MatchRecord(
                canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0")),
                fileName = "renamed.json",
                matchInSeries = 1,
                matchType = "tt",
                eventName = "Series",
                matchDateText = "2024-01-02",
                season = "2024",
                matchStartYear = "2024",
                matchStartDateKey = dateKey,
                ballsPerOver = 6,
                team1Key = home.id,
                team2Key = away.id,
                groundKey = ground.id,
                tossTeamKey = home.id,
                tossDecision = null,
                victoryType = "runs",
                winnerTeamKey = home.id,
                loserTeamKey = away.id
            )
        )
        adapter.insertSourceReferences(
            match.key,
            listOf(
                SourceReference(
                    provider = ProviderId.from("cricsheet"),
                    providerRecordKey = "12345",
                    sourceRecordId = SourceRecordId.from(UUID.fromString("c61f62bd-7b02-5c3f-ae44-572d533b5877")),
                    rawContentDigest = Sha256Digest.from("0".repeat(64))
                )
            )
        )
        adapter.insertMatchFact(match.key, dateKey, ground.id, 1, 10)
        adapter.close()

        expectThat(duplicate.key).isEqualTo(match.key)

        val matchCsv = Files.readString(output.resolve("dim_match.csv"))
        expectThat(matchCsv).contains("match_key,canonical_match_id,public_match_id,source_ca_id,file_name")
        expectThat(matchCsv).contains("1,1890a7a8-f76d-5f36-89f7-39b0319044b0,7922450146,\\N,${sourceFile.toString()}")
        expectThat(Files.readString(output.resolve("fact_match.csv")))
            .contains("match_key,match_date_key,ground_key,duration_days,margin,match_count\n1,20240102,1,1,10,1")
        expectThat(Files.readString(output.resolve("match_source_reference.csv")))
            .contains("1,cricsheet,12345,c61f62bd-7b02-5c3f-ae44-572d533b5877,${"0".repeat(64)}")
    }
}