package com.knowledgespike.cricsheet.parse.parser

import com.knowledgespike.cricsheet.parse.parser.structure.PersonRegistry
import java.io.File
import java.util.stream.Stream

class PlayerRegistryParser {
    fun parse(file: File): Stream<PersonRegistry> {
        return file.readLines().stream().skip(1).map {
            val values = it.split(",")
            PersonRegistry(values[0], values[1], values[11])
        } ?: listOf<PersonRegistry>().stream()
    }
}

