package com.knowledgespike.cricketarchive.shared.auth

import com.knowledgespike.cricketarchive.shared.http.getHttpResponse
import com.knowledgespike.cricketarchive.shared.http.ProxyConfig
import com.knowledgespike.cricketarchive.shared.http.setProxy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.jsoup.Connection
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class LoginServiceIntegrationTest {

    @Test
    fun `given configured credentials when fetching login page then response is 200 and fields are usable`() {
        val credentials = requiredCredentialsOrSkip()
        val responseStatuses = mutableListOf<Int>()

        val loginService = LoginService(
            email = credentials.email,
            password = credentials.password,
            requestExecutor = { connection ->
                connection.execute().also { responseStatuses.add(it.statusCode()) }
            }
        )

        val fields = loginService.fetchLoginPage().getOrElse {
            fail("Expected login page fetch to succeed but failed with: ${it.message}")
        }

        assertEquals(200, responseStatuses.lastOrNull(), "Expected login page request to return HTTP 200")
        assertTrue(fields.csrf.isNotBlank(), "Expected csrf value to be present")
        assertTrue(fields.pfp.isNotBlank(), "Expected pfp value to be present")
        assertEquals(md5(fields.csrf), fields.pfp, "Expected pfp to match MD5(csrf)")
    }

    @Test
    fun `given configured credentials when logging in then verify is 200 and pigeon access cookie is set`() {
        val credentials = requiredCredentialsOrSkip()
        val responseStatuses = mutableListOf<Int>()

        val loginService = LoginService(
            email = credentials.email,
            password = credentials.password,
            requestExecutor = { connection ->
                connection.execute().also { responseStatuses.add(it.statusCode()) }
            }
        )

        val loginResult = loginService.login()
        assertTrue(loginResult is LoginResult.Success, "Expected login to succeed but got: $loginResult")

        val verificationResult = loginService.verifyLogin()
        assertTrue(
            verificationResult.isSuccess,
            "Expected verifyLogin to succeed but got: ${verificationResult.exceptionOrNull()?.message}"
        )
        assertEquals(200, responseStatuses.lastOrNull(), "Expected verification request to return HTTP 200")
        assertTrue(loginService.isLoggedIn(), "Expected pigeon_access cookie to be set after successful login")
    }

    @Test
    fun `given configured credentials when logging in then cricket archive page fetch succeeds with cricket content`() {
        val credentials = requiredCredentialsOrSkip()
        val loginService = LoginService(
            email = credentials.email,
            password = credentials.password
        )

        val loginResult = loginService.login()
        assertTrue(loginResult is LoginResult.Success, "Expected login to succeed but got: $loginResult")
        assertTrue(loginService.isLoggedIn(), "Expected pigeon_access cookie to be set before page fetch")

        val response = getHttpResponse("https://cricketarchive.com", Connection.Method.GET).getOrElse {
            fail("Expected authenticated cricket archive fetch to succeed but failed with: ${it.message}")
        } ?: fail("Expected HTTP response from cricketarchive.com")

        val body = response.body()
        assertEquals(200, response.statusCode(), "Expected cricket archive page request to return HTTP 200")
        assertTrue(body.contains("cricketarchive", ignoreCase = true), "Expected cricket archive content in page body")
        assertFalse(
            body.contains("access_method=iframe", ignoreCase = true),
            "Expected authenticated page body, not login iframe redirect content"
        )
        assertFalse(
            body.contains("error in your requested page", ignoreCase = true),
            "Expected successful page body, not CricketArchive error page"
        )
        assertTrue(loginService.isLoggedIn(), "Expected pigeon_access cookie to remain present after page fetch")
    }

    private fun requiredCredentialsOrSkip(): LoginCredentials {
        configureProxyFromEnvironment()
        val credentials = resolveCredentials(cliEmail = null, cliPassword = null)
        assumeTrue(
            credentials.isSuccess,
            "Set CRICKETARCHIVE_EMAIL and CRICKETARCHIVE_PASSWORD to run integration tests"
        )
        return credentials.getOrThrow()
    }

    private fun configureProxyFromEnvironment() {
        val proxyHost = System.getenv("CRICKETARCHIVE_PROXY_HOST")?.takeIf { it.isNotBlank() } ?: return
        val proxyPort = System.getenv("CRICKETARCHIVE_PROXY_PORT")?.toIntOrNull() ?: return
        val proxyUser = System.getenv("CRICKETARCHIVE_PROXY_USER")?.takeIf { it.isNotBlank() }
        val proxyPassword = System.getenv("CRICKETARCHIVE_PROXY_PASSWORD")?.takeIf { it.isNotBlank() }

        setProxy(ProxyConfig(proxyHost, proxyPort, proxyUser, proxyPassword))
    }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}