# Project Memory

## What was shipped

- Moved module-specific Gradle configuration from the root build script into `bb-shared/build.gradle.kts` and `bb-update-database/build.gradle.kts`.

## Key decisions

- Each module now owns its plugins, repositories, dependencies, test configuration, and Java/Kotlin 21 configuration.
- The root build script retains root-level plugin setup, project metadata, and dependency-update version filtering.

## Gotchas

- The dependency update plugin uses the replacement `io.github.ben-manes.versions` ID.

## Gradle configuration ownership

### Title

Move project configuration into module build files

### Date/time completed

2026-09-07 16:35

### What was shipped

- `bb-shared` and `bb-update-database` now configure themselves independently of root `allprojects` and `project` blocks.

### Key decisions

- Kotlin plugin versions remain declared at the root with `apply(false)` to avoid loading the Kotlin Gradle plugin multiple times.

### Gotchas

- Neither module currently contains test sources, so Gradle reports their test tasks as `NO-SOURCE`.

### Test coverage areas

- Clean compilation and `check` execution for both modules.

## Build configuration best-practices update

### Title

Align Gradle configuration with Java toolchain and reproducibility best practices

### Date/time completed

2026-09-07 16:38

### What was shipped

- Centralized plugin and dependency repositories in `settings.gradle.kts` with project repository declarations rejected.
- Applied the Foojay toolchain resolver through the settings plugin DSL.
- Standardized both modules on the Java 21 Kotlin toolchain and removed redundant Java compatibility settings.
- Externalized Flyway connection credentials to Gradle properties or environment variables.
- Replaced the deprecated dependency update plugin ID in the version catalog.

### Key decisions

- `jvmToolchain(21)` is the single source of truth for the Java and Kotlin compilation toolchain.
- `mavenLocal()` was removed to keep dependency resolution reproducible across environments.
- Test configuration uses lazy `Test` task configuration so future test tasks inherit the same JUnit Platform setup.

### Gotchas

- Flyway uses local development defaults for the URL and user, while the password defaults to empty and should be supplied through `flyway.password` or `FLYWAY_PASSWORD` when migration tasks run.

### Test coverage areas

- `./gradlew clean check --no-daemon` passed for both modules.
- No test sources currently exist, so Gradle reported `NO-SOURCE` for test tasks.