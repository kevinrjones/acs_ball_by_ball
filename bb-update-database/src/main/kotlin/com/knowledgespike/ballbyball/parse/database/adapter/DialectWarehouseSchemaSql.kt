package com.knowledgespike.ballbyball.parse.database.adapter

import java.io.BufferedWriter

internal object WarehouseSchemaSql {
    private val tablesInDependencyOrder = listOf(
        "dim_date",
        "dim_team",
        "dim_person",
        "dim_ground",
        "dim_match",
        "dim_innings",
        "dim_wicket",
        "fact_match",
        "fact_delivery",
        "bridge_match_person",
        "bridge_delivery_wicket",
        "bridge_delivery_fielder"
    )

    fun writeTo(writer: BufferedWriter, dialect: SqlDialect) {
        when (dialect) {
            SqlDialect.MARIADB -> writer.appendLine("USE acs_ball_by_ball;")
            SqlDialect.POSTGRES -> {
                writer.appendLine("CREATE SCHEMA IF NOT EXISTS acs_ball_by_ball;")
                writer.appendLine("SET search_path TO acs_ball_by_ball;")
            }

            SqlDialect.SQLITE -> writer.appendLine("PRAGMA foreign_keys = ON;")
        }
        writer.appendLine()
        writer.appendLine("-- Reset and recreate the warehouse schema")
        tablesInDependencyOrder.asReversed().forEach { table ->
            writer.appendLine("DROP TABLE IF EXISTS $table;")
        }
        writer.appendLine()
        createStatements(dialect).forEach { statement ->
            writer.appendLine(statement.trimIndent())
            writer.appendLine()
        }
    }

    private fun createStatements(dialect: SqlDialect): List<String> {
        val key = keyType(dialect)
        val integer = if (dialect == SqlDialect.SQLITE) "INTEGER" else "INT"
        val smallInteger = if (dialect == SqlDialect.POSTGRES) "SMALLINT" else "TINYINT"
        val identity = identityKeyType(dialect)

        return listOf(
            """
            CREATE TABLE dim_date
            (
                date_key         $integer NOT NULL PRIMARY KEY,
                calendar_date    DATE NOT NULL,
                calendar_year    SMALLINT NOT NULL,
                calendar_quarter $smallInteger NOT NULL,
                calendar_month   $smallInteger NOT NULL,
                month_name       VARCHAR(9) NOT NULL,
                week_of_year     $smallInteger NOT NULL,
                day_of_month     $smallInteger NOT NULL,
                day_of_week      $smallInteger NOT NULL,
                day_name         VARCHAR(9) NOT NULL,
                is_weekend       BOOLEAN NOT NULL,
                CONSTRAINT uq_dim_date_calendar_date UNIQUE (calendar_date)
            );
            CREATE INDEX idx_dim_date_calendar_date ON dim_date (calendar_date);
            """,
            """
            CREATE TABLE dim_team
            (
                team_key       $identity,
                source_team_id $integer NOT NULL,
                team_name      VARCHAR(100) NOT NULL,
                CONSTRAINT uq_dim_team_source_id UNIQUE (source_team_id)
            );
            CREATE INDEX idx_dim_team_name ON dim_team (team_name);
            """,
            """
            CREATE TABLE dim_person
            (
                person_key       $identity,
                source_person_id VARCHAR(10) NOT NULL,
                full_name        VARCHAR(200) NOT NULL,
                sort_name_part   VARCHAR(200) NOT NULL,
                other_name_part  VARCHAR(200) NOT NULL,
                ca_id            $integer NOT NULL,
                CONSTRAINT uq_dim_person_source_id UNIQUE (source_person_id)
            );
            CREATE INDEX idx_dim_person_sort_name ON dim_person (sort_name_part);
            CREATE INDEX idx_dim_person_ca_id ON dim_person (ca_id);
            """,
            """
            CREATE TABLE dim_ground
            (
                ground_key       $identity,
                source_ground_id $integer NOT NULL,
                ground_name      VARCHAR(500) NOT NULL,
                CONSTRAINT uq_dim_ground_source_id UNIQUE (source_ground_id)
            );
            CREATE INDEX idx_dim_ground_name ON dim_ground (ground_name);
            """,
            """
            CREATE TABLE dim_match
            (
                match_key            $identity,
                source_match_id      $integer NOT NULL,
                source_ca_id         VARCHAR(10) NULL,
                file_name            VARCHAR(120) NOT NULL,
                match_in_series      $integer NOT NULL,
                match_type           VARCHAR(15) NOT NULL,
                event_name           VARCHAR(200) NOT NULL,
                match_date_text      VARCHAR(200) NOT NULL,
                season               VARCHAR(200) NOT NULL,
                match_start_year     VARCHAR(200) NOT NULL,
                match_start_date_key $integer NULL,
                balls_per_over       $integer NOT NULL,
                added_timestamp      TIMESTAMP NOT NULL,
                team1_key            $key NOT NULL,
                team2_key            $key NOT NULL,
                ground_key           $key NOT NULL,
                toss_team_key        $key NOT NULL,
                toss_decision        VARCHAR(10) NULL,
                victory_type         VARCHAR(15) NOT NULL,
                winner_team_key      $key NULL,
                loser_team_key       $key NULL,
                CONSTRAINT uq_dim_match_source_id UNIQUE (source_match_id),
                CONSTRAINT fk_dim_match_start_date FOREIGN KEY (match_start_date_key) REFERENCES dim_date (date_key),
                CONSTRAINT fk_dim_match_team1 FOREIGN KEY (team1_key) REFERENCES dim_team (team_key),
                CONSTRAINT fk_dim_match_team2 FOREIGN KEY (team2_key) REFERENCES dim_team (team_key),
                CONSTRAINT fk_dim_match_ground FOREIGN KEY (ground_key) REFERENCES dim_ground (ground_key),
                CONSTRAINT fk_dim_match_toss_team FOREIGN KEY (toss_team_key) REFERENCES dim_team (team_key),
                CONSTRAINT fk_dim_match_winner_team FOREIGN KEY (winner_team_key) REFERENCES dim_team (team_key),
                CONSTRAINT fk_dim_match_loser_team FOREIGN KEY (loser_team_key) REFERENCES dim_team (team_key)
            );
            CREATE INDEX idx_dim_match_type ON dim_match (match_type);
            CREATE INDEX idx_dim_match_season ON dim_match (season);
            CREATE INDEX idx_dim_match_start_date ON dim_match (match_start_date_key);
            CREATE INDEX idx_dim_match_team1 ON dim_match (team1_key);
            CREATE INDEX idx_dim_match_team2 ON dim_match (team2_key);
            CREATE INDEX idx_dim_match_ground ON dim_match (ground_key);
            """,
            """
            CREATE TABLE dim_innings
            (
                innings_key      $identity,
                match_key        $key NOT NULL,
                innings_number   $integer NOT NULL,
                batting_team_key $key NOT NULL,
                bowling_team_key $key NOT NULL,
                CONSTRAINT uq_dim_innings_match_number UNIQUE (match_key, innings_number),
                CONSTRAINT fk_dim_innings_match FOREIGN KEY (match_key) REFERENCES dim_match (match_key),
                CONSTRAINT fk_dim_innings_batting_team FOREIGN KEY (batting_team_key) REFERENCES dim_team (team_key),
                CONSTRAINT fk_dim_innings_bowling_team FOREIGN KEY (bowling_team_key) REFERENCES dim_team (team_key)
            );
            CREATE INDEX idx_dim_innings_batting_team ON dim_innings (batting_team_key);
            CREATE INDEX idx_dim_innings_bowling_team ON dim_innings (bowling_team_key);
            """,
            """
            CREATE TABLE dim_wicket
            (
                wicket_key       $identity,
                source_wicket_id $integer NOT NULL,
                wicket_kind      VARCHAR(30) NULL,
                CONSTRAINT uq_dim_wicket_source_id UNIQUE (source_wicket_id)
            );
            CREATE INDEX idx_dim_wicket_kind ON dim_wicket (wicket_kind);
            """,
            """
            CREATE TABLE fact_match
            (
                match_key       $key NOT NULL PRIMARY KEY,
                file_name       VARCHAR(120) NOT NULL,
                match_date_key  $integer NULL,
                ground_key       $key NOT NULL,
                duration_days   $integer NOT NULL,
                margin           $integer NOT NULL,
                match_count     $smallInteger NOT NULL DEFAULT 1,
                CONSTRAINT fk_fact_match_match FOREIGN KEY (match_key) REFERENCES dim_match (match_key),
                CONSTRAINT fk_fact_match_date FOREIGN KEY (match_date_key) REFERENCES dim_date (date_key),
                CONSTRAINT fk_fact_match_ground FOREIGN KEY (ground_key) REFERENCES dim_ground (ground_key)
            );
            CREATE INDEX idx_fact_match_date ON fact_match (match_date_key);
            CREATE INDEX idx_fact_match_ground ON fact_match (ground_key);
            """,
            """
            CREATE TABLE fact_delivery
            (
                delivery_key       $identity,
                source_ball_id     $integer NOT NULL,
                match_key          $key NOT NULL,
                match_date_key     $integer NULL,
                innings_key        $key NOT NULL,
                batting_team_key   $key NOT NULL,
                bowling_team_key   $key NOT NULL,
                batter_key         $key NOT NULL,
                non_striker_key    $key NOT NULL,
                bowler_key         $key NOT NULL,
                over_number        $integer NOT NULL,
                ball_number        $integer NOT NULL,
                ball_in_over       $integer NOT NULL,
                innings_order      $integer NOT NULL,
                batter_runs        $integer NOT NULL,
                extra_runs         $integer NOT NULL,
                total_runs         $integer NOT NULL,
                no_balls           $integer NOT NULL,
                wides               $integer NOT NULL,
                byes                $integer NOT NULL,
                leg_byes            $integer NOT NULL,
                non_boundary        $integer NULL,
                powerplay           $integer NOT NULL,
                wicket_count        $integer NOT NULL,
                CONSTRAINT uq_fact_delivery_source_id UNIQUE (source_ball_id),
                CONSTRAINT fk_fact_delivery_match FOREIGN KEY (match_key) REFERENCES dim_match (match_key),
                CONSTRAINT fk_fact_delivery_date FOREIGN KEY (match_date_key) REFERENCES dim_date (date_key),
                CONSTRAINT fk_fact_delivery_innings FOREIGN KEY (innings_key) REFERENCES dim_innings (innings_key),
                CONSTRAINT fk_fact_delivery_batting_team FOREIGN KEY (batting_team_key) REFERENCES dim_team (team_key),
                CONSTRAINT fk_fact_delivery_bowling_team FOREIGN KEY (bowling_team_key) REFERENCES dim_team (team_key),
                CONSTRAINT fk_fact_delivery_batter FOREIGN KEY (batter_key) REFERENCES dim_person (person_key),
                CONSTRAINT fk_fact_delivery_non_striker FOREIGN KEY (non_striker_key) REFERENCES dim_person (person_key),
                CONSTRAINT fk_fact_delivery_bowler FOREIGN KEY (bowler_key) REFERENCES dim_person (person_key)
            );
            CREATE INDEX idx_fact_delivery_match_order ON fact_delivery (match_key, innings_order);
            CREATE INDEX idx_fact_delivery_date ON fact_delivery (match_date_key);
            CREATE INDEX idx_fact_delivery_innings ON fact_delivery (innings_key);
            CREATE INDEX idx_fact_delivery_batting_team ON fact_delivery (batting_team_key);
            CREATE INDEX idx_fact_delivery_bowling_team ON fact_delivery (bowling_team_key);
            CREATE INDEX idx_fact_delivery_batter ON fact_delivery (batter_key);
            CREATE INDEX idx_fact_delivery_non_striker ON fact_delivery (non_striker_key);
            CREATE INDEX idx_fact_delivery_bowler ON fact_delivery (bowler_key);
            CREATE INDEX idx_fact_delivery_over ON fact_delivery (over_number);
            CREATE INDEX idx_fact_delivery_ball_in_over ON fact_delivery (ball_in_over);
            CREATE INDEX idx_fact_delivery_powerplay ON fact_delivery (powerplay);
            """,
            """
            CREATE TABLE bridge_match_person
            (
                match_person_key $identity,
                match_key         $key NOT NULL,
                person_key        $key NOT NULL,
                role_code         VARCHAR(32) NOT NULL,
                CONSTRAINT uq_bridge_match_person_role UNIQUE (match_key, person_key, role_code),
                CONSTRAINT fk_bridge_match_person_match FOREIGN KEY (match_key) REFERENCES dim_match (match_key),
                CONSTRAINT fk_bridge_match_person_person FOREIGN KEY (person_key) REFERENCES dim_person (person_key)
            );
            CREATE INDEX idx_bridge_match_person_person ON bridge_match_person (person_key);
            CREATE INDEX idx_bridge_match_person_role ON bridge_match_person (role_code);
            """,
            """
            CREATE TABLE bridge_delivery_wicket
            (
                delivery_key $key NOT NULL,
                wicket_key   $key NOT NULL,
                PRIMARY KEY (delivery_key, wicket_key),
                CONSTRAINT fk_bridge_delivery_wicket_delivery FOREIGN KEY (delivery_key) REFERENCES fact_delivery (delivery_key),
                CONSTRAINT fk_bridge_delivery_wicket_wicket FOREIGN KEY (wicket_key) REFERENCES dim_wicket (wicket_key)
            );
            """,
            """
            CREATE TABLE bridge_delivery_fielder
            (
                delivery_key $key NOT NULL,
                wicket_key   $key NOT NULL,
                person_key   $key NOT NULL,
                PRIMARY KEY (delivery_key, wicket_key, person_key),
                CONSTRAINT fk_bridge_delivery_fielder_delivery FOREIGN KEY (delivery_key) REFERENCES fact_delivery (delivery_key),
                CONSTRAINT fk_bridge_delivery_fielder_wicket FOREIGN KEY (wicket_key) REFERENCES dim_wicket (wicket_key),
                CONSTRAINT fk_bridge_delivery_fielder_person FOREIGN KEY (person_key) REFERENCES dim_person (person_key)
            );
            CREATE INDEX idx_bridge_delivery_fielder_person ON bridge_delivery_fielder (person_key);
            """
        )
    }

    private fun keyType(dialect: SqlDialect): String = when (dialect) {
        SqlDialect.MARIADB -> "BIGINT UNSIGNED"
        SqlDialect.POSTGRES -> "BIGINT"
        SqlDialect.SQLITE -> "INTEGER"
    }

    private fun identityKeyType(dialect: SqlDialect): String = when (dialect) {
        SqlDialect.MARIADB -> "BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY"
        SqlDialect.POSTGRES -> "BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY"
        SqlDialect.SQLITE -> "INTEGER PRIMARY KEY AUTOINCREMENT"
    }
}