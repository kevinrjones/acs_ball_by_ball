package com.knowledgespike.ballbyball.api.routing

import arrow.core.NonEmptyList
import com.knowledgespike.ballbyball.contracts.Envelope
import com.knowledgespike.ballbyball.types.error.Error
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall

suspend fun RoutingCall.respondBadRequest(message: String) =
    respond(HttpStatusCode.BadRequest, Envelope.failure(message))

suspend fun RoutingCall.respondBadRequest(errors: NonEmptyList<Error>) =
    respond(HttpStatusCode.BadRequest, Envelope.failure(errors))

suspend inline fun <reified T> RoutingCall.respondOk(dto: T) =
    respond(HttpStatusCode.OK, Envelope.success(dto))
