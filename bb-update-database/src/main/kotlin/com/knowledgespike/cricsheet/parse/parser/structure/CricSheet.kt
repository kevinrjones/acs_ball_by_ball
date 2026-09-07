package com.knowledgespike.cricsheet.parse.parser.structure

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class CricSheet(val meta: Meta, val info: Info, val innings: Array<Innings>)

@Serializable
data class Meta(@SerialName("data_version") val dataVersion: String, val created: String, val revision: Int)

/*
missing: Array<String> | Array<JsonObject>
    JsonObject ->
        "missing": [
      {
        "powerplays": {
          "1": [
            "batting"
          ],
          "2": [
            "batting"
          ]
        }
      }
    ],

maybe others as well


players:   "players": {
    "St Lucia Zouks": [
      "J Charles",
      "ADS Fletcher",
      "SR Watson",
      "MEK Hussey",
      "GD Elliott",
      "DJG Sammy",
      "KR Mayers",
      "DE Johnson",
      "S Shillingford",
      "K Lesporis",
      "JE Taylor"
    ],
    "Trinbago Knight Riders": [
      "WKD Perkins",
      "BB McCullum",
      "C Munro",
      "D Ramdin",
      "Umar Akmal",
      "DJ Bravo",
      "AP Devcich",
      "SP Narine",
      "KK Cooper",
      "NO Miller",
      "RR Beaton"
    ]
  },

  supersubs -> "supersubs": {
  "Adelaide Strikers": "MW Short",
  "Hobart Hurricanes": "M Wright"
}


 */

@Serializable
data class Info(
    @SerialName("balls_per_over") val ballsPerOver: Int,
    @SerialName("bowl_out") val bowlOut: Array<BowlOut>? = null,
    val city: String? = null,
    val dates: Array<String>,
    val event: Event? = null,
    val gender: String,
    @SerialName("match_type") val matchType: String,
    @SerialName("match_type_number") val matchTypeNumber: Int? = null,
    val missing: JsonElement? = null,
    val officials: Officials? = null,
    val outcome: Outcome,
    val overs: Int? = null,
    @SerialName("player_of_match") val playerOfMatch: Array<String>? = null,
    val players: JsonObject,
    val registry: PlayersRegistry,
    val season: String,
    val superSubs: JsonObject? = null,
    @SerialName("team_type") val teamType: String,
    val teams: Array<String>,
    val toss: Toss,
    val venue: String? = null
)

@Serializable
data class BowlOut(val bowler: String, val outcome: String)

@Serializable
data class Event(
    val name: String,
    @SerialName("match_number") val matchNumber: Int? = null,
    val group: String? = null,
    val stage: String? = null
)

// todo
@Serializable
data class Missing(val foo: String)

@Serializable
data class Officials(
    @SerialName("match_referees") val matchReferees: Array<String>? = null,
    @SerialName("reserve_umpires") val reserveUmpires: Array<String>? = null,
    @SerialName("tv_umpires") val tvUmpires: Array<String>? = null,
    val umpires: Array<String>? = null
)

@Serializable
data class Outcome(
    val by: By? = null,
    @SerialName("bowl_out") val bowlOut: String? = null,
    val eliminator: String? = null,
    val method: String? = null,
    val result: String? = null,
    val winner: String? = null
)


@Serializable
data class By(val innings: Int? = null, val runs: Int? = null, val wickets: Int? = null)


@Serializable
data class PlayersRegistry(val people: Map<String, String>)

@Serializable
data class Toss(val uncontested: Boolean? = null, val decision: String, val winner: String)

/*
miscounted_overs -> "miscounted_overs": {
  "35": {
    "balls": 7,
    "umpire": "Asad Rauf"
  },
  "39": {
    "balls": 5,
    "umpire": "Asad Rauf"
  }
}
 */
@Serializable
data class Innings(
    val team: String,
    val overs: Array<Over>? = null,
    @SerialName("absent_hurt") val absentHurt: Array<String>? = null,
    @SerialName("penalty_runs") val penaltyRuns: PenaltyRuns? = null,
    val declared: Boolean? = null,
    val forfeited: Boolean? = null,
    val powerplays: Array<PowerPlays>? = null,
    @SerialName("miscounted_overs") val miscountedOvers: JsonObject? = null,
    val target: Target? = null,
    @SerialName("super_over") val superOver: Boolean? = null
)


@Serializable
data class Over(val over: Int, val deliveries: Array<Delivery>)

@Serializable
data class PenaltyRuns(val pre: Int? = null, val post: Int? = null)

@Serializable
data class Delivery(
    val batter: String,
    val bowler: String,
    val extras: Extras? = null,
    @SerialName("non_striker") val nonStriker: String,
    val replacements: Replacements? = null,
    val review: Review? = null,
    val runs: Runs,
    val wickets: Array<Wickets>? = null
)

@Serializable
data class PowerPlays(val from: String, val to: String, val type: String)

@Serializable
data class MiscountedOvers(val balls: Int, val umpire: String? = null)

@Serializable
data class Target(val overs: String? = null, val runs: Int? = null)

@Serializable
data class Extras(
    val byes: Int? = null,
    val legbyes: Int? = null,
    val noballs: Int? = null,
    val penalty: Int? = null,
    val wides: Int? = null
)

@Serializable
data class Replacements(val match: Array<Match>? = null, val role: Array<Role>? = null)

@Serializable
data class Match(
    @SerialName("in") val cameIn: String,
    @SerialName("out") val wentOut: String,
    val reason: String,
    val team: String
)

@Serializable
data class Role(
    @SerialName("in") val cameIn: String,
    @SerialName("out") val wentOut: String? = null,
    val reason: String,
    val role: String
)

@Serializable
data class Review(
    val batter: String,
    val by: String,
    val decision: String,
    val umpire: String? = null,
    @SerialName("umpires_call") val umpiresCall: Boolean? = null
)

@Serializable
data class Runs(
    val batter: Int,
    val extras: Int,
    @SerialName("non_boundary") val nonBoundary: Boolean? = null,
    val total: Int
)

@Serializable
data class Wickets(
    val fielders: Array<Player>? = null,
    val kind: String,
    @SerialName("player_out") val playerOut: String
)

@Serializable
data class Player(val name: String? = null, val substitute: String? = null)