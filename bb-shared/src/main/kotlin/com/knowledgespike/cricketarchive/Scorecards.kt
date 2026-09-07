package com.knowledgespike.cricketarchive

import com.knowledgespike.cricketarchive.shared.http.buildHttpRequest
import com.knowledgespike.cricketarchive.shared.http.fetchResponseWithRetry
import com.knowledgespike.cricketarchive.shared.log
import kotlinx.coroutines.*
import org.jsoup.Connection
import org.jsoup.nodes.Document
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.exists

var log: Logger = LoggerFactory.getLogger("com.knowledgespike.cricketarchive.Scorecards")

const val baseUrl = "https://cricketarchive.com"

const val listUrl = "${baseUrl}/cgi-bin/scorecard_oracle_reveals_results.cgi"
const val counterFileName = "counter"
const val formatString = "%04d"

const val userAgent =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 11_1_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36"


fun getLastId(scorecardsDirectory: String, prefix: String): Int {
    val path = Paths.get("$scorecardsDirectory$prefix")

    if (!path.exists())
        Files.createDirectories(path)

    val fileName = "$scorecardsDirectory$prefix/$counterFileName"

    if (!File(fileName).exists()) {
        Files.createDirectories(path)
        log.info("write file: $fileName")
        File(fileName).writeText("0")
    }

    val value = File(fileName).readLines().first().toInt() + 1

    return value
}


private suspend fun tryGetCard(scorecardsDirectory: String, prefix: String, overwrite: Boolean) {
    var tryGetNextCard = true
    while (tryGetNextCard) {
        val id: Int = getLastId(scorecardsDirectory, prefix)

        val response = getScorecardWithId(prefix, id)
        yield()
        val matchId = "$prefix$id"
        if (response != null) {
            saveCard(response, scorecardsDirectory, overwrite, prefix)
            log.info("Try to get 'not played' matches")
            maybeGetCardsWithAlphabeticSuffix(
                scorecardsDirectory = scorecardsDirectory,
                prefix = prefix,
                matchId = matchId,
                overwrite = overwrite
            )
            updateCounter(scorecardsDirectory, prefix, id)
        } else {
            log.info("Unable to get card with prefix: $prefix and id: $id")
            maybeGetCardsWithAlphabeticSuffix(
                scorecardsDirectory = scorecardsDirectory,
                prefix = prefix,
                matchId = matchId,
                overwrite = overwrite
            )
            log.debug("After call to maybeGetCardsWithAlphabeticSuffix with prefix: $prefix and id: $id")
            tryGetNextCard = false
        }
    }
}

fun cardExists(fqnCardname: String): Boolean {
    return File(fqnCardname).exists()
}

fun saveCard(fileName: String, document: Document, overwrite: Boolean) {
    if (overwrite || !File(fileName).exists()) {
        writeFile(fileName, document)
    }
}

fun getCard(cardName: String): Connection.Response? {
    val url = if (cardName.startsWith("http")) cardName else "$baseUrl$cardName"
    val response = fetchResponseWithRetry(url) ?: return null

    if (response.body().contains("Error in Your Requested Page Address on CricketArchive")) {
        log.warn("Error in Your Requested Page Address on CricketArchive: $cardName")
        return null
    } else if (response.body().contains("does not exist on the database")) {
        log.warn("Match does not exist in database: $cardName")
        return null
    } else if (response.body().contains("Match still in progress")) {
        log.warn("Match still in progress: $cardName")
        return null
    } else if (response.body().contains("Match void")) {
        log.warn("Match void: $cardName")
        return null
    }

    return response
}


fun saveCard(response: Connection.Response, scorecardsDirectory: String, overwrite: Boolean, prefix: String) {

    val responseUrlParts = response.url().path.split("/")

    // URL looks like /Archive/Scorecards/xxx/yyyyyyy.html
    // response parts would be [0] = "", [1] = "Archive", [2] = "Scorecards", [3] = "xxx", [4] = "yyyyyyy.html"
    if (responseUrlParts.size != 5) throw Exception("Expected URL of the form '/Archive/Scorecards/xxx/yyyy.html' but got ${response.url().path}")

    val numericalDirectoryName = String.format(formatString, responseUrlParts[3].toInt())

    // strip of the ".html"
    val fileNamePart = responseUrlParts[4].split(".").first()

    val numericalfileName = String.format("%07d.html", fileNamePart.toInt())

    val directoryName = "${scorecardsDirectory}$prefix/$numericalDirectoryName"
    val fileName = "${directoryName}/$numericalfileName"


    if (overwrite || !File(fileName).exists()) {
        writeFile(response, directoryName, fileName)
    }

}

fun updateCounter(scorecardsDirectory: String, prefix: String, counter: Int) {
    val fullCounterFileName = "$scorecardsDirectory$prefix/$counterFileName"

    // update the counter only after we've successfully processed this URL
    File(fullCounterFileName).writeText(counter.toString())
}

fun getCardsUsingPrefixMap(scorecardsDirectory: String, prefixmap: Map<String, Int>, overwrite: Boolean = true) =
    runBlocking {

        val jobs = mutableListOf<Job>()

        withContext(Dispatchers.IO) {
            prefixmap.map { entry: Map.Entry<String, Int> ->

                val j = launch {
                    var id = getLastId(scorecardsDirectory, entry.key)
                    while (id < entry.value) {

                        val response = getScorecardWithId(entry.key, id)
                        val prefix = entry.key
                        val matchId = "${entry.key}$id"
                        if (response != null) {
                            saveCard(response, scorecardsDirectory, overwrite, entry.key)
                            log.info("Try to get 'not played' matches")
                            updateCounter(scorecardsDirectory, entry.key, id)
                            maybeGetCardsWithAlphabeticSuffix(
                                scorecardsDirectory = scorecardsDirectory,
                                prefix = prefix,
                                matchId = matchId,
                                overwrite = overwrite
                            )
                            yield()
                        } else {
                            log.info("Unable to get card with prefix: $prefix and id: $id")
                            maybeGetCardsWithAlphabeticSuffix(
                                scorecardsDirectory = scorecardsDirectory,
                                prefix = prefix,
                                matchId = matchId,
                                overwrite = overwrite
                            )
                            log.debug("After call to maybeGetCardsWithAlphabeticSuffix with prefix: $prefix and id: $id")
                        }
                        id++
                    }
                }
                jobs.add(j)
            }
        }
        jobs.forEach { j -> j.join() }

        println("getCardsUsingPrefixMap - finished")
    }

suspend fun getScorecardWithId(
    matchId: String,
    counter: Int,
): Connection.Response? {
    val id = "$matchId$counter"

    return getScorecardWithId(id)
}

suspend fun getScorecardWithId(
    matchId: String
): Connection.Response? {

    log.info("try to get matchId: $matchId")
    val connection = buildHttpRequest(listUrl, Connection.Method.POST)

    val response = connection
        .data(mapOf("match" to matchId, "team" to "", "matchtype" to "All", "searchtype" to "All"))
        .execute()

    if (response.statusCode() == 403) {
        log.error("getScorecardWithId ${matchId}: Unable to connect to URL $listUrl status 403")
        delay(1000)
        throw Exception("Unable to connect to URL $listUrl status 403")
    }

    if (response.body().contains("Error in Your Requested Page Address on CricketArchive")) {
        log.warn("Error in Your Requested Page Address on CricketArchive: $matchId")
        return null
    }
    if (response.body().contains("does not exist on the database")) {
        log.warn("Match does not exist in database: $matchId")
        return null
    }
    if (response.body().contains("Match still in progress")) {
        log.warn("Match still in progress: $matchId")
        return null
    }
    if (response.body().contains("Match void")) {
        log.warn("Match void: $matchId")
        return null
    }

    return response
}

suspend fun maybeGetCardsWithAlphabeticSuffix(
    scorecardsDirectory: String,
    prefix: String,
    matchId: String,
    overwrite: Boolean = true
) {

    var tryGetNextCard = true
    var alphaSuffixLetter = 'a'
    var alphaFirstLetter = ""
    var alphaSuffix: String = alphaSuffixLetter.toString()
    val testSuffix = 'z'
    while (tryGetNextCard) {

        log.info("Try to get card with '${matchId + alphaFirstLetter + alphaSuffix}' suffix")
        val response = getScorecardWithId(matchId + alphaFirstLetter + alphaSuffix)
        if (response != null) {
            saveCard(response, scorecardsDirectory, overwrite, prefix)
            alphaSuffixLetter++
            if (testSuffix + 1 == alphaSuffixLetter) {
                alphaSuffixLetter = 'a'
                alphaSuffix = "a"
                alphaFirstLetter = incrementFirstLetter(alphaFirstLetter)
            } else {
                alphaSuffix = alphaSuffixLetter.toString()
            }
        } else {
            log.info("Unable to get card with suffix: ${matchId}${alphaSuffixLetter} ")
            tryGetNextCard = false
        }
    }
}

fun incrementFirstLetter(alphaFirstLetter: String): String {
    return if (alphaFirstLetter.isEmpty()) {
        "a"
    } else {
        val letter = alphaFirstLetter.toCharArray().first()
        (letter + 1).toString()
    }
}

private fun writeFile(response: Connection.Response, directoryName: String, fileName: String) {
    val document = response.parse()

    val path = Paths.get(directoryName)
    log.debug("try to create directory: $directoryName")
    Files.createDirectories(path)
    log.info("write file: $fileName")
    val cardText = document.outerHtml()
    File(fileName).writeText(cardText)
}

private fun writeFile(fileName: String, document: Document) {
    val path = Paths.get(fileName)

    log.debug("try to create directory: $fileName")
    path.parent.toFile().mkdirs()

    log.info("write file: $fileName")
    val cardText = document.outerHtml()
    File(fileName).writeText(cardText)
}

