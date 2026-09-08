package com.knowledgespike.ballbyball.parse.parser.structure

import com.knowledgespike.cricketarchive.InvalidStateException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Provides utility functions to transform and map data from a CricSheet object
 * into person-based structures, including registry details, players, and officials.
 */
object Translate {

    /**
     * Retrieves a list of persons from the given CricSheet object.
     *
     * @param cricSheet The CricSheet object containing registry information.
     * @return A list of Person objects constructed from the registry data in the CricSheet.
     */
    fun getPeople(cricSheet: CricSheet): List<Person> {

        return cricSheet.info.registry.people.map { (name, id) ->
            Person(id, name)
        }

    }

    /**
     * Retrieves the players for each team from the specified JSON object and CricSheet data.
     *
     * The method processes the `players` property from the provided JSON object and maps it
     * to information from the CricSheet registry to produce a mapping of team names to their respective players.
     * Each player's existence and uniqueness are validated against the registry in the CricSheet.
     *
     * @param people A JSON object containing team-player mappings; each team is associated with a list of player names.
     * @param cricSheet An instance of the CricSheet data structure containing metadata and player registry information.
     * @return A map where the key is the team name as a string, and the value is a list of `Person` objects representing the players in that team.
     * @throws InvalidStateException If a player's name in the JSON object does not correspond to exactly one person in the CricSheet registry.
     */
    fun getPlayers(people: JsonObject, cricSheet: CricSheet): Map<String, List<Person>> {
        val teamPlayers = mutableMapOf<String, List<Person>>()
        val peopleInRegistry = getPeople(cricSheet)
        people.map { (team, players) ->
            val persons = players.jsonArray.map { person ->
                val registeredPerson = peopleInRegistry.filter { it.name == person.jsonPrimitive.content }
                if(registeredPerson.size != 1) throw InvalidStateException("Should be one matching person in the registry, actually ${registeredPerson.size}")
                return@map registeredPerson[0]
            }
            teamPlayers.put(team, persons)
        }
        return teamPlayers
    }

    /**
     * Extracts a list of `Person` objects corresponding to the given officials' names from a CricSheet registry.
     *
     * @param officials A list of official names as strings. Can be null, in which case an empty list will be returned.
     * @param cricSheet A `CricSheet` object containing the registry of people.
     * @return A list of `Person` objects that match the official names provided. Throws an `InvalidStateException` if the registry
     * contains zero or more than one match for a given official name.
     */
    fun getOfficials(officials: List<String>?, cricSheet: CricSheet): List<Person> {
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