package com.knowledgespike.ballbyball.api.feature.matches.data.repository

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import java.time.LocalDate

class JooqMatchRepositoryTest {

    @Test
    fun `formatResult formats runs victory`() {
        val result = JooqMatchRepository.formatResult("India", "runs", 40)
        expectThat(result).isEqualTo("India won by 40 runs")

        val singleRun = JooqMatchRepository.formatResult("India", "runs", 1)
        expectThat(singleRun).isEqualTo("India won by 1 run")
    }

    @Test
    fun `formatResult formats wickets victory`() {
        val result = JooqMatchRepository.formatResult("England", "wickets", 6)
        expectThat(result).isEqualTo("England won by 6 wickets")

        val singleWicket = JooqMatchRepository.formatResult("England", "wickets", 1)
        expectThat(singleWicket).isEqualTo("England won by 1 wicket")
    }

    @Test
    fun `formatResult formats innings victory`() {
        val result = JooqMatchRepository.formatResult("Australia", "innings", 150)
        expectThat(result).isEqualTo("Australia won by an innings and 150 runs")
    }

    @Test
    fun `formatResult handles tie, draw, and no result`() {
        expectThat(JooqMatchRepository.formatResult(null, "tie", null)).isEqualTo("Match tied")
        expectThat(JooqMatchRepository.formatResult(null, "draw", null)).isEqualTo("Match drawn")
        expectThat(JooqMatchRepository.formatResult(null, "no result", null)).isEqualTo("No result")
        expectThat(JooqMatchRepository.formatResult(null, null, null)).isEqualTo("MISSING")
    }

    @Test
    fun `mapFormat maps match types to UI formats`() {
        expectThat(JooqMatchRepository.mapFormat("t")).isEqualTo("test")
        expectThat(JooqMatchRepository.mapFormat("tt")).isEqualTo("t20")
        expectThat(JooqMatchRepository.mapFormat("itt")).isEqualTo("t20i")
        expectThat(JooqMatchRepository.mapFormat("witt")).isEqualTo("women's t20i")
        expectThat(JooqMatchRepository.mapFormat("f")).isEqualTo("fc")
        expectThat(JooqMatchRepository.mapFormat("a")).isEqualTo("lista")
        expectThat(JooqMatchRepository.mapFormat("wa")).isEqualTo("women's lista")
        expectThat(JooqMatchRepository.mapFormat("odi")).isEqualTo("odi")
        expectThat(JooqMatchRepository.mapFormat("wodi")).isEqualTo("women's odi")
        expectThat(JooqMatchRepository.mapFormat("unknown")).isEqualTo("unknown")
        expectThat(JooqMatchRepository.mapFormat("")).isEqualTo("MISSING")
    }

    @Test
    fun `formatDateText formats dates nicely`() {
        val localDate = LocalDate.of(2026, 9, 10)
        expectThat(JooqMatchRepository.formatDateText(null, localDate)).isEqualTo("10 Sep 2026")

        expectThat(JooqMatchRepository.formatDateText("2026-09-01", null)).isEqualTo("1 Sep 2026")
        expectThat(JooqMatchRepository.formatDateText("multi-day-date", null)).isEqualTo("multi-day-date")
        expectThat(JooqMatchRepository.formatDateText(null, null)).isEqualTo("MISSING")
        expectThat(JooqMatchRepository.formatDateText("", null)).isEqualTo("MISSING")
    }

    @Test
    fun `calculateTeamScore handles empty innings list`() {
        val (score, overs) = JooqMatchRepository.calculateTeamScore(emptyList(), 6)
        expectThat(score).isEqualTo("MISSING")
        expectThat(overs).isNull()
    }
}
