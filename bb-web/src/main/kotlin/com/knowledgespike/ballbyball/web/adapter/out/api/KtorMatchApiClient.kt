package com.knowledgespike.ballbyball.web.adapter.out.api

import com.knowledgespike.ballbyball.contracts.Envelope
import com.knowledgespike.ballbyball.contracts.RecentMatchesResponse
import com.knowledgespike.ballbyball.web.application.MatchApiClient
import com.knowledgespike.ballbyball.web.application.MatchApiResult
import com.knowledgespike.ballbyball.web.domain.service.TokenService
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

class KtorMatchApiClient(
    apiBaseUrl: String,
    private val httpClient: HttpClient,
    private val tokenService: TokenService? = null
) : MatchApiClient {
    private val baseUrl = apiBaseUrl.trimEnd('/')
    private val log = LoggerFactory.getLogger(KtorMatchApiClient::class.java)

    override suspend fun recentMatches(): MatchApiResult = try {
        val token = tokenService?.getAccessToken()
        val response = httpClient.get("$baseUrl/api/matches") {
            if (!token.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
        if (!response.status.isSuccess()) {
            MatchApiResult.Unavailable(response.status)
        } else {
            MatchApiResult.Success(response.body<Envelope<RecentMatchesResponse>>().result.matches)
        }
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        log.warn("API request failed", cause)
        MatchApiResult.Unavailable()
    }
}