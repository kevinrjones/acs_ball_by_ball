package com.knowledgespike.ballbyball.parse.parser

import com.knowledgespike.ballbyball.parse.parser.structure.PersonRegistry
import java.io.File
import java.util.stream.Stream

class PlayerRegistryParser {
    fun parse(file: File): Stream<PersonRegistry> = file.useLines { lines ->
        lines.drop(1).filter(String::isNotBlank).map(::parseLine).toList().stream()
    }

    private fun parseLine(line: String): PersonRegistry {
        val values = parseCsvLine(line)
        require(values.size > CRICKET_ARCHIVE_INDEX) { "Player registry row has too few columns" }
        return PersonRegistry(
            id = values[IDENTIFIER_INDEX],
            name = values[NAME_INDEX],
            caId = values[CRICKET_ARCHIVE_INDEX]
        )
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            when (val character = line[index]) {
                '"' -> if (quoted && index + 1 < line.length && line[index + 1] == '"') {
                    value.append('"')
                    index++
                } else {
                    quoted = !quoted
                }

                ',' -> if (quoted) value.append(character) else {
                    values += value.toString()
                    value.clear()
                }

                else -> value.append(character)
            }
            index++
        }
        require(!quoted) { "Player registry row contains an unterminated quoted field" }
        values += value.toString()
        return values
    }

    private companion object {
        const val IDENTIFIER_INDEX = 0
        const val NAME_INDEX = 1
        const val CRICKET_ARCHIVE_INDEX = 13
    }
}

