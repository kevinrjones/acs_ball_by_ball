package com.knowledgespike.cricsheet.parse.database

data class PersonRegistryEntity(val id: String, val name: String, val caId: Int)

data class Team(val id: Int, val name: String)

data class Location(val id: Int, val name: String)