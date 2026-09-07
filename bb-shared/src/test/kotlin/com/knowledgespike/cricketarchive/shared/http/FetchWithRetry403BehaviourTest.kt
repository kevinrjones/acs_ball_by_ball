package com.knowledgespike.cricketarchive.shared.http

import com.knowledgespike.cricketarchive.shared.auth.LoginResult
import org.amshove.kluent.shouldBeEqualTo
import org.jsoup.Connection
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.test.Test

class FetchWithRetry403BehaviourTest {

    @Test
    fun `given single 403 and retry then fetch backs off without immediate re-login`() {
        val responses = listOf(
            Result.success(responseWithStatus(403)),
            Result.failure(RuntimeException("stop"))
        )
        var responseIndex = 0
        var loginAttempts = 0
        val sleepCalls = mutableListOf<Long>()

        val result = fetchWithRetryInternal(
            url = "https://example.com/test",
            onSuccess = { "ok" },
            maxRetryTime = 10_000,
            getHttpResponse = { _, _ ->
                val response = responses[responseIndex]
                responseIndex += 1
                response
            },
            loginWithStoredCredentials = {
                loginAttempts += 1
                LoginResult.Success
            },
            sleep = { sleepCalls += it }
        )

        result shouldBeEqualTo null
        loginAttempts shouldBeEqualTo 0
        sleepCalls shouldBeEqualTo listOf(100L)
    }

    @Test
    fun `given repeated 403 beyond max retry time when fetching then re-login happens once and request continues`() {
        val responses = listOf(
            Result.success(responseWithStatus(403)),
            Result.success(responseWithStatus(403)),
            Result.success(responseWithStatus(403)),
            Result.failure(RuntimeException("stop"))
        )
        var responseIndex = 0
        var loginAttempts = 0
        val sleepCalls = mutableListOf<Long>()

        val result = fetchWithRetryInternal(
            url = "https://example.com/test",
            onSuccess = { "ok" },
            maxRetryTime = 250,
            getHttpResponse = { _, _ ->
                val response = responses[responseIndex]
                responseIndex += 1
                response
            },
            loginWithStoredCredentials = {
                loginAttempts += 1
                LoginResult.Success
            },
            sleep = { sleepCalls += it }
        )

        result shouldBeEqualTo null
        loginAttempts shouldBeEqualTo 1
        responseIndex shouldBeEqualTo 4
        sleepCalls shouldBeEqualTo listOf(100L, 200L)
    }

    @Test
    fun `given 403 immediately after re-login when fetching then request aborts to avoid infinite retries`() {
        val responses = listOf(
            Result.success(responseWithStatus(403)),
            Result.success(responseWithStatus(403)),
            Result.success(responseWithStatus(403))
        )
        var responseIndex = 0
        var loginAttempts = 0
        val sleepCalls = mutableListOf<Long>()

        val result = fetchWithRetryInternal(
            url = "https://example.com/test",
            onSuccess = { "ok" },
            maxRetryTime = 100,
            getHttpResponse = { _, _ ->
                val response = responses[responseIndex]
                responseIndex += 1
                response
            },
            loginWithStoredCredentials = {
                loginAttempts += 1
                LoginResult.Success
            },
            sleep = { sleepCalls += it }
        )

        result shouldBeEqualTo null
        loginAttempts shouldBeEqualTo 1
        responseIndex shouldBeEqualTo 3
        sleepCalls shouldBeEqualTo listOf(100L)
    }

    private fun responseWithStatus(statusCode: Int): Connection.Response {
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "statusCode" -> statusCode
                "body" -> ""
                "cookies" -> emptyMap<String, String>()
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
}