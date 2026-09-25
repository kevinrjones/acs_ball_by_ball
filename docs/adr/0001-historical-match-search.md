# ADR 0001: Historical match search boundary

## Status

Accepted for the search slice of BBB Sprint 1.

## Context

The warehouse has one `dim_match` row per source match. Match identity is
available through `match_key`, `source_match_id`, `file_name`, `match_type`,
`event_name`, `match_date_text`, `season`, the two team keys, and `ground_key`.
The optional `match_start_date_key` joins to `dim_date`; `fact_match` is a
one-to-zero-or-one extension and is not required for search. Team and ground
names are available through `dim_team` and `dim_ground`.

The scoresheet path is separately proven as `dim_match` to `dim_innings` to
`fact_delivery`, with `dim_person` roles and wicket/fielder bridges isolated
from delivery rows. Delivery ordering will use innings number/order, over,
ball-in-over, ball number, and `delivery_key`; it is not part of this search
implementation.

## Decision

- Use separate protected `GET /api/matches/search` and selected-match detail
  endpoints; keep `GET /api/matches` unchanged for recent matches.
- Use ACS-aligned structured filters for team, opponent, exactness, venue, date
  range, match type, and result over verified match identity fields.
- Reject neutral venue because the warehouse has no proven neutral-location
  field; do not broaden neutral requests into all venues.
- Use page-number pagination with defaults of page `1` and `20` results, a
  maximum page size of `50`, and a maximum page number of `10,000`.
- Return `Envelope<MatchSearchResponse>` with one match-row collection and
  metadata-only pagination. Date, type, season, team, ground, and result
  fields remain nullable; search does not infer a score.
- Parse raw query names once in the shared contract module and reuse that
  parser from the API and BFF routes.
- Order by known calendar date descending with null dates last, then the
  warehouse `match_key` descending as a deterministic tie-breaker.
- Forward only the BFF's server-side bearer token to the configured API and
  map upstream failures to the existing unavailable response model.

## Consequences

Search responses are bounded, deterministic, and safe when optional warehouse
metadata is absent. Free-text matching is intentionally limited to descriptor
fields; date parsing, tie/draw/no-result eligibility, score aggregates,
external links, and scoresheet pagination require a later product decision and
detail implementation. No migration is required for this slice.