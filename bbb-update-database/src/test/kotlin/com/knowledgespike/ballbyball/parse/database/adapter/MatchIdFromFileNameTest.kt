package com.knowledgespike.ballbyball.parse.database.adapter

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class MatchIdFromFileNameTest {
    @Test
    fun `given a filename with a prefix and suffix when parsed then only its numeric value is returned`() {
        expectThat(matchIdFromFileName("/input/wi_201706_revised.json")).isEqualTo(201706)
    }
}