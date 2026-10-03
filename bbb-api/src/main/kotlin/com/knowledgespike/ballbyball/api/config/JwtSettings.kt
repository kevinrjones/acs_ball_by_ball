package com.knowledgespike.ballbyball.api.config

import io.ktor.server.config.ApplicationConfig

data class JwtSettings(
    val jwksUrl: String,
    val issuer: String,
    val realm: String,
    val audiences: List<String> = emptyList(),
    val leewaySeconds: Long = 3L,
    val validScopes: Set<String> = emptySet()
) {
    constructor(
        jwksUrl: String,
        issuer: String,
        realm: String,
        audience: String,
        leewaySeconds: Long = 3L,
        validScopes: Set<String> = emptySet()
    ) : this(
        jwksUrl = jwksUrl,
        issuer = issuer,
        realm = realm,
        audiences = listOf(audience),
        leewaySeconds = leewaySeconds,
        validScopes = validScopes
    )

    val audience: String get() = audiences.firstOrNull() ?: "acs-bbb"

    companion object {
        fun from(config: ApplicationConfig): JwtSettings {
            val parsedAudiences = config.propertyOrNull("jwt.audiences")?.getList()
                ?: config.propertyOrNull("jwt.audience")?.getString()?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                ?: emptyList()
            require(parsedAudiences.isNotEmpty()) { "jwt.audience or jwt.audiences must be configured" }
            val validScopes = config.propertyOrNull("jwt.validScopes")?.getList()?.toSet().orEmpty()
            require(validScopes.isNotEmpty()) { "jwt.validScopes must be configured" }

            return JwtSettings(
                jwksUrl = config.property("jwt.jwksUrl").getString(),
                issuer = config.property("jwt.issuer").getString(),
                realm = config.property("jwt.realm").getString(),
                audiences = parsedAudiences.distinct(),
                leewaySeconds = config.propertyOrNull("jwt.leewaySeconds")?.getString()?.toLongOrNull() ?: 3L,
                validScopes = validScopes
            )
        }
    }
}
