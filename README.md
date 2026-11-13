# Strava Activity Mirror

Reference implementation for **Assignment 1, Part 1A** of
*AI-Augmented SDLC* (**SE ZG534**). The project demonstrates a
spec-driven, quality-gated development workflow: a specification and
constitution drive the code, and an automated harness enforces lint,
security, secret-scan, and test gates.

## Purpose

The service mirrors a single athlete's Strava activities into a local
PostgreSQL database and serves them locally. It exposes three REST
endpoints:

| Method | Path                         | Purpose                                                        |
|--------|------------------------------|----------------------------------------------------------------|
| `POST` | `/athlete/activities/sync`   | One-shot sync of activity summaries from Strava (one API call) |
| `GET`  | `/athlete/activities`        | List stored summaries with pagination; never calls Strava      |
| `GET`  | `/activities/{id}`           | Get activity detail; local-first, fetches from Strava on miss  |

Scope is deliberately small (Constitution Principle I — Simplicity):
no UI, no scheduling, no caching layer, no authentication flow, and no
Strava token refresh. See
[`specs/001-strava-activity-mirror/spec.md`](specs/001-strava-activity-mirror/spec.md)
for the full specification.

## Tech stack

- **Language**: Java 21
- **Framework**: Spring Boot 4.1.2 (current webmvc / restclient / data-jpa starters)
- **Build tool**: Maven
- **Database**: PostgreSQL (16 via Docker; Testcontainers for tests)
- **Migrations**: Flyway (versioned SQL)
- **JSON storage**: JSONB columns, Strava payload stored as-is
- **Static analysis**: Checkstyle (lint), SpotBugs + Find Security Bugs (security)
- **Coverage**: JaCoCo (minimum 80% line coverage)
- **Tests**: JUnit 5, WireMock (Strava stub), Testcontainers (PostgreSQL)

Exact versions and dependencies are defined in [`pom.xml`](pom.xml).

## Project structure

```text
Part-1A/
├── pom.xml                       # Maven build, plugins, quality-gate config
├── docker-compose.yml            # PostgreSQL 16 service (env-var driven)
├── .env                          # Local secrets (git-ignored; not committed)
├── .harness/                     # Quality-gate scripts and tool config
│   ├── run.sh                    # Sequential gate runner (writes evidence/)
│   ├── run-all.sh                # Runner for the four gate-N scripts
│   ├── gate-1-lint.sh            # Checkstyle
│   ├── gate-2-security.sh        # SpotBugs + Find Security Bugs
│   ├── gate-3-secret-scan.sh     # Grep-based secret scan
│   ├── gate-4-tests.sh           # Tests + JaCoCo coverage
│   ├── checkstyle.xml            # Lint rules
│   └── spotbugs-exclude.xml      # SpotBugs exclusion filter
├── .specify/                     # Spec Kit state, templates, and memory
│   ├── memory/constitution.md    # Project constitution (non-negotiable rules)
│   └── templates/                # spec / plan / tasks / checklist templates
├── .claude/skills/               # Spec Kit workflow skills (speckit-*)
├── specs/001-strava-activity-mirror/
│   ├── spec.md                   # Feature specification
│   ├── plan.md                   # Implementation plan
│   ├── tasks.md                  # Task breakdown
│   ├── research.md               # Technology decisions
│   ├── data-model.md             # Database schema
│   ├── quickstart.md             # Validation guide
│   ├── contracts/                # REST API contract
│   └── checklists/               # Spec quality checklist
├── evidence/                     # Recorded harness logs and gate results
└── src/
    ├── main/
    │   ├── java/com/stravamirror/
    │   │   ├── StravaActivityMirrorApplication.java
    │   │   ├── client/           # Single Strava HTTP client (StravaClient)
    │   │   ├── config/           # StravaProperties, StravaClientConfig
    │   │   ├── controller/       # ActivityController (3 endpoints)
    │   │   ├── service/          # ActivityService (business logic)
    │   │   ├── repository/       # JPA repositories + custom list query
    │   │   ├── entity/           # ActivitySummary, ActivityDetail
    │   │   ├── dto/              # Response records
    │   │   └── exception/        # GlobalExceptionHandler, ErrorCode, StravaApiException
    │   └── resources/
    │       ├── application.yml    # Config via env-var placeholders only
    │       ├── db/migration/      # Flyway migrations (V1__create_activity_tables.sql)
    │       └── postman/           # Postman collection
    └── test/java/com/stravamirror/
        ├── client/ config/ controller/ exception/
        ├── integration/          # Full-stack Testcontainers + WireMock tests
        └── repository/
```

## Prerequisites

- Java 21 (JDK)
- Maven 3.9+
- Docker and Docker Compose (for PostgreSQL)
- Bash (to run the `.harness/` scripts)
- A valid Strava API access token — only needed for manual runs; the
  automated tests use stubs and never call the real Strava API

## Configuration

All credentials come from environment variables (Constitution
Principle VI — Secrets). Nothing is hard-coded, and a local `.env`
file (git-ignored) supplies values to Docker Compose.

| Variable                      | Used by                        | Purpose                         |
|-------------------------------|--------------------------------|---------------------------------|
| `STRAVA_ACCESS_TOKEN`         | application                    | Bearer token for Strava calls   |
| `SPRING_DATASOURCE_URL`       | application                    | JDBC URL for PostgreSQL         |
| `SPRING_DATASOURCE_USERNAME`  | application                    | Database username               |
| `SPRING_DATASOURCE_PASSWORD`  | application                    | Database password               |
| `POSTGRES_USER`               | `docker-compose.yml`           | PostgreSQL container user       |
| `POSTGRES_PASSWORD`           | `docker-compose.yml`           | PostgreSQL container password   |
| `POSTGRES_DB`                 | `docker-compose.yml`           | PostgreSQL database name        |

The application refuses to start if `STRAVA_ACCESS_TOKEN` or the
database settings are missing (spec FR-024, FR-025). See
[`application.yml`](src/main/resources/application.yml) for the full
placeholder mapping.

## Build, run, and test

Commands below are taken from [`pom.xml`](pom.xml) and
[`quickstart.md`](specs/001-strava-activity-mirror/quickstart.md).

### 1. Start the database

```bash
docker-compose up -d
```

Reads `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_DB` from the
environment (or `.env`). PostgreSQL listens on `localhost:5432`.

### 2. Set application environment variables

```bash
export STRAVA_ACCESS_TOKEN=<your-token>
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/stravamirror
export SPRING_DATASOURCE_USERNAME=<db-user>
export SPRING_DATASOURCE_PASSWORD=<db-password>
```

### 3. Build and run

```bash
mvn clean package -DskipTests
java -jar target/strava-activity-mirror-*.jar
```

The service starts on `http://localhost:8080`. Flyway applies the
migrations under `src/main/resources/db/migration` on startup.

### 4. Run the tests

```bash
mvn verify
```

This runs the full test suite with WireMock stubbing Strava and a
throwaway Testcontainers PostgreSQL instance, then enforces the JaCoCo
80% line-coverage rule. The coverage report is written to
`target/site/jacoco/index.html`.

## Quality harness

The harness lives in [`.harness/`](.harness) and enforces four gates
(Constitution Principles II, III, IV, XI). Thresholds live in config
files and must not be weakened.

| Gate | Script                 | Check                                   | Pass condition                    |
|------|------------------------|-----------------------------------------|-----------------------------------|
| 1    | `gate-1-lint.sh`       | Checkstyle (`mvn checkstyle:check`)     | Zero violations                   |
| 2    | `gate-2-security.sh`   | SpotBugs + Find Security Bugs           | Zero medium/high findings         |
| 3    | `gate-3-secret-scan.sh`| Grep scan for hard-coded secrets/tokens | Zero matches                      |
| 4    | `gate-4-tests.sh`      | `mvn clean verify` + JaCoCo             | All tests pass, coverage ≥ 80%    |

Run everything:

```bash
# Orchestrator that runs the four gate-N scripts; non-zero if any fails
.harness/run-all.sh

# Alternative single runner: lint -> security -> secret-scan -> tests
.harness/run.sh
```

`run-all.sh` runs every gate even if one fails and exits non-zero if
any gate failed. `run.sh` stops at the first failing gate. Both write
logs and results under `evidence/` (and `run.sh` under
`.harness/evidence/`).

## Spec-driven workflow (Spec Kit)

The project is developed with [Spec Kit](https://github.com/github/spec-kit),
which captures intent as versioned artifacts before code is written
(Constitution Principle XII — Change Control).

- **`.specify/memory/constitution.md`** — the authoritative, non-negotiable
  project rules.
- **`.specify/templates/`** — templates for `spec`, `plan`, `tasks`, `checklist`,
  and `constitution`.
- **`.specify/`** — Spec Kit state, scripts (PowerShell), and integration manifests.
- **`.claude/skills/`** — the workflow skills that drive each phase:
  `speckit-constitution`, `speckit-specify`, `speckit-clarify`,
  `speckit-plan`, `speckit-tasks`, `speckit-taskstoissues`,
  `speckit-implement`, `speckit-analyze`, `speckit-checklist`, and
  `speckit-converge`.
- **`specs/001-strava-activity-mirror/`** — the feature artifacts:
  `spec.md`, `plan.md`, `tasks.md`, `research.md`, `data-model.md`,
  `quickstart.md`, plus `contracts/` and `checklists/`.

The flow is: **constitution → specify → clarify → plan → tasks → implement**,
with analysis and checklists supporting each step.

## Constitution principles (summary)

The full text is in
[`.specify/memory/constitution.md`](.specify/memory/constitution.md)
(version 1.0.0). The twelve principles:

1. **Simplicity** — build only what the spec requires; every addition traces to a requirement.
2. **Quality Gate** — Checkstyle/Spotless must report zero violations.
3. **Security Gate** — SpotBugs + Find Security Bugs must report zero medium/high findings.
4. **Tests** — JUnit 5; Strava always stubbed; PostgreSQL via Testcontainers; JaCoCo ≥ 80%.
5. **Traceability** — every functional requirement ID is covered by a named test.
6. **Secrets** — token and DB credentials come only from env vars; never hard-coded, logged, committed, or returned.
7. **External Calls** — all Strava calls go through one client with 3s connect / 10s read timeouts.
8. **Architecture** — controller/service/client/repository layers; DTOs for responses; Strava JSON passed through as-is.
9. **Data Integrity** — schema changes only via versioned migrations; all timestamps stored as UTC.
10. **Error Handling** — every error is JSON with machine-readable `code` and human-readable `message`; no token or raw Strava body.
11. **Harness Integrity** — gates live in `.harness/` behind one runner; gates and thresholds must not be weakened, skipped, or faked.
12. **Change Control** — any ambiguity or change is recorded in Spec Kit artifacts before code changes.
