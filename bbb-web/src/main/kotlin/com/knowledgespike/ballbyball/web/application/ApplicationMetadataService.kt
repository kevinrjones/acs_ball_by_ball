package com.knowledgespike.ballbyball.web.application

import com.knowledgespike.ballbyball.contracts.ApplicationMetadata
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Properties
import org.slf4j.LoggerFactory

class ApplicationMetadataService(
    private val matchApiClient: MatchApiClient,
    private val applicationVersion: String = loadApplicationVersion()
) {
    private val logger = LoggerFactory.getLogger(ApplicationMetadataService::class.java)

    suspend fun metadata(): ApplicationMetadata {
        val dataLastUpdated = when (val result = matchApiClient.recentMatches()) {
            is MatchApiResult.Success -> result.timeGenerated?.let(::formatDataTimestamp) ?: UNAVAILABLE
            is MatchApiResult.Unavailable -> {
                logger.warn("Unable to load the data timestamp for application metadata")
                UNAVAILABLE
            }
        }

        return ApplicationMetadata(
            dataLastUpdated = dataLastUpdated,
            applicationVersion = applicationVersion
        )
    }

    companion object {
        private const val UNAVAILABLE = "unavailable"
        private val DATA_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("d MMMM yyyy 'at' HH:mm z", Locale.ENGLISH)
        private val BUILD_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("MMMM yyyy 'at' HH:mm 'GMT'xxx", Locale.ENGLISH)

        private fun loadApplicationVersion(): String {
            val properties = Properties()
            val resource = ApplicationMetadataService::class.java.classLoader
                .getResourceAsStream("version.properties")

            if (resource == null) {
                return "Application version: unavailable"
            }

            resource.use { properties.load(it) }
            val version = properties.getProperty("version", "unavailable").withVersionPrefix()
            val buildDate = properties.getProperty("buildDate")?.let {
                runCatching { OffsetDateTime.parse(it) }.getOrNull()
            }

            return if (buildDate == null) {
                "Application version: $version"
            } else {
                "Application version: $version. Built on " +
                    "${buildDate.dayOfMonth.withOrdinalSuffix()} of " +
                    buildDate.format(BUILD_TIMESTAMP_FORMATTER)
            }
        }

        private fun formatDataTimestamp(timestamp: kotlin.time.Instant): String =
            timestamp.toJavaInstant()
                .atZone(ZoneId.of("Europe/London"))
                .format(DATA_TIMESTAMP_FORMATTER)

        private fun String.withVersionPrefix(): String =
            if (startsWith("v")) this else "v$this"

        private fun Int.withOrdinalSuffix(): String {
            val suffix = when {
                this in 11..13 -> "th"
                this % 10 == 1 -> "st"
                this % 10 == 2 -> "nd"
                this % 10 == 3 -> "rd"
                else -> "th"
            }
            return "$this$suffix"
        }
    }
}

private fun kotlin.time.Instant.toJavaInstant(): java.time.Instant =
    java.time.Instant.parse(toString())