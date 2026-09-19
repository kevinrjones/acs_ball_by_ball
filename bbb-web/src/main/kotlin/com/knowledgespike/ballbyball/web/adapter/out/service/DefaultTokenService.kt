package com.knowledgespike.ballbyball.web.adapter.out.service

import com.knowledgespike.ballbyball.web.domain.service.TokenService
import com.knowledgespike.feature.kbff.domain.model.KbffConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.parametersOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val OIDC_IDENTITY_SCOPES = setOf("openid", "profile", "email", "address", "phone", "offline_access")

class DefaultTokenService(
    private val httpClient: HttpClient,
    private val configuration: KbffConfiguration,
    private val clientCredentialsScopes: List<String>? = null
) : TokenService {

    private val logger = LoggerFactory.getLogger(DefaultTokenService::class.java)
    private val tokenCache = ConcurrentHashMap<String, CachedToken>()
    private val discoveryMutex = Mutex()
    @Volatile
    private var cachedTokenEndpoint: String? = null

    private suspend fun resolveTokenEndpoint(): String? {
        cachedTokenEndpoint?.let { return it }
        return discoveryMutex.withLock {
            cachedTokenEndpoint?.let { return it }
            val authority = configuration.oidc.authority.trim().removeSuffix("/")
            val discoveryUrl = "$authority/.well-known/openid-configuration"

            logger.info("Fetching OIDC metadata from {}", discoveryUrl)
            val metadataResponse = httpClient.get(discoveryUrl)
            if (!metadataResponse.status.isSuccess()) {
                logger.error("Failed to fetch OIDC metadata from {}: {}", discoveryUrl, metadataResponse.status)
                return null
            }

            val metadata = Json.parseToJsonElement(metadataResponse.bodyAsText()).jsonObject
            val endpoint = metadata["token_endpoint"]?.jsonPrimitive?.content
            if (endpoint == null) {
                logger.error("No token_endpoint found in OIDC metadata from {}", discoveryUrl)
                return null
            }
            cachedTokenEndpoint = endpoint
            endpoint
        }
    }

    override suspend fun getAccessToken(): String? {
        val oidc = configuration.oidc
        val cacheKey = oidc.clientId

        val cachedToken = tokenCache[cacheKey]
        if (cachedToken != null && System.currentTimeMillis() < cachedToken.expiresAt) {
            return cachedToken.token
        }

        return try {
            val tokenEndpoint = resolveTokenEndpoint() ?: return null

            val scopesToRequest = clientCredentialsScopes
                ?: oidc.scopes.filterNot { it.lowercase() in OIDC_IDENTITY_SCOPES }
            val requestedScopes = scopesToRequest.joinToString(" ")

            logger.info("Acquiring client credentials token from {}", tokenEndpoint)
            val tokenResponse = httpClient.submitForm(
                url = tokenEndpoint,
                formParameters = parametersOf(
                    "grant_type" to listOf("client_credentials"),
                    "scope" to listOf(requestedScopes),
                    "client_id" to listOf(oidc.clientId),
                    "client_secret" to listOf(oidc.clientSecret)
                )
            )

            if (tokenResponse.status.isSuccess()) {
                val bodyText = tokenResponse.bodyAsText()
                val body = Json.parseToJsonElement(bodyText).jsonObject
                val token = body["access_token"]?.jsonPrimitive?.content ?: return null
                val expiresIn = body["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L

                // Buffer of 60 seconds
                val expiresAt = System.currentTimeMillis() + (expiresIn * 1000) - 60000L
                tokenCache[cacheKey] = CachedToken(token, expiresAt)
                return token
            } else {
                logger.error(
                    "Failed to fetch client credentials token from {}: {} {}",
                    tokenEndpoint,
                    tokenResponse.status,
                    tokenResponse.bodyAsText()
                )
            }
            null
        } catch (e: Exception) {
            logger.error("Exception fetching client credentials token", e)
            null
        }
    }

    private data class CachedToken(val token: String, val expiresAt: Long)
}
