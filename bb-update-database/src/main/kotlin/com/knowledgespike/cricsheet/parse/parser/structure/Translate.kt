package com.knowledgespike.cricsheet.parse.parser.structure

import com.knowledgespike.cricketarchive.InvalidStateException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

object Translate {

    fun getPeople(cricSheet: CricSheet): List<Person> {

        return cricSheet.info.registry.people.map { (name, id) ->
            Person(id, name)
        }

    }

    fun getPlayers(people: JsonObject, cricSheet: CricSheet): Map<String, List<Person>> {
        val teamPlayers = mutableMapOf<String, List<Person>>()
        val peopleInRegistry = getPeople(cricSheet)
        people.map { (team, players) ->
            val persons = players.jsonArray.map { person ->
                val p = person.jsonPrimitive.content
                val registeredPerson = peopleInRegistry.filter { it.name == person.jsonPrimitive.content }
                if(registeredPerson.size != 1) throw InvalidStateException("Should be one matching person in the registry, actually ${registeredPerson.size}")
                return@map registeredPerson[0]
            }
            teamPlayers.put(team, persons)
        }
        return teamPlayers
    }

    fun getOfficials(officials: Array<String>?, cricSheet: CricSheet): List<Person> {
        val peopleInRegistry = getPeople(cricSheet)
        val listOfOfficials = mutableListOf<Person>()

        officials?.let { officialsList ->
            val foundUmpireAsPeople = officialsList.map {name->
                val registeredPerson = peopleInRegistry.filter { it.name == name }
                if(registeredPerson.size != 1) throw InvalidStateException("Should be one matching person in the registry, actually ${registeredPerson.size}")
                registeredPerson[0]
            }
            listOfOfficials.addAll(foundUmpireAsPeople)
        }

        return listOfOfficials

    }
}