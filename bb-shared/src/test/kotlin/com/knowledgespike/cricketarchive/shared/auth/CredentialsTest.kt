package com.knowledgespike.cricketarchive.shared.auth

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotBeNull
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class CredentialsTest {

    @BeforeTest
    fun clearCredentials() {
        clearConfiguredLoginCredentials()
    }

    @Test
    fun `given cli credentials when resolved then cli credentials are used`() {
        val result = resolveCredentials(
            cliEmail = "cli@example.com",
            cliPassword = "cli-password",
            environmentProvider = {
                mapOf(
                    "CRICKETARCHIVE_EMAIL" to "env@example.com",
                    "CRICKETARCHIVE_PASSWORD" to "env-password"
                )
            }
        )

        val credentials = result.getOrThrow()
        credentials.email shouldBeEqualTo "cli@example.com"
        credentials.password shouldBeEqualTo "cli-password"
    }

    @Test
    fun `given no cli credentials when resolved then environment credentials are used`() {
        val result = resolveCredentials(
            cliEmail = null,
            cliPassword = null,
            environmentProvider = {
                mapOf(
                    "CRICKETARCHIVE_EMAIL" to "env@example.com",
                    "CRICKETARCHIVE_PASSWORD" to "env-password"
                )
            }
        )

        val credentials = result.getOrThrow()
        credentials.email shouldBeEqualTo "env@example.com"
        credentials.password shouldBeEqualTo "env-password"
    }

    @Test
    fun `given partial cli credentials when resolved then failure is returned`() {
        val result = resolveCredentials(
            cliEmail = "cli@example.com",
            cliPassword = null,
            environmentProvider = { emptyMap() }
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        error.shouldNotBeNull()
        error.message.shouldNotBeNull().shouldContain("Both --email and --password")
    }

    @Test
    fun `given no available credentials when resolved then failure is returned`() {
        val result = resolveCredentials(
            cliEmail = null,
            cliPassword = null,
            environmentProvider = { emptyMap() }
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        error.shouldNotBeNull()
        error.message.shouldNotBeNull().shouldContain("CricketArchive credentials are not configured")
    }

    @Test
    fun `given configured credentials when resolving stored credentials then configured values are returned`() {
        configureLoginCredentials(
            email = "configured@example.com",
            password = "configured-password"
        )

        val result = resolveConfiguredOrEnvironmentCredentials {
            mapOf(
                "CRICKETARCHIVE_EMAIL" to "env@example.com",
                "CRICKETARCHIVE_PASSWORD" to "env-password"
            )
        }

        val credentials = result.getOrThrow()
        credentials.email shouldBeEqualTo "configured@example.com"
        credentials.password shouldBeEqualTo "configured-password"
    }
}