# BBB Sprint 1: Historical Match Scoresheet

**Status:** planned  
**Sprint:** 1  
**Companion tasks:** [BBB Sprint 1 task checklist](../tasks/bbb-tasks-sprint-1-historical-match-scoresheet.md)

## 1. Goal and problem

Give a visitor a reliable way to find a completed historical cricket match and read a linear, delivery-by-delivery scoresheet for exactly one selected match. The workflow must be navigable from search to results to a selected-match route and back again, while clearly explaining unavailable or incomplete warehouse data.

This sprint is deliberately not a live-match experience. It will not introduce polling, real-time controls, in-progress sections, or claims about statistics that cannot be demonstrated by the warehouse schema and queries.

## 2. Current baseline and evidence

The implementation sprint starts from these verified seams:

- `bbb-api` currently exposes a protected recent-match flow at `GET /api/matches?limit=...`; `MatchesRoute` validates boundary parameters with Arrow and delegates through the matches service and repository.
- `bbb-shared` owns serializable `Envelope`, `MatchSummary`, and recent-match response contracts. New wire contracts belong here and must preserve value-class serialization.
- `bbb-web` has a typed `MatchApiClient`, a Ktor `KtorMatchApiClient`, and proxy routes that forward server-side machine or user tokens. The existing recent-match behavior must remain compatible.
- The Angular application has match models and service seams, but `app.routes.ts` is currently empty. Standalone routed components and query/path parameter conventions must be confirmed during discovery.
- The matches feature slice is organized under presentation, domain, and data packages. `JooqMatchRepository` is the persistence seam and `ApiModule` is the route/service wiring seam.
- The warehouse evidence is the `dim_match`, `dim_team`, `dim_ground`, `dim_date`, optional `fact_match`, `dim_innings`, `fact_delivery`, `dim_person`, `bridge_delivery_wicket`, `dim_wicket`, and `bridge_delivery_fielder` model. ACS search/scorecard behavior and the Stitch scorecard explorer are reference material, not contracts.

## 3. User journey and UX states

1. A visitor opens the historical-match search page and sees an idle search form with clear labels and bounded input guidance.
2. The visitor submits a valid search. The page shows a loading state and prevents ambiguous duplicate submissions.
3. The page shows a deterministic, bounded result list when matches are found. Each result exposes enough verified identity context to distinguish matches.
4. The page shows a no-results state with an actionable way to revise the search.
5. The page shows an error state for unavailable or malformed upstream data without exposing internal details; retry and search revision remain available.
6. A visitor selects exactly one result. The selected match opens at a stable direct route containing a validated match key.
7. The selected-match page shows match context, completeness messaging, innings sections, and linear delivery rows ordered by the explicit warehouse ordering rules.
8. The page shows a not-found state for an unknown or no-longer-available match key, and an incomplete-data state when metadata or delivery rows are missing.
9. A back-to-results action restores the prior search criteria and results where practical; browser back and direct selected-match navigation remain meaningful.

The UI must use semantic headings, form labels, status announcements for loading/errors, keyboard-accessible result selection, semantic table headers, and a narrow-viewport strategy for wide delivery rows.

## 4. Scope

### 4.1 In scope

- Completed or historical match search with bounded, deterministic results.
- Exactly-one match selection and a direct selected-match route.
- Match context, innings summaries, raw delivery rows, source labels, extras, and available wicket/fielder associations.
- Explicit completeness indicators and user-facing messaging for missing or partial warehouse data.
- Separate shared contracts, protected API routes, BFF proxy operations, and Angular service/routes/components.
- Search and scoresheet pagination or chunking designed for bounded responses and large matches.
- Loading, empty, error, 404, incomplete-data, responsive, and accessibility behavior.
- Unit, repository integration, route/BFF, Angular, accessibility, and end-to-end verification appropriate to each slice.
- An ADR and documentation follow-ups when the accepted implementation changes architectural boundaries or contributor navigation.

### 4.2 Out of scope

- Live status, polling, refresh timers, real-time controls, or in-progress innings sections.
- Projections, win probability, unsupported visual-reference metrics, or claims that data is “verified” without an agreed source and proof.
- Derived batting/bowling aggregates, dismissal identities, fall-of-wickets, partnerships, or other summaries unless discovery proves the required source data and product approves them.
- Speculative external original-scan/download links and exports.
- Database migrations unless discovery proves a schema gap and that change is separately approved.
- Browser token storage or client-side access to upstream machine credentials.

## 5. Functional requirements

### 5.1 Search

- The API must validate all query parameters at the presentation boundary using existing Arrow validation conventions and tiny types.
- The proposed default is bounded free-text search over verified match identity fields. Team, opponent, date, type, season, or result filters require warehouse/query discovery and HITL approval before implementation.
- Blank, malformed, over-limit, and otherwise invalid values must produce a stable 400 envelope without invoking the repository.
- Search results must have explicit page-size and cursor/offset semantics, a stable deterministic order, and no unbounded response.
- Zero, one, many, and duplicate-looking results must render distinctly enough for exactly-one selection.

### 5.2 Selection and navigation

- A result must navigate to a typed match-key route rather than embedding scoresheet data in the result response.
- The selected route must support direct navigation and server-side/API authorization independently of the search page.
- Back-to-results must preserve search criteria and restore results when state is available; it must also behave safely when opened directly.
- An invalid match key must be rejected at the route boundary. An unknown valid key must produce a controlled 404 response and UI state.

### 5.3 Scoresheet

- The detail response must contain selected-match context, innings sections, delivery rows, and completeness metadata.
- Delivery ordering must be deterministic across innings boundaries, repeated over/ball labels, extras, multiple wickets, and tie-breaking delivery keys.
- The UI must display source labels and extras without treating every repeated label as a legal ball or assuming over/ball alone is unique.
- Missing match metadata, absent `fact_match`, absent innings, empty innings, partial deliveries, nullable dates/results/wickets, and multiple wickets on one delivery must produce explicit metadata/messages rather than fabricated values.
- Wicket/fielder fields are optional and may be shown only when supported by the warehouse joins without duplicating delivery rows.

## 6. Proposed API and shared contracts

Use separate protected endpoints and preserve the recent-match route:

- `GET /api/matches/search?...` returns an `Envelope` containing bounded `MatchSummary` results and pagination metadata. The exact filter set, limit, and cursor semantics are HITL gates.
- `GET /api/matches/{matchKey}/scoresheet?...` returns an `Envelope` containing selected-match context, innings summaries, ordered delivery rows, completeness indicators, and optional supported wicket/fielder details.
- `GET /api/matches?limit=...` remains backward-compatible and is not overloaded with delivery data.
- The corresponding BFF paths under `/api` call typed `MatchApiClient` operations. `KtorMatchApiClient` uses `TokenService`, forwards only the intended server-side authorization, and maps non-2xx, network, timeout, and malformed responses to the existing unavailable result model.
- New contracts belong in `bbb-shared/.../contracts/ApiContracts.kt`. Search filters, page metadata, scorecard context, innings, delivery, wicket/fielder, and completeness types must use validated tiny types and nullable fields where the warehouse is nullable.
- Error envelopes must be stable for browsers and must not expose stack traces, SQL, tokens, internal hostnames, or upstream response bodies.

The contract design must be finalized from discovery and tests before repository queries are treated as implementation commitments.

## 7. Warehouse mapping and deterministic ordering

### 7.1 Search mapping

Search should join `dim_match` to `dim_team`, `dim_ground`, and `dim_date`, with `fact_match` only when its optional data is needed. Queries must use available indexed identity, team, type, season, and date columns. Result ties must use a documented stable match key and date key ordering appropriate to the discovered schema.

### 7.2 Scoresheet mapping

The proposed path is `dim_match` → `dim_innings` → `fact_delivery`, with `dim_person` for batter, non-striker, and bowler names. Wicket information comes through `bridge_delivery_wicket`/`dim_wicket`, and fielders through `bridge_delivery_fielder`. Bridge joins must be aggregated or otherwise isolated so one delivery cannot appear more than once because of multiple associated records.

### 7.3 Delivery order and completeness

The implementation must establish and test this ordering, adjusting only when schema discovery proves a column unavailable:

1. innings number/order;
2. `innings_order`;
3. `over_number`;
4. `ball_in_over`;
5. `ball_number`;
6. `delivery_key` as a stable final tie-breaker.

The mapper must preserve source values and nullable data. It must not infer legal-ball counts, dismissal identities, results, or completeness from a convenient but unsupported field. Missing rows, incomplete innings, absent optional facts, and nullable match/date/wicket values become explicit completeness states.

## 8. BFF, security, and operational behavior

- API and BFF routes remain protected by the existing authentication boundary.
- The BFF forwards machine or user tokens through the existing `TokenService` path only to the configured trusted API; no target URL or credential is user-controlled.
- The browser never stores or receives upstream machine credentials.
- Search and match-key inputs are bounded before expensive work. Pagination limits, request timeouts, and response sizes must prevent resource exhaustion.
- Logs must include useful route/status/request context without access tokens, cookies, credentials, or full sensitive response bodies. Exceptions are logged through the project logger and mapped to controlled errors.
- Upstream timeout, status failure, malformed JSON, database unavailability, unauthenticated access, and invalid tokens must have tested, stable mappings.
- API 400/404/5xx behavior and BFF proxy behavior must not leak SQL, stack traces, internal paths, or upstream host details.

## 9. Accessibility, responsive design, and performance

- Search inputs and actions have programmatic labels, visible focus, keyboard operation, and clear validation messages.
- Loading, no-results, error, not-found, and incomplete states are announced or otherwise readable by assistive technology without relying on color alone.
- Result selection and back navigation work without a pointer. Focus is restored or intentionally moved after route changes.
- Delivery rows use semantic table markup with headers and an accessible narrow-screen overflow or equivalent linear presentation. The table must not require inaccessible horizontal gestures.
- Search results and delivery rows are bounded through approved pagination/chunking. The UI must not lock up on large result sets or large matches.
- Any client-side cancellation or stale-route handling must ensure an older response cannot overwrite a newer selected match.
- The Stitch design may guide hierarchy and linear matrix treatment, but illustrative live, projection, export, and verification content is excluded.

## 10. Test and verification strategy

- API/domain unit tests use JUnit 5 and Strikt for tiny-type validation, page/cursor rules, error mapping, ordering, completeness states, and 404 behavior.
- Repository integration tests use Testcontainers-backed warehouse fixtures for search joins, multiple innings, extras, repeated labels, multiple wickets, missing optional facts, bridge aggregation, and stable delivery ordering. Fixtures must be deterministic and must not require a live external service.
- Ktor route and BFF tests cover protected access, 400 boundary validation, 404, envelope serialization, machine/user token forwarding, malformed upstream responses, upstream failures, and unchanged recent-match behavior.
- Angular service and component tests mock HTTP at the service boundary and cover idle/loading/results/no-results/error/incomplete states, exactly-one selection, direct route navigation, back-to-results, pagination, stale requests, and delivery-row rendering.
- Accessibility and responsive checks cover keyboard-only search/selection/back behavior, labels, focus order, status messaging, table headers, and narrow viewport access.
- End-to-end verification covers historical search → result selection → selected route → innings/delivery view → back-to-results against deterministic data.
- Before sprint completion, run relevant frontend checks, `./gradlew clean check --no-daemon`, coverage review with configured tooling, and a cyclomatic-complexity check. Record gaps rather than suppressing failures.

## 11. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Unsupported statistics are presented as facts | Keep contracts limited to demonstrated warehouse fields; defer derived metrics until discovery and approval. |
| Over/ball labels do not uniquely order deliveries | Preserve source labels and test the full ordering tuple through delivery-key tie-breaking. |
| Bridge joins duplicate delivery rows | Aggregate wicket/fielder associations separately and verify with multi-association fixtures. |
| Large searches or matches exhaust API/browser resources | Enforce bounded limits and explicit pagination/chunking at every boundary. |
| API/BFF contract drift | Define shared contracts first and test both Ktor adapters with route and `MockEngine` coverage. |
| Authentication or token leakage regression | Keep protected routes, server-side token handling, trusted upstream configuration, and negative security tests. |
| Architecture documentation becomes stale | Add ADR and documentation follow-up tasks; update architecture docs only after accepted implementation changes. |

## 12. HITL decisions and proposed defaults

The following decisions must be confirmed before the dependent implementation task is marked complete:

1. **Search filters:** proposed default is bounded free-text over verified identity fields. Optional structured team/opponent/date/type/result semantics, informed by ACS, remain a review choice after query discovery.
2. **Historical eligibility:** proposed default is an API-defined historical record with `dim_match` metadata and available innings/delivery data. Tie, draw, and no-result `victory_type` values are not live status and require product confirmation.
3. **Score detail:** proposed default is context, innings, raw delivery rows, and available wicket/fielder associations. Aggregates, dismissal identities, fall-of-wickets, partnerships, projections, and “verified” labels are deferred unless proven and approved.
4. **Pagination:** proposed default is deterministic bounded search pagination plus innings/delivery pagination or chunking. Exact cursor and limit semantics require approval before implementation.
5. **Links:** proposed default is no external original-scan or download link unless a stable approved source URL exists.
6. **API boundary:** approved choice is separate search and detail endpoints, preserving recent-match compatibility.

## 13. Measurable acceptance criteria

- A valid historical search returns a bounded, deterministically ordered result set and pagination metadata; blank/invalid/over-limit inputs return controlled 400 responses.
- Zero, one, and many result states are distinguishable and keyboard usable; selecting one result reaches a stable match-key route.
- Direct navigation to a valid selected-match route returns the same scoresheet contract as navigation from search; an unknown match returns 404 and an invalid key returns 400.
- A selected match renders context, every available innings, and delivery rows in the tested deterministic order, including repeated labels, extras, multiple wickets, and delivery-key ties.
- Missing or incomplete data produces visible, accessible completeness messaging and no fabricated statistics.
- Search criteria/results are restored on back-to-results when available, and direct navigation has a safe fallback when no prior search state exists.
- API and BFF routes reject unauthenticated access, forward only approved server-side tokens, and map upstream failures without leaking internals.
- The responsive scoresheet remains usable at a narrow viewport, and keyboard-only users can complete search, selection, scoresheet navigation, and return navigation.
- Relevant automated tests and required Gradle/frontend/coverage/complexity checks are run with failures recorded and fixed or explicitly documented.

## 14. Definition of done

- All approved HITL gates are recorded in the implementation ADR or sprint review notes.
- Shared contracts, API/domain/repository, BFF, and Angular slices are implemented without breaking recent matches.
- Search-to-selection-to-scoresheet and direct/back navigation are verified end to end.
- Incomplete data, ordering edge cases, authentication boundaries, upstream failures, and large bounded responses have tests.
- Accessibility and responsive behavior have been checked against the requirements above.
- `./gradlew clean check --no-daemon`, relevant frontend checks, coverage review, and cyclomatic-complexity review are complete; no failures are hidden.
- If module/runtime boundaries changed, `docs/architecture/applications.md` and the relevant ADR are updated. `README.md` and `docs/project_memory.md` receive the implementation-sprint follow-up.
- The companion checklist is updated only as implementation work actually completes; this sprint specification remains a record of the approved scope and decisions.