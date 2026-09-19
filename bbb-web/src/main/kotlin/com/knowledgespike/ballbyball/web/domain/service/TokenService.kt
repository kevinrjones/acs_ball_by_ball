package com.knowledgespike.ballbyball.web.domain.service

interface TokenService {
    suspend fun getAccessToken(): String?
}
