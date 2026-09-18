package com.knowledgespike.ballbyball.api.config

import io.ktor.server.config.ApplicationConfig

data class JwtSettings(
    val jwksUrl: String,
    val issuer: String,
    val realm: String,
    val audience: String = "bb.api",
    val leewaySeconds: Long = 3L,
    val validScopes: Set<String> = DEFAULT_VALID_SCOPES
) {
    companion object {
        val DEFAULT_VALID_SCOPES: Set<String> = setOf("bb.api", "bb.api.read", "bb.api.write")

        fun from(config: ApplicationConfig): JwtSettings {
            val configuredScopes = config.propertyOrNull("jwt.validScopes")?.getList()?.toSet()
                ?: DEFAULT_VALID_SCOPES

            return JwtSettings(
                jwksUrl = config.property("jwt.jwksUrl").getString(),
                issuer = config.property("jwt.issuer").getString(),
                realm = config.property("jwt.realm").getString(),
                audience = config.propertyOrNull("jwt.audience")?.getString() ?: "bb.api",
                leewaySeconds = config.propertyOrNull("jwt.leewaySeconds")?.getString()?.toLongOrNull() ?: 3L,
                validScopes = configuredScopes
            )
        }
    }
}
