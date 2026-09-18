# Project Memory

## What was shipped

- Implemented three-tier access control model in `bb-api` distinguishing unauthenticated (`/api/heartbeat/alive`, `/health`), machine-authenticated (`/api/matches`), and user-authenticated (`/api/user/profile`) endpoints.
- Integrated `auth-jwt` in `bb-api` with JWKS key retrieval and claim inspection validating user `sub` and user roles.
- Integrated `kbff` (Backend-for-Frontend) in `bb-web` for secure HTTP-only encrypted session cookies (`bb_session`), anti-CSRF protection, and OIDC lifecycle (`/bff/login`, `/signin-oidc`, `/bff/user`, `/bff/logout`).
- Implemented `DefaultTokenService` in `bb-web` with OAuth2 client-credentials token caching to supply bearer tokens to `bb-api` for anonymous/background data calls.
- Implemented reactive `AuthenticationService` and `csrfInterceptor` in Angular `bb-web/ClientApp` using Angular signals (`session`, `isAuthenticated`, `isAnonymous`, `userName`, `email`, `logoutUrl`).
- Updated Angular header with dynamic Sign In / Sign Out controls and added user profile display component.
- Replaced HTMX front end in `bb-web` with a modern standalone Angular 19 application in `bb-web/ClientApp`.
- Preserved all original styling and layout elements (header, brand badge, hero search buttons, match cards, changelog) using Tailwind CSS.
- Implemented `MatchService` and TypeScript domain models matching shared JSON API contracts (`RecentMatchesResponse`, `MatchSummary`, `ApiError`).
- Configured Ktor `bb-web` backend with JSON `ContentNegotiation` and `singlePageApplication` resource serving.
- Wired Angular build and headless Karma testing into the Gradle lifecycle (`./gradlew check`).
- Added GitHub Actions CI/CD workflows and composite actions for multi-module Gradle verification, artifact packaging, and Docker Hub container publishing.
- Created multi-stage Dockerfiles and CI Dockerfiles for `bb-api`, `bb-web`, and `bb-update-database`.
- Created a `compose.yaml` development environment with MariaDB 11.4 (`acs_ball_by_ball`), automatic schema initialization, and connected Ktor web and API services.
- Moved module-specific Gradle configuration from the root build script into `bb-shared/build.gradle.kts` and `bb-update-database/build.gradle.kts`.
- Added MariaDB, PostgreSQL, and SQLite warehouse output adapters with dialect-specific direct JDBC and SQL-file support.

## Key decisions

- **Inspect Claims for User Distinction**: Route authorization inspects claims (`sub` and presence of user roles) to differentiate client-credentials machine tokens from human user tokens, avoiding redundant verifier chains.
- **Backend-for-Frontend (BFF) Pattern**: Kept all access and refresh tokens out of the browser DOM/localStorage by terminating sessions in HTTP-only encrypted cookies via `kbff`.
- **Dedicated Machine Token Service**: Implemented in-memory cached client credentials token service in `bb-web` so `bb-api` can require authentication on all data endpoints while still serving public visitors.
- **CSRF Protection**: Functional Angular HTTP interceptor automatically appends `withCredentials: true` and `X-CSRF: 1` header to proxied requests.
- Each module now owns its plugins, repositories, dependencies, test configuration, and Java/Kotlin 21 configuration.
- The root build script retains root-level plugin setup, project metadata, and dependency-update version filtering.

## Gotchas

- In Ktor `testApplication`, redirect follow must be disabled (`followRedirects = false`) when testing external OIDC redirects (`/bff/login`) to prevent resolving external hosts.
- OIDC discovery responses parsed by Nimbus OIDC SDK require `issuer` and `jwks_uri` fields in mock metadata.
- The dependency update plugin uses the replacement `io.github.ben-manes.versions` ID.
- Generated SQL-file schemas must stay aligned with the dialect-specific warehouse migrations.

## Authentication and authorization across bb-web and bb-api

### Title

Implement three-tier JWT authentication, BFF session management with kbff, and reactive Angular authentication controls

### Date/time completed

2026-09-18 08:30

### What was shipped

- Implemented three-tier access control model in `bb-api` distinguishing unauthenticated (`/api/heartbeat/alive`, `/health`), machine-authenticated (`/api/matches`), and user-authenticated (`/api/user/profile`) endpoints.
- Integrated `auth-jwt` in `bb-api` with JWKS key retrieval and claim inspection validating user `sub` and user roles.
- Added public `/api/heartbeat/alive` route in `bb-api` returning status `{ "message": "Heartbeat: Alive" }` and secured `/api/matches` for machine/user bearer tokens.
- Added user-protected `/api/user/profile` route returning user profile details.
- Integrated `kbff` (Backend-for-Frontend) in `bb-web` for secure HTTP-only encrypted session cookies (`bb_session`), anti-CSRF protection, and OIDC lifecycle (`/bff/login`, `/signin-oidc`, `/bff/user`, `/bff/logout`).
- Implemented `DefaultTokenService` in `bb-web` with OAuth2 client-credentials token caching to supply bearer tokens to `bb-api` for anonymous/background data calls.
- Implemented reactive `AuthenticationService` and `csrfInterceptor` in Angular `bb-web/ClientApp` using Angular signals (`session`, `isAuthenticated`, `isAnonymous`, `userName`, `email`, `logoutUrl`).
- Updated Angular header with dynamic Sign In / Sign Out controls and added user profile display component.
- Updated documentation in `docs/architecture/applications.md`.
- Refactored security and claim extraction: consolidated duplicate `extractScopes`/`extractRoles` logic into functional `Payload.extractStringOrListClaims` extension, introduced domain `UserPrincipal`, encapsulated user authorization into `userProtected` route-scoped plugin, and made accepted API scopes configurable via `jwt.validScopes`.
- Optimized `DefaultTokenService` to cache OIDC discovery metadata (`token_endpoint`) using mutex protection and filter OIDC identity scopes automatically instead of hardcoding one-off conditionals.
- Hardened Angular `csrfInterceptor` to only attach credentials and `X-CSRF` headers to internal/relative requests, protecting external endpoints.
- Extracted static mock data out of `AppComponent` into dedicated fixture `sample-matches.fixture.ts`.
- Deduplicated `apiBaseUrl` configuration between `KbffConfigFactory` and `WebModule`, and bound HTTP client lifecycle in `moduleWithApiClient`.

### Key decisions

- **Inspect Claims for User Distinction**: Route authorization inspects claims (`sub` and presence of user roles) to differentiate client-credentials machine tokens from human user tokens, avoiding redundant verifier chains.
- **Backend-for-Frontend (BFF) Pattern**: Kept all access and refresh tokens out of the browser DOM/localStorage by terminating sessions in HTTP-only encrypted cookies via `kbff`.
- **Dedicated Machine Token Service**: Implemented in-memory cached client credentials token service in `bb-web` so `bb-api` can require authentication on all data endpoints while still serving public visitors.
- **CSRF Protection**: Functional Angular HTTP interceptor automatically appends `withCredentials: true` and `X-CSRF: 1` header to proxied requests.

### Gotchas

- In Ktor `testApplication`, redirect follow must be disabled (`followRedirects = false`) when testing external OIDC redirects (`/bff/login`) to prevent resolving external hosts.
- OIDC discovery responses parsed by Nimbus OIDC SDK require `issuer` and `jwks_uri` fields in mock metadata.
- Angular's default production optimization enables Beasties `inlineCritical: true`, which inlines critical CSS and rewrites external stylesheets to `media="print" onload="this.media='all'"`. When combined with strict default Content Security Policy headers from `kbff` (`style-src 'self'`, `script-src 'self'`), inline styles and the `onload` handler are blocked, preventing all CSS from applying. Resolving this requires setting `"optimization": { "styles": { "inlineCritical": false } }` in `angular.json` and configuring CSP in `KbffConfigFactory` (`style-src 'self' 'unsafe-inline' fonts.googleapis.com`, `font-src 'self' data: fonts.gstatic.com`).
- Ktor `monitor` events (`ApplicationStopped`) are hosted on `ApplicationEnvironment` and shared across auto-reloads. Subscribing without checking `if (app == this)` causes the listener from the newly created application to execute when the previous application is stopped during development auto-reload, immediately closing the new `HttpClient` (or `DatabaseResources`) and causing subsequent calls to fail with `JobCancellationException: Parent job is Completed`. The handler must check `if (app == this)` and dispose the registration handle.

### Test coverage areas

- `ApiModuleTest` (11 unit/integration tests): Public `/api/heartbeat/alive`, unauthenticated 401 rejections, machine token access to `/api/matches`, machine token 403 Forbidden on `/api/user/profile`, user token 200 OK with profile claims, and user token without explicit roles 200 OK.
- `WebModuleTest` (11 unit/integration tests): `DefaultTokenService` caching & single discovery resolution, `KtorMatchApiClient` bearer header injection, anonymous `/bff/user` 401, session claim extraction with CSRF tokens, `/bff/login` OIDC redirect, CSP headers, and auto-reload stopping event isolation.
- Angular unit tests (17 specs): `AuthenticationService` signals under 401/session responses, profile proxy requests, `AppComponent` anonymous Sign In rendering, authenticated Sign Out + profile display, and `csrfInterceptor` relative/external request filtering.
- Clean `./gradlew check` across all modules.

## Angular frontend migration

### Title

Transition web front end from HTMX to Angular

### Date/time completed

2026-09-17 16:16

### What was shipped

- Stripped out HTMX templates and `MatchHtmlRenderer` in `bb-web`.
- Created modern Angular application in `bb-web/ClientApp` using standalone components, Angular Signals, and Tailwind CSS.
- Preserved all original HTMX layout styling (header, brand identity, navigation, hero buttons, match cards, and changelog).
- Implemented `MatchService` with `HttpClient` consuming `/api/matches` returning typed JSON contracts (`RecentMatchesResponse`).
- Configured Gradle `bb-web` build with `node-gradle` (`npmInstallClientApp`, `buildClientApp`, `testClientApp`, `syncClientAppResources`) and Ktor `singlePageApplication`.

### Key decisions

- Used modern standalone Angular components (`bootstrapApplication`) without legacy `AppModules`.
- Configured Ktor `singlePageApplication` resource serving for client SPA and fallback routing while servicing `/api/` endpoints.
- Maintained `FAIL_ON_PROJECT_REPOS` compliance by using installed system Node runtime (`download.set(false)`).
- Included Angular Karma unit testing in the Gradle `check` lifecycle.

### Gotchas

- Angular control flow syntax requires `@` inside text (like email addresses) to be written as HTML entity `&#64;`.
- Gradle resource processing requires duplicate handling strategy (`DuplicatesStrategy.INCLUDE`) when merging generated client assets.

### Test coverage areas

- Angular unit tests (`app.component.spec.ts`, `match.service.spec.ts`) running headless in Karma / ChromeHeadless (8 specs).
- Ktor `WebModuleTest` verifying SPA shell serving at `/` and typed JSON response / error status mappings for `/api/matches`.
- Full project `./gradlew check` across all modules.

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

## Dialect-specific warehouse adapters

### Title

Add PostgreSQL and SQLite output adapters

### Date/time completed

2026-09-08 07:00

### What was shipped

- Moved output adapters into dialect-specific packages under `database.adapter`.
- Added MariaDB, PostgreSQL, and SQLite direct JDBC adapters with shared warehouse persistence behavior.
- Added dialect-specific SQL-file adapters and schema generation for identity columns, conflict handling, transactions, and connection setup.
- Added `--database` selection for SQL-file output and automatic direct-adapter selection from JDBC URLs.
- Updated `README-DEV.md` and the database architecture documentation with adapter structure and commands.

### Key decisions

- Shared JDBC persistence remains in `JdbcOutputAdapter`; only dialect-specific conflict syntax and connection initialization are specialized.
- SQL-file generation uses separate wrapper classes for each dialect while sharing the warehouse row/key model.
- PostgreSQL uses the `cricsheet` schema, while SQLite uses its `main` database and enables foreign keys.

### Gotchas

- SQL-file output resets the warehouse tables and is destructive to existing warehouse data.
- PostgreSQL direct connections set `search_path` to `cricsheet`; SQLite direct connections enable foreign keys before loading rows.
- PostgreSQL runtime migration execution was not available locally because no PostgreSQL server or container image was present.

### Test coverage areas

- MariaDB, PostgreSQL, and SQLite SQL-file syntax and conflict clauses.
- SQLite execution of the generated warehouse schema against an in-memory database.
- Existing CSV and MariaDB SQL-file adapter behavior.

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
- `./gradlew :bb-update-database:test --tests 'com.knowledgespike.ballbyball.parse.database.SqlScriptOutputAdapterTest' --no-daemon` passed.

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

## PostgreSQL and SQLite migrations

### Title

Add PostgreSQL and SQLite database migrations

### Date/time completed

2026-09-08 06:51

### What was shipped

- Added complete `1__initial_tables.sql` and `2__initial_warehouse.sql`
  migration pairs under `bb-update-database/migrations/postgres` and
  `bb-update-database/migrations/sqlite`.
- Added dialect selection to the Flyway Gradle configuration through
  `migration.database` or `FLYWAY_DATABASE`.
- Documented PostgreSQL and SQLite migration commands and dialect-specific
  schema behavior in `README-DEV.md` and `docs/architecture/database.md`.

### Key decisions

- MySQL remains the default Flyway target for backward compatibility.
- PostgreSQL uses identity columns and a `cricsheet` schema; SQLite uses
  `INTEGER PRIMARY KEY AUTOINCREMENT` and the built-in `main` database name.
- Indexes are separate `CREATE INDEX` statements outside MySQL table syntax so
  all three dialects preserve the same logical indexes.

### Gotchas

- PostgreSQL and SQLite migrations are dialect-specific and must not be mixed
  with the MySQL directory in one Flyway location.
- SQLite direct execution uses `PRAGMA foreign_keys = ON`; Flyway executions
  pass `-Pflyway.executeInTransaction=false` because SQLite does not change
  this connection setting inside an active transaction.

### Test coverage areas

- Both SQLite migrations execute together successfully and create the complete
  operational and warehouse table sets.
- PostgreSQL migration scripts passed repository whitespace validation; a local
  PostgreSQL server was unavailable for execution testing.

## Reproducible MariaDB and PostgreSQL test environments

### Title

Document and automate dual-database Docker setup

### Date/time completed

2026-09-08 08:27

### What was shipped

- Added `docs/setup/SETUP-DB.md` with detailed Docker, credential, migration,
  population, verification, lifecycle, and reset instructions.
- Added executable `docs/setup/setup-db.sh` to create or start both database
  containers, provision application users and databases, run Flyway, and load
  the same Cricsheet input into MariaDB and PostgreSQL.
- Linked the setup guide from the developer prerequisites in `README-DEV.md`.

### Key decisions

- MariaDB and PostgreSQL use separate pinned image tags, named volumes, and
  non-default host ports so both environments can run concurrently.
- Database creation and data loading remain idempotent and use the existing
  dialect-specific migrations and JDBC adapters rather than a second schema or
  loading implementation.

### Gotchas

- The script's default passwords are for local disposable environments only.
- Changing an existing container's image, port, volume, or administrative
  password requires `CONFIRM_RESET=1 docs/setup/setup-db.sh reset` first.

### Test coverage areas

- Shell syntax validation, executable help output, Docker status inspection, and
  repository whitespace validation.
- Full container migration and data-load execution remains dependent on Docker
  image availability and a local Cricsheet data directory.

## Delivery fielder bridge

### Title

Track fielders named on wicket dismissals

### Date/time completed

2026-09-08 10:38

### What was shipped

- Added `bridge_delivery_fielder` to the generated warehouse schema and to new
  Flyway migration `3__delivery_fielder.sql` for MySQL, PostgreSQL, and SQLite.
- Resolved Cricsheet wicket fielder names through `dim_person` and emitted the
  delivery, wicket, and fielder person relationship through every output mode.
- Updated the database architecture, developer runbook, and database setup
  documentation with the new table and loading order.

### Key decisions

- The bridge retains `wicket_key` as well as `delivery_key`, preserving the
  association when one delivery contains multiple wickets or fielders.
- The composite key (`delivery_key`, `wicket_key`, `person_key`) prevents
  duplicate fielder associations while allowing multiple fielders per wicket.

### Gotchas

- Fielder names must exist in the Cricsheet player registry because the bridge
  references `dim_person`.

### Test coverage areas

- End-to-end parser-to-SQL output for a caught wicket with a named fielder.
- CSV, SQL-script, generated-schema, and full Gradle verification coverage.

## BallByBall package namespace

### Title

Rename application packages from Cricsheet to BallByBall

### Date/time completed

2026-09-08 11:24

### What was shipped

- Renamed the application namespace from `com.knowledgespike.cricsheet` to
  `com.knowledgespike.ballbyball` across production code, tests, the Gradle
  application entry point, and logging configuration.
- Moved the Kotlin source and test package directories to match the new
  namespace and updated developer and architecture documentation paths.

### Key decisions

- Only the Kotlin package namespace changed; the `cricsheet` database/schema,
  migration identifiers, and Cricsheet input terminology remain unchanged.

### Gotchas

- Fully qualified test selectors and source-directory references must use the
  new `ballbyball` namespace.

### Test coverage areas

- Repository-wide stale-namespace search and full Gradle `clean check`.

## Fact match source filename

### Title

Retain source JSON filenames on match facts

### Date/time completed

2026-09-08 13:58

### What was shipped

- Added the non-null `file_name` column to generated `fact_match` schema output
  and all SQL/CSV output adapters.
- Added a versioned filename upgrade path for MariaDB, PostgreSQL, and SQLite,
  with backfill logic from `dim_match.file_name` for existing rows.
- Updated the database architecture, developer runbook, and Docker setup guide.

### Key decisions

- The application passes the original JSON filename from `Database.writeMatch`
  into every `fact_match` output mode.
- The original implementation used a versioned migration rather than
  rewriting the already-applied initial warehouse migration.

### Gotchas

- The original SQLite upgrade path used an empty-string default only while
  adding the column because SQLite cannot add a non-null column to a populated
  table without a default; existing values were immediately backfilled from
  `dim_match`.

### Test coverage areas

- SQL-script and CSV regression tests assert the filename column and emitted
  value in `fact_match` rows.
- SQLite generated-schema execution and the full Gradle verification suite.

## Consolidate fact match filename into the initial warehouse migration

### Title

Merge the fact match filename schema into migration version 2

### Date/time completed

2026-09-08 14:26

### What was shipped

- Added the non-null `fact_match.file_name` column directly to each dialect's
  `2__initial_warehouse.sql` migration.
- Removed the redundant follow-up migration files.
- Updated the developer, setup, and database architecture documentation to
  describe version 2 as the complete initial warehouse schema.

### Key decisions

- The project is still using fresh database setup rather than incremental
  migration upgrades, so the filename column is created with the rest of the
  warehouse schema.
- No backfill statement is needed in the initial migration because
  `fact_match` has no rows when it is created.

### Gotchas

- Databases that have already applied the removed follow-up migration should
  not rerun the consolidated version 2 migration; this change targets fresh
  early-stage environments.

### Test coverage areas

- Fresh SQLite schema execution and the full Gradle verification suite should
  confirm the initial warehouse migration exposes `fact_match.file_name`.

## Index fact delivery ball position

### Title

Add an index for `fact_delivery.ball_in_over`

### Date/time completed

2026-09-08 14:50

### What was shipped

- Added `idx_fact_delivery_ball_in_over` to the MariaDB, PostgreSQL, and SQLite
  version 2 warehouse migrations.
- Added the same index to generated dialect-specific warehouse schema output.
- Updated the database architecture documentation to describe ball-position
  filtering support.

### Key decisions

- The index is single-column because queries filtering by the ball position
  should not require a preceding `over_number` predicate.

### Gotchas

- Existing databases need the equivalent `CREATE INDEX` statement applied
  separately because the project currently manages fresh schemas through the
  consolidated version 2 migration.

### Test coverage areas

- Fresh SQLite migration index inspection and the full Gradle verification
  suite.

## Remove duplicated fact match filename

### Title

Keep the source filename only on `dim_match`

### Date/time completed

2026-09-08 15:29

### What was shipped

- Removed `fact_match.file_name` from the MariaDB, PostgreSQL, and SQLite
  warehouse migrations and generated warehouse schema.
- Removed the filename argument and value from the shared output contract,
  database writer, JDBC/SQL/CSV adapters, and regression tests.
- Updated the developer, setup, and database architecture documentation to
  describe `dim_match.file_name` as the single source filename field.

### Key decisions

- `MatchRecord.fileName` remains unchanged because the filename is required by
  `dim_match` for provenance and duplicate-match lookup.
- No incremental migration was added because the project is using the
  consolidated initial schema for fresh early-stage environments.

### Gotchas

- Existing databases containing the duplicate fact column must be rebuilt or
  altered separately before loading the updated output.

### Test coverage areas

- SQL-script, CSV, and end-to-end parser output now assert the filename only on
  `dim_match` and the reduced `fact_match` column set.
- Full Gradle verification and fresh SQLite schema execution.

## Add warehouse query indexes

### Title

Add requested lookup and delivery-sequence indexes to the initial warehouse schema

### Date/time completed

2026-09-08 21:18

### What was shipped

- Added the requested delivery sequence, match lookup, person-name, and wicket
  bridge indexes to the MariaDB, PostgreSQL, and SQLite version 2 migrations.
- Added the same indexes to generated dialect-specific warehouse schema output.
- Updated database architecture documentation and SQLite schema assertions.

### Key decisions

- Preserved the existing indexes and added the requested indexes with the exact
  names and column order supplied for query compatibility.

### Gotchas

- Existing databases need the equivalent `CREATE INDEX` statements applied
  separately because the project currently manages fresh schemas through the
  consolidated version 2 migration.

### Test coverage areas

- Generated SQLite warehouse schema contains all seven requested indexes.
- Full Gradle verification and migration execution checks.

## Suppress duplicate delivery fielder associations

### Title

Make duplicate `bridge_delivery_fielder` inserts safe and observable

### Date/time completed

2026-09-08 16:20

### What was shipped

- Added composite-key checks to parser, SQL-script, and CSV output so repeated
  `(delivery_key, wicket_key, person_key)` associations are emitted once.
- Added MariaDB, PostgreSQL, and SQLite conflict-safe SQL for direct JDBC and
  generated script output.
- Added warning logs containing the source filename and all bridge keys when the
  parser detects a repeated fielder, plus adapter warnings for suppressed calls.
- Documented the duplicate handling contract in the developer and database
  architecture guides.

### Key decisions

- The composite primary key remains the source of truth; duplicate rows are
  ignored because they represent the same delivery/wicket/person association.
- Duplicate detection happens before script/CSV emission and is also enforced
  at the database boundary for direct JDBC callers.

### Gotchas

- A warning identifies duplicate keys but does not identify which upstream
  Cricsheet object produced each occurrence beyond the source filename and
  wicket kind; inspect the corresponding JSON when investigating a warning.

### Test coverage areas

- Duplicate fielder payloads produce one bridge row.
- Repeated SQL-script and CSV adapter calls are suppressed.
- Dialect-specific PostgreSQL/SQLite SQL syntax and full Gradle verification.

## Add Ktor API and web applications

### Title

Create the JOOQ-backed API and static HTMX web application skeletons

### Date/time completed

2026-09-08 21:36

### What was shipped

- Added the `bb-api` Ktor application with environment-backed database
  configuration, Hikari pooling, JOOQ match queries, health checking, and
  JSON endpoints.
- Added the `bb-web` Ktor application with a static HTMX page and an API-backed
  HTML fragment route.
- Added shared serializable API contracts, Gradle version-catalog entries,
  application configuration, logging, and focused tests for both applications.
- Documented the module responsibilities and local run commands in
  `docs/architecture/applications.md` and `README-DEV.md`.

### Key decisions

- Kept `MatchRepository` as the persistence seam so HTTP routes do not depend
  directly on JOOQ or Hikari.
- Used JOOQ's DSL against the existing warehouse tables while postponing code
  generation until the schema and query surface are stable.
- Kept the web application framework-light: Ktor serves static HTML and HTMX
  performs the browser interaction, while the server performs the API call.

### Gotchas

- PostgreSQL connections must set `currentSchema=acs_ball_by_ball` in the JDBC
  URL because the warehouse tables live in that schema.
- Both applications require a migrated warehouse before the production API
  module can answer database requests.

### Test coverage areas

- API health response and match limit validation using an injected repository.
- Web API handoff and HTML rendering using a Ktor `MockEngine` client.
- Focused `bb-api` and `bb-web` Gradle test suites passed.

## Harden Ktor applications and adopt YAML configuration

### Title

Fix API/web runtime risks and replace HOCON with YAML

### Date/time completed

2026-09-09 07:50

### What was shipped

- Made the API database pool non-fail-fast so `/health` remains available when
  the configured database is unavailable.
- Moved blocking JOOQ/JDBC work to an IO dispatcher, selected JOOQ dialects from
  JDBC URLs, and ordered matches by start date with a deterministic key tie-breaker.
- Added explicit API validation and cancellation-safe exception handling, plus
  web-client timeouts and `502 Bad Gateway` mapping for upstream failures.
- Replaced both HOCON resources with Ktor `application.yaml` files and added the
  YAML configuration dependency through the version catalog.
- Removed committed IntelliJ module/VCS metadata and ignored regenerated module
  metadata.

### Key decisions

- The requested redundant database-index cleanup remains deferred; no database
  index definitions were changed in this task.
- `MatchRepository` is suspendable so transport code can call it without
  blocking Ktor request threads, while the repository owns dispatcher selection.

### Gotchas

- YAML environment substitutions use `${ENV:default}` rather than HOCON's
  optional substitution syntax.
- A database outage is reported by `/health`; a failed match query still uses
  the API's normal server-error response.

### Test coverage areas

- API health, startup with an unavailable database, valid/invalid limits, and
  JSON serialization.
- Web success, upstream HTTP failure, connection failure, and malformed JSON.
- Focused module tests and the full Gradle `check` task.

## Layer bb-api and bb-web around application seams

### Title

Introduce pragmatic layered Ktor application architecture

### Date/time completed

2026-09-09 09:04

### What was shipped

- Reorganised `bb-api` into bootstrap/configuration, inbound HTTP routes,
  application validation/use cases, outbound ports, and a JOOQ adapter.
- Reorganised `bb-web` into bootstrap, inbound routes/presentation, an
  application API-client port, and an outbound Ktor HTTP adapter.
- Added typed `MatchLimit` validation, separated `DatabaseHealth` from the
  match repository, and added shared `RecentMatchesResponse` and `ApiError`
  JSON contracts.
- Added the layered architecture, dependency direction, request flows, and
  contributor navigation to `docs/architecture/applications.md` and
  `AGENTS.md`.

### Key decisions

- Kept the layers inside the existing Gradle applications instead of creating
  more Gradle modules; the interfaces are sufficient seams for this scope.
- Kept `bb-shared` as the single source of API JSON contracts while keeping
  JOOQ rows and HTML rendering out of the shared module.
- Used sealed results for expected validation/upstream failures and retained
  cancellation-safe exception handling for unexpected failures.

### Gotchas

- Kotlin requires backticks when importing packages named `in`.
- `bb-api` and `bb-web` YAML module paths now point to their `bootstrap`
  packages.

### Test coverage areas

- API route validation, shared response serialization, health status, and
  unavailable-database startup.
- Web shared-contract decoding, HTML rendering, upstream status/connection,
  and malformed JSON failures through the outbound adapter.

## GitHub Actions CI/CD and Docker development environment

### Title

Add GitHub Actions workflows, Dockerfiles, and Docker Compose development environment

### Date/time completed

2026-09-09 11:15

### What was shipped

- Added GitHub Actions composite actions (`setup-jdk`, `use-gradle`, `docker-push`) and reusable workflows (`reusable-gradle.yml`, `reusable-docker.yml`).
- Added CI workflow (`ci.yml`) running `./gradlew clean check --no-daemon` on pull requests and pushes to `main`.
- Added release workflows (`build-bb-api.yml`, `build-bb-web.yml`, `build-bb-update-database.yml`) for Gradle building, distribution artifact uploading, and multi-architecture Docker image publishing.
- Added standalone and CI Dockerfiles for `bb-api`, `bb-web`, and `bb-update-database` using Temurin Alpine images and non-root application users.
- Added `compose.yaml` with MariaDB 11.4 (`acs_ball_by_ball`, `ballbyball` user, `p4ssw0rd` password), health checks, auto-initialization SQL scripts, API/Web services, and an on-demand `update-database` tool service.
- Added `.env.example` and updated developer documentation in `README-DEV.md`.

### Key decisions

- Used Temurin 21 Alpine for minimal image attack surface and efficient multi-architecture container runtime.
- Docker containers run as non-root user `appuser` with explicit logging and working directory permissions.
- Database initialization in Docker Compose mounts `docker/mariadb/init/` to pre-create both relational and dimensional warehouse schemas upon container startup.
- Reusable workflows and composite actions keep CI definitions DRY and consistent across microservices.

### Gotchas

- When running `bb-update-database` in Docker Compose, point `--connectionString` to `jdbc:mariadb://mariadb:3306/acs_ball_by_ball` within the internal network.
- Docker Compose development stack exposes MariaDB on 3306, API on 8081, and Web on 8080 by default.

### Test coverage areas

- `./gradlew clean check --no-daemon` validated across all modules.
- Docker Compose configuration and service dependency graph verified.