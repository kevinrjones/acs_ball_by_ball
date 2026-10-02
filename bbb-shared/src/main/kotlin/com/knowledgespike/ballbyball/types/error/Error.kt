package com.knowledgespike.ballbyball.types.error

import kotlinx.serialization.Serializable

@Serializable
sealed class Error(val message: String)

class LimitError(message: String, val limit: String? = null) : Error(message)
class MatchKeyError(message: String, val matchKey: Long? = null) : Error(message)
class PublicMatchIdError(message: String, val publicMatchId: Long? = null) : Error(message)
class SourceMatchIdError(message: String, val sourceMatchId: Long? = null) : Error(message)
class MatchTypeError(message: String, val matchType: String? = null) : Error(message)
class SeasonError(message: String, val season: String? = null) : Error(message)
class PageNumberError(message: String, val page: String? = null) : Error(message)
class PageSizeError(message: String, val pageSize: String? = null) : Error(message)
class SearchTeamError(message: String, val team: String? = null) : Error(message)
class ExactMatchError(message: String, val value: String? = null) : Error(message)
class SearchDateError(message: String, val date: String? = null) : Error(message)
class SearchDateRangeError(
    message: String,
    val startDate: String? = null,
    val endDate: String? = null
) : Error(message)
class VenueFilterError(message: String, val venue: String? = null) : Error(message)
class MatchTypeFilterError(message: String, val matchType: String? = null) : Error(message)
class MatchResultFilterError(message: String, val matchResult: String? = null) : Error(message)
class ValidationError(message: String) : Error(message)
class DatabaseError(val stackTrace: String, message: String) : Error(message)
