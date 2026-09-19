use acs_ball_by_ball;

create table Teams
(
    Id   Int PRIMARY KEY AUTO_INCREMENT,
    Name varchar(100) not null
);
INSERT INTO Teams (Name)
VALUES ('unknown');


create table Grounds
(
    Id   Int PRIMARY KEY AUTO_INCREMENT,
    Name varchar(500) not null
);

create table PersonRegistry
(
    PersonId varchar(10) PRIMARY KEY,
    FullName varchar(200) not null,
    CaId     Int          not null
);
INSERT INTO PersonRegistry (PersonId, FullName, CaId)
VALUES ('unknown', '[substitute]', 0);

create table Players
(
    PersonId      varchar(10) PRIMARY KEY,
    FullName      varchar(200) not null,
    SortNamePart  varchar(200) not null,
    OtherNamePart varchar(200) not null,

    FOREIGN KEY (PersonId) REFERENCES PersonRegistry (PersonId) ON DELETE CASCADE
);
INSERT INTO Players (PersonId, FullName, SortNamePart, OtherNamePart)
VALUES ('unknown', '[substitute]', '[substitute]', '');

create table Umpires
(
    PersonId      varchar(10) PRIMARY KEY,
    FullName      varchar(200) not null,
    SortNamePart  varchar(200) not null,
    OtherNamePart varchar(200) not null,

    FOREIGN KEY (PersonId) REFERENCES PersonRegistry (PersonId) ON DELETE CASCADE
);

create table ReserveUmpires
(
    PersonId      varchar(10) PRIMARY KEY,
    FullName      varchar(200) not null,
    SortNamePart  varchar(200) not null,
    OtherNamePart varchar(200) not null,

    FOREIGN KEY (PersonId) REFERENCES PersonRegistry (PersonId) ON DELETE CASCADE
);

create table TvUmpires
(
    PersonId      varchar(10) PRIMARY KEY,
    FullName      varchar(200) not null,
    SortNamePart  varchar(200) not null,
    OtherNamePart varchar(200) not null,

    FOREIGN KEY (PersonId) REFERENCES PersonRegistry (PersonId) ON DELETE CASCADE
);

create table MatchReferees
(
    PersonId      varchar(10) PRIMARY KEY,
    FullName      varchar(200) not null,
    SortNamePart  varchar(200) not null,
    OtherNamePart varchar(200) not null,

    FOREIGN KEY (PersonId) REFERENCES PersonRegistry (PersonId) ON DELETE CASCADE
);



create table Matches
(
    Id             Int PRIMARY KEY AUTO_INCREMENT,
    CaId           varchar(10)  null,
    FileName       varchar(120) not null,
    MatchInSeries  Int          not null, -- 1(st test)
    MatchType      varchar(15)  not null,
    Event          varchar(200) not null,
    Team1Id        Int          not null,
    Team1Name      varchar(200) not null,
    Team2Id        Int          not null,
    Team2Name      varchar(200) not null,
    MatchDate      varchar(200) not null,
    Season         varchar(200) not null,
    MatchStartYear varchar(200) not null,
    MatchStartDate date         null,
    Duration       Int          not null, -- in days or MAX_INT for timeless test
    BallsPerOver   Int          not null,
    AddedDate      datetime     not null,
    Location       varchar(200) not null,
    LocationId     int          not null,
    TossTeamId     Int          not null,
    TossDecision   varchar(10)  null,
    VictoryType    varchar(15)  not null,
    Margin         INT          not null,
    WhoWonId       Int          null,
    WhoLostId      Int          null,

    FOREIGN KEY (Team1Id) REFERENCES Teams (Id) ON DELETE CASCADE,
    FOREIGN KEY (Team2Id) REFERENCES Teams (Id) ON DELETE CASCADE,
    FOREIGN KEY (TossTeamId) REFERENCES Teams (Id) ON DELETE CASCADE,
    FOREIGN KEY (WhoWonId) REFERENCES Teams (Id) ON DELETE CASCADE,
    FOREIGN KEY (WhoLostId) REFERENCES Teams (Id) ON DELETE CASCADE,
    FOREIGN KEY (LocationId) REFERENCES Grounds (Id) ON DELETE CASCADE,
    Index (MatchType)
);

create table PlayersMatches
(
    PersonId varchar(10) not null,
    MatchId  Int         not null,

    FOREIGN KEY (PersonId) references Players (PersonId) ON DELETE CASCADE,
    FOREIGN KEY (MatchId) references Matches (Id) ON DELETE CASCADE
);

create table UmpiresMatches
(
    PersonId varchar(10) not null,
    MatchId  Int         not null,

    FOREIGN KEY (PersonId) references Umpires (PersonId) ON DELETE CASCADE,
    FOREIGN KEY (MatchId) references Matches (Id) ON DELETE CASCADE
);

create table TvUmpiresMatches
(
    PersonId varchar(10) not null,
    MatchId  Int         not null,

    FOREIGN KEY (PersonId) references TvUmpires (PersonId) ON DELETE CASCADE,
    FOREIGN KEY (MatchId) references Matches (Id) ON DELETE CASCADE

);

create table MatchRefereesMatches
(
    PersonId varchar(10) not null,
    MatchId  Int         not null,

    FOREIGN KEY (PersonId) references MatchReferees (PersonId) ON DELETE CASCADE,
    FOREIGN KEY (MatchId) references Matches (Id) ON DELETE CASCADE
);

create table ReserveUmpiresMatches
(
    PersonId varchar(10) not null,
    MatchId  Int         not null,

    FOREIGN KEY (PersonId) references ReserveUmpires (PersonId) ON DELETE CASCADE,
    FOREIGN KEY (MatchId) references Matches (Id) ON DELETE CASCADE
);

create table BallByBall
(
    Id            Int PRIMARY KEY AUTO_INCREMENT,
    TeamId        Int         not null,
    OpponentsId   Int         not null,
    MatchId       Int         not null,
    InningsNumber Int         not null,
    InningsOrder  Int         not null,
    OverNumber    Int         not null,
    BallNumber    Int         not null,
    BallInOver    Int         not null,
    BowlerId      varchar(10) not null,
    BatterId      varchar(10) not null,
    NonStrikerId  varchar(10) not null,
    BatterRuns    INT         not null,
    ExtraRuns     INT         not null,
    TotalRuns     INT         not null,
    NoBalls       INT         not null,
    Wides         INT         not null,
    Byes          INT         not null,
    LegByes       INT         not null,
    NonBoundary   INT         null,
    Powerplay     INT         not null,
    Wicket        INT         not null,

    FOREIGN KEY (MatchId) references Matches (Id) ON DELETE CASCADE,
    FOREIGN KEY (TeamId) references Teams (Id) ON DELETE CASCADE,
    FOREIGN KEY (BowlerId) references Players (PersonId) ON DELETE CASCADE,
    FOREIGN KEY (BatterId) references Players (PersonId) ON DELETE CASCADE,

    INDEX (OverNumber),
    INDEX (BallNumber),
    INDEX (BallInOver),
    INDEX (BatterId),
    INDEX (BowlerId),
    INDEX (BatterRuns),
    INDEX (ExtraRuns),
    INDEX (Powerplay)

);

create table Wickets
(
    id   int primary key AUTO_INCREMENT,
    kind varchar(30)
);

create table BallsWickets
(
    BallId   Int not null,
    WicketId int not null,

    FOREIGN KEY (BallId) references BallByBall (Id) ON DELETE CASCADE,
    FOREIGN KEY (WicketId) references Wickets (Id) ON DELETE CASCADE
);

