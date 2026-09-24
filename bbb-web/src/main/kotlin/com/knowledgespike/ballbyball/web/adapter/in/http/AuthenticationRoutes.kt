package com.knowledgespike.ballbyball.web.adapter.`in`.http

import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.registerAuthenticationRoutes(registrationUrl: String) {
    get("/bff/signup") {
        call.respondRedirect(registrationUrl)
    }
}