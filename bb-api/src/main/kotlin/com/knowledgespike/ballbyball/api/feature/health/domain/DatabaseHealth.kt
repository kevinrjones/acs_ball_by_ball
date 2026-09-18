package com.knowledgespike.ballbyball.api.feature.health.domain

interface DatabaseHealth {
    suspend fun isHealthy(): Boolean
}
