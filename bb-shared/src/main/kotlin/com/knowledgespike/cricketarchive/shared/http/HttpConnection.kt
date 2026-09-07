package com.knowledgespike.cricketarchive.shared.http

import com.knowledgespike.cricketarchive.shared.auth.LoginResult
import com.knowledgespike.cricketarchive.shared.auth.LoginService
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.helper.ValidationException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.UncheckedIOException
import java.util.concurrent.ConcurrentHashMap
import java.net.http.HttpTimeoutException
import javax.net.ssl.SSLHandshakeException

const val userAgent =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"

val log = LoggerFactory.getLogger("com.knowledgespike.cricketarchive.shared.http.HttpConnection")

private const val BASE_URL = "https://cricketarchive.com"
private const val INITIAL_RETRY_DELAY_MILLISECONDS = 100L

internal val cookieJar = ConcurrentHashMap<String, String>()
private var sessionInitialised = false

data class ProxyConfig(
    val host: String,
    val port: Int,
    val user: String? = null,
    val password: String? = null
)

private var proxyConfig: ProxyConfig? = null

fun setProxy(config: ProxyConfig) {
    System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "")
    System.setProperty("jdk.http.auth.proxying.disabledSchemes", "")
    System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true")
    proxyConfig = config
}

@Synchronized
private fun initialiseSessionIfRequired() {
    if (sessionInitialised) return

    log.info("Initialising HTTP session cookies from $BASE_URL")

    val connection = Jsoup.connect(BASE_URL)
        .timeout(DEFAULT_TIMEOUT_MILLISECONDS)
        .ignoreHttpErrors(true)
        .userAgent(userAgent)
        .method(Connection.Method.GET)

    proxyConfig?.let {
        connection.proxy(it.host, it.port)
        if (it.user != null && it.password != null) {
            connection.auth { auth ->
                if (auth.isProxy) {
                    auth.credentials(it.user, it.password)
                } else {
                    null
                }
            }
        }
    }

    val response = connection.execute()

    cookieJar.putAll(response.cookies())
    sessionInitialised = true

    log.info("Initialised HTTP session with ${cookieJar.size} cookies")
}

@Synchronized
internal fun storeResponseCookies(response: Connection.Response) {
    val responseCookies = response.cookies()
    if (responseCookies.isNotEmpty()) {
        cookieJar.putAll(responseCookies)
        log.debug("Stored ${responseCookies.size} cookies; cookie jar now contains ${cookieJar.size} cookies")
    }
}

/**
 * Fetches the HTTP response of the specified URL using the given HTTP method.
 *
 * The method attempts to execute an HTTP request and retrieve the response. It handles various
 * exceptions, including validation errors, SSL handshake issues, and general exceptions. The response
 * is wrapped in a `Result` to distinguish between success, unrecoverable failures (404 errors
 * and most exceptions) and recoverable failures (403 errors and SSLHandshaker errors).
 * The SSLHandshake errors in particular appear to be an attempt at throttling the server.
 *
 * @param url The URL to which the HTTP request is sent.
 * @param method The HTTP method (e.g., GET, POST) used for the request.
 * @return A `Result` containing the HTTP response on success, null for SSL handshake issues, or an exception on failure.
 */
fun getHttpResponse(url: String, method: Connection.Method): Result<Connection.Response?> {
    try {
        val connection: Connection = buildHttpRequest(url, method)
        val response = connection.execute()
        storeResponseCookies(response)

        if (response.body().contains("error in your requested page")) {
            log.warn("getHttpResponse: Unable to connect to URL $url error in your requested page")
            return Result.failure(Exception("Unable to connect to URL $url error in your requested page"))
        }

        if (response.statusCode() == 404) {
            log.warn("getHttpResponse: Unable to connect to URL $url status 404")
            return Result.failure(Exception("Unable to connect to URL $url status 404"))
        }

        return Result.success(response)
    } catch (e: ValidationException) {
        log.error("getHttpResponse: Unable to connect to URL $url ValidationException")
        return Result.failure(e)
    } catch (e: SSLHandshakeException) {
        log.warn("SSL Handshake Error: $url", e.message)
        return Result.success(null)
    } catch (e: UncheckedIOException) {
        log.warn("Unchecked IO Exception: $url", e.message)
        return Result.success(null)
    } catch (e: IOException) {
        log.warn("IO Exception: $url", e.message)
        return Result.success(null)
    } catch (e: HttpTimeoutException) {
        log.warn("Timeout Exception: $url", e.message)
        return Result.success(null)
    } catch (e: Throwable) {
        log.error("Unable to connect to URL $url", e)
        return Result.failure(e)
    }
}

/**
 * Fetches data from the specified URL with support for automatic retries in case of certain errors.
 *
 * The method attempts to fetch the data using the specified HTTP method and processes the successful response
 * using a provided success-handler function. If the server responds with an HTTP 403 status, the method will
 * retry the request with exponential backoff until the retry delay reaches its maximum limit. For HTTP 200
 * responses, the provided success-handler function is invoked to process the data.
 *
 * Returns null if all retry attempts fail or the response status is invalid.
 *
 * @param url The URL to fetch data from.
 * @param method The HTTP method to use for the request (e.g., GET, POST). Defaults to GET.
 * @param onSuccess A lambda function to process the HTTP response on successful fetch. Takes a `Connection.Response` object as input and returns a result of type `T`.
 * @return Processed result of type `T` on successful fetch, or null if the operation fails after retries.
 */
fun <T> fetchWithRetry(
    url: String,
    method: Connection.Method = Connection.Method.GET,
    onSuccess: (Connection.Response) -> T,
    maxRetryTime: Int = 10 * 60 * 1000
): T? {
    return fetchWithRetryInternal(
        url = url,
        method = method,
        onSuccess = onSuccess,
        maxRetryTime = maxRetryTime,
        getHttpResponse = ::getHttpResponse,
        loginWithStoredCredentials = LoginService::loginWithStoredCredentials,
        sleep = Thread::sleep
    )
}

internal fun <T> fetchWithRetryInternal(
    url: String,
    method: Connection.Method = Connection.Method.GET,
    onSuccess: (Connection.Response) -> T,
    maxRetryTime: Int = 10 * 60 * 1000,
    getHttpResponse: (String, Connection.Method) -> Result<Connection.Response?>,
    loginWithStoredCredentials: () -> LoginResult,
    sleep: (Long) -> Unit
): T? {
    var retry: Boolean
    var retryDelay = INITIAL_RETRY_DELAY_MILLISECONDS
    var total403BackoffTime = 0L
    var reloginAttemptedAfter403 = false

    log.debug("fetchWithRetry: Try to get url: $url")
    retryLoop@ do {
        val result = getHttpResponse(url, method)

        when {
            result.isFailure -> {
                retry = false
                log.warn("Unable to get HTTP response from server for url: $url")
            }

            else -> {
                val response = result.getOrNull()
                // if response is null then we have an exception in the call, but it's retryable
                if (response == null) {
                    retry = true
                    log.warn("Request returned no response for: $url - wait for ${retryDelay} milliseconds and retrying")
                    sleep(retryDelay)

                    if (retryDelay > maxRetryTime) {
                        log.error("Error for: $url - after $retryDelay milliseconds")
                        return null
                    }

                    retryDelay *= 2
                } else if (response.statusCode() == 403) {
                    retry = true

                    if (reloginAttemptedAfter403) {
                        log.error("HTTP 403 received again immediately after re-login for: $url. Aborting to avoid infinite retry loop")
                        return null
                    }

                    if (total403BackoffTime >= maxRetryTime.toLong()) {
                        when (val loginResult = loginWithStoredCredentials()) {
                            LoginResult.Success -> {
                                reloginAttemptedAfter403 = true
                                retryDelay = INITIAL_RETRY_DELAY_MILLISECONDS
                                total403BackoffTime = 0L
                                log.info("Re-login succeeded after backing off from repeated HTTP 403 responses for: $url. Retrying request")
                                continue@retryLoop
                            }

                            is LoginResult.Error -> {
                                log.error("Re-login failed after repeated HTTP 403 responses for: $url (${loginResult.message}). Aborting request")
                                return null
                            }
                        }
                    }

                    log.warn("HTTP 403 response for: $url - wait for ${retryDelay} milliseconds and retrying")
                    sleep(retryDelay)
                    total403BackoffTime += retryDelay
                    retryDelay *= 2

                } else if (response.statusCode() != 200) {
                    retry = false
                    log.error("Invalid HTTP response of ${response.statusCode()} from server for url: $url")
                } else {
                    log.debug("fetchWithRetry: Got url: $url")
                    return onSuccess(response)
                }
            }
        }
    } while (retry)

    return null
}

fun fetchResponseWithRetry(
    url: String,
    method: Connection.Method = Connection.Method.GET
): Connection.Response? {
    return fetchWithRetry(
        url = url,
        method = method,
        onSuccess = { it }
    )
}

private const val DEFAULT_TIMEOUT_MILLISECONDS = 30_000

fun buildHttpRequest(url: String, httpMethod: Connection.Method): Connection {
    initialiseSessionIfRequired()

    val connection = Jsoup.connect(url)
        .timeout(DEFAULT_TIMEOUT_MILLISECONDS)
        .cookies(cookieJar)
        .ignoreHttpErrors(true)
        .userAgent(userAgent)
        .method(httpMethod)

    proxyConfig?.let {
        connection.proxy(it.host, it.port)
        if (it.user != null && it.password != null) {
            connection.auth { auth ->
                if (auth.isProxy) {
                    auth.credentials(it.user, it.password)
                } else {
                    null
                }
            }
        }
    }

    return connection
}

fun buildHttpGetRequest(url: String): Connection {
    initialiseSessionIfRequired()

    val connection = Jsoup.connect(url)
        .timeout(DEFAULT_TIMEOUT_MILLISECONDS)
        .cookies(cookieJar)
        .ignoreHttpErrors(true)
        .userAgent(userAgent)
        .method(Connection.Method.GET)

    proxyConfig?.let {
        connection.proxy(it.host, it.port)
        if (it.user != null && it.password != null) {
            connection.auth { auth ->
                if (auth.isProxy) {
                    auth.credentials(it.user, it.password)
                } else {
                    null
                }
            }
        }
    }

    return connection
}
