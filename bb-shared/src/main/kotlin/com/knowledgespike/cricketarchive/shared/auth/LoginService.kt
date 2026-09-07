package com.knowledgespike.cricketarchive.shared.auth

import com.knowledgespike.cricketarchive.LoggerDelegate
import com.knowledgespike.cricketarchive.shared.http.buildHttpGetRequest
import com.knowledgespike.cricketarchive.shared.http.buildHttpRequest
import com.knowledgespike.cricketarchive.shared.http.cookieJar
import com.knowledgespike.cricketarchive.shared.http.storeResponseCookies
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private const val LOGOUT_URL = "https://my.cricketarchive.com/action/public/vo/logout"
private const val LOGIN_PAGE_URL = "https://my.cricketarchive.com/?access_method=iframe&"
private const val LOGIN_POST_URL = "https://my.cricketarchive.com/action/public/vo/login-limited"
private const val PIGEON_SERVER_URL = "https://my.cricketarchive.com/action/public/vo/pigeon-server"
private const val LOGIN_SITE_URL = "https://my.cricketarchive.com"
private const val RESPONSE_PREVIEW_LIMIT = 600

private const val SESSION_ID_COOKIE = "ea8ff5eedb059380fe4f6edfea62f912_id"
private const val SESSION_HASH_COOKIE = "ea8ff5eedb059380fe4f6edfea62f912_hash"
private const val PIGEON_ACCESS_COOKIE = "pigeon_access"

private val csrfHashRegex = Regex("""var\s+csrfHash\s*=\s*['"]([^'"]+)['"]""")
private val inlinePfpRegex = Regex("""addInput\(\s*['"]pfp['"]\s*,\s*['"]([^'"]+)['"]""")
private val accessTokenJsonRegex = Regex("""("access_token"\s*:\s*")(.*?)(")""")

private val loginJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

sealed class LoginResult {
    data object Success : LoginResult()
    data class Error(val message: String) : LoginResult()
}

data class LoginPageFields(
    val pfp: String,
    val csrf: String
)

class LoginService(
    private val email: String,
    private val password: String,
    private val requestBuilder: (String, Connection.Method) -> Connection = ::buildHttpRequest,
    private val getRequestBuilder: (String) -> Connection = ::buildHttpGetRequest,
    private val responseStorer: (Connection.Response) -> Unit = ::storeResponseCookies,
    private val requestExecutor: (Connection) -> Connection.Response = { it.execute() },
    private val cookieStore: MutableMap<String, String> = cookieJar
) {
    private val log by LoggerDelegate()

    init {
        log.info("LoginService initialised for email={} (diagnostic logging enabled)", maskEmail(email))
    }

    fun isLoggedIn(): Boolean = cookieStore[PIGEON_ACCESS_COOKIE]?.isNotBlank() == true

    fun login(): LoginResult {
        logout()

        val loginPageFields = fetchLoginPage().getOrElse {
            return it.toLoginError("Unable to fetch login page")
        }

        postCredentials(loginPageFields.pfp, loginPageFields.csrf).getOrElse {
            return it.toLoginError("Unable to post credentials")
        }

        verifyLogin().getOrElse {
            return it.toLoginError("Unable to verify login")
        }

        val accessToken = postPigeonServer().getOrElse {
            return it.toLoginError("Unable to obtain pigeon access token")
        }

        cookieStore[PIGEON_ACCESS_COOKIE] = accessToken
        log.info("Successfully obtained pigeon access token")

        return LoginResult.Success
    }

    fun logout() {
        runCatching {
            log.debug("Sending logout request to {}", LOGOUT_URL)
            val response = requestExecutor(getRequestBuilder(LOGOUT_URL))
            responseStorer(response)
            logResponseDetails("logout", response)
        }.onFailure {
            log.warn("Logout call failed before login; continuing with login flow", it)
        }
    }

    fun fetchLoginPage(): Result<LoginPageFields> = runCatching {
        log.debug("Sending login page request to {}", LOGIN_PAGE_URL)
        val response = requestExecutor(getRequestBuilder(LOGIN_PAGE_URL))
        responseStorer(response)
        logResponseDetails("fetch-login-page", response)
        ensureSuccessfulStatus(response, "Failed to fetch login page")

        val fields = extractLoginPageFields(response.body()).getOrElse {
            throw IllegalStateException(it.message ?: "Unable to extract login form fields")
        }

        log.info("Fetched login page and extracted login fields csrf={} pfp={}", maskValue(fields.csrf), maskValue(fields.pfp))
        fields
    }

    fun postCredentials(pfp: String, csrf: String): Result<Unit> = runCatching {
        val requestBody = buildLoginRequestBody(
            pfp = pfp,
            email = email,
            password = password,
            csrf = csrf
        )

        log.debug(
            "Posting credentials to {} using email={} csrf={} pfp={}",
            LOGIN_POST_URL,
            maskEmail(email),
            maskValue(csrf),
            maskValue(pfp)
        )

        val response = requestExecutor(
            requestBuilder(LOGIN_POST_URL, Connection.Method.POST)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .requestBody(requestBody)
        )

        responseStorer(response)
        logResponseDetails("post-credentials", response)
        ensureSuccessfulStatus(response, "Credential submission failed")

        val body = response.body()
        if (
            body.contains("incorrect", ignoreCase = true) ||
            body.contains("invalid", ignoreCase = true)
        ) {
            throw IllegalStateException("CricketArchive rejected the supplied credentials")
        }

        log.info("Submitted CricketArchive credentials")
    }

    fun verifyLogin(): Result<Unit> = runCatching {
        log.debug("Verifying login via {}", LOGIN_SITE_URL)
        val response = requestExecutor(getRequestBuilder(LOGIN_SITE_URL))
        responseStorer(response)
        logResponseDetails("verify-login", response)
        ensureSuccessfulStatus(response, "Login verification request failed")

        if (!containsCricketArchiveLink(response.body())) {
            throw IllegalStateException("Login verification failed: expected CricketArchive link was not found")
        }

        log.info("Login verified successfully")
    }

    fun postPigeonServer(): Result<String> = runCatching {
        val sessionId = cookieStore[SESSION_ID_COOKIE]
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Missing session cookie: $SESSION_ID_COOKIE")

        val sessionHash = cookieStore[SESSION_HASH_COOKIE]
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Missing session cookie: $SESSION_HASH_COOKIE")

        val requestBody = buildPigeonServerRequestBody(
            sessionId = sessionId,
            sessionHash = sessionHash
        )
        val formRequestBody = buildPigeonServerFormRequestBody(requestBody)

        log.debug(
            "Posting pigeon request to {} sessionId={} sessionHash={} payloadPreview={}",
            PIGEON_SERVER_URL,
            maskValue(sessionId),
            maskValue(sessionHash),
            bodyPreview(requestBody)
        )

        val response = requestExecutor(
            requestBuilder(PIGEON_SERVER_URL, Connection.Method.POST)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .ignoreContentType(true)
                .requestBody(formRequestBody)
        )

        responseStorer(response)
        logResponseDetails("post-pigeon-server", response)
        ensureSuccessfulStatus(response, "Pigeon server request failed")

        extractPigeonAccessToken(response.body())
            .onFailure {
                log.error(
                    "Failed to extract pigeon access token from response preview={}",
                    bodyPreview(response.body()),
                    it
                )
            }
            .getOrElse {
                throw IllegalStateException(it.message ?: "Missing pigeon access token")
            }
    }

    private fun ensureSuccessfulStatus(response: Connection.Response, errorMessage: String) {
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("$errorMessage (HTTP ${response.statusCode()})")
        }
    }

    private fun logResponseDetails(step: String, response: Connection.Response) {
        log.info(
            "login-step={} status={} contentType={} cookies={} bodyPreview={}",
            step,
            response.statusCode(),
            response.contentType(),
            summarizeCookies(response.cookies()),
            bodyPreview(response.body())
        )
    }

    private fun summarizeCookies(cookies: Map<String, String>): String =
        cookies.entries
            .joinToString(prefix = "[", postfix = "]") { (name, value) ->
                "$name=${maskValue(value)}"
            }

    private fun bodyPreview(body: String): String =
        sanitizeBody(body)
            .replace("\\n", " ")
            .replace("\\r", " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .take(RESPONSE_PREVIEW_LIMIT)

    private fun sanitizeBody(body: String): String =
        accessTokenJsonRegex.replace(body) { match ->
            val token = match.groupValues.getOrNull(2).orEmpty()
            "${match.groupValues[1]}${maskValue(token)}${match.groupValues[3]}"
        }

    private fun maskEmail(value: String): String {
        val atIndex = value.indexOf('@')
        if (atIndex <= 1) {
            return "***"
        }

        val start = value.take(2)
        val domain = value.substring(atIndex)
        return "$start***$domain"
    }

    private fun maskValue(value: String): String {
        if (value.isBlank()) {
            return "***"
        }

        return when {
            value.length <= 8 -> "${value.take(1)}***${value.takeLast(1)}"
            else -> "${value.take(4)}...${value.takeLast(4)}"
        }
    }

    companion object {
        fun loginWithStoredCredentials(
            environmentProvider: () -> Map<String, String> = System::getenv
        ): LoginResult {
            val credentials = resolveConfiguredOrEnvironmentCredentials(environmentProvider).getOrElse {
                return LoginResult.Error(it.message ?: "CricketArchive credentials are not configured")
            }

            return LoginService(
                email = credentials.email,
                password = credentials.password
            ).login()
        }
    }
}

@Serializable
private data class PigeonServerRequest(
    @SerialName("pigeon_version")
    val pigeonVersion: String = "2.3",
    val uri: String = "https://cricketarchive.com/",
    val referrer: String = "https://cricketarchive.com/",
    @SerialName("server_gate")
    val serverGate: Int = 1,
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("session_hash")
    val sessionHash: String,
    @SerialName("content_access")
    val contentAccess: Boolean = false,
    @SerialName("content_id")
    val contentId: Int = 0,
    @SerialName("content_title")
    val contentTitle: String = "",
    @SerialName("content_date")
    val contentDate: String = "",
    @SerialName("content_price")
    val contentPrice: Int = 0,
    @SerialName("content_value")
    val contentValue: Int = 0,
    @SerialName("content_prompt")
    val contentPrompt: Int = 0,
    val redirect: Boolean = false
)

internal fun buildPigeonServerRequestBody(sessionId: String, sessionHash: String): String =
    loginJson.encodeToString(
        PigeonServerRequest.serializer(),
        PigeonServerRequest(
            sessionId = sessionId,
            sessionHash = sessionHash
        )
    )

internal fun buildPigeonServerFormRequestBody(jsonPayload: String): String =
    "json=${URLEncoder.encode(jsonPayload, StandardCharsets.UTF_8)}"

internal fun buildLoginRequestBody(
    pfp: String,
    email: String,
    password: String,
    csrf: String
): String {
    val encoder = StandardCharsets.UTF_8

    return "pfp=${URLEncoder.encode(pfp, encoder)}" +
            "&email=${URLEncoder.encode(email, encoder)}" +
            "&password=${URLEncoder.encode(password, encoder)}" +
            "&vo-action=login" +
            "&csrf=${URLEncoder.encode(csrf, encoder)}"
}

internal fun extractLoginPageFields(html: String): Result<LoginPageFields> = runCatching {
    val document = Jsoup.parse(html)
    val csrf = document.selectFirst("input[name=csrf]")
        ?.attr("value")
        ?.takeIf(String::isNotBlank)
        ?: extractCsrfHashFromScript(html)
        ?: throw IllegalStateException("Unable to find csrf value or csrfHash on login page")

    val pfp = document.selectFirst("input[name=pfp]")
        ?.attr("value")
        ?.takeIf(String::isNotBlank)
        ?: extractInlinePfp(html)
        ?: derivePfpFromCsrf(csrf)

    LoginPageFields(pfp = pfp, csrf = csrf)
}

internal fun extractCsrfHashFromScript(html: String): String? =
    csrfHashRegex.find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf(String::isNotBlank)

internal fun extractInlinePfp(html: String): String? =
    inlinePfpRegex.find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf(String::isNotBlank)

internal fun derivePfpFromCsrf(csrf: String): String =
    MessageDigest.getInstance("MD5")
        .digest(csrf.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }

internal fun containsCricketArchiveLink(html: String): Boolean =
    html.contains("https://cricketarchive.com", ignoreCase = true)

@Serializable
private data class PigeonServerResponse(
    @SerialName("access_token")
    val accessToken: String? = null
)

internal fun extractPigeonAccessToken(responseBody: String): Result<String> = runCatching {
    val response = loginJson.decodeFromString(PigeonServerResponse.serializer(), responseBody)

    response.accessToken
        ?.takeIf(String::isNotBlank)
        ?: throw IllegalStateException("Pigeon server response did not contain a usable access_token")
}

private fun Throwable.toLoginError(prefix: String): LoginResult.Error =
    LoginResult.Error("$prefix: ${message ?: "unknown error"}")