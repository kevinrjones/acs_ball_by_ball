---
sessionId: session-261001-214221-1ohi
---

# Requirements

### Goal
Add a compact, deterministic public match identifier for URLs without exposing the canonical UUID or replacing the warehouse surrogate key. The public identifier is a ten-digit decimal value derived from the same stable namespace/name concept as UUIDv5 and is persisted so the API can resolve it safely.

### Agreed Identity Model
- `sourceRecordId` remains UUIDv5 over the fixed provider namespace and normalized provider-local key.
- `canonicalMatchId` remains the stable identity of the reconciled match; it is the input name for the public-ID derivation so merged records have one URL identity.
- `publicMatchId` is a separate ten-digit URL-facing identifier in the inclusive range `1_000_000_000..9_999_999_999`.
- `matchKey` remains the generated numeric warehouse join key and is never exposed as the URL identifier.
- The public ID uses a fixed, separately documented UUIDv5 namespace plus the canonical UUID string, then maps the UUID bytes into the ten-digit range. It is not generated with Kotlin/JVM `hashCode()` or database insertion order.

### Acceptance Criteria
- The same canonical match identity always produces the same ten-digit public ID on every machine.
- Re-importing an envelope reuses the same `publicMatchId`; it never creates a second match or changes the URL.
- The database has a uniqueness constraint, and a ten-digit collision fails loudly with an actionable error rather than silently selecting another match.
- `bbb-api` accepts and validates a ten-digit `publicMatchId` in the scoresheet URL and resolves the internal `matchKey` before running existing fact joins.
- Match list/search/scoresheet contracts expose `publicMatchId`, and the scoresheet route uses it instead of `matchKey`.
- Existing source UUIDs, canonical UUIDs, source lineage, and warehouse foreign keys remain unchanged.
- Existing rows with no canonical identity are not assigned IDs from filenames; they require replay of canonical envelopes before they are addressable through the public route.

### Scope
**In scope:** public-ID value type and deterministic generator, updater persistence and collision handling, PostgreSQL/MariaDB/SQLite schema migrations, generated jOOQ model updates, API match contracts/routes/repository queries, web client URL usage, tests, ADR, and documentation.

**Out of scope:** collision probing/allocation, changing source or canonical UUID semantics, changing warehouse foreign-key types, or implementing cross-provider reconciliation.

# Technical Design

### Current Implementation
- `bbb-cli-shared/.../identity/MatchIdentity.kt` owns `ProviderId`, `SourceRecordId`, `CanonicalMatchId`, fixed namespaces, and the private UUIDv5 implementation in `DeterministicIdentity`.
- `bbb-update-database` already persists `canonical_match_id` and source lineage through dialect migrations, while `Entities.kt` exposes `WarehouseMatch(key)` and `OutputAdapter`/`Database` coordinate idempotent loading.
- `bbb-shared/.../types/values/MatchKey.kt` validates internal positive `Long` join keys.
- `bbb-shared/.../contracts/ApiContracts.kt` currently exposes `MatchKey`; `ScoresheetParser.kt` validates `matchKey`; `MatchesRoute.kt`, `MatchRepository.kt`, and `JooqMatchRepository.kt` use it for the scoresheet request and internal joins.
- `bbb-web/ClientApp/src/app/feature/matches` consumes the match key in result links and scoresheet loading, so the client must follow the API contract change.

### Key Decisions
- **Canonical input:** derive the URL ID from `canonicalMatchId`, not a provider key, source digest, filename, or warehouse key. This keeps one URL after future providers are merged.
- **Hash-and-reject collisions:** map the UUIDv5 result into the fixed ten-digit range and enforce a unique database column. A finite space cannot mathematically guarantee collision-free mapping for unlimited matches; rejecting a collision preserves correctness and reproducibility, while probing would make an empty database/insertion order observable.
- **Separate public type:** add `PublicMatchId` as an immutable `@JvmInline` value class in `bbb-shared`, with Arrow boundary parsing and range validation. Keep generation in the deterministic identity module and add the required `bbb-cli-shared` dependency on `bbb-shared` rather than duplicating the type in the API.
- **Breaking URL migration:** replace `/api/matches/{matchKey}/scoresheet` with `/api/matches/{publicMatchId}/scoresheet`; do not retain a second public route because the selected contract makes the compact ID canonical for clients.

### Public-ID Algorithm
Use a fixed namespace recorded in code and ADR, for example:

```kotlin
fun publicMatchId(canonicalMatchId: CanonicalMatchId): PublicMatchId {
    val publicUuid = uuidV5(PUBLIC_MATCH_NAMESPACE, canonicalMatchId.value.toString())
    val unsignedValue = firstEightBytesAsUnsignedLong(publicUuid)
    return PublicMatchId.from(MIN_PUBLIC_ID + (unsignedValue % PUBLIC_ID_RANGE).toInt())
}
```

The byte extraction, unsigned arithmetic, UTF-8 encoding, namespace UUID, range constants, and golden vectors are part of the persisted contract. The public UUID is an internal derivation intermediate and is never serialized in the URL or API response.

### Runtime and Data Flow
```mermaid
graph LR
Envelope[Canonical envelope] --> Generator[UUIDv5 namespace and canonical name]
Generator --> PublicId[Ten digit PublicMatchId]
PublicId --> Loader[bbb-update-database]
Loader --> Warehouse[(dim_match public_match_id)]
Client --> Route[/api matches public ID]
Route --> Repository[JooqMatchRepository]
Repository --> Warehouse
Repository --> Joins[Existing match_key joins]
Joins --> Scoresheet[Scoresheet response]
```

### Warehouse Changes
- Add immutable migration `4__public_match_id.sql` in `bbb-update-database/migrations/mysql`, `postgres`, and `sqlite`.
- Add nullable `public_match_id` to `dim_match` for populated legacy schemas, with a unique index/constraint and dialect-appropriate integer type. New canonical loads must write a non-null ten-digit value.
- Extend `MatchRecord`, `WarehouseMatch` or its identity representation, `Database`, `OutputAdapter`, and JDBC/SQL-script/CSV adapters to calculate, insert, look up, and verify the public ID alongside `canonical_match_id`.
- When replaying a canonical envelope for a legacy row that already has `canonical_match_id` but no public ID, backfill the deterministic value; leave rows with no canonical identity null and document replay as the migration path. Do not infer identity from `file_name`.
- Update committed generated jOOQ `DimMatch`/`DimMatchRecord` fields and any forced-type configuration so API queries can select `public_match_id` while retaining internal `id`/`match_key` joins.

### API and Web Changes
- Add `PublicMatchId` and its error type/factories beside `MatchKey` in `bbb-shared`; use Arrow `Either` at `ScoresheetParser.kt` to validate path input and reject non-ten-digit values as a bad request.
- Replace `matchKey` with `publicMatchId` in the URL-facing `MatchSummary`, `MatchSearchResult`, and `MatchScoresheetContext` contracts and their feature models/mappers. Keep `MatchKey` private to repository/database joins.
- Change `MatchRepository.scoresheet` and `MatchService.scoresheet` to accept `PublicMatchId`; in `JooqMatchRepository`, resolve `dim_match.public_match_id` to the internal match key and reuse the existing innings/delivery query structure.
- Update `MatchesRoute.kt`, API serialization tests, and `bbb-web/ClientApp/src/app/feature/matches` link/presenter/client code so generated links use `/api/matches/{publicMatchId}/scoresheet` and preserve the existing external-template convention.

### Risks and Mitigations
- **Ten-digit collision:** unique constraints and a deterministic collision error prevent incorrect routing; golden vectors and a forced-collision test make the limitation visible.
- **Legacy rows:** nullable migration plus replay/backfill avoids inventing IDs from filenames; API tests must cover a not-yet-addressable legacy row.
- **Contract mismatch:** update Kotlin contracts, generated jOOQ accessors, Angular models, and scoresheet links in one vertical slice; compile and integration tests catch stale `matchKey` references.
- **Namespace drift:** make the public namespace immutable, document it in ADR 0004, and never reuse it for another identifier.

# Testing

### Test-First Validation
- Add `PublicMatchId` tests in `bbb-shared` for valid ten-digit values, lower/upper bounds, malformed input, and serialization.
- Add deterministic identity golden vectors in `bbb-cli-shared` proving namespace/name repeatability, exact decimal range, directory independence, and distinct canonical inputs; test unsigned conversion around high-bit values.
- Add updater tests for first insert, repeated canonical import, legacy canonical-row backfill, duplicate public-ID rejection, and all JDBC/SQL-script/CSV generated statements.
- Extend `SqliteMatchIdentityMigrationTest` and add representative PostgreSQL/MariaDB migration coverage for nullable legacy rows, unique constraints, and foreign-key integrity; use Testcontainers where configured.
- Add API route/repository tests proving valid public IDs return the expected scoresheet, malformed IDs return `400`, unknown IDs return `404`, and internal delivery joins still use `match_key`.
- Update Angular match feature tests for response mapping and URL generation; include a regression that no link uses the internal `matchKey`.
- Run focused module checks, then `./gradlew clean check --no-daemon`; run configured Detekt/Ktlint, Kover, Pitest, and cyclomatic-complexity checks and report any pre-existing failures separately rather than suppressing them.

# Decision Record

### ADR 0004: Deterministic public match identifiers
Document:
- The distinction between source UUID, canonical UUID, ten-digit public URL ID, and warehouse `matchKey`.
- The fixed public UUIDv5 namespace, canonical UUID string as the name, exact byte-to-range mapping, and ten-digit bounds.
- Why hash-and-reject is selected over collision probing and sequential allocation.
- Why public URLs use canonical IDs and why replacing the `matchKey` route is an intentional API contract change.
- The legacy migration rule: backfill rows with canonical identity; replay rows without it; never infer from filenames.

Update `docs/architecture/applications.md`, `README-DEV.md`, `README.md`, and `docs/project_memory.md` to describe public-ID generation, the new route, collision behavior, and the remaining full-check MariaDB limitation.

# Delivery Steps

### ✓ Step 1: Define the public ID type and deterministic algorithm
`bbb-shared` and `bbb-cli-shared` expose one validated ten-digit type and a reproducible canonical-ID-to-public-ID function.

- Add `PublicMatchId` with Arrow parsing, serialization, bounds, and structured validation errors.
- Add an immutable public namespace and deterministic UUIDv5-derived decimal mapping in `MatchIdentity.kt`.
- Add golden vectors, high-bit/unsigned tests, range tests, and a documented collision policy before wiring persistence.
- Add the minimal module dependency needed to share the public type without duplicating it in API or updater code.

### ✓ Step 2: Persist public IDs during canonical warehouse loading
Canonical imports store and reuse the deterministic public ID while preserving internal warehouse joins and legacy migration behavior.

- Add migration 4 for MySQL/MariaDB, PostgreSQL, and SQLite with nullable legacy-safe columns and unique constraints.
- Extend `Database`, `Entities.kt`, `OutputAdapter`, `MatchRecord`, and JDBC/SQL-script/CSV adapters to insert, backfill, and verify `public_match_id`.
- Ensure duplicate canonical imports are idempotent and collisions stop with a clear error rather than probing for another value.
- Add migration and adapter tests for populated schemas, replay backfill, null legacy identity, uniqueness, and foreign-key integrity.

### ✓ Step 3: Replace API match URLs with the public identifier
The API resolves scoresheets from ten-digit public IDs and no longer exposes internal `matchKey` as the URL contract.

- Update `ApiContracts.kt`, `ScoresheetParser.kt`, match domain models, `MatchRepository`, `MatchService`, and `MatchesRoute.kt`.
- Update `JooqMatchRepository.kt` and generated `DimMatch`/`DimMatchRecord` accessors to select public IDs and resolve internal join keys.
- Update recent/search/scoresheet response mappers and API tests for success, invalid input, not-found, and missing-legacy-ID behavior.
- Update `bbb-web/ClientApp/src/app/feature/matches` models, links, and presenters to generate the new route and stop using `matchKey` in URLs.

### ✓ Step 4: Document and validate the public URL contract
The repository explains and verifies the stable public-ID boundary and reports validation status accurately.

- Create ADR 0004 and update `docs/architecture/applications.md`, `README-DEV.md`, `README.md`, and `docs/project_memory.md`.
- Add the human verification checklist for repeat imports, moved source files, stable URLs, correction digests, and forced collision handling.
- Run module checks and the full `clean check`, preserving the known live MariaDB integration failures in the final report if they remain.
- Run configured static analysis, coverage, mutation, and complexity checks and record unavailable or pre-existing tooling limitations.

### ✓ Step 5: Widen the public match identifier to ten digits
The existing deterministic public-ID contract is widened consistently so current collisions are resolved without changing canonical identity or warehouse joins.

- Update the validated `PublicMatchId` range, boundary messages, serialization tests, and deterministic golden vectors.
- Update all API, updater, generated jOOQ, web-client, migration, and documentation references that encode the public-ID contract.
- Add regression coverage proving ten-digit validation and preserving deterministic reuse and unique-constraint collision behavior.
- Run focused checks and the full validation commands, reporting the known live MariaDB integration limitation separately.