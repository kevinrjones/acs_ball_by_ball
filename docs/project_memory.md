# Project Memory

## What was shipped

- Moved module-specific Gradle configuration from the root build script into `bb-shared/build.gradle.kts` and `bb-update-database/build.gradle.kts`.

## Key decisions

- Each module now owns its plugins, repositories, dependencies, test configuration, and Java/Kotlin 21 configuration.
- The root build script retains root-level plugin setup, project metadata, and dependency-update version filtering.

## Gotchas

- Gradle reports that the legacy `com.github.ben-manes.versions` plugin ID is deprecated; this was not changed as part of the configuration move.

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