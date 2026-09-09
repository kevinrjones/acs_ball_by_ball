# Contributor navigation

Read `docs/architecture/applications.md` before changing `bb-api`, `bb-web`,
or their `bb-shared` JSON contracts. It is the architecture map for the
application layers, dependency direction, request flows, validation/error
handling, and test seams.

- Keep HTTP routes in inbound adapters and database/HTTP integrations in
  outbound adapters.
- Put use-case validation and orchestration in application packages.
- Define JSON request/response/error types in `bb-shared` with
  `kotlinx.serialization`.
- Run `./gradlew clean check --no-daemon` after application changes.

The broader project standards remain in `.junie/AGENTS.md`.