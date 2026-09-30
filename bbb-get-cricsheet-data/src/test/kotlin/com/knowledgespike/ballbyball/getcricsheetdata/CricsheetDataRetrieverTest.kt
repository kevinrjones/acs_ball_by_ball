package com.knowledgespike.ballbyball.getcricsheetdata

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertFailsWith

class CricsheetDataRetrieverTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `given matching page when retrieved then JSON archives and register CSVs are stored`() {
        val sources = testSources()
        val archive = URI("https://cricsheet.org/downloads/odis_json.zip")
        val httpClient = FakeHttpClient(
            page = """
                <a href="/downloads/tests_json.zip">Tests</a>
                <a href="/downloads/all_json.zip">All</a>
                <a href="/downloads/tests.zip">YAML</a>
                <a href="https://example.com/other_json.zip">External</a>
                <a href="https://cricsheet.org/">Home</a>
                <a href="${archive.path}">ODIs</a>
            """.trimIndent(),
            files = mapOf(
                URI("https://cricsheet.org/downloads/tests_json.zip") to zipBytes("tests.json"),
                archive to zipBytes("odis.json"),
                sources.peopleCsv to PEOPLE_CSV.toByteArray(),
                sources.namesCsv to NAMES_CSV.toByteArray()
            )
        )
        val configuration = configuration()

        val result = CricsheetDataRetriever(httpClient, sources).retrieve(configuration)

        expectThat(result.succeeded).isTrue()
        expectThat(httpClient.downloads.toList()).isEqualTo(
            listOf(
                URI("https://cricsheet.org/downloads/odis_json.zip"),
                URI("https://cricsheet.org/downloads/tests_json.zip"),
                sources.peopleCsv,
                sources.namesCsv
            )
        )
        expectThat(Files.readString(configuration.dataDirectory.resolve("tests_json/tests.json"))).isEqualTo("tests.json")
        expectThat(Files.readString(configuration.dataDirectory.resolve("odis_json/odis.json"))).isEqualTo("odis.json")
        expectThat(Files.exists(configuration.dataDirectory.resolve("zips/odis_json.zip"))).isTrue()
        expectThat(Files.readString(configuration.namesDirectory.resolve("people.csv"))).isEqualTo(PEOPLE_CSV)
        expectThat(Files.readString(configuration.namesDirectory.resolve("names.csv"))).isEqualTo(NAMES_CSV)
    }

    @Test
    fun `given existing destination without force when retrieved then no network request is made`() {
        val configuration = configuration()
        Files.createDirectories(configuration.dataDirectory)
        val httpClient = FakeHttpClient(page = "")

        assertFailsWith<IllegalArgumentException> {
            CricsheetDataRetriever(httpClient, testSources()).retrieve(configuration)
        }

        expectThat(httpClient.downloads).isEmpty()
        expectThat(httpClient.pageRequests).isEmpty()
    }

    @Test
    fun `given failed archive when retrieved then register CSVs still complete and failure is reported`() {
        val sources = testSources()
        val failedArchive = URI("https://cricsheet.org/downloads/tests_json.zip")
        val httpClient = FakeHttpClient(
            page = "<a href=\"/downloads/tests_json.zip\">Tests</a>",
            files = mapOf(
                sources.peopleCsv to PEOPLE_CSV.toByteArray(),
                sources.namesCsv to NAMES_CSV.toByteArray()
            ),
            failedDownloads = setOf(failedArchive)
        )

        val result = CricsheetDataRetriever(httpClient, sources).retrieve(configuration())

        expectThat(result.succeeded).isFalse()
        expectThat(result.failures.map { it.artifact }).contains("tests_json.zip")
        expectThat(Files.exists(temporaryDirectory.resolve("names/people.csv"))).isTrue()
        expectThat(Files.exists(temporaryDirectory.resolve("names/names.csv"))).isTrue()
    }

    @Test
    fun `given unsafe archive entry when retrieved then archive fails without escaping data directory`() {
        val sources = testSources()
        val archive = URI("https://cricsheet.org/downloads/tests_json.zip")
        val httpClient = FakeHttpClient(
            page = "<a href=\"/downloads/tests_json.zip\">Tests</a>",
            files = mapOf(
                archive to zipBytes("../outside.json"),
                sources.peopleCsv to PEOPLE_CSV.toByteArray(),
                sources.namesCsv to NAMES_CSV.toByteArray()
            )
        )

        val result = CricsheetDataRetriever(httpClient, sources).retrieve(configuration())

        expectThat(result.failures.map { it.artifact }).contains("tests_json.zip")
        expectThat(Files.exists(temporaryDirectory.resolve("outside.json"))).isFalse()
    }

    @Test
    fun `given invalid CSV response when forced then existing CSV is preserved`() {
        val sources = testSources()
        val configuration = configuration(force = true)
        Files.createDirectories(configuration.dataDirectory)
        Files.createDirectories(configuration.namesDirectory)
        val existingPeople = configuration.namesDirectory.resolve("people.csv")
        Files.writeString(existingPeople, PEOPLE_CSV)
        val httpClient = FakeHttpClient(
            page = "",
            files = mapOf(
                sources.peopleCsv to "not,a,register".toByteArray(),
                sources.namesCsv to NAMES_CSV.toByteArray()
            )
        )

        val result = CricsheetDataRetriever(httpClient, sources).retrieve(configuration)

        expectThat(result.failures.map { it.artifact }).contains("archive discovery")
        expectThat(result.failures.map { it.artifact }).contains("people.csv")
        expectThat(Files.readString(existingPeople)).isEqualTo(PEOPLE_CSV)
    }

    @Test
    fun `given nightly configuration when retrieved then fixed archives precede register CSVs`() {
        val sources = testSources()
        val httpClient = FakeHttpClient(
            page = "unexpected page request",
            files = mapOf(
                sources.nightlyArchives[0] to zipBytes("recent.json"),
                sources.nightlyArchives[1] to zipBytes("recent.json"),
                sources.peopleCsv to PEOPLE_CSV.toByteArray(),
                sources.namesCsv to NAMES_CSV.toByteArray()
            )
        )
        val configuration = configuration(nightly = true)

        val result = CricsheetDataRetriever(httpClient, sources).retrieve(configuration)

        expectThat(result.succeeded).isTrue()
        expectThat(httpClient.pageRequests).isEmpty()
        expectThat(httpClient.downloads.toList()).isEqualTo(
            listOf(
                sources.nightlyArchives[0],
                sources.nightlyArchives[1],
                sources.peopleCsv,
                sources.namesCsv
            )
        )
        expectThat(Files.exists(configuration.zipDirectory.resolve("recently_added_7_json.zip"))).isTrue()
        expectThat(Files.exists(configuration.zipDirectory.resolve("recently_added_2_json.zip"))).isTrue()
    }

    @Test
    fun `given failed first nightly archive when retrieved then remaining artifacts are attempted`() {
        val sources = testSources()
        val httpClient = FakeHttpClient(
            page = "unexpected page request",
            files = mapOf(
                sources.nightlyArchives[1] to zipBytes("recent.json"),
                sources.peopleCsv to PEOPLE_CSV.toByteArray(),
                sources.namesCsv to NAMES_CSV.toByteArray()
            ),
            failedDownloads = setOf(sources.nightlyArchives[0])
        )

        val result = CricsheetDataRetriever(httpClient, sources).retrieve(configuration(nightly = true))

        expectThat(result.succeeded).isFalse()
        expectThat(httpClient.downloads.toList()).isEqualTo(
            listOf(
                sources.nightlyArchives[0],
                sources.nightlyArchives[1],
                sources.peopleCsv,
                sources.namesCsv
            )
        )
        expectThat(result.failures.map { it.artifact }).contains("recently_added_7_json.zip")
    }

    @Test
    fun `given existing nightly directories when retrieved then directories are reused`() {
        val sources = testSources()
        val configuration = configuration(nightly = true)
        Files.createDirectories(configuration.dataDirectory)
        Files.createDirectories(configuration.namesDirectory)
        val staleFile = configuration.dataDirectory.resolve("stale.json")
        Files.writeString(staleFile, "stale")
        Files.writeString(configuration.dataDirectory.resolve("recent.json"), "old")
        Files.writeString(configuration.namesDirectory.resolve("people.csv"), "old")
        Files.writeString(configuration.namesDirectory.resolve("names.csv"), "old")
        val httpClient = FakeHttpClient(
            page = "unexpected page request",
            files = mapOf(
                sources.nightlyArchives[0] to zipBytes("recent.json"),
                sources.nightlyArchives[1] to zipBytes("recent.json"),
                sources.peopleCsv to PEOPLE_CSV.toByteArray(),
                sources.namesCsv to NAMES_CSV.toByteArray()
            )
        )

        val result = CricsheetDataRetriever(httpClient, sources).retrieve(configuration)

        expectThat(result.succeeded).isTrue()
        expectThat(Files.readString(staleFile)).isEqualTo("stale")
        expectThat(Files.readString(configuration.dataDirectory.resolve("recent.json"))).isEqualTo("recent.json")
        expectThat(Files.readString(configuration.namesDirectory.resolve("people.csv"))).isEqualTo(PEOPLE_CSV)
        expectThat(Files.readString(configuration.namesDirectory.resolve("names.csv"))).isEqualTo(NAMES_CSV)
    }

    @Test
    fun `given nightly data destination is a file when retrieved then no network request is made`() {
        val configuration = configuration(nightly = true)
        Files.createDirectories(requireNotNull(configuration.dataDirectory.parent))
        Files.createFile(configuration.dataDirectory)
        val httpClient = FakeHttpClient(page = "unexpected page request")

        assertFailsWith<IllegalArgumentException> {
            CricsheetDataRetriever(httpClient, testSources()).retrieve(configuration)
        }

        expectThat(httpClient.downloads).isEmpty()
        expectThat(httpClient.pageRequests).isEmpty()
    }

    @Test
    fun `given overlapping nightly archives when retrieved then second archive wins in flat layout`() {
        val sources = testSources()
        val configuration = configuration(nightly = true)
        val httpClient = FakeHttpClient(
            page = "unexpected page request",
            files = mapOf(
                sources.nightlyArchives[0] to zipBytes(
                    mapOf(
                        "shared.json" to "from seven",
                        "only-seven.json" to "seven"
                    )
                ),
                sources.nightlyArchives[1] to zipBytes(
                    mapOf(
                        "shared.json" to "from two",
                        "only-two.json" to "two"
                    )
                ),
                sources.peopleCsv to PEOPLE_CSV.toByteArray(),
                sources.namesCsv to NAMES_CSV.toByteArray()
            )
        )

        val result = CricsheetDataRetriever(httpClient, sources).retrieve(configuration)

        expectThat(result.succeeded).isTrue()
        expectThat(Files.readString(configuration.dataDirectory.resolve("shared.json"))).isEqualTo("from two")
        expectThat(Files.readString(configuration.dataDirectory.resolve("only-seven.json"))).isEqualTo("seven")
        expectThat(Files.readString(configuration.dataDirectory.resolve("only-two.json"))).isEqualTo("two")
        expectThat(Files.exists(configuration.dataDirectory.resolve("recently_added_7_json"))).isFalse()
        expectThat(Files.exists(configuration.dataDirectory.resolve("recently_added_2_json"))).isFalse()
    }

    private fun configuration(
        force: Boolean = false,
        nightly: Boolean = false
    ): RetrievalConfiguration = RetrievalConfiguration(
        baseDirectory = temporaryDirectory,
        dataDirectory = if (nightly) {
            temporaryDirectory.resolve("nightly/data")
        } else {
            temporaryDirectory.resolve("data")
        },
        namesDirectory = temporaryDirectory.resolve("names"),
        force = force,
        nightly = nightly
    )

    private fun testSources(): CricsheetSources = CricsheetSources(
        matchesPage = URI("https://cricsheet.org/matches/"),
        peopleCsv = URI("https://cricsheet.org/register/people.csv"),
        namesCsv = URI("https://cricsheet.org/register/names.csv"),
        nightlyArchives = listOf(
            URI("https://cricsheet.org/downloads/recently_added_7_json.zip"),
            URI("https://cricsheet.org/downloads/recently_added_2_json.zip")
        )
    )

    private fun zipBytes(fileName: String): ByteArray = zipBytes(mapOf(fileName to fileName))

    private fun zipBytes(files: Map<String, String>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            files.forEach { (fileName, content) ->
                zip.putNextEntry(ZipEntry(fileName))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        output.toByteArray()
    }

    private class FakeHttpClient(
        private val page: String,
        private val files: Map<URI, ByteArray> = emptyMap(),
        private val failedDownloads: Set<URI> = emptySet()
    ) : CricsheetHttpClient {
        val pageRequests = mutableListOf<URI>()
        val downloads = mutableListOf<URI>()

        override fun getText(uri: URI): String {
            pageRequests += uri
            return page
        }

        override fun download(uri: URI, destination: Path) {
            downloads += uri
            if (uri in failedDownloads) {
                throw IOException("simulated failure")
            }
            Files.write(destination, files.getValue(uri))
        }
    }

    private companion object {
        const val PEOPLE_CSV = "identifier,name,unique_name,key_bcci,key_bcci_2,key_bigbash,key_cricbuzz,key_cricheroes,key_crichq,key_cricinfo,key_cricinfo_2,key_cricinfo_3,key_cricingif,key_cricketarchive,key_cricketarchive_2,key_cricketworld,key_nvplay,key_nvplay_2,key_opta,key_opta_2,key_pulse,key_pulse_2\np1,A Player,A Player,,,,,,,,,,,,,,,,,,,\n"
        const val NAMES_CSV = "identifier,name\np1,Player\n"
    }
}