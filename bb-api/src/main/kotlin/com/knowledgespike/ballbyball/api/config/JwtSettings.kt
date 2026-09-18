package com.knowledgespike.ballbyball.api.config

import io.ktor.server.config.ApplicationConfig

data class JwtSettings(
    val jwksUrl: String,
    val issuer: String,
    val realm: String,
    val audience: String = "bb.api",
    val leewaySeconds: Long = 3L
) {
    companion object {
        fun from(config: ApplicationConfig): JwtSettings = JwtSettings(
            jwksUrl = config.property("jwt.jwksUrl").getString(),
            issuer = config.property("jwt.issuer").getString(),
            realm = config.property("jwt.realm").getString(),
            audience = config.propertyOrNull("jwt.audience")?.getString() ?: "bb.api",
            leewaySeconds = config.propertyOrNull("jwt.leewaySeconds")?.getString()?.toLongOrNull() ?: 3L
        )
    }
}
