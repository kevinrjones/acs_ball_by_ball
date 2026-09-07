package com.knowledgespike.cricketarchive.shared

import com.knowledgespike.cricketarchive.InvalidStateException
import com.knowledgespike.cricketarchive.LoggerDelegate
import com.knowledgespike.cricketarchive.cardExists
import com.knowledgespike.cricketarchive.formatString
import com.knowledgespike.cricketarchive.getCard
import com.knowledgespike.cricketarchive.saveCard
import com.knowledgespike.cricketarchive.shared.http.fetchWithRetry
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.collections.forEach

var log: Logger = LoggerFactory.getLogger("com.knowledgespike.cricketarchive.shared.MatchListPage")
val internationalPrefixes = listOf("t", "wt", "o", "wo", "itt", "witt")
const val baseUrl = "https://cricketarchive.com"

internal class MatchListPageParser {
    private val log by LoggerDelegate()


    fun parseCardsPage(
        html: String,
        baseUri: String,
        tableSelector: String = "#columnLeft > center > table > tbody > tr"
    ): List<Pair<String, String>> {

        val document: Document = Jsoup.parse(html, baseUri)

        val matchTable: Elements = document.select(tableSelector)
        return matchTable.map {

            val tds = it.getElementsByTag("td")

            if (tds.size == 7) {
                val a = tds[4].getElementsByTag("a").first()
                val href = a?.attr("abs:href")
                // URL, MatchId (eg: f12345)
                if (href != null)
                    Pair(href, tds[6].text())
                else
                    Pair("", "")
            } else {
                Pair("", "")
            }
        }
    }


    fun parseMatchNumbers(elements: Elements): String {
        val matchOrderSelector = "tbody > tr:nth-child(1) > td:nth-child(1)"

        val matchOrderContainer: Elements? = elements.select(matchOrderSelector)
        if (matchOrderContainer != null) {
            val matchOrderContainerNodes: MutableList<TextNode> = matchOrderContainer[0].textNodes()


            // D == non-digit, d == digit
            // so, one or more non-digits followed by one or more digits
            val regex = Regex("(\\D+)(\\d+)")


            matchOrderContainerNodes.forEach {

                val values = regex.find(it.text())

                if (values == null || values.groupValues.size != 3) throw InvalidStateException()

                val matchId = it.text()

                // some South African matches have a code of 'franXXXX' e.g. "f/40001/00042633.html" so have to check
                // that it's a fc code not a 'fran' code
                if ((matchId.startsWith("f") && !it.text().startsWith("fr"))
                    || matchId.startsWith("a")
                    || matchId.startsWith("tt")
                    || matchId.startsWith("wf")
                    || matchId.startsWith("wa")
                    || matchId.startsWith("wtt")
                ) {
                    log.info("get match with id $matchId")
                    return matchId
                }
            }
        }

        return ""
    }
}

/**
 * Parses the given HTML body and extracts card-related data in the form of pairs.
 * Each pair consists of a URL and a match identifier.
 *
 * @param body The HTML content of the page to be parsed.
 * @return A list of pairs where each pair contains a URL as the first element
 *         and a match identifier as the second element. If the extraction fails
 *         for a row, an empty pair ("", "") is returned.
 */
private fun parseCardsPage(body: String, baseUri: String, selector: String): List<Pair<String, String>> {

    val parser = MatchListPageParser()

    return parser.parseCardsPage(body, baseUri, selector)
}

fun getListOfCardUrlsAndMatchIdsFromPage(parPageUrl: String, selector: String): List<Pair<String, String>>? {
    val fqPageUrl = if (parPageUrl.startsWith("http")) {
        parPageUrl
    } else if (parPageUrl.startsWith("/")) {
        "${baseUrl}${parPageUrl}"
    } else {
        "${baseUrl}/${parPageUrl}"
    }
    return fetchWithRetry(
        url = fqPageUrl,
        onSuccess = { response ->
            parseCardsPage(response.body(), fqPageUrl, selector).filter { it.first.isNotEmpty() }
        }
    )
}

fun getPage(
    prefixes: List<String>,
    scorecardsDirectory: String,
    nightlyScorecardsDirectory: String?,
    cards: List<Pair<String, String>>,
    overwrite: Boolean = false
): Boolean {

    var allSucceeded = true
    cards.forEach { card ->
        val (url, matchId) = card
        val prefix = getPrefix(matchId)


        if (prefixes.contains(prefix)) {
            log.info("Try to get card with url: $url and id: $matchId")
            val success = if (nightlyScorecardsDirectory != null) {
                getCardFromUrlAndMatchId(url, matchId, scorecardsDirectory, nightlyScorecardsDirectory, overwrite)
            } else {
                getCardFromUrlAndMatchId(url, matchId, scorecardsDirectory, overwrite)
            }
            if (!success) allSucceeded = false
        }
    }
    return allSucceeded
}

fun getCardFromUrlAndMatchId(
    url: String, matchId: String,
    scorecardsDirectory: String,
    nightlyScorecardsDirectory: String,
    overwrite: Boolean
): Boolean {
    val prefix = getPrefix(matchId)

    val fqnCardNameWithFullDirectory =
        getFullyQualifiedCardName(scorecardsDirectory = scorecardsDirectory, url = url, prefix = prefix)
    val fqnCardScorcardsNameWithNightlyDirectory =
        getFullyQualifiedCardName(
            scorecardsDirectory = nightlyScorecardsDirectory,
            url = url,
            prefix = prefix
        )
    if (overwrite
        || !cardExists(fqnCardNameWithFullDirectory)
        || !cardExists(fqnCardScorcardsNameWithNightlyDirectory)
    ) {
        val getCardResponse = getCard(url)

        if (getCardResponse?.statusCode() == 200) {
            val document = getCardResponse.parse()
            saveCard(fileName = fqnCardNameWithFullDirectory, document = document, overwrite = overwrite)
            saveCard(
                fileName = fqnCardScorcardsNameWithNightlyDirectory,
                document = document,
                overwrite = overwrite
            )
            if (prefix in internationalPrefixes) {
                val otherPrefix = getOtherPrefix(document)
                val fqnCardNameWithFullDirectory =
                    getFullyQualifiedCardName(
                        scorecardsDirectory = scorecardsDirectory,
                        url = url,
                        prefix = otherPrefix
                    )
                val fqnCardScorcardsNameWithNightlyDirectory =
                    getFullyQualifiedCardName(
                        scorecardsDirectory = nightlyScorecardsDirectory,
                        url = url,
                        prefix = otherPrefix
                    )

                saveCard(fileName = fqnCardNameWithFullDirectory, document = document, overwrite = overwrite)
                saveCard(
                    fileName = fqnCardScorcardsNameWithNightlyDirectory,
                    document = document,
                    overwrite = overwrite
                )
            }
            return true
        } else {
            log.warn("Unable to get card with url: ${url} and id: ${matchId}, response code : ${getCardResponse?.statusCode()}")
            return false
        }
    }
    return true
}

fun getCardFromUrlAndMatchId(
    url: String, matchId: String,
    scorecardsDirectory: String,
    overwrite: Boolean
): Boolean {
    val prefix = getPrefix(matchId)

    val fqnCardNameWithFullDirectory =
        getFullyQualifiedCardName(scorecardsDirectory = scorecardsDirectory, url = url, prefix = prefix)
    if (overwrite
        || !cardExists(fqnCardNameWithFullDirectory)
    ) {
        val getCardResponse = getCard(url)

        if (getCardResponse?.statusCode() == 200) {
            val document = getCardResponse.parse()
            saveCard(fileName = fqnCardNameWithFullDirectory, document = document, overwrite = overwrite)
            if (prefix in internationalPrefixes) {
                val otherPrefix = getOtherPrefix(document)
                val fqnCardNameWithFullDirectory =
                    getFullyQualifiedCardName(
                        scorecardsDirectory = scorecardsDirectory,
                        url = url,
                        prefix = otherPrefix
                    )
                saveCard(fileName = fqnCardNameWithFullDirectory, document = document, overwrite = overwrite)
            }
            return true
        } else {
            log.warn("Unable to get card with url: ${url} and id: ${matchId}, response code : ${getCardResponse?.statusCode()}")
            return false
        }
    }
    return true
}

fun getFullyQualifiedCardName(prefix: String, url: String, scorecardsDirectory: String): String {
    val relativeUrl = if (url.startsWith("http")) {
        val uri = java.net.URI(url)
        uri.path
    } else {
        url
    }
    val parts = relativeUrl.split("/")
    if (parts.size != 5) throw Exception("Expected URL of the form '/Archive/Scorecards/xxx/yyyy.html' but got ${url}")

    val numericalDirectoryName = String.format(formatString, parts[3].toInt())

    // strip of the ".html"
    val fileNamePart = parts[4].split(".").first()

    val numericalfileName = String.format("%07d.html", fileNamePart.toInt())

    val directoryName = "${scorecardsDirectory}$prefix/$numericalDirectoryName"
    return "${directoryName}/$numericalfileName"
}

fun getPrefix(cardId: String): String {
    val regex = Regex("([a-z]*)([0-9]*)([a-z]*)")
    val result = regex.find(cardId)
    check(result != null)
    check(result.groupValues.size == 4)
    val prefix = result.groupValues[1]
    return prefix
}


fun getOtherPrefix(
    document: Document
): String {
    val parser = MatchListPageParser()

    val headerSelector = "#columnLeft > table:nth-child(3)"

    val elements: Elements = document.select(headerSelector) ?: throw InvalidStateException()
    val matchId = parser.parseMatchNumbers(elements)

    if (matchId.isNotEmpty()) {
        val regex = Regex("(\\D+)(\\d+)")

        val values = regex.find(matchId)

        if (values == null || values.groupValues.size != 3) throw InvalidStateException()
        return values.groupValues[1]
    }
    return ""
}