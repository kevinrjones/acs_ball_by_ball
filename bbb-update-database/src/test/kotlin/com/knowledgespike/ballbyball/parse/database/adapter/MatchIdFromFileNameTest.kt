package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.clishared.identity.CanonicalMatchId
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.util.UUID

class MatchIdFromFileNameTest {
    @Test
    fun `given a canonical UUID when represented then filename conventions are irrelevant`() {
        val id = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0"))

        expectThat(id.value.toString()).isEqualTo("1890a7a8-f76d-5f36-89f7-39b0319044b0")
    }
}