package com.knowledgespike.ballbyball.getcricsheetdata

import com.knowledgespike.cricketarchive.LoggerDelegate
import org.jsoup.Jsoup
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.UUID
import java.util.zip.ZipFile

data class RetrievalConfiguration(
    val baseDirectory: Path,
    val dataDirectory: Path,
    val namesDirectory: Path,
    val force: Boolean,
    val nightly: Boolean = false
) {
    val zipDirectory: Path
        get() = dataDirectory.resolve("zips")
}

data class CricsheetSources(
    val matchesPage: URI = URI("https://cricsheet.org/matches/"),
    val peopleCsv: URI = URI("https://cricsheet.org/register/people.csv"),
    val namesCsv: URI = URI("https://cricsheet.org/register/names.csv"),
    val nightlyArchives: List<URI> = listOf(
        URI("https://cricsheet.org/downloads/recently_added_7_json.zip"),
        URI("https://cricsheet.org/downloads/recently_added_2_json.zip")
    )
)

data class RetrievalFailure(
    val artifact: String,
    val reason: String
)

data class RetrievalResult(
    val failures: List<RetrievalFailure>
) {
    val succeeded: Boolean
        get() = failures.isEmpty()
}

class CricsheetDataRetriever(
    private val httpClient: CricsheetHttpClient = JdkHttpClientTransport(),
    private val sources: CricsheetSources = CricsheetSources()
) {
    private val log by LoggerDelegate()

    fun retrieve(configuration: RetrievalConfiguration): RetrievalResult {
        prepareDirectories(configuration)

        val failures = mutableListOf<RetrievalFailure>()
        val archiveLinks = if (configuration.nightly) {
            sources.nightlyArchives
        } else {
            discoverArchiveLinks(failures)
        }
        if (!configuration.nightly && archiveLinks.isEmpty() && failures.none { it.artifact == "archive discovery" }) {
            failures += RetrievalFailure("archive discovery", "No qualifying JSON archives were found")
            log.error("No qualifying JSON archives were found at {}", sources.matchesPage)
        }

        archiveLinks.forEach { archive ->
            processArchive(archive, configuration, failures)
        }
        processCsv(
            uri = sources.peopleCsv,
            destination = configuration.namesDirectory.resolve("people.csv"),
            expectedHeader = PEOPLE_HEADER,
            failures = failures
        )
        processCsv(
            uri = sources.namesCsv,
            destination = configuration.namesDirectory.resolve("names.csv"),
            expectedHeader = NAMES_HEADER,
            failures = failures
        )

        log.info(
            "Cricsheet retrieval completed with {} failures out of {} artifacts",
            failures.size,
            archiveLinks.size + 2
        )
        return RetrievalResult(failures.toList())
    }

    private fun discoverArchiveLinks(failures: MutableList<RetrievalFailure>): List<URI> = try {
        val document = Jsoup.parse(httpClient.getText(sources.matchesPage), sources.matchesPage.toString())
        document.select("a[href]")
            .mapNotNull { link -> qualifyingArchiveUri(link.attr("href")) }
            .distinctBy { it.path }
            .sortedBy { archiveName(it) }
            .also { archives ->
                log.info("Discovered {} JSON archives at {}", archives.size, sources.matchesPage)
            }
    } catch (exception: Exception) {
        recordFailure("archive discovery", exception, failures)
        emptyList()
    }

    private fun qualifyingArchiveUri(href: String): URI? = try {
        val uri = sources.matchesPage.resolve(href)
        val path = uri.path ?: return null
        val name = archiveName(uri) ?: return null
        uri.takeIf {
            it.scheme.equals("https", ignoreCase = true) &&
                it.host.equals("cricsheet.org", ignoreCase = true) &&
                path.startsWith("/downloads/") &&
                name.endsWith("_json.zip") &&
                name != "all_json.zip"
        }
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun processArchive(
        uri: URI,
        configuration: RetrievalConfiguration,
        failures: MutableList<RetrievalFailure>
    ) {
        val name = requireNotNull(archiveName(uri)) { "Archive URI has no filename: $uri" }
        try {
            val zipFile = configuration.zipDirectory.resolve(name)
            log.info("Downloading archive {}", uri)
            downloadAtomically(uri, zipFile)
            val stagingDirectory = Files.createTempDirectory(
                configuration.dataDirectory,
                ".${name.removeSuffix(".zip")}-${UUID.randomUUID()}-"
            )
            try {
                val extractionDirectory = if (configuration.nightly) {
                    stagingDirectory
                } else {
                    stagingDirectory.resolve(name.removeSuffix(".zip")).also(Files::createDirectories)
                }
                extractSafely(zipFile, extractionDirectory)
                commitExtraction(stagingDirectory, configuration.dataDirectory)
            } finally {
                deleteRecursively(stagingDirectory)
            }
            log.info("Downloaded and extracted {}", name)
        } catch (exception: Exception) {
            recordFailure(name, exception, failures)
        }
    }

    private fun processCsv(
        uri: URI,
        destination: Path,
        expectedHeader: String,
        failures: MutableList<RetrievalFailure>
    ) {
        try {
            log.info("Downloading CSV {}", uri)
            val temporaryFile = temporaryFile(destination)
            try {
                httpClient.download(uri, temporaryFile)
                validateCsv(temporaryFile, expectedHeader)
                moveReplacing(temporaryFile, destination)
            } finally {
                Files.deleteIfExists(temporaryFile)
            }
            log.info("Downloaded {}", destination.fileName)
        } catch (exception: Exception) {
            recordFailure(destination.fileName.toString(), exception, failures)
        }
    }

    private fun downloadAtomically(uri: URI, destination: Path) {
        val temporaryFile = temporaryFile(destination)
        try {
            httpClient.download(uri, temporaryFile)
            validateZip(temporaryFile)
            moveReplacing(temporaryFile, destination)
        } finally {
            Files.deleteIfExists(temporaryFile)
        }
    }

    private fun validateCsv(file: Path, expectedHeader: String) {
        val header = Files.newBufferedReader(file).use { reader -> reader.readLine()?.removePrefix("\uFEFF") }
        require(header == expectedHeader) {
            "${file.fileName} has unexpected header: ${header ?: "<empty>"}"
        }
    }

    private fun validateZip(file: Path) {
        ZipFile(file.toFile()).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                safeEntryPath(entry.name)
            }
        }
    }

    private fun extractSafely(zipFile: Path, stagingDirectory: Path) {
        ZipFile(zipFile.toFile()).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val destination = stagingDirectory.resolve(safeEntryPath(entry.name)).normalize()
                require(destination.startsWith(stagingDirectory)) {
                    "ZIP entry escapes the staging directory: ${entry.name}"
                }
                if (entry.isDirectory) {
                    Files.createDirectories(destination)
                } else {
                    Files.createDirectories(destination.parent)
                    zip.getInputStream(entry).use { input ->
                        Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
        }
    }

    private fun commitExtraction(stagingDirectory: Path, dataDirectory: Path) {
        Files.walk(stagingDirectory).use { files ->
            files.filter { Files.isRegularFile(it) }
                .sorted()
                .forEach { stagedFile ->
                    val destination = dataDirectory.resolve(stagingDirectory.relativize(stagedFile)).normalize()
                    require(destination.startsWith(dataDirectory)) {
                        "Staged file escapes the data directory: $stagedFile"
                    }
                    Files.createDirectories(destination.parent)
                    moveReplacing(stagedFile, destination)
                }
        }
    }

    private fun safeEntryPath(name: String): Path {
        require(name.isNotBlank()) { "ZIP entry must have a name" }
        val path = Path.of(name).normalize()
        require(!path.isAbsolute && !path.startsWith(Path.of(".."))) {
            "ZIP entry escapes the destination: $name"
        }
        return path
    }

    private fun prepareDirectories(configuration: RetrievalConfiguration) {
        val destinations = listOf(configuration.dataDirectory, configuration.namesDirectory).distinct()
        destinations.filter { Files.exists(it) }.forEach { destination ->
            require(Files.isDirectory(destination)) {
                "Destination is not a directory: $destination"
            }
            require(configuration.nightly || configuration.force) {
                "Destination already exists; use --force to continue: $destination"
            }
        }
        Files.createDirectories(configuration.baseDirectory)
        destinations.forEach(Files::createDirectories)
        Files.createDirectories(configuration.zipDirectory)
    }

    private fun temporaryFile(destination: Path): Path = Files.createTempFile(
        destination.parent,
        ".${destination.fileName}.",
        ".part"
    )

    private fun moveReplacing(source: Path, destination: Path) {
        try {
            Files.move(
                source,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun deleteRecursively(directory: Path) {
        if (!Files.exists(directory)) {
            return
        }
        Files.walk(directory).use { files ->
            files.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private fun recordFailure(artifact: String, exception: Exception, failures: MutableList<RetrievalFailure>) {
        val reason = exception.message ?: exception::class.simpleName.orEmpty()
        failures += RetrievalFailure(artifact, reason)
        log.error("Unable to retrieve {}: {}", artifact, reason, exception)
    }

    companion object {
        private const val PEOPLE_HEADER = "identifier,name,unique_name,key_bcci,key_bcci_2,key_bigbash,key_cricbuzz,key_cricheroes,key_crichq,key_cricinfo,key_cricinfo_2,key_cricinfo_3,key_cricingif,key_cricketarchive,key_cricketarchive_2,key_cricketworld,key_nvplay,key_nvplay_2,key_opta,key_opta_2,key_pulse,key_pulse_2"
        private const val NAMES_HEADER = "identifier,name"

        private fun archiveName(uri: URI): String? =
            uri.path?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }
}