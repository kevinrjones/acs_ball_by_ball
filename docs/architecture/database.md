# Database architecture

## Authoritative schema

The authoritative match-data schema is normalized and has no production
`dim_*` or `fact_*` tables. It is versioned independently for MariaDB,
PostgreSQL, and SQLite under `bbb-update-database/migrations`; migration `6`
is the rebuild-and-replay cutover.

The schema contains:

| Table | Responsibility |
|---|---|
| `dates` | Calendar attributes keyed by `date_id`. |
| `teams` | Source team identity and display name. |
| `people` | Shared players and officials with source names and `ca_id`. |
| `grounds` | Source ground identity and display name. |
| `matches` | Match entity, metadata, teams, result, measures, and identities. |
| `match_source_reference` | Provider/source key and raw-content digest provenance. |
| `innings` | Ordered innings and batting/bowling team roles. |
| `deliveries` | Ordered ball data, runs, extras, powerplay, and wicket count. |
| `wickets` | Source wicket identity and kind. |
| `match_people` | Match/player/official role assignments. |
| `delivery_wickets` | Delivery-to-wicket association. |
| `delivery_fielders` | Wicket-specific fielder association. |

`matches.id` is the generated internal relational key. It is distinct from
`canonical_match_id`, the stable ingestion identity, and nullable unique
`public_match_id`, the API identity. `duration_days`, `margin`, and
`match_count` live on `matches`; delivery measures live directly on
`deliveries`. All dependent rows reference the internal keys of their parent
rows.

## Identity and provenance

The updater derives canonical identity from the canonical envelope and never
from a filename. Replaying an unchanged envelope is idempotent through unique
source, match, innings, delivery, role, wicket, and association keys. A source
reference with the same canonical match identity but a changed digest is
rejected so corrected source data cannot silently leave stale rows.

`match_people.role_code` represents `PLAYER`, `UMPIRE`, `TV_UMPIRE`,
`RESERVE_UMPIRE`, and `MATCH_REFEREE`. `match_source_reference` stores the
provider, provider record key, source record ID, and raw-content digest needed
for correction detection.

## Loading paths

`bbb-update-database` writes the same relational contract through three output
paths:

1. `JdbcOutputAdapter` writes directly to MariaDB, PostgreSQL, or SQLite inside
   match-scoped transactions.
2. `SqlScriptOutputAdapter` emits executable dialect-specific DDL and inserts.
3. `CsvOutputAdapter` emits parent-before-child files for bulk loading.

The loading order is `dates`, `teams`, `people`, `grounds`, `matches`, source
references, `innings`, `wickets`, `deliveries`, `match_people`,
`delivery_wickets`, and `delivery_fielders`. Duplicate match roles and
fielder associations are suppressed using their relational unique keys.

`RelationalSchemaSql.kt` is the destructive schema generator used for output
scripts. The versioned dialect migrations remain authoritative for deployed
databases and define the same table contract with dialect-specific identity,
foreign-key, and conflict syntax.

## API reads

`bbb-api` generates checked-in jOOQ bindings from the relational schema. The
matches feature uses:

- `JooqMatchRepository` for recent summaries and filtered search, joining
  `matches`, `dates`, `teams`, `grounds`, `innings`, and `deliveries`.
- `JooqMatchScoresheetLoader` for ordered innings and deliveries, joining
  `people`, `wickets`, `delivery_wickets`, and `delivery_fielders`.

The repository and loader map into the existing `MatchSummary`, search, and
scoresheet domain models. Public HTTP contracts in `bbb-shared` are unchanged;
API callers use `public_match_id` and never depend on the internal numeric key.

## Cutover procedure

Existing databases are rebuild targets, not in-place translation targets:

1. Back up the existing database and retain the backup until verification is
   complete.
2. Apply migration `6` to create the normalized schema and remove legacy
   tables.
3. Replay every canonical envelope through the updater.
4. Verify counts, identity/provenance rejection, recent summaries, search, and
   scoresheets before pointing the API at the rebuilt database.

The migration intentionally contains no warehouse-row copy logic. Numeric
internal IDs may change during replay; canonical and public identities are the
stable operational references.