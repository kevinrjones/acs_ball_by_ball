package com.knowledgespike.cricketarchive.shared.auth

import java.util.concurrent.atomic.AtomicReference

private const val CRICKET_ARCHIVE_EMAIL_ENV_VAR = "CRICKETARCHIVE_EMAIL"
private const val CRICKET_ARCHIVE_PASSWORD_ENV_VAR = "CRICKETARCHIVE_PASSWORD"

data class LoginCredentials(
    val email: String,
    val password: String
)

private val configuredCredentials = AtomicReference<LoginCredentials?>(null)

fun configureLoginCredentials(email: String, password: String): LoginCredentials {
    val credentials = LoginCredentials(
        email = email.trim(),
        password = password
    )

    configuredCredentials.set(credentials)
    return credentials
}

fun resolveCredentials(
    cliEmail: String?,
    cliPassword: String?,
    environmentProvider: () -> Map<String, String> = System::getenv
): Result<LoginCredentials> {
    val normalisedCliEmail = cliEmail.normaliseToNull()
    val normalisedCliPassword = cliPassword.normaliseToNull()

    if (normalisedCliEmail != null || normalisedCliPassword != null) {
        if (normalisedCliEmail == null || normalisedCliPassword == null) {
            return Result.failure(
                IllegalArgumentException(
                    "Both --email and --password must be provided when using CLI credentials"
                )
            )
        }

        return Result.success(configureLoginCredentials(normalisedCliEmail, normalisedCliPassword))
    }

    val environmentVariables = environmentProvider()
    val envEmail = environmentVariables[CRICKET_ARCHIVE_EMAIL_ENV_VAR].normaliseToNull()
    val envPassword = environmentVariables[CRICKET_ARCHIVE_PASSWORD_ENV_VAR].normaliseToNull()

    if (envEmail != null || envPassword != null) {
        if (envEmail == null || envPassword == null) {
            return Result.failure(
                IllegalArgumentException(
                    "Both $CRICKET_ARCHIVE_EMAIL_ENV_VAR and $CRICKET_ARCHIVE_PASSWORD_ENV_VAR must be set"
                )
            )
        }

        return Result.success(configureLoginCredentials(envEmail, envPassword))
    }

    return Result.failure(
        IllegalStateException(
            "CricketArchive credentials are not configured. Provide --email/--password or set $CRICKET_ARCHIVE_EMAIL_ENV_VAR and $CRICKET_ARCHIVE_PASSWORD_ENV_VAR"
        )
    )
}

internal fun resolveConfiguredOrEnvironmentCredentials(
    environmentProvider: () -> Map<String, String> = System::getenv
): Result<LoginCredentials> {
    configuredCredentials.get()?.let { credentials ->
        return Result.success(credentials)
    }

    return resolveCredentials(
        cliEmail = null,
        cliPassword = null,
        environmentProvider = environmentProvider
    )
}

internal fun clearConfiguredLoginCredentials() {
    configuredCredentials.set(null)
}

private fun String?.normaliseToNull(): String? =
    this?.trim()?.takeIf(String::isNotEmpty)