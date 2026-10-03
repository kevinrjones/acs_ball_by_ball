package com.knowledgespike.ballbyball.parse.database

import com.knowledgespike.ballbyball.types.values.PublicMatchId

data class PersonEntity(val id: String, val name: String, val caId: Int)

data class Team(val id: Long, val name: String)

data class Ground(val id: Long, val name: String)

data class MatchEntity(val id: Long, val publicMatchId: PublicMatchId)

data class InningsEntity(val id: Long)
