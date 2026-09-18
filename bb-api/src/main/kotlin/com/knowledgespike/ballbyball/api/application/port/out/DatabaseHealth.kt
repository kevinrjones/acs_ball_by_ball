package com.knowledgespike.ballbyball.api.application.port.out

interface DatabaseHealth {
    suspend fun isHealthy(): Boolean
}