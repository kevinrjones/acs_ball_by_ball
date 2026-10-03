package com.knowledgespike.ballbyball.parse.parser

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.nio.file.Files

class PlayerRegistryParserTest {
    @Test
    fun `given quoted registry values when parsed then fields and cricket archive id are preserved`() {
        val file = Files.createTempFile("people", ".csv")
        Files.writeString(
            file,
            "identifier,name,unique_name,key_bcci,key_bcci_2,key_bigbash,key_cricbuzz,key_cricheroes," +
                "key_crichq,key_cricinfo,key_cricinfo_2,key_cricinfo_3,key_cricingif,key_cricketarchive\n" +
                "person-1,\"Doe, Jane\",jane,,,,,,,,,ci-3,,ca-14\n"
        )

        val person = PlayerRegistryParser().parse(file.toFile()).toList().single()

        expectThat(person.id).isEqualTo("person-1")
        expectThat(person.name).isEqualTo("Doe, Jane")
        expectThat(person.caId).isEqualTo("ca-14")
    }
}