package com.knowledgespike.ballbyball.web.adapter.`in`.http

import com.knowledgespike.ballbyball.web.application.MatchApiClient
import com.knowledgespike.ballbyball.web.application.MatchApiResult
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondResource
import io.ktor.server.response.respondText
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.registerWebRoutes(matchApiClient: MatchApiClient) {
    get("/") {
        call.respondResource("static/index.html")
    }
    get("/matches") {
        when (val result = matchApiClient.recentMatches()) {
            is MatchApiResult.Success -> call.respondText(
                renderMatches(result.matches),
                ContentType.Text.Html
            )

            is MatchApiResult.Unavailable -> call.respondText(
                result.status?.let { "The API is unavailable (${it.value})." }
                    ?: "The API is unavailable.",
                status = HttpStatusCode.BadGateway
            )
        }
    }
    staticResources("/static", "static")
}