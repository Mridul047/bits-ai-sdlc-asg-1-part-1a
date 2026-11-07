# Implementation Plan: Strava Activity Mirror

**Branch**: `001-strava-activity-mirror` | **Date**: 2026-11-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-strava-activity-mirror/spec.md`

## Summary

Build a Spring Boot 4.0.4 REST backend that mirrors a single athlete's
Strava activities into a local PostgreSQL database and serves them
locally. Three endpoints: sync summaries from Strava (one Strava call
per request), list stored summaries with pagination, and get activity
detail (local-first with Strava fallback). All Strava calls go through
a single client with enforced timeouts. A `.harness/` runner enforces
lint, security, secret-scan, and test gates.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies** (Spring Boot 4.0.4, managed by BOM):

| Starter / Dependency                         | Purpose                    |
|----------------------------------------------|----------------------------|
| `spring-boot-starter-webmvc`                 | REST controllers           |
| `spring-boot-starter-restclient`             | Strava HTTP client         |
| `spring-boot-starter-data-jpa`               | JPA repositories           |
| `spring-boot-starter-flyway`                 | Database migrations        |
| `spring-boot-starter-validation`             | Jakarta Bean Validation    |
| `org.postgresql:postgresql`                  | JDBC driver (runtime)      |
| `spring-boot-starter-webmvc-test`            | MockMvc tests (test)       |
| `spring-boot-starter-data-jpa-test`          | DataJpaTest (test)         |
| `spring-boot-starter-restclient-test`        | RestClient tests (test)    |
| `spring-boot-starter-flyway-test`            | Flyway test support (test) |
| `org.testcontainers:postgresql`              | Testcontainers PG (test)   |
| `org.wiremock:wiremock-standalone`           | Strava stub (test)         |

See [research.md](research.md) R-1, R-2 for starter migration details.

**Storage**: PostgreSQL with JSONB columns. Schema via Flyway.
See [data-model.md](data-model.md).

**Testing**: JUnit 5, Mockito 5.20, WireMock (Strava stub),
Testcontainers 2.0 (PostgreSQL). Per-technology test starters.

**Target Platform**: JVM on Linux/macOS/Windows, PostgreSQL via Docker.

**Project Type**: web-service (REST API)

**Performance Goals**: N/A — single-athlete, manual-trigger service.
Strava client: 3s connect, 10s read timeout (Principle VII).

**Constraints**: No authentication on service API. Token from
environment only. All timestamps UTC. Exclusive date boundaries.

**Scale/Scope**: Single athlete, hundreds to low thousands of
activities. Two database tables, three endpoints.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1.*

| # | Principle          | Status | How Addressed                                                    |
|---|--------------------|--------|------------------------------------------------------------------|
| I | Simplicity         | ✅ PASS | Three endpoints only. No UI, caching, scheduling, auth flow, token refresh. |
| II | Quality Gate      | ✅ PASS | Checkstyle in `.harness/run.sh`, zero violations required.       |
| III | Security Gate    | ✅ PASS | SpotBugs + Find Security Bugs in harness, zero high/medium.     |
| IV | Tests             | ✅ PASS | JUnit 5, WireMock stubs Strava, Testcontainers PG, JaCoCo ≥80%. |
| V | Traceability      | ✅ PASS | Test names include FR-nnn / EC-n requirement IDs.               |
| VI | Secrets           | ✅ PASS | Token via `@ConfigurationProperties` from env. Never logged. docker-compose uses env vars. Secret-scan gate in harness. |
| VII | External Calls   | ✅ PASS | Single `StravaClient` class, 3s connect / 10s read timeout.    |
| VIII | Architecture    | ✅ PASS | Controller → Service → Client + Repository. DTO records. JSON pass-through. |
| IX | Data Integrity    | ✅ PASS | Flyway versioned migrations. TIMESTAMPTZ stored as UTC.         |
| X | Error Handling     | ✅ PASS | `@RestControllerAdvice` global handler. JSON `{code, message}`. No token/raw body. |
| XI | Harness Integrity | ✅ PASS | `.harness/run.sh` with lint, security, secret-scan, test gates. Thresholds in config files. |
| XII | Change Control   | ✅ PASS | Spec Kit workflow: constitution → specify → clarify → plan → tasks → implement. |

**Post-Phase-1 re-check**: All gates still pass. Data model uses
Flyway migrations (IX), JSONB pass-through (VIII), and
`INSERT ... ON CONFLICT DO NOTHING` for atomic sync (IX).

## Project Structure

### Documentation (this feature)

```text
specs/001-strava-activity-mirror/
├── spec.md
├── plan.md              # This file
├── research.md          # Phase 0: technology decisions
├── data-model.md        # Phase 1: database schema
├── quickstart.md        # Phase 1: validation guide
├── contracts/
│   └── api.md           # Phase 1: REST API contract
└── checklists/
    └── requirements.md  # Spec quality checklist
```

### Source Code (repository root)

```text
pom.xml
docker-compose.yml
.harness/
├── run.sh                    # Gate runner script
├── checkstyle.xml            # Lint rules
└── spotbugs-exclude.xml      # SpotBugs exclusion filter
src/
├── main/
│   ├── java/<base-package>/
│   │   ├── StravaActivityMirrorApplication.java
│   │   ├── config/
│   │   │   ├── StravaProperties.java          # @ConfigurationProperties
│   │   │   └── StravaClientConfig.java        # RestClient bean with timeouts
│   │   ├── controller/
│   │   │   └── ActivityController.java        # 3 endpoints
│   │   ├── service/
│   │   │   └── ActivityService.java           # Business logic
│   │   ├── client/
│   │   │   └── StravaClient.java              # Single Strava HTTP client
│   │   ├── repository/
│   │   │   ├── ActivitySummaryRepository.java # JPA + native query
│   │   │   └── ActivityDetailRepository.java  # JPA
│   │   ├── entity/
│   │   │   ├── ActivitySummary.java           # JPA entity
│   │   │   └── ActivityDetail.java            # JPA entity
│   │   ├── dto/
│   │   │   ├── SyncResponse.java              # record
│   │   │   ├── ActivityListResponse.java      # record (with pagination)
│   │   │   ├── ActivitySummaryDto.java        # record
│   │   │   ├── ActivityDetailResponse.java    # record
│   │   │   └── ErrorResponse.java             # record
│   │   └── exception/
│   │       ├── GlobalExceptionHandler.java    # @RestControllerAdvice
│   │       ├── StravaApiException.java        # Strava error wrapper
│   │       └── ErrorCode.java                 # Enum of error codes
│   └── resources/
│       ├── application.yml                    # Config (env-var placeholders only)
│       └── db/migration/
│           └── V1__create_activity_tables.sql
└── test/
    └── java/<base-package>/
        ├── controller/
        │   └── ActivityControllerTest.java    # MockMvc tests
        ├── service/
        │   └── ActivityServiceTest.java       # Unit tests with Mockito
        ├── client/
        │   └── StravaClientTest.java          # WireMock tests
        ├── repository/
        │   ├── ActivitySummaryRepositoryTest.java  # Testcontainers
        │   └── ActivityDetailRepositoryTest.java   # Testcontainers
        └── integration/
            └── ActivityIntegrationTest.java   # Full stack: Testcontainers + WireMock
```

**Structure Decision**: Standard Maven single-module layout with
Spring Boot 4 conventions. Package-by-layer follows the constitution's
controller/service/client/repository separation (Principle VIII).

## Complexity Tracking

No constitution violations to justify — all gates pass.

## Key Implementation Decisions

### Sync: INSERT ... ON CONFLICT DO NOTHING

The sync uses a native SQL insert:
```sql
INSERT INTO activity_summaries (activity_id, start_date, summary_json)
VALUES (?, ?, ?::jsonb)
ON CONFLICT (activity_id) DO NOTHING
```
The number of rows actually inserted gives `stored`; `total - stored`
gives `skipped`. This avoids a check-then-insert race condition and
is atomic within a transaction (Principle IX, FR-004).

### Strava Client: Single Class, RestClient

`StravaClient` uses Spring's `RestClient` (via
`spring-boot-starter-restclient`). Timeouts configured programmatically
on `ClientHttpRequestFactoryBuilder` (3s connect, 10s read) per
research R-3. The Bearer token is injected from `StravaProperties`
and set as a default header. The client maps Strava HTTP responses to
`StravaApiException` with the appropriate `ErrorCode`.

### Error Handling: Global @RestControllerAdvice

`GlobalExceptionHandler` catches:
- `ConstraintViolationException` / `MethodArgumentNotValidException`
  → 400 `VALIDATION_ERROR`
- `StravaApiException` → mapped HTTP status + error code
- `DataAccessException` → 500 `DATABASE_ERROR`
- Catch-all for unexpected errors → 500 `DATABASE_ERROR`

All handlers produce `ErrorResponse(code, message)` and ensure no
token or raw Strava body leaks (Principle X, FR-022).

### Entity JSONB Mapping

JPA entities store the Strava JSON as a `String` mapped to JSONB via
a Hibernate `@JdbcTypeCode(SqlTypes.JSON)` annotation. Jackson 3
(`tools.jackson` packages) handles serialization in the REST layer.
The JSON is passed through as-is (Principle VIII) — no field-by-field
re-modeling.

### Startup Validation

`StravaProperties` is annotated with `@Validated` and the token field
with `@NotBlank`. Spring refuses to start if the token is missing or
empty (FR-024). Database connection validation is handled by Spring
Boot's default connection pool startup check (FR-025).

### Epoch-to-Timestamp Conversion

`after` and `before` query parameters arrive as `Long` (epoch seconds).
The controller converts them to `Instant` via `Instant.ofEpochSecond()`
for use in repository queries. Filtering uses exclusive boundaries:
`start_date > :after AND start_date < :before` (Clarification Q1).

### Pagination Metadata

The list response includes `total_count`, `page`, `per_page`, and
`has_next` (Clarification Q2). The repository returns a Spring Data
`Page<ActivitySummary>`, from which these fields are derived.

## Harness Design (.harness/)

### run.sh

Executes gates sequentially, writing each gate's output to
`evidence/<gate-name>.log` and result to `evidence/<gate-name>.result`.
Exits non-zero on first failure.

**Gate 1: Lint** — `mvn checkstyle:check`
- Config: `.harness/checkstyle.xml` (Google Java Style or similar)
- Threshold: zero violations

**Gate 2: Security** — `mvn spotbugs:check`
- SpotBugs 4.9.1 + Find Security Bugs 1.13.0
- Threshold: zero high or medium findings
- Exclusions: `.harness/spotbugs-exclude.xml` (minimal, documented)

**Gate 3: Secret Scan** — `gitleaks detect` (or grep-based fallback)
- Scans the entire repository including `docker-compose.yml`,
  `application.yml`, `application-test.yml`, and test resources
- Exits non-zero if any secret pattern found

**Gate 4: Tests + Coverage** — `mvn verify`
- JaCoCo minimum: 80% line coverage
- Configured in `pom.xml` `<rules>` block
- Threshold must not be lowered (Principle XI)

### Threshold Configuration

All thresholds live in configuration files (`pom.xml` plugin config,
`.harness/checkstyle.xml`, `.harness/spotbugs-exclude.xml`). The
coding agent must not modify these to weaken gates (Principle XI).

### Tool Versions (verified compatible with Java 21 + Boot 4.0.4)

See [research.md](research.md) R-6 for the full version table.
