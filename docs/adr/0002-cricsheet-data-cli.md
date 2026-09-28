# ADR 0002: Cricsheet data retrieval command-line boundary

## Status

Accepted for the initial `bbb-get-cricsheet-data` project skeleton.

## Context

The repository already contains `bbb-update-database`, which parses local
Cricsheet files into warehouse output. Retrieving source data is a separate
workflow and needs its own executable boundary so future network and file
handling concerns do not become part of the database loader.

## Decision

- Add `bbb-get-cricsheet-data` as a standalone Gradle application.
- Use Apache Commons CLI for command-line parsing, matching the existing
  command-line application.
- Use the shared `LoggerDelegate` and a module-local Logback configuration for
  console and rolling-file logging.
- Start with `--help`, `--version`, `--run`, and `--output-directory`; defer
  network retrieval and source-specific options until the next implementation
  task.

## Consequences

The project is independently runnable and testable without network access or
credentials. Future retrieval behavior can be added behind this boundary
without changing the existing database loader or HTTP applications.