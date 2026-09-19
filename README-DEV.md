# DataLoader developer commands

This document is the developer runbook for the `DataLoader` project. Keep it
updated whenever a Gradle task, parser option, output adapter, migration, or
database-loading workflow is added or changed.

## Prerequisites

- Java 21. Gradle uses the Java toolchain configured in the module build files.
- MariaDB client tools (`mariadb`) when loading data into MariaDB.
- Docker and the reproducible dual-database setup in `docs/setup/SETUP-DB.md`
  when running the MariaDB and PostgreSQL test environments.
- A Cricsheet data directory containing the scorecard directories and the
  player registry CSV. The directory must contain the subdirectories expected
  by `Application.kt`, such as `tests_json`, `odis_json`, and `t20s_json`.

The commands below assume the shell variables have been set. Use paths without
spaces when passing them through Gradle's `--args` option.

```bash
export CRICSHEET_DIR=/path/to/cricsheet
export PLAYER_REGISTRY=people.csv
export CSV_DIR=/path/to/generated-warehouse-csv
export SQL_FILE=/path/to/generated-warehouse.sql
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=cricsheet
export DB_USER=cricsheet
export DB_PASSWORD='change-me'
```

## Build and verify

Run the complete verification suite:

```bash
./gradlew clean check --no-daemon
```

Run the database-loader tests only:

```bash
./gradlew :bbb-update-database:test --no-daemon
```

Compile the loader without running it:

```bash
./gradlew :bbb-update-database:compileKotlin --no-daemon
```

Display the parser command-line help:

```bash
./gradlew :bbb-update-database:run --no-daemon --args="-h"
```

Check dependency updates and refresh the version catalog when required:

```bash
./gradlew dependencyUpdates --no-daemon
./gradlew versionCatalogUpdate --no-daemon
```

## Database schema

The database schema is created by the Flyway migrations in the dialect-specific
directories under `bbb-update-database/migrations`: `mysql`, `postgres`, and
`sqlite`. MySQL remains the default. Select another directory with
`FLYWAY_DATABASE` (or `-Pmigration.database`) and configure the connection through
environment variables rather than putting credentials in a command history.

```bash
export FLYWAY_URL="jdbc:mariadb://${DB_HOST}:${DB_PORT}/${DB_NAME}"
export FLYWAY_USER="$DB_USER"
export FLYWAY_PASSWORD="$DB_PASSWORD"
./gradlew :bbb-update-database:flywayMigrate --no-daemon
```

For PostgreSQL, use the PostgreSQL JDBC URL and migration directory:

```bash
export FLYWAY_DATABASE=postgres
export FLYWAY_URL="jdbc:postgresql://localhost:5432/cricsheet"
export FLYWAY_USER=cricsheet
export FLYWAY_PASSWORD='change-me'
./gradlew :bbb-update-database:flywayMigrate --no-daemon
```

For SQLite, point the SQLite JDBC URL at a database file. SQLite has no
application schema, so the migration task uses SQLite's built-in `main` database
name instead of `cricsheet`. SQLite migrations run without Flyway's outer
transaction so the first migration statement can enable foreign keys:

```bash
export FLYWAY_DATABASE=sqlite
export FLYWAY_URL="jdbc:sqlite:/path/to/cricsheet.db"
./gradlew :bbb-update-database:flywayMigrate --no-daemon \
  -Pflyway.executeInTransaction=false
```

For a new MySQL or PostgreSQL database, ensure the `cricsheet` database exists
before running the migrations. Each dialect directory includes the original
schema `1__initial_tables.sql` and the complete warehouse schema
`2__initial_warehouse.sql`, with the source filename stored on `dim_match`.

```bash
mariadb --host="$DB_HOST" --port="$DB_PORT" \
  --user="$DB_USER" --password="$DB_PASSWORD" \
  --execute="CREATE DATABASE IF NOT EXISTS $DB_NAME"
```

## Parser command-line options

All parser runs use the Gradle application task:

```bash
./gradlew :bbb-update-database:run --no-daemon --args="<options>"
```

| Option                     | Required            | Description                                                                   |
|----------------------------|---------------------|-------------------------------------------------------------------------------|
| `-h`, `--help`             | No                  | Print the command-line help.                                                  |
| `-bd`, `--baseDirectory`   | Yes                 | Root directory containing scorecards and the player registry.                 |
| `-pr`, `--playerRegistry`  | Yes                 | Player-registry filename relative to `baseDirectory`.                         |
| `-ot`, `--outputType`      | No                  | `SQL` (default), `DATABASE`, `SQL_FILE`, or `CSV`.                            |
| `-c`, `--connectionString` | For database output | JDBC connection string.                                                       |
| `-u`, `--userName`         | For database output | Database username.                                                            |
| `-p`, `--password`         | No                  | Database password. Prefer an environment variable or protected shell history. |
| `--database`               | For SQL files       | SQL dialect: `mariadb`, `postgres`, or `sqlite` (default: `mariadb`).         |
| `-o`, `--outputFile`       | For SQL files       | SQL script path. With `SQL`, specifying this option selects file output.      |
| `-sf`, `--sqlFile`         | For SQL files       | Alias for `--outputFile`.                                                     |
| `-cd`, `--csvDir`          | For CSV output      | Directory in which warehouse CSV files are written.                           |

## Produce CSV output

Run the parser with `CSV` output and a destination directory:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--outputType CSV --baseDirectory $CRICSHEET_DIR --playerRegistry $PLAYER_REGISTRY --csvDir $CSV_DIR"
```

The adapter clears `CSV_DIR` before writing. It emits one file per warehouse
table, with headers matching the generated warehouse schema, including files such
as `dim_person.csv`, `dim_match.csv`, `fact_delivery.csv`,
`bridge_delivery_wicket.csv`, and `bridge_delivery_fielder.csv`. Nullable
values are written as MariaDB's `\N`
marker.

The `dim_match.csv` file includes the source JSON filename in its `file_name`
column. The `fact_match.csv` file contains only match-level measures and keys.

## Load CSV output into MariaDB

The database must have the warehouse tables before loading CSV data. The
following Bash loop loads files in foreign-key dependency order and skips
optional files that were not generated because the source data contained no
rows for that table:

```bash
DB_PASSWORD='change-me'
CSV_DIR=csv
DB_HOST=localhost
DB_PORT=3306
DB_NAME=acs_ball_by_ball
DB_USER=ballbyball

for table in \
  dim_date \
  dim_team \
  dim_person \
  dim_ground \
  dim_match \
  dim_innings \
  dim_wicket \
  fact_match \
  fact_delivery \
  bridge_match_person \
  bridge_delivery_wicket \
  bridge_delivery_fielder
do
  file="$CSV_DIR/$table.csv"
  if [ -f "$file" ]; then
    mariadb --verbose --local-infile=1 \
      --host="$DB_HOST" --port="$DB_PORT" \
      --user="$DB_USER" --password="$DB_PASSWORD" "$DB_NAME" <<SQL
LOAD DATA LOCAL INFILE '$file'
INTO TABLE $table
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"' ESCAPED BY '"'
LINES TERMINATED BY '\n'
IGNORE 1 LINES;
SQL
  fi
done
```

The loader must use `--local-infile=1`. The MariaDB server may also need
`local_infile=ON`. The CSV adapter allocates warehouse keys in the files, so
load CSV output into an empty warehouse (or coordinate key and duplicate
handling explicitly before loading it into an existing warehouse).

## Alternative output modes

Write an executable MariaDB SQL script instead of CSV:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--outputType SQL_FILE --baseDirectory $CRICSHEET_DIR --playerRegistry $PLAYER_REGISTRY --outputFile $SQL_FILE"
```

Load that script into MariaDB:

```bash
mariadb --host="$DB_HOST" --port="$DB_PORT" \
  --user="$DB_USER" --password="$DB_PASSWORD" "$DB_NAME" < "$SQL_FILE"
```

The generated SQL file is self-contained for the warehouse schema: it drops
the warehouse tables in foreign-key dependency order, recreates them from the
shared warehouse schema definition, and then loads the generated rows. This is
destructive to the existing warehouse data, so use it only when replacing the
entire warehouse is intended. The target database must already exist.

`bridge_delivery_fielder` uses `(delivery_key, wicket_key, person_key)` as its
composite primary key. The parser suppresses repeated fielder associations and
logs the source filename and key values; generated SQL and direct JDBC output
also use dialect-specific conflict handling as a final safeguard.

Generate a PostgreSQL script by selecting the PostgreSQL adapter:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--outputType SQL_FILE --database postgres --baseDirectory $CRICSHEET_DIR --playerRegistry $PLAYER_REGISTRY --outputFile $SQL_FILE"
```

The PostgreSQL script creates and selects the `cricsheet` schema, uses identity
columns, and uses PostgreSQL `ON CONFLICT` syntax. Load it after creating the
target database:

```bash
psql --host=localhost --port=5432 --username="$DB_USER" --dbname=cricsheet \
  --file="$SQL_FILE"
```

Generate and load a SQLite script:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--outputType SQL_FILE --database sqlite --baseDirectory $CRICSHEET_DIR --playerRegistry $PLAYER_REGISTRY --outputFile $SQL_FILE"
sqlite3 /path/to/cricsheet.db < "$SQL_FILE"
```

SQLite scripts enable foreign keys, use `INTEGER PRIMARY KEY AUTOINCREMENT`,
and start a SQLite transaction after the schema has been recreated.

The SQL-file adapters are implemented in the dialect-specific packages under
`bbb-update-database/src/main/kotlin/com/knowledgespike/ballbyball/parse/database/adapter`.
The direct JDBC adapters are selected automatically from the `jdbc:mariadb:`,
`jdbc:mysql:`, `jdbc:postgresql:`, or `jdbc:sqlite:` connection prefix.

Write directly to MariaDB through the JDBC adapter:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--outputType DATABASE --baseDirectory $CRICSHEET_DIR --playerRegistry $PLAYER_REGISTRY --connectionString jdbc:mariadb://${DB_HOST}:${DB_PORT}/${DB_NAME} --userName $DB_USER --password $DB_PASSWORD"
```

Write directly to PostgreSQL through the JDBC adapter:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--outputType DATABASE --baseDirectory $CRICSHEET_DIR --playerRegistry $PLAYER_REGISTRY --connectionString jdbc:postgresql://localhost:5432/cricsheet --userName $DB_USER --password $DB_PASSWORD"
```

Write directly to SQLite through the JDBC adapter. The SQLite JDBC URL should
point to the migrated database file; username and password are not required:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--outputType DATABASE --baseDirectory $CRICSHEET_DIR --playerRegistry $PLAYER_REGISTRY --connectionString jdbc:sqlite:/path/to/cricsheet.db"
```

`SQL` is the default output type and behaves like direct database output when
no output file is supplied. Supplying `--outputFile` with `SQL` selects the SQL
script adapter instead. `SQL_FILE` makes that offline behavior explicit. The
SQL-file adapter resets and recreates the warehouse tables; direct `DATABASE`
output does not reset tables.

## Ktor applications

The repository now includes two runnable Ktor applications. The architecture,
configuration, endpoints, and browser-to-API flow are documented in
`docs/architecture/applications.md`.

Run the JOOQ-backed API against a migrated local database:

```bash
DB_JDBC_URL='jdbc:mariadb://localhost:3307/acs_ball_by_ball' \
DB_USER=acs_ball_by_ball \
DB_PASSWORD='acs_ball_by_ball-local-password' \
./gradlew :bbb-api:run --no-daemon
```

Run the static HTMX web application in a second shell:

```bash
API_BASE_URL=http://localhost:8081 \
./gradlew :bbb-web:run --no-daemon
```

The browser-facing application listens on port `8080` and the API listens on
port `8081` by default. Override `WEB_PORT`, `API_PORT`, and the database
variables rather than storing credentials in source files. Both Ktor modules
use `application.yaml` with `${ENV:default}` substitutions; do not add an
`application.conf` file. The API remains startable when the database is down
and exposes that state through `/health`; the web application maps API
connection, timeout, status, and malformed-response failures to `502 Bad
Gateway`. The complete database container setup remains in
`docs/setup/SETUP-DB.md`.

## Docker and Compose Development Environment

A full development environment is available using Docker Compose in `compose.yaml`. This brings up:
- **MariaDB 11.4** database configured with database `acs_ball_by_ball`, user `ballbyball`, password `p4ssw0rd`, and auto-initializes relational and dimensional warehouse schemas from `docker/mariadb/init/`.
- **bbb-api** listening on port `8081` connected to the database.
- **bbb-web** listening on port `8080` connected to `bbb-api`.
- **bbb-update-database** container runnable on demand with the `tools` profile.

### Start the development stack (MariaDB, API, Web)

```bash
docker compose up -d --build
```

Access the web interface at `http://localhost:8080` and the API at `http://localhost:8081`.

### Run database updates with bbb-update-database in Docker

Place Cricsheet JSON/CSV files in `./data` (or set `DATA_DIR` in `.env`), then run:

```bash
docker compose run --rm update-database \
  --outputType DATABASE \
  --baseDirectory /data \
  --playerRegistry people.csv \
  --connectionString jdbc:mariadb://mariadb:3306/acs_ball_by_ball \
  --userName ballbyball \
  --password p4ssw0rd
```

### Stop the development environment

```bash
docker compose down
# Or to remove volumes and reset the database:
docker compose down -v
```

## GitHub Actions CI/CD

Workflows are located in `.github/workflows/`:

- **CI (`ci.yml`)**: Runs `./gradlew clean check` on all pull requests and pushes to `main`.
- **BBB-API (`build-bbb-api.yml`)**: Builds and checks `bbb-api`, creates install distribution artifacts, and on version tags (`v*.*.*`) builds and pushes multi-architecture (`linux/amd64`, `linux/arm64`) Docker images to Docker Hub.
- **BBB-Web (`build-bbb-web.yml`)**: Builds and checks `bbb-web`, creates install distribution artifacts, and pushes Docker images on version tags.
- **BBB-Update-Database (`build-bbb-update-database.yml`)**: Builds and checks `bbb-update-database`, creates distribution artifacts, and pushes Docker images on version tags.
- **Reusable Workflows & Actions**:
  - `reusable-gradle.yml` & `reusable-docker.yml`
  - Composite actions in `.github/workflows/actions/` (`setup-jdk`, `use-gradle`, `docker-push`)

## Updating this runbook

When adding a tool or command:

1. Add its normal invocation and a minimal example here.
2. Document required inputs, output files, and database prerequisites.
3. Update the parser option table when `Application.kt` changes.
4. Add or update verification commands when tests, migrations, or adapters
   change.
5. Record the completed documentation change in `docs/project_memory.md`.