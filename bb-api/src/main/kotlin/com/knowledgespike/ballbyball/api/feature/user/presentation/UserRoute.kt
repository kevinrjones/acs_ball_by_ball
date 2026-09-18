package com.knowledgespike.ballbyball.api.feature.user.presentation

import com.knowledgespike.ballbyball.api.bootstrap.AUTH_JWT
import com.knowledgespike.ballbyball.api.bootstrap.requireUserPrincipal
import com.knowledgespike.ballbyball.api.bootstrap.userPrincipal
import com.knowledgespike.ballbyball.api.bootstrap.userProtected
import com.knowledgespike.ballbyball.api.routing.respondOk
import com.knowledgespike.ballbyball.contracts.UserProfileResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.routeUser() {
    authenticate(AUTH_JWT) {
        userProtected {
            route("/api") {
                get("/user/profile") {
                    val user = call.userPrincipal() ?: return@get

                    call.respondOk(
                        UserProfileResponse(
                            subject = user.id,
                            name = user.name,
                            email = user.email,
                            roles = user.roles
                        )
                    )
                }
            }
        }
    }
}
