package com.knowledgespike.ballbyball.api.feature.matches.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.slf4j.Logger

internal suspend fun <T> withMatchQuery(
    log: Logger,
    ioDispatcher: CoroutineDispatcher,
    operation: String,
    block: () -> T
): T = withContext(ioDispatcher) {
    try {
        block()
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        log.error("$operation failed", cause)
        throw cause
    }
}
