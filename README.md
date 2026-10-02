# Ball by Ball

[![CI](https://github.com/kevinrjones/acs_ball_by_ball/actions/workflows/ci.yml/badge.svg)](https://github.com/kevinrjones/acs_ball_by_ball/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Ktor](https://img.shields.io/badge/Ktor-3.5-087CFA?logo=ktor&logoColor=white)](https://ktor.io/)
[![Angular](https://img.shields.io/badge/Angular-19-DD0031?logo=angular&logoColor=white)](https://angular.dev/)
[![Gradle](https://img.shields.io/badge/Gradle-build-02303A?logo=gradle&logoColor=white)](https://gradle.org/)

Gradle multi-module applications for retrieving cricket ball-by-ball data,
normalizing it into canonical match envelopes, loading a warehouse, and serving
match search and scoresheet APIs through a Ktor backend and Angular web UI.

| | |
| --- | --- |
| **Repository** | [`kevinrjones/acs_ball_by_ball`](https://github.com/kevinrjones/acs_ball_by_ball) |
| **Root project** | `BallByBall` |
| **Default web port** | `8080` |
| **Default API port** | `8081` |
| **Warehouse dialects** | MariaDB / MySQL, PostgreSQL, SQLite |

## Contents

- [What this repository contains](#what-this-repository-contains)
- [Data pipeline](#data-pipeline)
- [Modules](#modules)
- [Tech stack](#tech-stack)
- [Match identity and public URLs](#match-identity-and-public-urls)
- [API surface](#api-surface)
- [Quick start](#quick-start)
- [Docker Compose](#docker-compose)
- [CI/CD](#cicd)
- [Documentation map](#documentation-map)
- [Design notes](#design-notes)

## What this repository contains

- Command-line tools to download Cricsheet archives and player registers
- A Cricsheet adapter that emits source and canonical match envelopes
- A warehouse loader that writes SQL scripts, CSV files, or JDBC inserts, with
  transactional JDBC match writes
- A read-only Ktor REST API over the warehouse
- A Ktor-hosted, feature-sliced Angular SPA with OIDC BFF login and API proxying
- Shared contracts, tiny types, Flyway migrations, and architecture docs

## Data pipeline

```text
bbb-get-cricsheet-data
        │
        ▼
  Raw Cricsheet JSON + people/names CSV
        │
        ▼
bbb-parse-cricsheet
        │
        ▼
  SourceMatchEnvelope
        │
        ├─ current: single-source canonicalizer
        └─ future: multi-provider reconciliation app
        │
        ▼
  CanonicalMatchEnvelope
        │
        ▼
bbb-update-database
        │
        ▼
  Warehouse (MariaDB / PostgreSQL / SQLite, SQL, or CSV)
        │
        ▼
bbb-api  ──proxied by──▶  bbb-web (Angular)
```

Warehouse loading accepts **canonical envelopes only**. Cross-provider
reconciliation is intentionally a separate future application.

## Modules

| Module | Type | Responsibility |
| --- | --- | --- |
| `bbb-get-cricsheet-data` | CLI | Download raw Cricsheet JSON archives and player registers |
| `bbb-parse-cricsheet` | CLI | Convert raw Cricsheet JSON into source/canonical envelopes |
| `bbb-cli-shared` | Library | Shared CLI match schema and deterministic identity contracts |
| `bbb-update-database` | CLI | Load canonical envelopes into SQL, CSV, or JDBC warehouse output |
| `bbb-shared` | Library | Shared HTTP contracts and validated tiny types |
| `bbb-api` | Service | Read-only warehouse REST API (default port `8081`) |
| `bbb-web` | Service | Feature-sliced Angular SPA, OIDC BFF, and API proxy (default port `8080`) |

Module registration lives in `settings.gradle.kts`. Dependency versions live in
`gradle/libs.versions.toml`.

## Tech stack

| Area | Choices in this repo |
| --- | --- |
| Language / build | Kotlin, Java 21 toolchain, Gradle |
| HTTP services | Ktor |
| Frontend | Angular 19 SPA hosted by `bbb-web` |
| Functional types / validation | Arrow (`Either`, boundary parsing) |
| Serialization | `kotlinx.serialization` |
| Persistence access | jOOQ (API), JDBC adapters (loader) |
| Schema migration | Flyway (`bbb-update-database/migrations`) |
| Databases | MariaDB / MySQL, PostgreSQL, SQLite |
| Auth | JWT on the API; OIDC BFF sessions on the web app |
| Local runtime | Docker Compose (`compose.yaml`) |
| CI | GitHub Actions (`.github/workflows`) |

## Match identity and public URLs

Matches use layered deterministic identifiers. They are not interchangeable.

| Identifier | Purpose | Exposed in URLs? |
| --- | --- | --- |
| `sourceRecordId` | Provider-scoped UUIDv5 for one source record | No |
| `rawContentDigest` | SHA-256 of exact downloaded bytes; revision evidence only | No |
| `canonicalMatchId` | Stable UUID of the reconciled/canonical match | No |
| `publicMatchId` | Ten-digit URL identifier derived from the canonical UUID | Yes |
| `matchKey` | Generated warehouse surrogate key for SQL joins | No |

Public scoresheet URLs look like:

```text
/api/matches/{publicMatchId}/scoresheet
```

`publicMatchId` is deterministic and unique-constrained. Collisions fail loudly
rather than probing another value.

For generation details, clash probability, and recovery guidance, see:

- **[Generating match keys](docs/GENERATING_KEYS.md)**

Related decisions:

- [ADR 0003: Deterministic source and canonical match identity](docs/adr/0003-deterministic-match-identity.md)
- [ADR 0004: Deterministic public match identifiers](docs/adr/0004-deterministic-public-match-identifiers.md)

## API surface

Authenticated match routes (JWT bearer):

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/matches` | Recent matches (`days` / `limit`) |
| `GET` | `/api/matches/search` | Paginated match search |
| `GET` | `/api/matches/{publicMatchId}/scoresheet` | Match scoresheet |

Operational routes:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/health` | Database health |
| `GET` | `/api/heartbeat/alive` | Liveness heartbeat |

JSON request/response/error contracts are defined in `bbb-shared`.

## Quick start

### Prerequisites

- Java 21
- Docker (Compose stack and optional dual-database test setup)
- MariaDB client tools when loading SQL into MariaDB
- A Cricsheet working directory for download/parse/load workflows

### Clone and verify

```bash
git clone git@github.com:kevinrjones/acs_ball_by_ball.git
cd acs_ball_by_ball

./gradlew clean check --no-daemon
```

### Common developer workflows

Full command examples for retrieval, parsing, warehouse loading, API, and web
are in:

- **[README-DEV.md](README-DEV.md)**

Database container and migration setup:

- **[docs/setup/SETUP-DB.md](docs/setup/SETUP-DB.md)**

Typical local data path (details and flags in the developer runbook):

```bash
# 1. Download raw Cricsheet archives + registers
./gradlew :bbb-get-cricsheet-data:run --no-daemon \
  --args="--base-directory /path/to/data --data-directory cricsheet"

# 2. Normalize into canonical envelopes
./gradlew :bbb-parse-cricsheet:run --no-daemon --args="..."

# 3. Load warehouse output (SQL / CSV / JDBC)
./gradlew :bbb-update-database:run --no-daemon --args="..."

# 4. Run API and web against a migrated database
./gradlew :bbb-api:run --no-daemon
./gradlew :bbb-web:run --no-daemon
```

### Environment defaults

Copy `.env.example` when using Compose:

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_NAME` | `acs_ball_by_ball` | Database name |
| `DB_USER` | `ballbyball` | Application DB user |
| `DB_PASSWORD` | `p4ssw0rd` | Application DB password |
| `DB_PORT` | `3306` | Host MariaDB port |
| `API_PORT` | `8081` | Host API port |
| `WEB_PORT` | `8080` | Host web port |
| `DATA_DIR` | `./data` | Data mount for the updater container |

Do not commit real secrets. Prefer environment variables over hard-coded
credentials in source files.

## Docker Compose

`compose.yaml` starts:

- MariaDB with init scripts from `docker/mariadb/init/`
- `bbb-api` on port `8081`
- `bbb-web` on port `8080`
- `bbb-update-database` on demand via the `tools` profile

```bash
docker compose up -d --build
```

- Web UI: `http://localhost:8080`
- API: `http://localhost:8081`

Run the updater container after placing data under `./data` (or `DATA_DIR`):

```bash
docker compose run --rm update-database \
  --outputType DATABASE \
  --baseDirectory /data \
  --playerRegistry people.csv \
  --connectionString jdbc:mariadb://mariadb:3306/acs_ball_by_ball \
  --userName ballbyball \
  --password p4ssw0rd
```

Stop / reset:

```bash
docker compose down
docker compose down -v   # also remove the MariaDB volume
```

## CI/CD

Workflows live in `.github/workflows/`:

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `ci.yml` | PRs and pushes to `main`, version tags, manual | `./gradlew clean check --no-daemon` |
| `build-bbb-api.yml` | Version tags / manual | Build/check API; push multi-arch images on `v*.*.*` |
| `build-bbb-web.yml` | Version tags / manual | Build/check web; push images on version tags |
| Reusable workflows | Called by the above | Gradle setup and Docker publish helpers |

Composite actions under `.github/workflows/actions/` provide JDK setup, Gradle
execution, and Docker push steps.

## Documentation map

| Document | Contents |
| --- | --- |
| [README-DEV.md](README-DEV.md) | Developer runbook and command-line workflows |
| [docs/GENERATING_KEYS.md](docs/GENERATING_KEYS.md) | Match key generation, collision risk, and recovery |
| [docs/architecture/applications.md](docs/architecture/applications.md) | Module boundaries and runtime flows |
| [docs/architecture/database.md](docs/architecture/database.md) | Warehouse schema notes |
| [docs/setup/SETUP-DB.md](docs/setup/SETUP-DB.md) | Local database container setup |
| [docs/project_memory.md](docs/project_memory.md) | Shipped work, decisions, and gotchas |
| [docs/adr/](docs/adr/) | Architecture decision records |
| [docs/sprints/](docs/sprints/) | Sprint task notes |

### Architecture decision records

| ADR | Topic |
| --- | --- |
| [0001](docs/adr/0001-historical-match-search.md) | Historical match search |
| [0002](docs/adr/0002-cricsheet-data-cli.md) | Cricsheet data CLI |
| [0003](docs/adr/0003-deterministic-match-identity.md) | Deterministic source and canonical identity |
| [0004](docs/adr/0004-deterministic-public-match-identifiers.md) | Deterministic public match identifiers |

## Design notes

- Provider adapters emit source identity plus a raw-content digest.
- `bbb-parse-cricsheet` derives Cricsheet identity from the safe basename stem,
  not the full path.
- Warehouse loading accepts **canonical envelopes only**.
- Cross-provider reconciliation is intentionally a separate future application.
- `publicMatchId` is deterministic and unique-constrained; collisions fail
  loudly rather than probing another value.
- Internal warehouse joins continue to use generated `matchKey` values.
- API feature code is organized in vertical slices under
  `feature.<name>` (`presentation` / `domain` / `data`).
