package com.knowledgespike.cricsheet.parse.database

data class PersonRegistryEntity(val id: String, val name: String, val caId: Int)

data class Team(val id: Long, val name: String)

data class Location(val id: Long, val name: String)

data class WarehouseMatch(val key: Long)

data class WarehouseInnings(val key: Long)