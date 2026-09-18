package com.knowledgespike.ballbyball.contracts

import kotlinx.serialization.Serializable

/**
 * A generic class representing the result of a database query.
 *
 * @param T The type of the items contained within the result.
 * @property data A list containing the result items of the database query.
 * @property count The total number of items included in the result.
 */
@Serializable
open class DatabaseResult<T>(val data: List<T>, val count: Int)

