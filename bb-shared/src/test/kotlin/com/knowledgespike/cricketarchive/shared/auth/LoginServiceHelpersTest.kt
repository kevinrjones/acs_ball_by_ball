package com.knowledgespike.cricketarchive.shared.auth

import kotlinx.serialization.json.Json
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotBeNull
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginServiceHelpersTest {

    @Test
    fun `given rendered login html with csrfHash when extracting fields then pfp and csrf are returned`() {
        val html = """
            <html>
              <head>
                <script>
                  var csrfHash = 'csrf-value';
                </script>
              </head>
              <body>
                <form>
                  <input type="hidden" name="pfp" value="pfp-value" />
                </form>
              </body>
            </html>
        """.trimIndent()

        val result = extractLoginPageFields(html)

        val fields = result.getOrThrow()
        fields.pfp shouldBeEqualTo "pfp-value"
        fields.csrf shouldBeEqualTo "csrf-value"
    }

    @Test
    fun `given downloaded login html when extracting fields then csrfHash is used and pfp is derived`() {
        val html = """
            <html>
              <head>
                <script>
                  var csrfHash = 'b8447a22caa3c3664e0991ac32a7c4c6';
                </script>
              </head>
              <body>
                <form>
                  <input name="email" />
                  <input name="password" />
                </form>
              </body>
            </html>
        """.trimIndent()

        val fields = extractLoginPageFields(html).getOrThrow()

        fields.csrf shouldBeEqualTo "b8447a22caa3c3664e0991ac32a7c4c6"
        fields.pfp shouldBeEqualTo derivePfpFromCsrf("b8447a22caa3c3664e0991ac32a7c4c6")
    }

    @Test
    fun `given login html missing csrf and csrfHash when extracting fields then failure is returned`() {
        val html = """
            <html>
              <body>
                <form>
                  <input type="hidden" name="pfp" value="pfp-value" />
                </form>
              </body>
            </html>
        """.trimIndent()

        val result = extractLoginPageFields(html)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        error.shouldNotBeNull()
        error.message.shouldNotBeNull().shouldContain("csrf")
    }

    @Test
    fun `given pigeon response json when extracting token then token is returned`() {
        val responseJson = """
            {
              "status": "logged in",
              "access_token": "token-value"
            }
        """.trimIndent()

        val result = extractPigeonAccessToken(responseJson)

        result.getOrThrow() shouldBeEqualTo "token-value"
    }

    @Test
    fun `given pigeon response json without token when extracting token then failure is returned`() {
        val responseJson = """
            {
              "status": "logged in"
            }
        """.trimIndent()

        val result = extractPigeonAccessToken(responseJson)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        error.shouldNotBeNull()
        error.message.shouldNotBeNull().shouldContain("access_token")
    }

    @Test
    fun `given html body when checking verification link then expected result is returned`() {
        assertTrue(containsCricketArchiveLink("<a href=\"https://cricketarchive.com\">Home</a>"))
        assertFalse(containsCricketArchiveLink("<a href=\"https://example.com\">Home</a>"))
    }

    @Test
    fun `given login payload values when building form body then values are url encoded`() {
        val body = buildLoginRequestBody(
            pfp = "pfp value",
            email = "user+test@example.com",
            password = "a+b c",
            csrf = "csrf/value"
        )

        body shouldContain "pfp=pfp+value"
        body shouldContain "email=user%2Btest%40example.com"
        body shouldContain "password=a%2Bb+c"
        body shouldContain "csrf=csrf%2Fvalue"
    }

    @Test
    fun `given session values when building pigeon request body then form encoded json matches chrome contract`() {
        val jsonPayload = buildPigeonServerRequestBody(
            sessionId = "112066114",
            sessionHash = "a7ad6566e5d404ca713daafedb2454c0"
        )
        val body = buildPigeonServerFormRequestBody(jsonPayload)
        val decodedPayload = URLDecoder.decode(body.removePrefix("json="), StandardCharsets.UTF_8)

        body shouldContain "json="

        val payload = Json.parseToJsonElement(decodedPayload).toString()

        payload shouldContain "\"pigeon_version\":\"2.3\""
        payload shouldContain "\"uri\":\"https://cricketarchive.com/\""
        payload shouldContain "\"referrer\":\"https://cricketarchive.com/\""
        payload shouldContain "\"server_gate\":1"
        payload shouldContain "\"session_id\":\"112066114\""
        payload shouldContain "\"session_hash\":\"a7ad6566e5d404ca713daafedb2454c0\""
        payload shouldContain "\"content_access\":false"
        payload shouldContain "\"content_id\":0"
        payload shouldContain "\"content_title\":\"\""
        payload shouldContain "\"content_date\":\"\""
        payload shouldContain "\"content_price\":0"
        payload shouldContain "\"content_value\":0"
        payload shouldContain "\"content_prompt\":0"
        payload shouldContain "\"redirect\":false"
    }
}