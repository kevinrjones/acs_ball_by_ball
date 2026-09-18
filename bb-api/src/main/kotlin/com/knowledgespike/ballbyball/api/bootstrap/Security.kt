package com.knowledgespike.ballbyball.api.bootstrap

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.JWTVerifier
import com.knowledgespike.ballbyball.api.config.JwtSettings
import com.knowledgespike.ballbyball.contracts.ApiError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.TimeUnit

const val AUTH_JWT = "auth-jwt"

val VALID_SCOPES: Set<String> = setOf("bb.api", "bb.api.read", "bb.api.write", "acs.api", "acs.api.read")

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
                    .build()

                verifier(provider, jwtSettings.issuer) {
                    withAudience(jwtSettings.audience)
                    acceptLeeway(jwtSettings.leewaySeconds)
                }
            }

            validate { credential ->
                val scopes = extractScopes(credential)
                if (scopes.any { it in VALID_SCOPES }) {
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
 * Extracts OAuth2/OIDC scopes from 'scope' or 'scp' claims (supporting array or space-delimited string).
 */
fun extractScopes(credential: JWTCredential): List<String> {
    val list = mutableListOf<String>()
    val scopeClaim = credential.payload.getClaim("scope")
    if (!scopeClaim.isMissing && !scopeClaim.isNull) {
        val array = scopeClaim.asArray(String::class.java)
        if (array != null) {
            list.addAll(array)
        } else {
            val str = scopeClaim.asString()
            if (!str.isNullOrBlank()) {
                list.addAll(str.split(" "))
            }
        }
    }

    val scpClaim = credential.payload.getClaim("scp")
    if (!scpClaim.isMissing && !scpClaim.isNull) {
        val array = scpClaim.asArray(String::class.java)
        if (array != null) {
            list.addAll(array)
        } else {
            val str = scpClaim.asString()
            if (!str.isNullOrBlank()) {
                list.addAll(str.split(" "))
            }
        }
    }
    return list
}

/**
 * Extracts assigned user roles from 'role' or 'roles' claims.
 */
fun extractRoles(principal: JWTPrincipal): List<String> {
    val roles = mutableListOf<String>()
    val roleClaim = principal.payload.getClaim("role")
    if (!roleClaim.isMissing && !roleClaim.isNull) {
        val array = roleClaim.asArray(String::class.java)
        if (array != null) {
            roles.addAll(array)
        } else {
            val str = roleClaim.asString()
            if (!str.isNullOrBlank()) {
                roles.addAll(str.split(" "))
            }
        }
    }

    val rolesClaim = principal.payload.getClaim("roles")
    if (!rolesClaim.isMissing && !rolesClaim.isNull) {
        val array = rolesClaim.asArray(String::class.java)
        if (array != null) {
            roles.addAll(array)
        } else {
            val str = rolesClaim.asString()
            if (!str.isNullOrBlank()) {
                roles.addAll(str.split(" "))
            }
        }
    }
    return roles
}

/**
 * Inspects token claims to determine if the caller is an authenticated human user,
 * distinguishing them from a machine / client-credentials token.
 */
fun isUserPrincipal(principal: JWTPrincipal): Boolean {
    val clientId = principal.payload.getClaim("client_id")?.asString()
    val sub = principal.payload.getClaim("sub")?.asString()

    if (sub.isNullOrBlank()) return false
    if (clientId != null && sub == clientId) return false

    val roles = extractRoles(principal)
    return roles.isNotEmpty()
}

/**
 * Validates that the active request contains a verified human user principal.
 * Responds with 401 Unauthorized if missing, or 403 Forbidden if called with a machine token.
 */
suspend fun ApplicationCall.requireUserPrincipal(): JWTPrincipal? {
    val principal = principal<JWTPrincipal>()
    if (principal == null) {
        respond(
            HttpStatusCode.Unauthorized,
            ApiError(code = "unauthorized", message = "Authentication required")
        )
        return null
    }

    if (!isUserPrincipal(principal)) {
        respond(
            HttpStatusCode.Forbidden,
            ApiError(code = "forbidden", message = "User authorization required")
        )
        return null
    }

    return principal
}
