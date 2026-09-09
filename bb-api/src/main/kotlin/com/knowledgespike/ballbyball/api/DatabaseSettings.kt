package com.knowledgespike.ballbyball.api

import io.ktor.server.config.ApplicationConfig

data class DatabaseSettings(
    val jdbcUrl: String,
    val user: String,
    val password: String,
    val maximumPoolSize: Int
) {
    companion object {
        fun from(config: ApplicationConfig): DatabaseSettings = DatabaseSettings(
            jdbcUrl = config.property("database.jdbcUrl").getString(),
            user = config.property("database.user").getString(),
            password = config.property("database.password").getString(),
            maximumPoolSize = config.property("database.maximumPoolSize").getString().toInt()
        )
    }
}