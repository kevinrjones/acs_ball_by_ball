# Project Memory

## What was shipped

- Moved module-specific Gradle configuration from the root build script into `bb-shared/build.gradle.kts` and `bb-update-database/build.gradle.kts`.

## Key decisions

- Each module now owns its plugins, repositories, dependencies, test configuration, and Java/Kotlin 21 configuration.
- The root build script retains root-level plugin setup, project metadata, and dependency-update version filtering.

## Gotchas

- The dependency update plugin uses the replacement `io.github.ben-manes.versions` ID.

## Gradle configuration ownership

### Title

Move project configuration into module build files

### Date/time completed

2026-09-07 16:35

### What was shipped

- `bb-shared` and `bb-update-database` now configure themselves independently of root `allprojects` and `project` blocks.

### Key decisions

- Kotlin plugin versions remain declared at the root with `apply(false)` to avoid loading the Kotlin Gradle plugin multiple times.

### Gotchas

- Neither module currently contains test sources, so Gradle reports their test tasks as `NO-SOURCE`.

### Test coverage areas

- Clean compilation and `check` execution for both modules.

## Build configuration best-practices update

### Title

Align Gradle configuration with Java toolchain and reproducibility best practices

### Date/time completed

2026-09-07 16:38

### What was shipped

- Centralized plugin and dependency repositories in `settings.gradle.kts` with project repository declarations rejected.
- Applied the Foojay toolchain resolver through the settings plugin DSL.
- Standardized both modules on the Java 21 Kotlin toolchain and removed redundant Java compatibility settings.
- Externalized Flyway connection credentials to Gradle properties or environment variables.
- Replaced the deprecated dependency update plugin ID in the version catalog.

### Key decisions

- `jvmToolchain(21)` is the single source of truth for the Java and Kotlin compilation toolchain.
- `mavenLocal()` was removed to keep dependency resolution reproducible across environments.
- Test configuration uses lazy `Test` task configuration so future test tasks inherit the same JUnit Platform setup.

### Gotchas

- Flyway uses local development defaults for the URL and user, while the password defaults to empty and should be supplied through `flyway.password` or `FLYWAY_PASSWORD` when migration tasks run.

### Test coverage areas

- `./gradlew clean check --no-daemon` passed for both modules.
- No test sources currently exist, so Gradle reported `NO-SOURCE` for test tasks.

## Cricket warehouse schema

### Title

Add dimensional warehouse DDL

### Date/time completed

2026-09-07 17:14

### What was shipped

- Added `bb-update-database/migrations/mysql/2__initial_warehouse.sql`.
- Added conformed date, team, person, ground, match, innings, and wicket dimensions.
- Added match and delivery facts plus bridges for match person roles and delivery wickets.

### Key decisions

- Warehouse surrogate keys are separate from source identifiers, which remain on dimensions for ETL traceability.
- Fact grains are one row per match and one row per ball delivery.
- Many-to-many relationships are represented as factless bridge tables rather than duplicated fact rows.

### Gotchas

- The date dimension must be populated before loading facts; nullable date foreign keys preserve source matches with no start date.
- The migration creates schema only; ETL loading and role-code standardisation remain application responsibilities.

### Test coverage areas

- The migration executed successfully in an isolated MariaDB 12.3.2 database and created 11 tables.
- `./gradlew check --no-daemon` passed; modules currently contain no test sources.

## Warehouse persistence loader

### Title

Migrate database statements to the dimensional warehouse schema

### Date/time completed

2026-09-07 17:32

### What was shipped

- Reworked `Database` persistence from the operational tables to `dim_*`, `fact_*`, and `bridge_*` tables.
- Added surrogate-key lookups and inserts for teams, people, grounds, dates, matches, innings, deliveries, and wickets.
- Replaced role-specific person-match tables with `bridge_match_person` role assignments.

### Key decisions

- Source identifiers are allocated from warehouse tables while warehouse-generated keys are used for foreign-key columns.
- Delivery rows retain the source grain and link to innings, date, teams, and people through warehouse keys.
- Existing match-file detection uses `dim_match.file_name` so repeated loads remain idempotent.

### Gotchas

- The application entry point still contains pre-existing commented database invocation code; this change updates the active `Database` persistence implementation.
- Both modules currently have no test sources, so Gradle reports `NO-SOURCE` for test tasks.

### Test coverage areas

- `./gradlew :bb-update-database:compileKotlin --no-daemon` passed.
- `./gradlew check --no-daemon` passed.
- SQL search confirmed no active Kotlin statements reference the original operational tables.

## Warehouse output adapters

### Title

Add SQL script output using the adapter model

### Date/time completed

2026-09-07 18:05

### What was shipped

- Introduced `OutputAdapter` so warehouse persistence is independent of its destination.
- Added `SqlOutputAdapter` for direct MariaDB writes and `SqlScriptOutputAdapter` for executable SQL bulk-load scripts.
- Added `SQL`, `DATABASE`, and `SQL_FILE` output selection with `-o/--outputFile` and `-sf/--sqlFile` support.

### Key decisions

- `SQL` remains the default direct-database mode, matching the reference application; providing an output file selects offline SQL generation.
- Offline scripts use explicit surrogate keys and a single transaction so foreign-key relationships remain valid when loaded into an empty warehouse schema.
- SQL literals are escaped centrally in the script adapter, and duplicate source entities are suppressed in adapter state.

### Gotchas

- Generated scripts target the warehouse schema from `2__initial_warehouse.sql` and should be loaded after that migration has run.
- Offline key allocation starts at one and is intended for a new warehouse; loading into a populated database should use direct `SQL` mode or an explicitly coordinated merge process.

### Test coverage areas

- SQL literal escaping and duplicate person suppression.
- Explicit match/fact surrogate-key relationships in generated scripts.
- `./gradlew :bb-update-database:test --no-daemon` passed.

## CSV warehouse output

### Title

Add CSV output using the adapter model

### Date/time completed

2026-09-07 21:05

### What was shipped

- Added `CsvOutputAdapter` with one UTF-8 CSV file for each warehouse dimension, fact, and bridge table.
- Added `CSV` output selection and the `-cd/--csvDir` command-line argument.
- Added CSV escaping, MariaDB `\N` null values, stable surrogate keys, and duplicate relationship suppression.

### Key decisions

- CSV headers and column order follow `2__initial_warehouse.sql` so the files can be bulk-loaded with the warehouse DDL.
- CSV output uses the same in-memory key model as SQL and SQL-script output, preserving foreign-key relationships in a new warehouse.

### Gotchas

- The selected CSV output directory is cleared before generation, matching the reference project and preventing stale rows from previous runs.
- CSV files should be loaded after `2__initial_warehouse.sql`; nullable values are represented as MariaDB's `\N` marker.

### Test coverage areas

- CSV headers, escaping, duplicate suppression, null handling, and foreign-key values.
- `./gradlew :bb-update-database:test --no-daemon` and CLI help verification passed.

## Developer command runbook

### Title

Document CSV generation and database loading commands

### Date/time completed

2026-09-07 21:13

### What was shipped

- Added `README-DEV.md` with build, migration, parser, CSV, SQL-script, direct-database, and MariaDB bulk-load commands.
- Documented the foreign-key-safe CSV load order and the optional CSV files that may not be generated for empty source data.
- Added a maintenance checklist requiring new tools and command-line options to be documented as they are added.

### Key decisions

- CSV loading is documented with `LOAD DATA LOCAL INFILE` in dependency order so generated surrogate keys and foreign keys remain valid.
- Database credentials are shown as shell variables and Flyway environment variables rather than committed values.

### Gotchas

- The warehouse schema must be migrated before loading CSV output; generated SQL files recreate the warehouse schema themselves.
- CSV output is intended for a new or explicitly coordinated warehouse because the adapter writes its own surrogate keys.

### Test coverage areas

- Documentation examples were checked against the current CLI options, Flyway configuration, migration filenames, and CSV adapter output order.

## Self-contained SQL-file warehouse reset

### Title

Drop and recreate warehouse tables in generated SQL files

### Date/time completed

2026-09-07 21:24

### What was shipped

- Updated `SqlScriptOutputAdapter` to emit foreign-key-safe `DROP TABLE IF EXISTS` statements and the complete warehouse `CREATE TABLE` definitions before generated rows.
- Added a regression test covering reset order, schema recreation order, and transaction placement.
- Updated `README-DEV.md` to document that SQL-file loads replace the existing warehouse data.

### Key decisions

- Only offline SQL-file output resets the schema; direct `DATABASE` output retains its existing upsert and insert behavior.
- DDL is emitted before `START TRANSACTION` because MariaDB implicitly commits DDL statements.

### Gotchas

- Loading a generated SQL file is destructive to all warehouse tables and requires the target database itself to already exist.
- The generated schema must remain aligned with `2__initial_warehouse.sql` when warehouse migrations change.

### Test coverage areas

- Foreign-key-safe table drop order and parent-before-child create order.
- Existing SQL literal escaping and generated foreign-key relationship tests.
- `./gradlew :bb-update-database:test --tests 'com.knowledgespike.cricsheet.parse.database.SqlScriptOutputAdapterTest' --no-daemon` passed.

## Warehouse database documentation

### Title

Document warehouse schema and relationships

### Date/time completed

2026-09-08 06:41

### What was shipped

- Added `docs/architecture/database.md` as the reference guide for the
  dimensional warehouse created by `2__initial_warehouse.sql`.
- Documented all dimensions, facts, bridges, grains, keys, columns, indexes,
  foreign keys, nullable relationships, and loading order.
- Added Mermaid entity-relationship, analytical-path, and load-order diagrams.

### Key decisions

- The documentation treats `2__initial_warehouse.sql` as the authoritative
  schema source and distinguishes source identifiers from warehouse surrogate
  keys.
- The table guide explicitly records role-specific joins and fact grains to
  help prevent double-counting in analytical queries.

### Gotchas

- `dim_date` must be populated before date-bearing facts, and all parent rows
  must exist before bridge rows because the migration declares restrictive
  foreign-key relationships without cascades.
- The migration creates schema only; ETL population and `role_code`
  standardisation remain application responsibilities.

### Test coverage areas

- Documentation was checked against the supplied migration DDL, the developer
  runbook’s warehouse loading order, and the existing warehouse project-memory
  entry.