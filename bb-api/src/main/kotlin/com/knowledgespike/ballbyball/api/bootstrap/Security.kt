package com.knowledgespike.ballbyball.api.bootstrap

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.JWTVerifier
import com.auth0.jwt.interfaces.Payload
import com.knowledgespike.ballbyball.api.config.JwtSettings
import com.knowledgespike.ballbyball.contracts.ApiError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.util.AttributeKey
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.TimeUnit

const val AUTH_JWT = "auth-jwt"

val DEFAULT_VALID_SCOPES: Set<String> = JwtSettings.DEFAULT_VALID_SCOPES

val VALID_SCOPES: Set<String> = DEFAULT_VALID_SCOPES

private val logger = LoggerFactory.getLogger("com.knowledgespike.ballbyball.api.bootstrap.Security")

val UserPrincipalKey: AttributeKey<UserPrincipal> = AttributeKey("UserPrincipal")

/**
 * Route-scoped plugin that validates user authorization after authentication has verified the bearer token.
 */
val UserAuthorizationPlugin = createRouteScopedPlugin("UserAuthorizationPlugin") {
    on(AuthenticationChecked) { call ->
        call.requireUserPrincipal()
    }
}

/**
 * Domain principal representing an authenticated human user with verified identity claims.
 */
data class UserPrincipal(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val roles: List<String> = emptyList()
) {
    fun hasRole(role: String): Boolean = roles.any { it.equals(role, ignoreCase = true) }
}

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

/**
 * Extracts assigned user roles from 'role' or 'roles' claims.
 */
fun extractRoles(principal: JWTPrincipal): List<String> =
    principal.payload.extractStringOrListClaims("role", "roles")

/**
 * Maps a verified JWTPrincipal into a domain UserPrincipal,
 * distinguishing human users from machine / client-credentials tokens.
 */
fun JWTPrincipal.toUserPrincipal(): UserPrincipal? {
    val sub = payload.subject ?: payload.getClaim("sub")?.asString()
    val clientId = payload.getClaim("client_id")?.asString()

    if (sub.isNullOrBlank() || (clientId != null && sub == clientId)) {
        return null
    }

    val name = payload.getClaim("name")?.asString()
    val email = payload.getClaim("email")?.asString()
    val roles = payload.extractStringOrListClaims("role", "roles")

    return UserPrincipal(
        id = sub,
        name = name,
        email = email,
        roles = roles
    )
}

/**
 * Inspects token claims to determine if the caller is an authenticated human user,
 * distinguishing them from a machine / client-credentials token.
 */
fun isUserPrincipal(principal: JWTPrincipal): Boolean =
    principal.toUserPrincipal() != null

/**
 * Retrieves the cached UserPrincipal from call attributes if already validated by userProtected.
 */
fun ApplicationCall.userPrincipal(): UserPrincipal? = attributes.getOrNull(UserPrincipalKey)

/**
 * Validates that the active request contains a verified human user principal.
 * Responds with 401 Unauthorized if missing, or 403 Forbidden if called with a machine token.
 */
suspend fun ApplicationCall.requireUserPrincipal(): UserPrincipal? {
    val cached = userPrincipal()
    if (cached != null) return cached

    val jwtPrincipal = principal<JWTPrincipal>()
    if (jwtPrincipal == null) {
        respond(
            HttpStatusCode.Unauthorized,
            ApiError(code = "unauthorized", message = "Authentication required")
        )
        return null
    }

    val user = jwtPrincipal.toUserPrincipal()
    if (user == null) {
        respond(
            HttpStatusCode.Forbidden,
            ApiError(code = "forbidden", message = "User authorization required")
        )
        return null
    }

    attributes.put(UserPrincipalKey, user)
    return user
}

/**
 * Route extension that encapsulates user-only authorization, ensuring non-user calls
 * are rejected before route handlers execute.
 */
fun Route.userProtected(build: Route.() -> Unit): Route {
    val route = createChild(object : RouteSelector() {
        override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
            RouteSelectorEvaluation.Constant
    })
    route.install(UserAuthorizationPlugin)
    route.build()
    return route
}
