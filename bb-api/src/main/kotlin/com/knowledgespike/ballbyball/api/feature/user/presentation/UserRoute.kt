package com.knowledgespike.ballbyball.api.feature.user.presentation

import com.knowledgespike.ballbyball.api.bootstrap.AUTH_JWT
import com.knowledgespike.ballbyball.api.bootstrap.requireUserPrincipal
import com.knowledgespike.ballbyball.api.bootstrap.userProtected
import com.knowledgespike.ballbyball.contracts.Envelope
import com.knowledgespike.ballbyball.contracts.UserProfileResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.routeUser() {
    authenticate(AUTH_JWT) {
        userProtected {
            route("/api") {
                get("/user/profile") {
                    val user = call.requireUserPrincipal() ?: return@get

                    call.respond(
                        HttpStatusCode.OK,
                        Envelope.success(
                            UserProfileResponse(
                                subject = user.id,
                                name = user.name,
                                email = user.email,
                                roles = user.roles
                            )
                        )
                    )
                }
            }
        }
    }
}
