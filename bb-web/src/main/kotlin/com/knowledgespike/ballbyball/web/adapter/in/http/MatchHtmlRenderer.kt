package com.knowledgespike.ballbyball.web.adapter.`in`.http

import com.knowledgespike.ballbyball.contracts.MatchSummary

internal fun renderMatches(matches: List<MatchSummary>): String = if (matches.isEmpty()) {
    "<p>No matches found.</p>"
} else {
    buildString {
        append("<ul>")
        matches.forEach { match ->
            append("<li><strong>${match.fileName.escapeHtml()}</strong> ")
            append("(${match.matchType.escapeHtml()}, ${match.season.escapeHtml()})</li>")
        }
        append("</ul>")
    }
}

private fun String.escapeHtml(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")