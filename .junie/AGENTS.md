# Project Guidelines

This document outlines the coding standards, architecture, and deployment procedures for this project.

## General Coding Standards

** For Kotlin**

- **Kotlin First**: Use idiomatic Kotlin (data classes, extension functions, sealed classes, null safety).
- **Provide Tests**: Prefer JUnit and Strikt for the tests and always provide tests
- **Functional Style**: Prefer Arrow's `Either` for error handling and data flow instead of throwing exceptions.
- **Functional Style**: Prefer a functional style for all code.
- **Small function**: Prefer smaller descriptively named functions over large blocks of code
- **Naming**:
    - Classes/Interfaces: PascalCase (e.g., `JooqPartnershipsRepository`)
    - Functions/Properties: camelCase (e.g., `getOverallPartnership`)
    - Constants: UPPER_SNAKE_CASE (e.g., `OVERALL_ROUTE`)
- **Immutability**: Prefer `val` over `var` and `List` over `MutableList`.
- Prefer immutable data classes, for example prefer
- **Static analysis** Make use of static analysis tools like Detekt and Ktlint to enforce coding standards and catch potential issues early. Run these tools as part of your CI/CD pipeline and integrate them into your development workflow.

```kotlin
data class OidcConfiguration(
    val authority: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val scopes: List<String> = listOf("openid", "profile")
)
``` 

over

```kotlin
data class OidcConfiguration(
    var authority: String = "",
    var clientId: String = "",
    var clientSecret: String = "",
    var scopes: List<String> = listOf("openid", "profile")
)
``` 

- When building DSLs prefer the use of context receivers to make the DSLs easier to construct and read
- When creating errors prefer to sealed classes and sealed interfaces for the error hierarchy
- Prefer sealed interfaces over sealed classes where possible
- Prefer to use the type system where possible, for example use **Tiny Types** in thd code where you can rather than
  scattering say 'Int' or 'String' types throughout the code
- Use these tiny types as a validation mechanism
- Log all Exceptions using the standard logger mechanism
- Add logs extensively throughout the code, making use of the different logging levels

### After Adding Code at Any Time

- After adding code, make sure all the tests run
- After adding code, make sure that the gradle `check` task runs successfully for all modules
- Do not simply 'suppress' any errors or warnings, fix the code instead

### Build & Dependencies

- Use Gradle (`build.gradle.kts`) for dependency management and builds in JVM based projects (eg Kotlin or Java).
- Use a toml file for all dependencies and reference that TOML file in the gradle build files
- Make sure to create the appropriate .gitignore file. This should make sure it ignores any files that are built as well
  as files that are created by the ide that should not be shared.
- Make sure that .gitignore files also includes any files that contains secrets that should not be pushed to a public
  repository
- when referencing plugins add the plugin reference to the toml file and then use that reference in the gradle file,
  i.e.
  prefer this

``` kotlin
    alias(libs.plugins.ktor.plugin)
```

to this

```
    id("io.ktor.plugin") version "3.4.1"
```

## Architecture Guidelines

- Read `docs/ARCHITECTURE.md` before making large structural changes or when you need a module-by-module overview of the application.
- Update `docs/ARCHITECTURE.md` at the end of any sprint that changes or extends the architecture.
- **SOLID** prefer to follow the SOLID principals
- **Patterns** prefer using GoF or other established patterns in the code
- GoF patterns are opt-in, never speculative. No pattern without a named reason in an ADR or the sprint Architect
  design.
- Pre-blessed patterns for all codebases:
    - Command
    - Adapter
    - Strategy
- Discouraged: Template Method. Prefer composition.
- Inheritance: capped at one level. Prefer composition.
- **COMPOSITION** Prefer composition over inheritance
- **ADR** generate an ADR for every architectural decision
- **Testing** Code must be test first, prefer to use BDD for user stories and TDD for testing smaller units
- **TDD** Prefer JUnit and Strikt for TDD
- **BDD** Prefer Cucumber JVM for BDD and use Gherkin spec files

## Testing Strategies

* **Frameworks**: Use JUnit 5 with Kluent or Strikt for assertions.
* **Mocking**: When testing parsers, use local HTML files instead of making real network calls.
* **Database**: Integration tests for should use test-containers
* **coverage**  Use kover for test Coverage
*               Attempt to get 100% test coverage before completing the task, if you cannon get that coverage report the reasons to the user
* **Mutation Testing**  Use Pitest for mutation testing


### Git Workflow

#### Guidelines

- ***Only commit when explicitly asked to***
- Use clear, descriptive commit messages.
- DO NOT add any ads such as "Co-authored-by: Junie <junie@jetbrains.com>`."
- Only generate the message for staged files/changes
- Don't add any files using `git add`. The user will decide what to add.
- Follow the rules below for the commit message.
- Use standard labels and scoping
- Make sure I'm not committing to `main`. If I'm trying to do that, suggest a branch name, ask me to confirm, then
  create the branch.
- Ask me to confirm the message, then add all files and run the commit.

#### Format

```
<type>:<space><message title>

<bullet points summarizing what was updated>
```

#### Example Titles

```
feat(auth): add JWT login flow
fix(ui): handle null pointer in sidebar
refactor(api): split user controller logic
docs(readme): add usage section
```

#### Example with Title and Body

```
feat(auth): add JWT login flow

- Implemented JWT token validation logic
- Added documentation for the validation component
```

#### Rules

* title is lowercase, no period at the end.
* Title should be a clear summary, max 50 characters.
* Use the body (optional) to explain *why*, not just *what*.
* Bullet points should be concise and high-level.

Avoid

* Vague titles like: "update", "fix stuff"
* Overly long or unfocused titles
* Excessive detail in bullet points

#### Allowed Types

| Type     | Description                           |
|----------|---------------------------------------|
| feat     | New feature                           |
| fix      | Bug fix                               |
| chore    | Maintenance (e.g., tooling, deps)     |
| docs     | Documentation changes                 |
| refactor | Code restructure (no behavior change) |
| test     | Adding or refactoring tests           |
| style    | Code formatting (no logic change)     |
| perf     | Performance improvements              |

## Adding Features

* When adding features prefer using an 'outside-in' rather than in 'inside-out' style (also known as creating a 'walking
  skeleton').
* Prefer creating the UI or command line first and filling in the internal details later
* If the feature is part of a CLI then make sure that the feature is always 'runnable' from the command line with the
  correct command line flags in place
* If the feature is part of a web or mobile user interface make sure that the UI is navigable and available to a human
  user and tester

## Tasks

### Task List Structure

- By default task lists are maintained in the docs/tasks/TASKS-SPRINT-[SPRINTNUMBER]-[SPRINTNAME].md file unless 
  otherwise specified. Where [SPRINTNUMBER] and [SPRINTNAME] are placeholders for the sprint number and name.
- Tasks are organised hierarchically with main tasks and subtasks
- Each task has a unique identifier (e;g; 1, 1.1, 1.2 etc.)
- Tasks are grouped into logical sections based on the feature

### Task Status Tracking

- Tasks are marked with checkboxes:
    - `[ ]` indicates a task that has not been started or is in progress
    - `[x]` indicates a task that has been completed
- A parent task should only be marked as completed when all its subtasks are completed
- The tasks file should be updated as you progress through the tasks

### Structure

### Notes

- ALWAYS IGNORE node_modules folders when evaluating code
- Use `docs/ARCHITECTURE.md` as the primary architecture map for module boundaries, runtime flows, and contributor entry points.
- Use the **UBIQUITOUS_LANGUAGE.md*, if it exists, to understand the domain language of the project
- Use the **docs/RECAP.md* to understand what has happened in project
- Use the **docs/project_memory.md* to understand what has happened in project

** Sprint Completion or Task Completion **

At the end of each phase - either a sprint has completed or a full task has completed (not just subtasks) update a
`docs/project_memory.md` file. This file should have several sections

If the sprint changed module responsibilities, runtime flows, or major subsystem boundaries, also update
`docs/ARCHITECTURE.md` and make sure any contributor-navigation guidance here still points to the right files.

Overall (this will be expanded after each sprint/task completion)
**What was shipped**
**Key decisions**
**Gotchas**
For each sprint/task
**Title**
**Date/time completed**
**What was shipped**
**Key decisions**
**Gotchas**
**Test coverage areas**

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