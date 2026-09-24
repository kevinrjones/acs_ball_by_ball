package com.knowledgespike.ballbyball.api.bootstrap

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.JWTVerifier
import com.auth0.jwt.interfaces.Payload
import com.knowledgespike.ballbyball.api.config.JwtSettings
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.TimeUnit

const val AUTH_JWT = "auth-jwt"

private val logger = LoggerFactory.getLogger("com.knowledgespike.ballbyball.api.bootstrap.Security")

/**
 * Configures JWT bearer authentication against OIDC identity provider.
 */
fun Application.configureSecurity(
    jwtSettings: JwtSettings,
    jwkProvider: JwkProvider? = null,
    customVerifier: JWTVerifier? = null
) {
    install(Authentication) {
        jwt(AUTH_JWT) {
            realm = jwtSettings.realm

            if (customVerifier != null) {
                verifier(customVerifier)
            } else {
                val provider = jwkProvider ?: JwkProviderBuilder(URI.create(jwtSettings.jwksUrl).toURL())
                    .cached(10, 24, TimeUnit.HOURS)
                    .rateLimited(10, 1, TimeUnit.MINUTES)
                    .timeouts(15_000, 15_000)
                    .build()

                verifier(provider, jwtSettings.issuer) {
                    if (jwtSettings.audiences.isNotEmpty()) {
                        withAnyOfAudience(*jwtSettings.audiences.toTypedArray())
                    }
                    acceptLeeway(jwtSettings.leewaySeconds)
                }
            }

            validate { credential ->
                val scopes = extractScopes(credential)
                if (scopes.any { it in jwtSettings.validScopes }) {
                    JWTPrincipal(credential.payload)
                } else {
                    logger.warn("Token rejected: required scope not found in {}", scopes)
                    null
                }
            }
        }
    }
}

/**
 * Extracts string or space-delimited string-list claims from a JWT payload across multiple claim aliases.
 */
fun Payload.extractStringOrListClaims(vararg claimNames: String): List<String> =
    claimNames.flatMap { name ->
        val claim = getClaim(name)
        when {
            claim.isMissing || claim.isNull -> emptyList()
            else -> claim.asArray(String::class.java)?.toList()
                ?: claim.asString()?.split(" ")?.filter { it.isNotBlank() }
                ?: emptyList()
        }
    }.distinct()

/**
 * Extracts OAuth2/OIDC scopes from 'scope' or 'scp' claims (supporting array or space-delimited string).
 */
fun extractScopes(credential: JWTCredential): List<String> =
    credential.payload.extractStringOrListClaims("scope", "scp")
