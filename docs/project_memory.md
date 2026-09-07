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