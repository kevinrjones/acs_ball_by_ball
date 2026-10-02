package com.knowledgespike.ballbyball.parse.database

import com.knowledgespike.ballbyball.types.values.PublicMatchId

data class PersonRegistryEntity(val id: String, val name: String, val caId: Int)

data class Team(val id: Long, val name: String)

data class Location(val id: Long, val name: String)

data class WarehouseMatch(val key: Long, val publicMatchId: PublicMatchId)

data class WarehouseInnings(val key: Long)