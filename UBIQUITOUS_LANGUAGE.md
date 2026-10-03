# Ball by Ball Ubiquitous Language

This glossary defines the terms used by the Ball by Ball applications, their
JSON contracts, the warehouse, and the Cricsheet data pipeline. Use these
terms consistently in code, tests, documentation, pull requests, and product
discussions.

## How to use this glossary

The same word should mean the same thing across the command-line applications,
`bbb-api`, `bbb-web`, and the Angular client. When a term has a narrower
technical meaning, use the qualified term rather than an ambiguous shortcut.

For example:

- Say `public match ID` when referring to a URL identifier.
- Say `match key` when referring to the warehouse join key.
- Say `canonical match ID` when referring to the stable identity of a
  reconciled match.
- Say `source record ID` when referring to one provider's record.

## Cricket and source data

### Ball-by-ball data

The event-level record of a match: deliveries, runs, extras, wickets, players,
innings, and related metadata. The system stores this in the warehouse and
serves it as a scoresheet.

### Delivery

One recorded ball in an innings. A delivery has ordering information, batter,
non-striker, bowler, runs, extras, and optional wicket associations.

### Innings

A match segment in which one team bats and the other team bowls. The warehouse
links innings to deliveries and records the batting and bowling teams when
those values are available.

### Scoresheet

The complete read model for one selected match: match context, completeness
information, innings, and ordered deliveries. A scoresheet is not the same as
a match summary or a search result.

### Cricsheet

The external provider of raw cricket match JSON archives and player-register
CSV files used by the ingestion pipeline.

### Provider

An external source that supplies match records. Cricsheet is the first provider
implemented by this repository. Provider identity is scoped so that two
providers can use the same local record key without colliding.

### Source record

One provider's representation of a match. A source record can be corrected by
the provider without becoming a different real-world match.

### Source record ID (`sourceRecordId`)

A provider-scoped UUIDv5 identifying one source record. For Cricsheet it is
derived from the immutable Cricsheet namespace and the safe basename stem of
the source file.

### Raw content digest (`rawContentDigest`)

The lowercase SHA-256 digest of the exact downloaded source bytes. It is
revision evidence, not match identity: a changed digest means the source
content changed, but it does not by itself mean that a new real-world match
exists.

### Player register

The provider-supplied CSV data used to resolve player identities and external
provider keys. In the current pipeline this is represented by `people.csv` and
`names.csv` at the configured base directory.

## Normalized match identity

### Source match envelope

The versioned, provider-neutral payload emitted by a source adapter. It contains
normalized match data plus the source record ID, raw content digest, provider,
and source metadata.

### Canonical match envelope

The versioned payload accepted by `bbb-update-database`. It contains one stable
canonical match identity, normalized match data, source references, and merge
evidence.

### Canonical match ID (`canonicalMatchId`)

A stable UUID identifying the real-world match represented by a canonical
envelope. The current single-source promotion derives it deterministically from
the source record ID. A future reconciliation application will own the
multi-provider decision.

### Single-source canonicalizer

The current explicit promotion step that turns one source match envelope into
one canonical match envelope. It is a temporary, deliberate form of
canonicalization rather than multi-provider reconciliation.

### Reconciliation

The future decision process that determines whether source records from
multiple providers describe the same real-world match and combines their
evidence into one canonical envelope.

### Merge evidence

The explanation recorded in a canonical envelope for why source records were
combined or promoted. The current implementation records single-source
promotion; it is intended to make future reconciliation reviewable.

### Source reference

The canonical envelope's record of a provider source, including provider key,
source record ID, and raw content digest. Source references preserve provenance
without making provider identifiers the warehouse's public identity.

### Deterministic identity

An identity generated from stable inputs and a fixed algorithm so that the same
input produces the same identifier across machines and re-imports. The project
uses UUIDv5 namespaces for source, canonical, and public identities.

## Warehouse language

### Warehouse

The relational read store loaded by `bbb-update-database` and queried by
`bbb-api`. It can be produced as JDBC writes, SQL scripts, or CSV output and is
supported for MariaDB/MySQL, PostgreSQL, and SQLite.

### Dimension table

A warehouse table describing reusable entities or descriptors, such as a match,
team, ground, date, person, innings, or wicket.

### Fact table

A warehouse table containing measurable or event-level records, such as match
facts or deliveries, linked to dimensions by internal keys.

### Match key (`matchKey`)

The generated numeric warehouse surrogate key used for compact SQL joins. It is
internal storage identity and must not be exposed in URLs or public JSON
contracts.

### Public match ID (`publicMatchId`)

The deterministic ten-digit identifier exposed in API responses and scoresheet
URLs. It is derived from the canonical match ID using the fixed public UUIDv5
namespace and is stored with a uniqueness constraint.

### Public contract

The stable data and behavior promised to external clients: URL shapes, JSON
fields, status codes, validation rules, error semantics, and identity meaning.
Changing a warehouse column or internal join must not silently change the
public contract.

### Migration

An immutable, ordered database change that moves a warehouse schema forward.
Migration history is not rewritten to represent later end-state code; fresh
database initialization and generated jOOQ models must separately reflect the
current schema.

## Application architecture

### Feature slice

A vertical feature boundary containing the presentation, domain, and data code
that changes together. In `bbb-api`, features live below `feature.*`, such as
`feature.matches`.

### Presentation layer

The boundary that translates HTTP requests and responses. It parses untrusted
query/path values, validates them, maps domain outcomes to HTTP status codes,
and serializes shared contracts.

### Domain layer

The business-facing core of a feature. It owns validated models, use cases, and
repository interfaces and should not depend on Ktor, SQL, or raw request
strings.

### Data layer

The adapter that translates domain operations to persistence technology. In the
API this includes jOOQ repositories and warehouse queries.

### Port

An interface at an application boundary. `MatchRepository` and `MatchApiClient`
are ports: callers depend on their capabilities, not on jOOQ or Ktor clients.

### Adapter

An implementation that connects a port to an external technology or format.
Examples include `JooqMatchRepository`, `KtorMatchApiClient`, the Cricsheet
adapter, and the SQL/CSV/JDBC output adapters.

### Repository

The persistence-facing port or adapter used to load domain data. A repository
should hide query construction, joins, connection handling, and storage details
from its caller.

### Backend-for-Frontend (`BFF`)

The `bbb-web` server-side boundary between the browser and `bbb-api`. It owns
OIDC login/session behavior, keeps bearer tokens server-side, proxies approved
API calls, and presents browser-oriented behavior.

### Angular client

The browser SPA hosted by `bbb-web`. It owns navigation, interaction, display
state, and client-side query-string state; it does not own bearer tokens or
database access.

## Contracts and boundaries

### Contract

A shared agreement between producer and consumer. In this project a contract
includes more than a Kotlin or TypeScript class: it includes the JSON shape,
field meaning, nullability, serialization, validation, allowed values, HTTP
status behavior, error behavior, versioning, and identity semantics.

`bbb-shared` contains the Kotlin HTTP contract models used by the API and BFF.
The Angular client currently mirrors those wire shapes in TypeScript models.

### Shared contract

A contract deliberately owned in a shared module so that multiple applications
use the same meaning and serialization rules. Shared contracts should contain
transport data and stable semantics, not database records or UI-only state.

### Envelope

The common HTTP response wrapper containing `result`, `errorMessage`, and
`timeGenerated`. A successful envelope carries the typed result; a failure
envelope carries a client-safe message and a default result value.

### Boundary validation

Validation performed as soon as untrusted input enters the system. HTTP routes
validate query parameters and path segments before calling domain ports; CLI
arguments and file paths are validated before downloading, parsing, or writing.

### Tiny type

A small type, usually a Kotlin `@JvmInline value class`, that gives a primitive
domain value a name and invariant. Examples include `Limit`, `PageSize`,
`PublicMatchId`, `MatchType`, `Season`, and `Sha256Digest`.

### Invariant

A rule that must always hold for a value, such as a page size being within its
allowed range or a digest containing exactly 64 lowercase hexadecimal digits.
Constructing a tiny type is the preferred place to establish its invariant.

### `Either`, `Raise`, and accumulation

Arrow types used to represent validation outcomes without throwing for expected
bad input. A successful value is on the right; a validation error is on the
left. Multi-field parsing accumulates errors so a client can correct all invalid
fields in one response.

### Serialization boundary

The point where an in-memory value becomes JSON or is reconstructed from JSON.
Value-class serialization is intentionally transparent where the public JSON
contract requires a primitive string or number.

### Error contract

The stable meaning of an error: its safe message, validation context, HTTP
status, and whether it is retryable. Internal stack traces, SQL, credentials,
and provider details are not error-contract data.

## Authentication and security

### OIDC

The identity protocol used by `bbb-web` for browser login, logout, discovery,
and token acquisition.

### JWT

The signed bearer token presented to `bbb-api`. The API validates its signature,
issuer, audience, time leeway, and required scopes.

### JWKS

The identity provider's JSON Web Key Set endpoint. The API uses it to obtain
public keys for validating JWT signatures.

### PKCE

Proof Key for Code Exchange. The BFF uses a code verifier/challenge pair during
the authorization-code flow to protect the browser login exchange.

### User token and machine token

A user token represents a logged-in OIDC session. A machine token is obtained
by the BFF through the client-credentials grant for server-side requests such
as anonymous recent-match loading. Both are forwarded only server-to-server.

### Session cookie

The browser cookie representing the BFF session. It is `HttpOnly`, uses
`SameSite=Lax`, and is secure in production; access and refresh tokens must not
be stored in browser-managed storage.

### CSRF protection

A check that prevents an unrelated site from causing a browser's authenticated
session to perform an unwanted request. The BFF expects its configured CSRF
header on protected browser operations.

## User-facing match operations

### Recent matches

The bounded list served by `GET /api/matches`. It is a lightweight, authenticated
API operation and is distinct from historical search.

### Historical search

The structured, paginated operation served by
`GET /api/matches/search`. It validates filters, applies bounded page values,
and returns deterministic results with pagination metadata.

### Match summary

A compact representation used in recent-match lists and search results. It is
not a scoresheet and may contain nullable display fields.

### Completeness

An explicit statement about whether the available warehouse data is complete
enough for the scoresheet view. Incomplete data is a valid present state; it is
not automatically the same as not-found or unavailable.
