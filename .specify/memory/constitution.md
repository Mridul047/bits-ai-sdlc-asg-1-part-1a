<!--
Sync Impact Report
===================
Version change: (none — unfilled template) → 1.0.0
Bump rationale: MAJOR — initial adoption of project constitution.

Modified principles: N/A (all new)
Added sections:
  - Core Principles (12 principles: I–XII)
  - Technology Stack
  - Development Workflow
  - Governance
Removed sections: N/A (prior file was unfilled template)
Follow-up TODOs: none
-->

# Strava Activity Mirror Constitution

## Core Principles

### I. Simplicity

Implement only what the current specification requires.
No UI, no caching layers, no scheduling, no authentication flow,
no token refresh. Every addition MUST be traceable to a
specification requirement.

### II. Quality Gate

The lint/format check (Checkstyle or Spotless) MUST report zero
violations. A build with any violation MUST fail.

### III. Security Gate

SpotBugs with the Find Security Bugs plugin MUST report zero
high-severity or medium-severity findings. A build with any such
finding MUST fail.

### IV. Tests

- Framework: JUnit 5.
- Strava MUST always be stubbed; no automated test may call the
  real Strava API or require a real token.
- PostgreSQL in tests MUST run in a throwaway Testcontainers
  container.
- JaCoCo line coverage MUST be at least 80 percent.

### V. Traceability

Every functional requirement ID in the specification MUST be
covered by at least one test whose name or `@DisplayName`
includes that requirement ID.

### VI. Secrets

The Strava access token and all database credentials MUST come
only from environment variables. They MUST NOT be:

- Hard-coded in source or configuration files.
- Logged at any level.
- Present in any committed file (including `docker-compose.yml`,
  `application.properties`, `application.yml`, and test resources).
- Returned in API responses or error messages.

### VII. External Calls

All Strava API calls MUST go through a single client class.
That client MUST enforce:

- A 3-second connect timeout.
- A 10-second read timeout.

### VIII. Architecture

Clean separation into controller, service, client, and repository
layers. API responses MUST use DTOs. The stored Strava JSON MUST
be passed through as-is; it MUST NOT be re-modelled field by field.

### IX. Data Integrity

Schema changes MUST be made only through versioned migrations
(e.g., Flyway). All timestamps MUST be stored as UTC.

### X. Error Handling

Every error response MUST be JSON containing:

- A machine-readable `code` field.
- A human-readable `message` field.

Error responses MUST NOT include the Strava access token or
Strava's raw response body.

### XI. Harness Integrity

Lint, security, secret-scan, and test gates live in `.harness/`
and run through one runner script that exits non-zero if any gate
fails. The coding agent MUST NOT:

- Weaken acceptance criteria.
- Delete or disable failing tests.
- Lower coverage or severity thresholds.
- Suppress lint or security rules.
- Skip harness gates.
- Edit recorded harness results to fabricate a pass.

### XII. Change Control

Any ambiguity or requirement change MUST be recorded in the
Spec Kit artifacts first, before any corresponding code change
is made.

## Technology Stack

- **Language**: Java 21.
- **Framework**: Spring Boot 4.x with current starters and APIs
  only. Deprecated "classic" starters and Spring Boot 3-era class
  or starter names MUST NOT be used.
- **Build tool**: Maven.
- **Database**: PostgreSQL (Testcontainers for tests).
- **Migrations**: Versioned (Flyway or equivalent).
- **Static analysis**: SpotBugs + Find Security Bugs, Checkstyle
  or Spotless.
- **Coverage**: JaCoCo (minimum 80% line coverage).

## Development Workflow

1. Specification changes are recorded in Spec Kit artifacts before
   code changes (Principle XII).
2. Code follows controller → service → client → repository
   layering (Principle VIII).
3. The `.harness/` runner script executes all gates in sequence:
   lint/format, security scan, secret scan, and tests with
   coverage.
4. A single non-zero exit from any gate fails the entire run.
5. No gate may be weakened, skipped, or its results edited
   (Principle XI).

## Governance

This constitution is the authoritative set of non-negotiable
constraints for the Strava Activity Mirror project. It supersedes
any conflicting practice, convention, or default.

**Amendment procedure**: Amendments MUST be proposed as changes to
this document, reviewed, and recorded with an incremented version
number before they take effect in code or process.

**Versioning policy**: This constitution follows Semantic
Versioning:

- **MAJOR**: Removal or incompatible redefinition of a principle.
- **MINOR**: Addition of a new principle or material expansion of
  existing guidance.
- **PATCH**: Clarification, wording fix, or non-semantic
  refinement.

**Compliance review**: Every code change MUST be verifiable
against these principles. The `.harness/` runner script is the
automated enforcement mechanism for Principles II, III, IV, and V.

**Version**: 1.0.0 | **Ratified**: 2026-11-07 | **Last Amended**: 2026-11-07
