package com.knowledgespike.ballbyball.web

import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.netty.EngineMain
import java.io.File

/**
 * Main application entry point for local IDE, CLI, and containerized executions.
 *
 * Before handing control over to Ktor's [io.ktor.server.netty.EngineMain], this function
 * programmatically loads key-value pairs from a local `.env` file (if present) into Java
 * System properties.
 *
 * Why this is necessary:
 * 1. Ktor's HOCON configuration (`application.conf`) resolves `${?ENV_VAR}` substitutions
 *    against both OS environment variables and Java System properties (`System.getProperties()`).
 * 2. When running directly in IntelliJ IDEA or via `./gradlew run`, IDEs do not automatically
 *    load `.env` files unless an external plugin or manual run configuration is set up.
 * 3. Loading `.env` into System properties here eliminates developer onboarding friction and
 *    ensures zero-config local runs from any IDE or command line.
 * 4. Priority / Precedence:
 *    - Real OS environment variables (e.g. from Docker / Kubernetes / CI) take highest precedence.
 *    - Existing Java System properties (e.g. passed via -D flags) are preserved.
 *    - `.env` entries are only injected if neither an OS environment variable nor a System property
 *      already exists for that key.
 *    - `ignoreIfMissing = true` ensures that missing `.env` files in production/CI do not cause failures.
 */
fun main(args: Array<String>) {
    loadEnvironmentVariables()
    EngineMain.main(args)
}

/**
 * Reads the local `.env` file (if present) and sets its key-value pairs as JVM System properties,
 * skipping any keys that are already present in the OS environment or JVM system properties.
 */
fun loadEnvironmentVariables() {
    val dir = when {
        File("bb-web/.env").exists() -> "bb-web"
        else -> "./"
    }
    val dotenv = dotenv {
        directory = dir
        ignoreIfMissing = true
        ignoreIfMalformed = true
    }

    dotenv.entries().forEach { entry ->
        val key = entry.key
        val value = entry.value
        // Only set the property if not already defined in the OS environment or JVM system properties
        if (System.getenv(key) == null && System.getProperty(key) == null) {
            System.setProperty(key, value)
        }
    }
}