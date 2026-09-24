package com.knowledgespike.ballbyball.types.error

import kotlinx.serialization.Serializable

@Serializable
sealed class Error(val message: String)

class LimitError(message: String, val limit: String? = null) : Error(message)
class MatchKeyError(message: String, val matchKey: Long? = null) : Error(message)
class SourceMatchIdError(message: String, val sourceMatchId: Int? = null) : Error(message)
class MatchTypeError(message: String, val matchType: String? = null) : Error(message)
class SeasonError(message: String, val season: String? = null) : Error(message)
class ValidationError(message: String) : Error(message)
class DatabaseError(val stackTrace: String, message: String) : Error(message)
