package com.knowledgespike.ballbyball.api.config

import io.ktor.server.config.ApplicationConfig

data class JwtSettings(
    val jwksUrl: String,
    val issuer: String,
    val realm: String,
    val audiences: List<String> = DEFAULT_AUDIENCES,
    val leewaySeconds: Long = 3L,
    val validScopes: Set<String> = DEFAULT_VALID_SCOPES
) {
    constructor(
        jwksUrl: String,
        issuer: String,
        realm: String,
        audience: String,
        leewaySeconds: Long = 3L,
        validScopes: Set<String> = DEFAULT_VALID_SCOPES
    ) : this(
        jwksUrl = jwksUrl,
        issuer = issuer,
        realm = realm,
        audiences = (listOf(audience) + DEFAULT_AUDIENCES).distinct(),
        leewaySeconds = leewaySeconds,
        validScopes = (validScopes + DEFAULT_VALID_SCOPES)
    )

    val audience: String get() = audiences.firstOrNull() ?: "acs-bbb"

    companion object {
        val DEFAULT_AUDIENCES: List<String> = listOf("acs-bbb", "bb.api")
        val DEFAULT_VALID_SCOPES: Set<String> = setOf(
            "bb.api", "bb.api.read", "bb.api.write",
            "bbb.api", "bbb.api.read", "bbb.api.write"
        )

        fun from(config: ApplicationConfig): JwtSettings {
            val configuredScopes = config.propertyOrNull("jwt.validScopes")?.getList()?.toSet()
                ?: emptySet()
            val validScopes = (configuredScopes + DEFAULT_VALID_SCOPES)

            val parsedAudiences = config.propertyOrNull("jwt.audiences")?.getList()
                ?: config.propertyOrNull("jwt.audience")?.getString()?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                ?: emptyList()
            val audiences = (parsedAudiences + DEFAULT_AUDIENCES).distinct()

            return JwtSettings(
                jwksUrl = config.property("jwt.jwksUrl").getString(),
                issuer = config.property("jwt.issuer").getString(),
                realm = config.property("jwt.realm").getString(),
                audiences = audiences,
                leewaySeconds = config.propertyOrNull("jwt.leewaySeconds")?.getString()?.toLongOrNull() ?: 3L,
                validScopes = validScopes
            )
        }
    }
}
