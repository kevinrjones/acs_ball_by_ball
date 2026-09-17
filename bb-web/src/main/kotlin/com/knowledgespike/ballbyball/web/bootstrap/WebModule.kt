package com.knowledgespike.ballbyball.web.bootstrap

import com.knowledgespike.ballbyball.web.adapter.`in`.http.registerWebRoutes
import com.knowledgespike.ballbyball.web.adapter.out.api.HttpClientFactory
import com.knowledgespike.ballbyball.web.adapter.out.api.KtorMatchApiClient
import com.knowledgespike.ballbyball.web.application.MatchApiClient
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun Application.module() {
    val apiBaseUrl = environment.config.property("api.baseUrl").getString().trimEnd('/')
    val client = HttpClientFactory.create()
    monitor.subscribe(ApplicationStopped) {
        client.close()
    }
    moduleWithApiClient(KtorMatchApiClient(apiBaseUrl, client))
}

fun Application.moduleWithApiClient(matchApiClient: MatchApiClient) {
    install(CallLogging)
    install(ContentNegotiation) {
        json(Json { prettyPrint = true })
    }

    routing {
        registerWebRoutes(matchApiClient)
    }
}
