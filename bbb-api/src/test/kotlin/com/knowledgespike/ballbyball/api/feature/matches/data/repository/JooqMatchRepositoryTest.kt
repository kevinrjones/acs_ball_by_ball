package com.knowledgespike.ballbyball.api.feature.matches.data.repository

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import java.time.LocalDate

class JooqMatchRepositoryTest {

    @Test
    fun `formatResult formats runs victory`() {
        val result = MatchResponseMapper.formatResult("India", "runs", 40)
        expectThat(result).isEqualTo("India won by 40 runs")

        val singleRun = MatchResponseMapper.formatResult("India", "runs", 1)
        expectThat(singleRun).isEqualTo("India won by 1 run")
    }

    @Test
    fun `formatResult formats wickets victory`() {
        val result = MatchResponseMapper.formatResult("England", "wickets", 6)
        expectThat(result).isEqualTo("England won by 6 wickets")

        val singleWicket = MatchResponseMapper.formatResult("England", "wickets", 1)
        expectThat(singleWicket).isEqualTo("England won by 1 wicket")
    }

    @Test
    fun `formatResult formats innings victory`() {
        val result = MatchResponseMapper.formatResult("Australia", "innings", 150)
        expectThat(result).isEqualTo("Australia won by an innings and 150 runs")
    }

    @Test
    fun `formatResult handles tie, draw, and no result`() {
        expectThat(MatchResponseMapper.formatResult(null, "tie", null)).isEqualTo("Match tied")
        expectThat(MatchResponseMapper.formatResult(null, "draw", null)).isEqualTo("Match drawn")
        expectThat(MatchResponseMapper.formatResult(null, "no result", null)).isEqualTo("No result")
        expectThat(MatchResponseMapper.formatResult(null, null, null)).isNull()
    }

    @Test
    fun `mapFormat maps match types to UI formats`() {
        expectThat(MatchResponseMapper.mapFormat("t")).isEqualTo("test")
        expectThat(MatchResponseMapper.mapFormat("tt")).isEqualTo("t20")
        expectThat(MatchResponseMapper.mapFormat("itt")).isEqualTo("t20i")
        expectThat(MatchResponseMapper.mapFormat("witt")).isEqualTo("women's t20i")
        expectThat(MatchResponseMapper.mapFormat("f")).isEqualTo("fc")
        expectThat(MatchResponseMapper.mapFormat("a")).isEqualTo("lista")
        expectThat(MatchResponseMapper.mapFormat("wa")).isEqualTo("women's lista")
        expectThat(MatchResponseMapper.mapFormat("odi")).isEqualTo("odi")
        expectThat(MatchResponseMapper.mapFormat("wodi")).isEqualTo("women's odi")
        expectThat(MatchResponseMapper.mapFormat("unknown")).isEqualTo("unknown")
        expectThat(MatchResponseMapper.mapFormat("")).isEqualTo("")
    }

    @Test
    fun `formatDateText formats dates nicely`() {
        val localDate = LocalDate.of(2026, 9, 10)
        expectThat(MatchResponseMapper.formatDateText(null, localDate)).isEqualTo("10 Sep 2026")

        expectThat(MatchResponseMapper.formatDateText("2026-09-01", null)).isEqualTo("1 Sep 2026")
        expectThat(MatchResponseMapper.formatDateText("multi-day-date", null)).isEqualTo("multi-day-date")
        expectThat(MatchResponseMapper.formatDateText(null, null)).isNull()
        expectThat(MatchResponseMapper.formatDateText("", null)).isNull()
    }

    @Test
    fun `calculateTeamScore handles empty innings list`() {
        val (score, overs) = MatchResponseMapper.calculateTeamScore(emptyList(), 6)
        expectThat(score).isNull()
        expectThat(overs).isNull()
    }
}
