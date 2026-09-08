-- Required for direct SQLite execution. Flyway users must also set
-- foreign_keys=true on the JDBC URL because SQLite ignores this pragma inside
-- an active transaction.
PRAGMA foreign_keys = ON;

CREATE TABLE Teams
(
    Id   INTEGER PRIMARY KEY AUTOINCREMENT,
    Name VARCHAR(100) NOT NULL
);

INSERT INTO Teams (Name)
VALUES ('unknown');

CREATE TABLE Grounds
(
    Id   INTEGER PRIMARY KEY AUTOINCREMENT,
    Name VARCHAR(500) NOT NULL
);

CREATE TABLE PersonRegistry
(
    PersonId VARCHAR(10) PRIMARY KEY,
    FullName VARCHAR(200) NOT NULL,
    CaId     INTEGER      NOT NULL
);
INSERT INTO PersonRegistry (PersonId, FullName, CaId)
VALUES ('unknown', '[substitute]', 0);

CREATE TABLE Players
(
    PersonId      VARCHAR(10) PRIMARY KEY,
    FullName      VARCHAR(200) NOT NULL,
    SortNamePart  VARCHAR(200) NOT NULL,
    OtherNamePart VARCHAR(200) NOT NULL,

    FOREIGN KEY (PersonId) REFERENCES PersonRegistry (PersonId) ON DELETE CASCADE
);
INSERT INTO Players (PersonId, FullName, SortNamePart, OtherNamePart)
VALUES ('unknown', '[substitute]', '[substitute]', '');

CREATE TABLE Umpires
(
    PersonId      VARCHAR(10) PRIMARY KEY,
    FullName      VARCHAR(200) NOT NULL,
    SortNamePart  VARCHAR(200) NOT NULL,
    OtherNamePart VARCHAR(200) NOT NULL,

    FOREIGN KEY (PersonId) REFERENCES PersonRegistry (PersonId) ON DELETE CASCADE
);

CREATE TABLE ReserveUmpires
(
    PersonId      VARCHAR(10) PRIMARY KEY,
    FullName      VARCHAR(200) NOT NULL,
    SortNamePart  VARCHAR(200) NOT NULL,
    OtherNamePart VARCHAR(200) NOT NULL,

    FOREIGN KEY (PersonId) REFERENCES PersonRegistry (PersonId) ON DELETE CASCADE
);

CREATE TABLE TvUmpires
(
    PersonId      VARCHAR(10) PRIMARY KEY,
    FullName      VARCHAR(200) NOT NULL,
    SortNamePart  VARCHAR(200) NOT NULL,
    OtherNamePart VARCHAR(200) NOT NULL,

    FOREIGN KEY (PersonId) REFERENCES PersonRegistry (PersonId) ON DELETE CASCADE
);

CREATE TABLE MatchReferees
(
    PersonId      VARCHAR(10) PRIMARY KEY,
    FullName      VARCHAR(200) NOT NULL,
    SortNamePart  VARCHAR(200) NOT NULL,
    OtherNamePart VARCHAR(200) NOT NULL,

    FOREIGN KEY (PersonId) REFERENCES PersonRegistry (PersonId) ON DELETE CASCADE
);

CREATE TABLE Matches
(
    Id             INTEGER PRIMARY KEY AUTOINCREMENT,
    CaId           VARCHAR(10)  NULL,
    FileName       VARCHAR(120) NOT NULL,
    MatchInSeries  INTEGER      NOT NULL,
    MatchType      VARCHAR(15)  NOT NULL,
    Event          VARCHAR(200) NOT NULL,
    Team1Id        INTEGER      NOT NULL,
    Team1Name      VARCHAR(200) NOT NULL,
    Team2Id        INTEGER      NOT NULL,
    Team2Name      VARCHAR(200) NOT NULL,
    MatchDate      VARCHAR(200) NOT NULL,
    Season         VARCHAR(200) NOT NULL,
    MatchStartYear VARCHAR(200) NOT NULL,
    MatchStartDate DATE         NULL,
    Duration       INTEGER      NOT NULL,
    BallsPerOver   INTEGER      NOT NULL,
    AddedDate      DATETIME     NOT NULL,
    Location       VARCHAR(200) NOT NULL,
    LocationId     INTEGER      NOT NULL,
    TossTeamId     INTEGER      NOT NULL,
    TossDecision   VARCHAR(10)  NULL,
    VictoryType    VARCHAR(15)  NOT NULL,
    Margin         INTEGER      NOT NULL,
    WhoWonId       INTEGER      NULL,
    WhoLostId      INTEGER      NULL,

    FOREIGN KEY (Team1Id) REFERENCES Teams (Id) ON DELETE CASCADE,
    FOREIGN KEY (Team2Id) REFERENCES Teams (Id) ON DELETE CASCADE,
    FOREIGN KEY (TossTeamId) REFERENCES Teams (Id) ON DELETE CASCADE,
    FOREIGN KEY (WhoWonId) REFERENCES Teams (Id) ON DELETE CASCADE,
    FOREIGN KEY (WhoLostId) REFERENCES Teams (Id) ON DELETE CASCADE,
    FOREIGN KEY (LocationId) REFERENCES Grounds (Id) ON DELETE CASCADE
);

CREATE INDEX idx_matches_match_type ON Matches (MatchType);

CREATE TABLE PlayersMatches
(
    PersonId VARCHAR(10) NOT NULL,
    MatchId  INTEGER     NOT NULL,

    FOREIGN KEY (PersonId) REFERENCES Players (PersonId) ON DELETE CASCADE,
    FOREIGN KEY (MatchId) REFERENCES Matches (Id) ON DELETE CASCADE
);

CREATE TABLE UmpiresMatches
(
    PersonId VARCHAR(10) NOT NULL,
    MatchId  INTEGER     NOT NULL,

    FOREIGN KEY (PersonId) REFERENCES Umpires (PersonId) ON DELETE CASCADE,
    FOREIGN KEY (MatchId) REFERENCES Matches (Id) ON DELETE CASCADE
);

CREATE TABLE TvUmpiresMatches
(
    PersonId VARCHAR(10) NOT NULL,
    MatchId  INTEGER     NOT NULL,

    FOREIGN KEY (PersonId) REFERENCES TvUmpires (PersonId) ON DELETE CASCADE,
    FOREIGN KEY (MatchId) REFERENCES Matches (Id) ON DELETE CASCADE
);

CREATE TABLE MatchRefereesMatches
(
    PersonId VARCHAR(10) NOT NULL,
    MatchId  INTEGER     NOT NULL,

    FOREIGN KEY (PersonId) REFERENCES MatchReferees (PersonId) ON DELETE CASCADE,
    FOREIGN KEY (MatchId) REFERENCES Matches (Id) ON DELETE CASCADE
);

CREATE TABLE ReserveUmpiresMatches
(
    PersonId VARCHAR(10) NOT NULL,
    MatchId  INTEGER     NOT NULL,

    FOREIGN KEY (PersonId) REFERENCES ReserveUmpires (PersonId) ON DELETE CASCADE,
    FOREIGN KEY (MatchId) REFERENCES Matches (Id) ON DELETE CASCADE
);

CREATE TABLE BallByBall
(
    Id            INTEGER PRIMARY KEY AUTOINCREMENT,
    TeamId        INTEGER     NOT NULL,
    OpponentsId   INTEGER     NOT NULL,
    MatchId       INTEGER     NOT NULL,
    InningsNumber INTEGER     NOT NULL,
    InningsOrder  INTEGER     NOT NULL,
    OverNumber    INTEGER     NOT NULL,
    BallNumber    INTEGER     NOT NULL,
    BallInOver    INTEGER     NOT NULL,
    BowlerId      VARCHAR(10) NOT NULL,
    BatterId      VARCHAR(10) NOT NULL,
    NonStrikerId  VARCHAR(10) NOT NULL,
    BatterRuns    INTEGER     NOT NULL,
    ExtraRuns     INTEGER     NOT NULL,
    TotalRuns     INTEGER     NOT NULL,
    NoBalls       INTEGER     NOT NULL,
    Wides         INTEGER     NOT NULL,
    Byes          INTEGER     NOT NULL,
    LegByes       INTEGER     NOT NULL,
    NonBoundary   INTEGER     NULL,
    Powerplay     INTEGER     NOT NULL,
    Wicket        INTEGER     NOT NULL,

    FOREIGN KEY (MatchId) REFERENCES Matches (Id) ON DELETE CASCADE,
    FOREIGN KEY (TeamId) REFERENCES Teams (Id) ON DELETE CASCADE,
    FOREIGN KEY (BowlerId) REFERENCES Players (PersonId) ON DELETE CASCADE,
    FOREIGN KEY (BatterId) REFERENCES Players (PersonId) ON DELETE CASCADE
);

CREATE INDEX idx_ball_by_ball_over_number ON BallByBall (OverNumber);
CREATE INDEX idx_ball_by_ball_ball_number ON BallByBall (BallNumber);
CREATE INDEX idx_ball_by_ball_ball_in_over ON BallByBall (BallInOver);
CREATE INDEX idx_ball_by_ball_batter_id ON BallByBall (BatterId);
CREATE INDEX idx_ball_by_ball_bowler_id ON BallByBall (BowlerId);
CREATE INDEX idx_ball_by_ball_batter_runs ON BallByBall (BatterRuns);
CREATE INDEX idx_ball_by_ball_extra_runs ON BallByBall (ExtraRuns);
CREATE INDEX idx_ball_by_ball_powerplay ON BallByBall (Powerplay);

CREATE TABLE Wickets
(
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    kind VARCHAR(30)
);

CREATE TABLE BallsWickets
(
    BallId   INTEGER NOT NULL,
    WicketId INTEGER NOT NULL,

    FOREIGN KEY (BallId) REFERENCES BallByBall (Id) ON DELETE CASCADE,
    FOREIGN KEY (WicketId) REFERENCES Wickets (id) ON DELETE CASCADE
);