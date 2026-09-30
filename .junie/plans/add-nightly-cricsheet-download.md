---
sessionId: session-260929-205011-c72y
---

# Requirements

### Overview & Goals
Add a `--nightly`/`-n` retrieval mode to `bbb-get-cricsheet-data` for scheduled partial refreshes without changing the existing full-download behavior.

### Functional Requirements
- `--nightly` is an exclusive workflow selector; it does not discover or download the full archive set.
- Download exactly these fixed HTTPS sources, sequentially and in this order:
  1. `https://cricsheet.org/downloads/recently_added_7_json.zip`
  2. `https://cricsheet.org/downloads/recently_added_2_json.zip`
- Store nightly ZIPs in `[base directory]/nightly/[data directory]/zips`.
- Extract both archives directly into `[base directory]/nightly/[data directory]/`, without archive-named subdirectories.
- Process `recently_added_7_json.zip` before `recently_added_2_json.zip`; if both contain the same relative file, the second archive overwrites the first.
- Always download and validate `people.csv` and `names.csv` using the existing register sources and destination `[base directory]/[names directory]`; `names directory` continues to default to `data directory`.
- Nightly mode may reuse existing target directories and always replaces managed ZIP, extracted, and CSV files. It preserves stale/unrelated files and rejects destination paths that are regular files.
- Existing `--force`/`-f` remains accepted in nightly mode as a harmless no-op.
- Preserve existing retry, atomic-download, ZIP-safety, CSV-validation, continue-and-report, logging, and exit-code behavior. Nightly attempts remaining archives and CSVs after an individual failure and returns `1` for any operational failure.
- Preserve the current full mode, including archive discovery, archive-named extraction directories, and its existing preflight semantics.

### Scope
**In scope:** CLI parsing, nightly path resolution, fixed-source retrieval, flat staged extraction, overwrite ordering, regression tests, and contributor documentation.

**Out of scope:** new dependencies, changes to Cricsheet’s source data, deletion of stale nightly files, concurrent downloads, or changes to the full retrieval workflow.

# Technical Design

### Current Implementation
- `bbb-get-cricsheet-data/src/main/kotlin/com/knowledgespike/ballbyball/getcricsheetdata/Application.kt` parses the existing Commons CLI command and constructs the retrieval configuration.
- `CricsheetDataRetriever.kt` currently discovers `_json.zip` links from the matches page, downloads them through the injected JDK HTTP transport, validates/stages ZIPs, commits extracted files, and downloads the register CSVs.
- Full-mode extraction currently creates an archive-specific directory such as `data/bbl_json`; nightly mode must bypass that directory wrapper.
- `CommandLineArgumentsTest.kt` and `CricsheetDataRetrieverTest.kt` are the established parser and deterministic local HTTP/ZIP test seams.
- The module already uses JDK 21, Jsoup, Commons CLI, and the version catalog; no dependency change is needed.

### Proposed Changes
- Extend the parsed run command with an explicit nightly mode value/flag while retaining required absolute `bd`, safe relative `dd`, optional `nd`, and `-f`/`--force` behavior.
- Resolve the effective archive data directory centrally: full mode uses `[bd]/[dd]`; nightly mode uses `[bd]/nightly/[dd]`. Keep register output at `[bd]/[nd]`.
- Add fixed nightly archive URIs to the Cricsheet source configuration. The nightly branch must not fetch or parse the downloads page.
- Reuse the existing downloader, retry policy, atomic temporary-file replacement, ZIP validation, safe-entry validation, logging, CSV validation, and failure aggregation.
- Add a nightly archive processing path that stages entries without an archive-name child directory, commits them to the nightly data directory, and runs the two archives in the fixed `7`, then `2` order.
- Keep CSV processing independent and execute it for both full and nightly modes.
- Adjust preflight directory handling so nightly can create or reuse its nightly data and register directories, while regular-file conflicts remain failures.

### Architecture Diagram
```mermaid
graph LR
    CLI[Application CLI] --> MODE{Retrieval mode}
    MODE --> FULL[Full retrieval]
    MODE --> NIGHTLY[Nightly retrieval]
    FULL --> DISCOVER[Matches-page discovery]
    NIGHTLY --> FIXED[Two fixed archive URIs]
    DISCOVER --> COMMON[Shared download and validation]
    FIXED --> COMMON
    COMMON --> EXTRACT[Staged ZIP extraction]
    EXTRACT --> DATA[Full or nightly data directory]
    FULL --> CSV[Register CSV retrieval]
    NIGHTLY --> CSV
    CSV --> NAMES[Shared names directory]
```

### Key Decisions
- Use a mode branch inside the existing `CricsheetDataRetriever`, rather than a new module or independent application, because both workflows share transport, artifact validation, CSV retrieval, logging, and failure aggregation.
- Represent the nightly archive list as an explicit ordered collection, not a discovered/sorted set, so the required `7`-before-`2` overwrite precedence is visible and testable.
- Keep staging and ZIP-slip validation for nightly extraction; only the destination layout changes from archive-specific directories to a flat merge.
- Treat `--nightly` as the overwrite permission for nightly directories; accept `--force` for script compatibility but do not require it.

### Files and Tests
- Modify `bbb-get-cricsheet-data/src/main/kotlin/com/knowledgespike/ballbyball/getcricsheetdata/Application.kt` for the new option and mode-aware configuration.
- Modify `bbb-get-cricsheet-data/src/main/kotlin/com/knowledgespike/ballbyball/getcricsheetdata/CricsheetDataRetriever.kt` for effective path resolution, fixed sources, ordered nightly processing, and flat extraction.
- Extend `bbb-get-cricsheet-data/src/test/kotlin/com/knowledgespike/ballbyball/getcricsheetdata/CommandLineArgumentsTest.kt` for `-n`/`--nightly`, compatibility with `-f`, and unchanged full-mode validation.
- Extend `bbb-get-cricsheet-data/src/test/kotlin/com/knowledgespike/ballbyball/getcricsheetdata/CricsheetDataRetrieverTest.kt` with local ZIP fixtures that prove fixed request order, nightly paths, direct extraction, collision precedence, CSV reuse, directory reuse, stale-file preservation, and continuation after a failed archive.
- Update `README-DEV.md`, `docs/architecture/applications.md`, and `docs/project_memory.md` with the nightly command, layout, mode distinction, and operational decisions.

# Testing

### Validation Approach
Use the existing JUnit 5, Strikt, and injected local HTTP transport seams. Do not call the live Cricsheet site from tests.

### Key Scenarios
- Parse both `-n` and `--nightly` and confirm full-mode arguments remain backward compatible.
- Run nightly retrieval against local fixtures and verify requests occur as `recently_added_7_json.zip`, `recently_added_2_json.zip`, `people.csv`, then `names.csv`.
- Verify ZIPs are stored under `[bd]/nightly/[dd]/zips` and extracted JSON is directly below `[bd]/nightly/[dd]`, not below archive-named directories.
- Use overlapping ZIP fixtures to prove the second archive overwrites the first archive’s same-path file.
- Verify existing nightly directories work without `--force`, managed files are replaced, stale files remain, and `-n -f` is accepted.
- Verify a failed first archive does not prevent the second archive or either CSV from being attempted and produces a non-zero result.
- Verify existing full-mode tests still assert archive-named extraction and current directory/force behavior.

### Edge Cases
- Reject absolute or traversal-escaping `dd`/`nd` values using existing validation.
- Reject regular files where the nightly data, nightly `zips`, names, or required parent path must be directories.
- Preserve atomic download and staged extraction guarantees when replacing existing artifacts.
- Run `./gradlew :bbb-get-cricsheet-data:test --no-daemon`, `./gradlew clean check --no-daemon`, and `git diff --check` after implementation.

# Delivery Steps

### ✓ Step 1: Add nightly CLI mode and path resolution
The command accepts `-n`/`--nightly` and resolves nightly archive destinations without changing full-mode arguments.

- Update `Application.kt` and its command model to carry the nightly mode.
- Preserve required absolute base and safe relative data/name directory validation.
- Resolve nightly archives to `[bd]/nightly/[dd]` while keeping CSVs at `[bd]/[nd]`.
- Accept `-f`/`--force` alongside nightly as a no-op.
- Add parser tests for both aliases, mode compatibility, and path validation.

### ✓ Step 2: Implement ordered nightly downloads and CSV reuse
Nightly retrieval downloads the two fixed archives in `7`-then-`2` order and still refreshes both register CSVs.

- Add fixed nightly source URIs to `CricsheetDataRetriever` configuration.
- Bypass downloads-page discovery in nightly mode.
- Reuse the existing sequential HTTP transport, retry, atomic replacement, validation, logging, and failure aggregation.
- Allow nightly target directories to be created or reused while rejecting regular-file conflicts.
- Add deterministic local HTTP tests for source selection, request order, destination paths, CSV reuse, and continuation after failures.

### ✓ Step 3: Implement flat staged nightly extraction
Nightly ZIP contents are safely merged directly into the nightly data directory with later archive content winning collisions.

- Add a nightly extraction path that stages validated entries without an archive-name directory.
- Commit `recently_added_7_json.zip` before `recently_added_2_json.zip` so the latter overwrites same-relative-path files.
- Preserve ZIP-slip protection, corrupt-archive rejection, stale-file preservation, and atomic/staged final-state guarantees.
- Add overlapping-fixture tests for flat layout, overwrite precedence, existing-directory reuse, and stale files.
- Run the module tests and full `clean check` validation.

### ✓ Step 4: Document the nightly workflow
Contributor documentation describes how to run and reason about full versus nightly retrieval.

- Update `README-DEV.md` with the nightly command, flags, output layout, overwrite behavior, and CSV location.
- Update `docs/architecture/applications.md` with the mode-specific retrieval flow and destination semantics.
- Add the completed nightly task, decisions, gotchas, and test coverage to `docs/project_memory.md`.
- Confirm formatting and repository diff checks pass.