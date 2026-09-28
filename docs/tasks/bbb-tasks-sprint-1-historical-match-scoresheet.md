# BBB Sprint 1 Tasks: Historical Match Scoresheet

**Status:** sections 1–3 complete; sections 4–6 pending
**Sprint specification:** [BBB Sprint 1: Historical Match Scoresheet](../sprints/bbb-sprint-1-historical-match-scoresheet.md)  
**Rule:** checkboxes record implementation evidence; end-to-end integration, sprint verification, and review work remains unchecked.

## 1. Discovery and UX skeleton

### 1.1 Confirm boundaries and warehouse capabilities

- [x] **Dependencies:** none. **Areas:** `docs/architecture/applications.md`, `docs/architecture/database.md`, `bbb-update-database/migrations/mysql/2__initial_warehouse.sql`, loader/mapping code. **Completion:** record the verified match identity, innings, delivery, person, wicket, fielder, optional fact, nullable-field, index, and key semantics; explicitly list fields that cannot be promised. **Testing:** add or update deterministic fixture notes/tests where discovery changes an assumption.
- [x] **Dependencies:** 1.1. **Areas:** matches repository queries and warehouse fixtures. **Completion:** prove the search joins and scoresheet joins, bridge aggregation approach, and delivery ordering tuple; document any unavailable column or schema gap without changing migrations. **Testing:** use a local/Testcontainers-backed fixture or an executable query test, never a live external service.
- [x] **Dependencies:** 1.1. **Areas:** ACS `acs-api`/`acs-web` references and `bbb-web/design/stitch_cricket_scorecard_explorer`. **Completion:** capture reusable search/result/navigation hierarchy and completed-scorecard presentation while excluding live, projection, export, and unsupported illustrative content. **Testing:** record accessibility and responsive behavior to verify in the BBB UI.

### 1.2 Confirm product decisions before dependent implementation

- [x] **Dependencies:** 1.1. **Areas:** sprint review/product decision record. **Completion:** accepted ACS-aligned structured card filters (team, opponent, exactness, venue, date range, match type, and result), historical eligibility including tie/draw/no-result handling, score detail, paged search results, complete scorecards returned in one roundtrip, external-link policy, and authenticated access for every feature beyond the initial recent-match list. **Testing:** add contract examples for every accepted decision and negative examples for rejected behavior.
- [x] **Dependencies:** 1.2. **Areas:** `bbb-shared` contract design and implementation ADR. **Completion:** record the approved separate search/detail endpoint boundary, recent-match compatibility, completion semantics, and deterministic ordering decision in the ADR planned for this sprint. **Testing:** define route/serialization assertions before implementation slices begin.

### 1.3 Build the navigable UI skeleton

- [x] **Dependencies:** 1.1 and 1.2. **Areas:** `bbb-web/ClientApp/src/app/app.routes.ts`, feature components under a confirmed Angular directory, `app.component.*`. **Completion:** expose navigable search/results and selected-match routes with placeholder states and typed route/query parameters; do not add fabricated match or scoresheet data. **Testing:** route tests cover idle navigation, a valid match-key route, invalid route input, and back-to-results state restoration.
- [x] **Dependencies:** 1.3. **Areas:** Angular templates/styles and shared UI conventions. **Completion:** render semantic headings, labelled controls, keyboard-focusable result actions, status regions, and a responsive delivery-table shell for loading, empty, error, not-found, and incomplete states. **Testing:** component/accessibility checks cover keyboard order, status messaging, table headers, and narrow viewport behavior.

## 2. Search contracts, API, and results

### 2.1 Define shared search contracts

- [x] **Dependencies:** 1.2. **Areas:** `bbb-shared/src/main/kotlin/com/knowledgespike/ballbyball/contracts/ApiContracts.kt`, existing envelope/error contracts, tiny types. **Completion:** add validated search request/filter, bounded page, cursor/continuation metadata, and result contracts without changing the existing recent-match response shape. **Testing:** JUnit 5/Strikt serialization and boundary-value tests cover valid, blank, malformed, over-limit, zero-result, and page-boundary cases.
- [x] **Dependencies:** 2.1. **Areas:** shared contract tests and generated/client-facing model expectations. **Completion:** define stable nullable/error-safe fields for match identity and date/type/team/ground values actually proven by discovery. **Testing:** assert value-class serialization remains transparent and malformed payloads map to controlled errors.

### 2.2 Implement search domain and API

- [x] **Dependencies:** 2.1 and 1.1. **Areas:** `bbb-api/src/main/kotlin/com/knowledgespike/ballbyball/api/feature/matches/domain`, `MatchesRoute.kt`, `ApiModule.kt`. **Completion:** extend repository/service ports with validated tiny types and a separate ACS card-search use case; add protected `GET /api/matches/search` with Arrow `fold`/`zipOrAccumulate`, bounded pagination, 400 mapping, and stable envelope responses. **Testing:** unit/route tests verify validation before domain calls, authorization, pagination semantics, zero/many results, and error mapping.
- [x] **Dependencies:** 2.2. **Areas:** `JooqMatchRepository.kt` and matches data mappers. **Completion:** implement discovery-backed indexed search joins and deterministic tie-breakers without changing recent-match behavior; log failures safely and use the repository’s established IO boundary. **Testing:** repository tests cover identity matching, nullable fields, duplicate-looking matches, deterministic ordering, page boundaries, database failure, and no live dependency.

### 2.3 Implement BFF search operation

- [x] **Dependencies:** 2.1 and 2.2. **Areas:** `bbb-web/src/main/kotlin/com/knowledgespike/ballbyball/web/application/MatchApiClient.kt`, `adapter/out/api/KtorMatchApiClient.kt`, `adapter/in/http/WebRoutes.kt`. **Completion:** add typed BFF search methods/routes under `/api`, preserve recent-match proxy behavior, forward only approved machine/user tokens, and map non-2xx/network/timeout/malformed responses to the existing unavailable model. **Testing:** `MockEngine`/route tests cover token forwarding, protected access, query forwarding, upstream errors, malformed JSON, and unchanged recent behavior.

### 2.4 Implement search results UI

- [x] **Dependencies:** 1.3, 2.1, and 2.3. **Areas:** `bbb-web/ClientApp/src/app/models`, `services/match.service.ts`, routed search/results components. **Completion:** replicate the ACS card-search workflow with team/opponent fields, exact-match flags, venue, dates, type, result, presets, bounded results, idle/loading/results/no-results/error states, exactly-one selectable result, deterministic display, query-state preservation, and authenticated routes. **Testing:** Angular service/component tests mock HTTP at the service boundary and cover valid/invalid requests, loading, empty, failure, multiple results, selection, pagination, stale request handling, and route protection.

## 3. Scoresheet contracts, API, and UI

### 3.1 Define shared scoresheet contracts

- [x] **Dependencies:** 1.1, 1.2, and 2.1. **Areas:** `bbb-shared/src/main/kotlin/com/knowledgespike/ballbyball/contracts/ApiContracts.kt`, `MatchKey`, error/completeness types. **Completion:** define selected-match context, innings, every delivery in the scorecard response, optional wicket/fielder associations, and explicit completeness indicators using only proven fields. **Testing:** serialization tests cover nullable context/date/result/wicket values, multiple wickets/fielders, empty innings, absent optional facts, and malformed payloads.
- [x] **Dependencies:** 3.1. **Areas:** shared API error contracts. **Completion:** distinguish invalid match key (400), unknown match (404), unavailable upstream/data (controlled error), and incomplete but present scorecard data without leaking internals. **Testing:** route and contract tests assert status/envelope mappings and absence of stack traces or SQL.

### 3.2 Implement scoresheet domain and API

- [x] **Dependencies:** 3.1 and 1.1. **Areas:** matches domain repository/service ports, `MatchesRoute.kt`, `ApiModule.kt`. **Completion:** add a validated scoresheet-detail use case and protected `GET /api/matches/{matchKey}/scoresheet`; validate match keys at the boundary, return the complete delivery ledger in one response, and map 400/404/incomplete/error outcomes. **Testing:** unit/route tests cover invalid/unknown keys, missing innings, empty deliveries, completeness states, complete-response behavior, authorization, and recent-route compatibility.
- [x] **Dependencies:** 3.2 and 1.1. **Areas:** `JooqMatchRepository.kt`, row mappers, warehouse fixtures. **Completion:** implement `dim_match` → `dim_innings` → `fact_delivery` mapping with `dim_person` names and isolated/aggregated wicket/fielder associations; preserve nullable/source fields and avoid bridge duplication. **Testing:** integration tests cover available warehouse context, innings, all ordered deliveries, nullable/empty data, and database-unavailable assumptions; bridge associations are loaded separately to avoid duplication.
- [x] **Dependencies:** 3.2. **Areas:** scoresheet repository ordering/mappers. **Completion:** enforce and document innings/order, `innings_order`, `over_number`, `ball_in_over`, `ball_number`, and `delivery_key` ordering, adjusting only for proven schema differences. **Testing:** repository integration coverage verifies innings and delivery ordering and delivery-key tie-breaking; no legal-ball or dismissal values are fabricated.

### 3.3 Implement BFF scoresheet operation

- [x] **Dependencies:** 3.1 and 3.2. **Areas:** `MatchApiClient.kt`, `KtorMatchApiClient.kt`, `WebRoutes.kt`. **Completion:** add the typed selected-match BFF method and route, use `TokenService`, constrain upstream target configuration, and map upstream 400/404/5xx/network/malformed responses safely without reintroducing scorecard pagination. **Testing:** `MockEngine`/route tests cover path-key encoding, no pagination query forwarding, protected access, user/machine token forwarding, failures, and no sensitive error leakage.

### 3.4 Implement selected-match UI

- [x] **Dependencies:** 1.3, 3.1, and 3.3. **Areas:** Angular routes, models, service, scoresheet components/templates/styles. **Completion:** navigate from one result to a typed match-key route and render context, four-column responsive innings summary cards labelled with batting team, ordinal innings number, prepared score, overs, and run rate, plus deterministic semantic delivery tables; support loading, 404, error, empty, incomplete, and retry states. **Testing:** component/service tests cover direct navigation, invalid/unknown keys, empty innings, missing metadata, optional wicket/fielder data, row order, innings card selection and totals, and accessible status messages.
- [x] **Dependencies:** 3.4. **Areas:** Angular navigation and responsive styles. **Completion:** provide keyboard-accessible back-to-results, restore prior search criteria/results where available, safely handle direct entry without history, and make wide delivery rows usable on narrow screens. **Testing:** route tests and accessibility/responsive checks cover keyboard-only search → selection → scoresheet → back, focus movement, table headers, and narrow viewport access.

### 3.5 Refine scoresheet presentation architecture

- [x] **Dependencies:** 3.4. **Areas:** `scoresheet-presenter.ts`, selected-match component/template, feature-scoped styles, scoresheet contract documentation. **Completion:** prepare the complete scoresheet view model once per response, keep batter lanes stable, show each batter name only when entering a lane, use counted `wd`, `nb`, `lb`, and `b` delivery symbols, separate over and cumulative ledger formatting, document delivery-batter wicket attribution, and remove matrix presentation rules from the global stylesheet. **Testing:** presenter and component regression coverage verifies entry-only batter headers, counted extra symbols, cumulative ledgers, wicket/dismissal details, placeholders, stable lanes, input immutability, and accessible rendering without positional selectors.

## 4. End-to-end feature integration

### 4.1 Connect the vertical workflow

- [ ] **Dependencies:** 2.4, 3.4, and approved 1.2 decisions. **Areas:** Angular route configuration, search/scoresheet services, BFF routes, API module wiring. **Completion:** a visitor can complete historical search → multiple results → exactly-one selection → selected route → innings/delivery view → back-to-results without fabricated data or recent-match regressions. **Testing:** end-to-end test uses deterministic data and verifies both browser navigation and direct selected-match navigation.
- [ ] **Dependencies:** 4.1. **Areas:** all API/BFF/UI boundaries. **Completion:** verify stale search/detail responses cannot overwrite newer route state, search pagination remains bounded, scorecards return their complete delivery sets, and error/incomplete states are consistent across layers. **Testing:** integration tests cover delayed/failing upstream responses, large bounded result sets, large complete scorecards, and retry behavior.
- [ ] **Dependencies:** 4.1. **Areas:** API and BFF security configuration/routes. **Completion:** confirm every new route is protected, token handling remains server-side, and no user-controlled upstream URL or credential path exists. **Testing:** negative tests cover unauthenticated and invalid-token requests, upstream failure mapping, and absence of token/secret data in responses/log assertions.

## 5. Tests and verification

### 5.1 Complete automated coverage

- [ ] **Dependencies:** 2.2, 2.3, 2.4, 3.2, 3.3, and 3.4. **Areas:** API/domain/repository tests, BFF tests, Angular tests. **Completion:** all critical acceptance paths and negative/edge cases from the sprint specification have executable tests; no tests are weakened or disabled to pass. **Testing:** run focused tests first, then the affected module suites and frontend checks.
- [ ] **Dependencies:** 5.1. **Areas:** repository fixtures and contract tests. **Completion:** coverage includes blank/invalid/over-limit input, zero/one/many results, duplicate-looking matches, invalid/unknown keys, absent innings/deliveries, partial data, nullable values, extras, repeated labels, multiple wickets, bridge ties, upstream failures, and database unavailability. **Testing:** use deterministic local/Testcontainers fixtures and document any untestable warehouse behavior.

### 5.2 Accessibility and responsive verification

- [ ] **Dependencies:** 4.1 and 5.1. **Areas:** Angular templates/styles and browser verification. **Completion:** labels, focus order, status announcements, semantic tables, keyboard navigation, and narrow viewport delivery access satisfy the acceptance criteria without color-only meaning. **Testing:** perform keyboard-only and responsive checks and retain actionable findings in the sprint review record.

### 5.3 Required project checks

- [ ] **Dependencies:** all implementation tasks. **Areas:** Gradle and Angular build/test configuration. **Completion:** relevant frontend checks and `./gradlew clean check --no-daemon` pass without suppressed warnings/errors. **Testing:** record commands, results, and any environment limitation.
- [ ] **Dependencies:** 5.3. **Areas:** configured coverage and complexity tooling. **Completion:** coverage is reviewed, gaps are recorded, and a sprint-end cyclomatic-complexity check is run; complexity follow-ups are identified rather than hidden. **Testing:** attach/report the actual tool output and any approved follow-up tasks.

## 6. Documentation, review, and follow-up

### 6.1 Record architectural decisions

- [ ] **Dependencies:** 1.2 and accepted implementation behavior. **Areas:** `docs/adr/` (new ADR), sprint review notes. **Completion:** document separate search/detail endpoints, recent-route compatibility, completion eligibility, paged search versus complete one-roundtrip scorecards, and deterministic delivery ordering with rationale and consequences. **Testing:** review ADR claims against the implemented contracts and route tests.

### 6.2 Update architecture and contributor documentation when applicable

- [ ] **Dependencies:** 4.1 and 6.1. **Areas:** `docs/architecture/applications.md`. **Completion:** update module/runtime flow and contributor navigation only if the accepted implementation changes those boundaries. **Testing:** verify every referenced file, route, and module exists and matches the implementation.
- [ ] **Dependencies:** 4.1. **Areas:** `README.md`. **Completion:** update usage or navigation guidance to reflect the delivered historical workflow when user-facing setup or behavior changes. **Testing:** follow the documented path from a clean local setup where feasible.
- [ ] **Dependencies:** 4.1 and 5.3. **Areas:** `docs/project_memory.md`. **Completion:** record title, completion date/time, shipped behavior, key decisions, gotchas, and test coverage areas after the implementation sprint/task is actually complete. **Testing:** cross-check entries against the final review and command results.

### 6.3 Sprint review and task status

- [ ] **Dependencies:** all preceding sections. **Areas:** this checklist and sprint specification. **Completion:** review every acceptance criterion, link evidence, mark only genuinely completed tasks as complete, and leave blocked/follow-up work explicit. **Testing:** confirm no parent is marked complete while any child remains incomplete and no implementation task was pre-marked.
- [ ] **Dependencies:** 6.3. **Areas:** product/architecture review. **Completion:** obtain final sign-off for outstanding HITL choices, unsupported-statistic exclusions, links policy, and any schema-gap decision before declaring the sprint complete. **Testing:** preserve the decision record and unresolved follow-ups.