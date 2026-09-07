package com.knowledgespike.cricketarchive.shared.auth

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoginServiceTest {

    @Test
    fun `given logout request failure when logout called then failure is handled gracefully`() {
        val service = loginServiceWithScript(
            script = listOf(Result.failure(IllegalStateException("logout failed")))
        )

        service.logout()
    }

    @Test
    fun `given login page response with pfp and csrf when fetching login page then fields are returned`() {
        val service = loginServiceWithScript(
            script = listOf(
                Result.success(
                    responseWith(
                        statusCode = 200,
                        body = """
                            <html>
                              <body>
                                <form>
                                  <input type="hidden" name="pfp" value="pfp-value" />
                                  <input type="hidden" name="csrf" value="csrf-value" />
                                </form>
                              </body>
                            </html>
                        """.trimIndent()
                    )
                )
            )
        )

        val fields = service.fetchLoginPage().getOrThrow()

        fields.pfp shouldBeEqualTo "pfp-value"
        fields.csrf shouldBeEqualTo "csrf-value"
    }

    @Test
    fun `given login page missing csrf and fallback script field when fetching login page then failure is returned`() {
        val service = loginServiceWithScript(
            script = listOf(
                Result.success(
                    responseWith(
                        statusCode = 200,
                        body = """
                            <html>
                              <body>
                                <form>
                                  <input type="hidden" name="pfp" value="pfp-value" />
                                </form>
                              </body>
                            </html>
                        """.trimIndent()
                    )
                )
            )
        )

        val result = service.fetchLoginPage()

        assertTrue(result.isFailure)
        result.exceptionOrNull()?.message.orEmpty() shouldContain "csrf"
    }

    @Test
    fun `given credential post succeeds when posting credentials then success is returned`() {
        val service = loginServiceWithScript(
            script = listOf(Result.success(responseWith(statusCode = 200, body = "ok")))
        )

        val result = service.postCredentials(pfp = "pfp", csrf = "csrf")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `given credential post indicates invalid credentials when posting credentials then failure is returned`() {
        val service = loginServiceWithScript(
            script = listOf(Result.success(responseWith(statusCode = 200, body = "invalid credentials")))
        )

        val result = service.postCredentials(pfp = "pfp", csrf = "csrf")

        assertTrue(result.isFailure)
        result.exceptionOrNull()?.message.orEmpty() shouldContain "rejected"
    }

    @Test
    fun `given credential post returns http error when posting credentials then failure is returned`() {
        val service = loginServiceWithScript(
            script = listOf(Result.success(responseWith(statusCode = 403, body = "forbidden")))
        )

        val result = service.postCredentials(pfp = "pfp", csrf = "csrf")

        assertTrue(result.isFailure)
        result.exceptionOrNull()?.message.orEmpty() shouldContain "Credential submission failed"
    }

    @Test
    fun `given verification page contains cricket archive link when verifying login then success is returned`() {
        val service = loginServiceWithScript(
            script = listOf(
                Result.success(
                    responseWith(
                        statusCode = 200,
                        body = "<a href=\"https://cricketarchive.com\">CricketArchive</a>"
                    )
                )
            )
        )

        val result = service.verifyLogin()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `given verification page does not contain cricket archive link when verifying login then failure is returned`() {
        val service = loginServiceWithScript(
            script = listOf(
                Result.success(responseWith(statusCode = 200, body = "<html>login page</html>"))
            )
        )

        val result = service.verifyLogin()

        assertTrue(result.isFailure)
        result.exceptionOrNull()?.message.orEmpty() shouldContain "verification failed"
    }

    @Test
    fun `given pigeon response has access token when posting pigeon request then token is returned`() {
        val cookieStore = mutableMapOf(
            "ea8ff5eedb059380fe4f6edfea62f912_id" to "session-id",
            "ea8ff5eedb059380fe4f6edfea62f912_hash" to "session-hash"
        )
        val service = loginServiceWithScript(
            script = listOf(
                Result.success(responseWith(statusCode = 200, body = "{\"access_token\":\"abc123\"}"))
            ),
            cookieStore = cookieStore
        )

        val result = service.postPigeonServer()

        result.getOrThrow() shouldBeEqualTo "abc123"
    }

    @Test
    fun `given pigeon response is malformed when posting pigeon request then failure is returned`() {
        val cookieStore = mutableMapOf(
            "ea8ff5eedb059380fe4f6edfea62f912_id" to "session-id",
            "ea8ff5eedb059380fe4f6edfea62f912_hash" to "session-hash"
        )
        val service = loginServiceWithScript(
            script = listOf(Result.success(responseWith(statusCode = 200, body = "not-json"))),
            cookieStore = cookieStore
        )

        val result = service.postPigeonServer()

        assertTrue(result.isFailure)
        result.exceptionOrNull()?.message.orEmpty() shouldContain "Unexpected JSON token"
    }

    @Test
    fun `given session cookies are missing when posting pigeon request then failure is returned`() {
        val service = loginServiceWithScript(script = emptyList())

        val result = service.postPigeonServer()

        assertTrue(result.isFailure)
        result.exceptionOrNull()?.message.orEmpty() shouldContain "Missing session cookie"
    }

    @Test
    fun `given all login steps succeed when logging in then success is returned and pigeon cookie is set`() {
        val cookieStore = mutableMapOf(
            "ea8ff5eedb059380fe4f6edfea62f912_id" to "session-id",
            "ea8ff5eedb059380fe4f6edfea62f912_hash" to "session-hash"
        )
        val service = loginServiceWithScript(
            script = listOf(
                Result.success(responseWith(statusCode = 200, body = "logged out")),
                Result.success(responseWith(statusCode = 200, body = loginPageHtml())),
                Result.success(responseWith(statusCode = 200, body = "credentials accepted")),
                Result.success(responseWith(statusCode = 200, body = "<a href=\"https://cricketarchive.com\">Home</a>")),
                Result.success(responseWith(statusCode = 200, body = "{\"access_token\":\"fresh-token\"}"))
            ),
            cookieStore = cookieStore
        )

        val result = service.login()

        result shouldBeEqualTo LoginResult.Success
        cookieStore["pigeon_access"] shouldBeEqualTo "fresh-token"
        assertTrue(service.isLoggedIn())
    }

    @Test
    fun `given login page request fails when logging in then descriptive fetch error is returned`() {
        val service = loginServiceWithScript(
            script = listOf(
                Result.success(responseWith(statusCode = 200, body = "logged out")),
                Result.success(responseWith(statusCode = 500, body = "server error"))
            )
        )

        val result = service.login()

        val error = assertIs<LoginResult.Error>(result)
        error.message shouldContain "Unable to fetch login page"
    }

    @Test
    fun `given credential submission fails when logging in then descriptive credential error is returned`() {
        val service = loginServiceWithScript(
            script = listOf(
                Result.success(responseWith(statusCode = 200, body = "logged out")),
                Result.success(responseWith(statusCode = 200, body = loginPageHtml())),
                Result.success(responseWith(statusCode = 200, body = "incorrect password"))
            )
        )

        val result = service.login()

        val error = assertIs<LoginResult.Error>(result)
        error.message shouldContain "Unable to post credentials"
    }

    @Test
    fun `given verification fails when logging in then descriptive verification error is returned`() {
        val service = loginServiceWithScript(
            script = listOf(
                Result.success(responseWith(statusCode = 200, body = "logged out")),
                Result.success(responseWith(statusCode = 200, body = loginPageHtml())),
                Result.success(responseWith(statusCode = 200, body = "credentials accepted")),
                Result.success(responseWith(statusCode = 200, body = "<html>still logged out</html>"))
            )
        )

        val result = service.login()

        val error = assertIs<LoginResult.Error>(result)
        error.message shouldContain "Unable to verify login"
    }

    @Test
    fun `given pigeon response is missing token when logging in then descriptive pigeon error is returned`() {
        val cookieStore = mutableMapOf(
            "ea8ff5eedb059380fe4f6edfea62f912_id" to "session-id",
            "ea8ff5eedb059380fe4f6edfea62f912_hash" to "session-hash"
        )
        val service = loginServiceWithScript(
            script = listOf(
                Result.success(responseWith(statusCode = 200, body = "logged out")),
                Result.success(responseWith(statusCode = 200, body = loginPageHtml())),
                Result.success(responseWith(statusCode = 200, body = "credentials accepted")),
                Result.success(responseWith(statusCode = 200, body = "<a href=\"https://cricketarchive.com\">Home</a>")),
                Result.success(responseWith(statusCode = 200, body = "{\"status\":\"ok\"}"))
            ),
            cookieStore = cookieStore
        )

        val result = service.login()

        val error = assertIs<LoginResult.Error>(result)
        error.message shouldContain "Unable to obtain pigeon access token"
    }

    private fun loginServiceWithScript(
        script: List<Result<Connection.Response>>,
        cookieStore: MutableMap<String, String> = mutableMapOf()
    ): LoginService {
        val queue = ArrayDeque(script)

        return LoginService(
            email = "user@example.com",
            password = "password",
            requestBuilder = { url, method -> Jsoup.connect(url).method(method) },
            getRequestBuilder = { url -> Jsoup.connect(url).method(Connection.Method.GET) },
            responseStorer = { response -> cookieStore.putAll(response.cookies()) },
            requestExecutor = {
                queue.removeFirstOrNull()?.getOrThrow()
                    ?: throw IllegalStateException("Unexpected HTTP request in test")
            },
            cookieStore = cookieStore
        )
    }

    private fun responseWith(
        statusCode: Int,
        body: String,
        cookies: Map<String, String> = emptyMap(),
        contentType: String = "text/html"
    ): Connection.Response {
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "statusCode" -> statusCode
                "body" -> body
                "cookies" -> cookies
                "contentType" -> contentType
                "toString" -> "Response(statusCode=$statusCode)"
                "hashCode" -> statusCode
                "equals" -> proxy === args?.firstOrNull()
                else -> throw UnsupportedOperationException("Method ${method.name} is not supported in this test stub")
            }
        }

        return Proxy.newProxyInstance(
            Connection.Response::class.java.classLoader,
            arrayOf(Connection.Response::class.java),
            handler
        ) as Connection.Response
    }

    private fun loginPageHtml(): String = """
        <html>
          <body>
            <form>
              <input type="hidden" name="pfp" value="pfp-value" />
              <input type="hidden" name="csrf" value="csrf-value" />
            </form>
          </body>
        </html>
    """.trimIndent()
}