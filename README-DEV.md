# DataLoader developer commands

This document is the developer runbook for the `DataLoader` project. Keep it
updated whenever a Gradle task, parser option, output adapter, migration, or
database-loading workflow is added or changed.

## Prerequisites

- Java 21. Gradle uses the Java toolchain configured in the module build files.
- MariaDB client tools (`mariadb`) when loading data into MariaDB.
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
./gradlew :bb-update-database:test --no-daemon
```

Compile the loader without running it:

```bash
./gradlew :bb-update-database:compileKotlin --no-daemon
```

Display the parser command-line help:

```bash
./gradlew :bb-update-database:run --no-daemon --args="-h"
```

Check dependency updates and refresh the version catalog when required:

```bash
./gradlew dependencyUpdates --no-daemon
./gradlew versionCatalogUpdate --no-daemon
```

## Database schema

The warehouse schema is created by the Flyway migrations in
`bb-update-database/migrations/mysql`. Configure the connection through
environment variables rather than putting credentials in a command history:

```bash
export FLYWAY_URL="jdbc:mariadb://${DB_HOST}:${DB_PORT}/${DB_NAME}"
export FLYWAY_USER="$DB_USER"
export FLYWAY_PASSWORD="$DB_PASSWORD"
./gradlew :bb-update-database:flywayMigrate --no-daemon
```

For a new database, ensure the `cricsheet` database exists before running the
migrations. The migration files currently include the original schema
`1__initial_tables.sql` followed by the warehouse schema
`2__initial_warehouse.sql`.

```bash
mariadb --host="$DB_HOST" --port="$DB_PORT" \
  --user="$DB_USER" --password="$DB_PASSWORD" \
  --execute="CREATE DATABASE IF NOT EXISTS $DB_NAME"
```

## Parser command-line options

All parser runs use the Gradle application task:

```bash
./gradlew :bb-update-database:run --no-daemon --args="<options>"
```

| Option | Required | Description |
| --- | --- | --- |
| `-h`, `--help` | No | Print the command-line help. |
| `-bd`, `--baseDirectory` | Yes | Root directory containing scorecards and the player registry. |
| `-pr`, `--playerRegistry` | Yes | Player-registry filename relative to `baseDirectory`. |
| `-ot`, `--outputType` | No | `SQL` (default), `DATABASE`, `SQL_FILE`, or `CSV`. |
| `-c`, `--connectionString` | For database output | JDBC connection string. |
| `-u`, `--userName` | For database output | Database username. |
| `-p`, `--password` | No | Database password. Prefer an environment variable or protected shell history. |
| `-o`, `--outputFile` | For SQL files | SQL script path. With `SQL`, specifying this option selects file output. |
| `-sf`, `--sqlFile` | For SQL files | Alias for `--outputFile`. |
| `-cd`, `--csvDir` | For CSV output | Directory in which warehouse CSV files are written. |

## Produce CSV output

Run the parser with `CSV` output and a destination directory:

```bash
./gradlew :bb-update-database:run --no-daemon \
  --args="--outputType CSV --baseDirectory $CRICSHEET_DIR --playerRegistry $PLAYER_REGISTRY --csvDir $CSV_DIR"
```

The adapter clears `CSV_DIR` before writing. It emits one file per warehouse
table, with headers matching `2__initial_warehouse.sql`, including files such
as `dim_person.csv`, `dim_match.csv`, `fact_delivery.csv`, and
`bridge_delivery_wicket.csv`. Nullable values are written as MariaDB's `\N`
marker.

## Load CSV output into MariaDB

The database must have the warehouse tables before loading CSV data. The
following Bash loop loads files in foreign-key dependency order and skips
optional files that were not generated because the source data contained no
rows for that table:

```bash
CSV_DIR=csv/
DB_HOST=localhost
DB_PORT=3306
DB_NAME=acs_ball_by_ball
DB_USER=ballbyball
DB_PASSWORD='change-me'

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
  bridge_delivery_wicket
do
  file="$CSV_DIR/$table.csv"
  if [ -f "$file" ]; then
    mariadb --local-infile=1 \
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

Write an executable SQL script instead of CSV:

```bash
./gradlew :bb-update-database:run --no-daemon \
  --args="--outputType SQL_FILE --baseDirectory $CRICSHEET_DIR --playerRegistry $PLAYER_REGISTRY --outputFile $SQL_FILE"
```

Load that script into MariaDB:

```bash
mariadb --host="$DB_HOST" --port="$DB_PORT" \
  --user="$DB_USER" --password="$DB_PASSWORD" "$DB_NAME" < "$SQL_FILE"
```

The generated SQL file is self-contained for the warehouse schema: it drops
the warehouse tables in foreign-key dependency order, recreates them from
`2__initial_warehouse.sql`, and then loads the generated rows. This is
destructive to the existing warehouse data, so use it only when replacing the
entire warehouse is intended. The target database must already exist.
or
```bash
mariadb  --user=ballbyball --password=[password] "acs_ball_by_ball" < "sql/update.sql"
````

Write directly to MariaDB through the JDBC adapter:

```bash
./gradlew :bb-update-database:run --no-daemon \
  --args="--outputType DATABASE --baseDirectory $CRICSHEET_DIR --playerRegistry $PLAYER_REGISTRY --connectionString jdbc:mariadb://${DB_HOST}:${DB_PORT}/${DB_NAME} --userName $DB_USER --password $DB_PASSWORD"
```

`SQL` is the default output type and behaves like direct database output when
no output file is supplied. Supplying `--outputFile` with `SQL` selects the SQL
script adapter instead. `SQL_FILE` makes that offline behavior explicit. The
SQL-file adapter resets and recreates the warehouse tables; direct `DATABASE`
output does not reset tables.

## Updating this runbook

When adding a tool or command:

1. Add its normal invocation and a minimal example here.
2. Document required inputs, output files, and database prerequisites.
3. Update the parser option table when `Application.kt` changes.
4. Add or update verification commands when tests, migrations, or adapters
   change.
5. Record the completed documentation change in `docs/project_memory.md`.