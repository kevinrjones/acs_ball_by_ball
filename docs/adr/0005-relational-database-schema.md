# ADR 0005: normalized relational match-data schema

## Status

Accepted

## Context

The updater and API had converged on a dimensional warehouse model with `dim_*`,
`fact_*`, and bridge tables. That model preserved the data needed by ingestion
and scoresheets, but it made the canonical match data harder to load and query
because the same match relationships were spread across dimensions and facts.

The schema also gained deterministic `canonical_match_id`, nullable unique
`public_match_id`, and `match_source_reference` provenance after the original
relational migration was written. A replacement must retain those contracts as
well as match measures, date metadata, officials, deliveries, wickets, and
fielder associations.

## Decision

Use normalized domain tables as the authoritative model:

- `dates`, `teams`, `people`, and `grounds` hold shared entities.
- `matches` is the match entity and owns `duration_days`, `margin`, and
  `match_count`; its generated `id` is an internal relational key distinct from
  `canonical_match_id` and `public_match_id`.
- `innings` and `deliveries` hold match event data directly. Delivery rows own
  the former delivery fact columns and use a unique `source_ball_id` for
  ingestion idempotency.
- `wickets` holds wicket data, while `delivery_wickets` and
  `delivery_fielders` make wicket and fielder relationships explicit.
- `match_people` represents every player and official assignment through a
  `role_code`, including `PLAYER`, `UMPIRE`, `TV_UMPIRE`, `RESERVE_UMPIRE`, and
  `MATCH_REFEREE`.
- `match_source_reference` retains provider, provider record key, source record
  ID, and raw-content digest for provenance and changed-source rejection.

The next versioned migration is a rebuild cutover. Operators back up the
warehouse, apply the replacement schema, and replay all canonical envelopes;
the migration does not translate warehouse rows or preserve compatibility
tables. This prevents old surrogate-key assumptions from leaking into the new
model and ensures canonical replay remains the source of truth.

## Consequences

The API and updater must change their internal table bindings and joins, but
their canonical envelope and public HTTP contracts remain stable. Internal
numeric IDs may change after replay, so callers must use canonical or public
identities rather than relying on historical warehouse keys. MariaDB,
PostgreSQL, and SQLite retain dialect-specific identity and DDL syntax in their
own migrations, while the generated SQL schema source must produce the same
relational tables and loading order.