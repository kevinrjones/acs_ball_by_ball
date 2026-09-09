-- Required for direct SQLite execution. Flyway users must also set
-- foreign_keys=true on the JDBC URL because SQLite ignores this pragma inside
-- an active transaction.
PRAGMA foreign_keys = ON;

CREATE TABLE dim_date
(
    date_key        INTEGER     NOT NULL PRIMARY KEY,
    calendar_date   DATE        NOT NULL,
    calendar_year   INTEGER     NOT NULL,
    calendar_quarter INTEGER    NOT NULL,
    calendar_month  INTEGER     NOT NULL,
    month_name      VARCHAR(9)  NOT NULL,
    week_of_year    INTEGER     NOT NULL,
    day_of_month    INTEGER     NOT NULL,
    day_of_week     INTEGER     NOT NULL,
    day_name        VARCHAR(9)  NOT NULL,
    is_weekend      BOOLEAN     NOT NULL,

    UNIQUE (calendar_date)
);

CREATE TABLE dim_team
(
    team_key       INTEGER PRIMARY KEY AUTOINCREMENT,
    source_team_id INTEGER      NOT NULL,
    team_name      VARCHAR(100) NOT NULL,

    UNIQUE (source_team_id)
);

CREATE INDEX idx_dim_team_name ON dim_team (team_name);

CREATE TABLE dim_person
(
    person_key       INTEGER PRIMARY KEY AUTOINCREMENT,
    source_person_id VARCHAR(10)  NOT NULL,
    full_name        VARCHAR(200) NOT NULL,
    sort_name_part   VARCHAR(200) NOT NULL,
    other_name_part  VARCHAR(200) NOT NULL,
    ca_id            INTEGER      NOT NULL,

    UNIQUE (source_person_id)
);

CREATE INDEX idx_dim_person_sort_name ON dim_person (sort_name_part);
CREATE INDEX idx_dim_person_ca_id ON dim_person (ca_id);
CREATE INDEX idx_dim_person_full_name ON dim_person (full_name);

CREATE TABLE dim_ground
(
    ground_key       INTEGER PRIMARY KEY AUTOINCREMENT,
    source_ground_id INTEGER      NOT NULL,
    ground_name      VARCHAR(500) NOT NULL,

    UNIQUE (source_ground_id)
);

CREATE INDEX idx_dim_ground_name ON dim_ground (ground_name);

CREATE TABLE dim_match
(
    match_key            INTEGER PRIMARY KEY AUTOINCREMENT,
    source_match_id      INTEGER      NOT NULL,
    source_ca_id         VARCHAR(10)  NULL,
    file_name            VARCHAR(120) NOT NULL,
    match_in_series      INTEGER      NOT NULL,
    match_type           VARCHAR(15)  NOT NULL,
    event_name           VARCHAR(200) NOT NULL,
    match_date_text      VARCHAR(200) NOT NULL,
    season               VARCHAR(200) NOT NULL,
    match_start_year     VARCHAR(200) NOT NULL,
    match_start_date_key INTEGER      NULL,
    balls_per_over       INTEGER      NOT NULL,
    added_timestamp      DATETIME     NOT NULL,
    team1_key            INTEGER      NOT NULL,
    team2_key            INTEGER      NOT NULL,
    ground_key           INTEGER      NOT NULL,
    toss_team_key        INTEGER      NOT NULL,
    toss_decision        VARCHAR(10)  NULL,
    victory_type         VARCHAR(15)  NOT NULL,
    winner_team_key      INTEGER      NULL,
    loser_team_key       INTEGER      NULL,

    UNIQUE (source_match_id),
    FOREIGN KEY (match_start_date_key) REFERENCES dim_date (date_key),
    FOREIGN KEY (team1_key) REFERENCES dim_team (team_key),
    FOREIGN KEY (team2_key) REFERENCES dim_team (team_key),
    FOREIGN KEY (ground_key) REFERENCES dim_ground (ground_key),
    FOREIGN KEY (toss_team_key) REFERENCES dim_team (team_key),
    FOREIGN KEY (winner_team_key) REFERENCES dim_team (team_key),
    FOREIGN KEY (loser_team_key) REFERENCES dim_team (team_key)
);

CREATE INDEX idx_dim_match_type ON dim_match (match_type);
CREATE INDEX idx_dim_match_file_name ON dim_match (file_name);
CREATE INDEX idx_dim_match_type_year ON dim_match (match_type, match_start_year);
CREATE INDEX idx_dim_match_teams_type ON dim_match (match_type, team1_key, team2_key);
CREATE INDEX idx_dim_match_season ON dim_match (season);
CREATE INDEX idx_dim_match_start_date ON dim_match (match_start_date_key);
CREATE INDEX idx_dim_match_team1 ON dim_match (team1_key);
CREATE INDEX idx_dim_match_team2 ON dim_match (team2_key);
CREATE INDEX idx_dim_match_ground ON dim_match (ground_key);

CREATE TABLE dim_innings
(
    innings_key      INTEGER PRIMARY KEY AUTOINCREMENT,
    match_key        INTEGER NOT NULL,
    innings_number   INTEGER NOT NULL,
    batting_team_key INTEGER NOT NULL,
    bowling_team_key INTEGER NOT NULL,

    UNIQUE (match_key, innings_number),
    FOREIGN KEY (match_key) REFERENCES dim_match (match_key),
    FOREIGN KEY (batting_team_key) REFERENCES dim_team (team_key),
    FOREIGN KEY (bowling_team_key) REFERENCES dim_team (team_key)
);

CREATE INDEX idx_dim_innings_batting_team ON dim_innings (batting_team_key);
CREATE INDEX idx_dim_innings_bowling_team ON dim_innings (bowling_team_key);

CREATE TABLE dim_wicket
(
    wicket_key       INTEGER PRIMARY KEY AUTOINCREMENT,
    source_wicket_id INTEGER     NOT NULL,
    wicket_kind      VARCHAR(30) NULL,

    UNIQUE (source_wicket_id)
);

CREATE INDEX idx_dim_wicket_kind ON dim_wicket (wicket_kind);

CREATE TABLE fact_match
(
    match_key      INTEGER NOT NULL PRIMARY KEY,
    match_date_key INTEGER NULL,
    ground_key     INTEGER NOT NULL,
    duration_days  INTEGER NOT NULL,
    margin         INTEGER NOT NULL,
    match_count    INTEGER NOT NULL DEFAULT 1,

    FOREIGN KEY (match_key) REFERENCES dim_match (match_key),
    FOREIGN KEY (match_date_key) REFERENCES dim_date (date_key),
    FOREIGN KEY (ground_key) REFERENCES dim_ground (ground_key)
);

CREATE INDEX idx_fact_match_date ON fact_match (match_date_key);
CREATE INDEX idx_fact_match_ground ON fact_match (ground_key);

CREATE TABLE fact_delivery
(
    delivery_key     INTEGER PRIMARY KEY AUTOINCREMENT,
    source_ball_id   INTEGER NOT NULL,
    match_key        INTEGER NOT NULL,
    match_date_key   INTEGER NULL,
    innings_key      INTEGER NOT NULL,
    batting_team_key INTEGER NOT NULL,
    bowling_team_key INTEGER NOT NULL,
    batter_key       INTEGER NOT NULL,
    non_striker_key  INTEGER NOT NULL,
    bowler_key       INTEGER NOT NULL,
    over_number      INTEGER NOT NULL,
    ball_number      INTEGER NOT NULL,
    ball_in_over     INTEGER NOT NULL,
    innings_order    INTEGER NOT NULL,
    batter_runs      INTEGER NOT NULL,
    extra_runs       INTEGER NOT NULL,
    total_runs       INTEGER NOT NULL,
    no_balls         INTEGER NOT NULL,
    wides            INTEGER NOT NULL,
    byes             INTEGER NOT NULL,
    leg_byes         INTEGER NOT NULL,
    non_boundary     INTEGER NULL,
    powerplay        INTEGER NOT NULL,
    wicket_count     INTEGER NOT NULL,

    UNIQUE (source_ball_id),
    FOREIGN KEY (match_key) REFERENCES dim_match (match_key),
    FOREIGN KEY (match_date_key) REFERENCES dim_date (date_key),
    FOREIGN KEY (innings_key) REFERENCES dim_innings (innings_key),
    FOREIGN KEY (batting_team_key) REFERENCES dim_team (team_key),
    FOREIGN KEY (bowling_team_key) REFERENCES dim_team (team_key),
    FOREIGN KEY (batter_key) REFERENCES dim_person (person_key),
    FOREIGN KEY (non_striker_key) REFERENCES dim_person (person_key),
    FOREIGN KEY (bowler_key) REFERENCES dim_person (person_key)
);

CREATE INDEX idx_fact_delivery_match_order ON fact_delivery (match_key, innings_order);
CREATE INDEX idx_fact_delivery_match_seq ON fact_delivery (match_key, innings_order, over_number, ball_in_over);
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

CREATE TABLE bridge_match_person
(
    match_person_key INTEGER PRIMARY KEY AUTOINCREMENT,
    match_key        INTEGER      NOT NULL,
    person_key       INTEGER      NOT NULL,
    role_code        VARCHAR(32)  NOT NULL,

    UNIQUE (match_key, person_key, role_code),
    FOREIGN KEY (match_key) REFERENCES dim_match (match_key),
    FOREIGN KEY (person_key) REFERENCES dim_person (person_key)
);

CREATE INDEX idx_bridge_match_person_person ON bridge_match_person (person_key);
CREATE INDEX idx_bridge_match_person_role ON bridge_match_person (role_code);

CREATE TABLE bridge_delivery_wicket
(
    delivery_key INTEGER NOT NULL,
    wicket_key   INTEGER NOT NULL,

    PRIMARY KEY (delivery_key, wicket_key),

    FOREIGN KEY (delivery_key) REFERENCES fact_delivery (delivery_key),
    FOREIGN KEY (wicket_key) REFERENCES dim_wicket (wicket_key)
);

CREATE INDEX idx_bridge_delivery_wicket_wicket ON bridge_delivery_wicket (wicket_key);


CREATE TABLE bridge_delivery_fielder
(
    delivery_key INTEGER NOT NULL,
    wicket_key   INTEGER NOT NULL,
    person_key   INTEGER NOT NULL,

    PRIMARY KEY (delivery_key, wicket_key, person_key),
    FOREIGN KEY (delivery_key) REFERENCES fact_delivery (delivery_key),
    FOREIGN KEY (wicket_key) REFERENCES dim_wicket (wicket_key),
    FOREIGN KEY (person_key) REFERENCES dim_person (person_key)
);

CREATE INDEX idx_bridge_delivery_fielder_wicket ON bridge_delivery_fielder (wicket_key);
CREATE INDEX idx_bridge_delivery_fielder_person ON bridge_delivery_fielder (person_key);