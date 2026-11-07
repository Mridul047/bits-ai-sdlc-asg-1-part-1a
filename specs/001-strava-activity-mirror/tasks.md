# Tasks: Strava Activity Mirror

**Input**: Design documents from `specs/001-strava-activity-mirror/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md,
contracts/api.md, quickstart.md

**Tests**: Included — required by constitution Principle IV (JaCoCo
≥80%) and Principle V (test names include requirement IDs).

**Organization**: Tasks grouped by user story. Each story is
independently testable after Phase 2 completes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story (US1, US2, US3)
- All file paths relative to repository root

---

## Phase 1: Setup (Project Initialization)

**Purpose**: Maven project skeleton, build configuration, harness
infrastructure.

- [x] T001 Create Maven `pom.xml` with Spring Boot 4.0.4 parent, Java 21 compiler settings, and all dependencies: `spring-boot-starter-webmvc`, `spring-boot-starter-restclient`, `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `spring-boot-starter-validation`, `org.postgresql:postgresql` (runtime), `spring-boot-starter-webmvc-test` (test), `spring-boot-starter-data-jpa-test` (test), `spring-boot-starter-restclient-test` (test), `spring-boot-starter-flyway-test` (test), `org.testcontainers:postgresql` (test), `org.wiremock:wiremock-standalone` (test). Configure plugins: `maven-checkstyle-plugin:3.6.0` (Checkstyle 10.21), `spotbugs-maven-plugin:4.9.1.0` with `findsecbugs-plugin:1.13.0`, `jacoco-maven-plugin:0.8.12` with 80% line coverage minimum. See research.md R-1, R-6
- [x] T002 [P] Create `src/main/resources/application.yml` with env-var placeholders only: `${STRAVA_ACCESS_TOKEN}` for the token, `${SPRING_DATASOURCE_URL}`, `${SPRING_DATASOURCE_USERNAME}`, `${SPRING_DATASOURCE_PASSWORD}` for the database. No hard-coded secrets. Configure `spring.flyway.enabled=true` and `spring.jpa.hibernate.ddl-auto=validate`. Set Jackson 3 property namespace `spring.jackson.json.*` per research.md R-2
- [x] T003 [P] Create `docker-compose.yml` with a PostgreSQL service. All credentials via environment variables (`${POSTGRES_USER}`, `${POSTGRES_PASSWORD}`, `${POSTGRES_DB}`). No hard-coded passwords (Principle VI)
- [x] T004 [P] Create `.harness/checkstyle.xml` with Google Java Style rules (or equivalent zero-tolerance ruleset compatible with Java 21)
- [x] T005 [P] Create `.harness/spotbugs-exclude.xml` with minimal documented exclusions (if any). SpotBugs 4.9.1 + Find Security Bugs 1.13.0 must report zero high/medium findings (Principle III)
- [x] T006 Create `.harness/run.sh` — gate runner script executing in order: (1) `mvn checkstyle:check` → lint gate, (2) `mvn spotbugs:check` → security gate, (3) secret scan via gitleaks or grep-based check over entire repo including docker-compose.yml and properties files, (4) `mvn verify` → test+coverage gate. Each gate writes stdout/stderr to `.harness/evidence/<gate>.log` and pass/fail to `.harness/evidence/<gate>.result`. Exits non-zero on first failure. Thresholds in config files only — the script must not contain inline thresholds (Principle XI)
- [x] T007 [P] Create `src/main/java/<base-package>/StravaActivityMirrorApplication.java` — Spring Boot main class with `@SpringBootApplication`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Database schema, JPA entities, Strava client, error
handling infrastructure. MUST complete before any user story.

**CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T008 Create Flyway migration `src/main/resources/db/migration/V1__create_activity_tables.sql` per data-model.md: `activity_summaries` table (activity_id BIGINT PRIMARY KEY, start_date TIMESTAMPTZ NOT NULL, summary_json JSONB NOT NULL) with index `idx_summaries_start_date` on `start_date DESC`; `activity_details` table (activity_id BIGINT PRIMARY KEY, start_date TIMESTAMPTZ NOT NULL, detail_json JSONB NOT NULL)
- [ ] T009 [P] Create `ActivitySummary` JPA entity in `src/main/java/<base-package>/entity/ActivitySummary.java` — maps to `activity_summaries` table. Fields: `activityId` (Long, `@Id`, no generation), `startDate` (Instant, mapped to TIMESTAMPTZ), `summaryJson` (String, `@JdbcTypeCode(SqlTypes.JSON)` mapped to JSONB). No field-by-field re-modeling of the JSON (Principle VIII)
- [ ] T010 [P] Create `ActivityDetail` JPA entity in `src/main/java/<base-package>/entity/ActivityDetail.java` — maps to `activity_details` table. Fields: `activityId` (Long, `@Id`, no generation), `startDate` (Instant), `detailJson` (String, `@JdbcTypeCode(SqlTypes.JSON)`)
- [ ] T011 [P] Create `StravaProperties` in `src/main/java/<base-package>/config/StravaProperties.java` — `@ConfigurationProperties(prefix = "strava")` with `@Validated`. Field: `accessToken` (`@NotBlank String`). Application refuses to start if token is missing or empty (FR-024). The token value must never be logged (Principle VI)
- [ ] T012 [P] Create error types in `src/main/java/<base-package>/exception/`: (a) `ErrorCode` enum with values `VALIDATION_ERROR`, `STRAVA_AUTH_ERROR`, `ACTIVITY_NOT_FOUND`, `STRAVA_RATE_LIMITED`, `DATABASE_ERROR`, `STRAVA_SERVER_ERROR`, `STRAVA_TIMEOUT`, each with its HTTP status per contracts/api.md error table; (b) `ErrorResponse` record (`String code`, `String message`); (c) `StravaApiException` extending `RuntimeException` with an `ErrorCode` field
- [ ] T013 Create `StravaClientConfig` in `src/main/java/<base-package>/config/StravaClientConfig.java` — configures a `RestClient` bean using `ClientHttpRequestFactoryBuilder` with 3-second connect timeout and 10-second read timeout (Principle VII). Sets `Authorization: Bearer {token}` as default header from `StravaProperties`. Base URL: `https://www.strava.com/api/v3`. See research.md R-3
- [ ] T014 Create `StravaClient` in `src/main/java/<base-package>/client/StravaClient.java` — single class for all Strava calls (Principle VII). Methods: `getAthleteActivities(Long after, Long before, Integer page, Integer perPage)` returning the raw JSON array string, and `getActivityDetail(long id)` returning the raw JSON object string. Map HTTP responses: 401→`STRAVA_AUTH_ERROR`, 404→`ACTIVITY_NOT_FOUND`, 429→`STRAVA_RATE_LIMITED`, 5xx→`STRAVA_SERVER_ERROR`, timeout→`STRAVA_TIMEOUT`. Throw `StravaApiException` for all failures. Never log the token
- [ ] T015 Create `GlobalExceptionHandler` in `src/main/java/<base-package>/exception/GlobalExceptionHandler.java` — `@RestControllerAdvice`. Handle: `ConstraintViolationException` and `MethodArgumentNotValidException` → 400 `VALIDATION_ERROR`; `StravaApiException` → mapped HTTP status + error code; `DataAccessException` → 500 `DATABASE_ERROR`; catch-all → 500 `DATABASE_ERROR`. All responses produce `ErrorResponse(code, message)`. Never include token or raw Strava body (Principle X, FR-022)
- [ ] T016 Write `StravaClientTest` in `src/test/java/<base-package>/client/StravaClientTest.java` — WireMock tests covering both `getAthleteActivities` and `getActivityDetail`: (a) successful JSON response, (b) Strava 401 → `STRAVA_AUTH_ERROR`, (c) Strava 404 → `ACTIVITY_NOT_FOUND`, (d) Strava 429 → `STRAVA_RATE_LIMITED`, (e) Strava 500 → `STRAVA_SERVER_ERROR`, (f) connection timeout → `STRAVA_TIMEOUT`. Test display names must include the error code being tested

**Checkpoint**: Foundation ready — all infrastructure tested, user
story implementation can begin.

---

## Phase 3: User Story 1 — Sync Activity Summaries (Priority: P1) — MVP

**Goal**: Athlete can trigger a sync that pulls activity summaries
from Strava and stores them locally. Duplicate activities are skipped.
Response reports counts.

**Independent Test**: POST /athlete/activities/sync with a WireMock
Strava stub and Testcontainers PostgreSQL. Verify stored records and
count accuracy. Verify Strava called exactly 1 time.

### Tests for User Story 1

- [ ] T017 [P] [US1] Write `ActivitySummaryRepositoryTest` in `src/test/java/<base-package>/repository/ActivitySummaryRepositoryTest.java` — Testcontainers PostgreSQL. Test native INSERT ON CONFLICT DO NOTHING: inserting 3 new activities returns inserted=3; re-inserting the same 3 returns inserted=0; inserting 5 where 2 exist returns inserted=3. Verify start_date stored as UTC. Test display names include FR-003, FR-004
- [ ] T018 [P] [US1] Write sync controller test in `src/test/java/<base-package>/controller/ActivityControllerSyncTest.java` — MockMvc. Test: valid params → 200 with `{total, stored, skipped}` (FR-005); `after=abc` → 400 `VALIDATION_ERROR` (FR-006); `after=-1` → 400 (FR-006); `after=1000&before=500` → 400 (FR-006); `after=1000&before=1000` → 400 (FR-006); `page=0` → 400 (FR-006); `per_page=-1` → 400 (FR-006). Strava called 0 times for all validation errors
- [ ] T019 [US1] Write sync integration test in `src/test/java/<base-package>/integration/SyncIntegrationTest.java` — Testcontainers + WireMock. Cover AC-1.1 (3 new → stored=3), AC-1.2 (same 3 → skipped=3), AC-1.3 (mixed 5 → stored=3, skipped=2), AC-1.4 (empty → all zeros), AC-1.11 (Strava 401 → `STRAVA_AUTH_ERROR`, zero stored), AC-1.12 (429), AC-1.13 (502), AC-1.14 (timeout). Verify Strava called exactly 1 time per request (FR-002). Verify error responses do not contain token (FR-022)

### Implementation for User Story 1

- [ ] T020 [US1] Create `ActivitySummaryRepository` in `src/main/java/<base-package>/repository/ActivitySummaryRepository.java` — extends `JpaRepository<ActivitySummary, Long>`. Add native query method for bulk sync: `INSERT INTO activity_summaries (activity_id, start_date, summary_json) VALUES (?, ?, ?::jsonb) ON CONFLICT (activity_id) DO NOTHING`. Return the number of rows inserted. See data-model.md "Identity & Uniqueness"
- [ ] T021 [P] [US1] Create `SyncResponse` DTO record in `src/main/java/<base-package>/dto/SyncResponse.java` — fields: `int total`, `int stored`, `int skipped` (FR-005)
- [ ] T022 [US1] Implement sync logic in `ActivityService` at `src/main/java/<base-package>/service/ActivityService.java` — call `StravaClient.getAthleteActivities`, parse JSON array with Jackson 3 (`tools.jackson` ObjectMapper) to extract activity IDs and `start_date` fields (ISO-8601 string → Instant), bulk insert via repository, compute `stored` from returned row count and `skipped = total - stored`. Wrap in `@Transactional` — if DB fails, nothing is stored (EC-5)
- [ ] T023 [US1] Implement `POST /athlete/activities/sync` in `ActivityController` at `src/main/java/<base-package>/controller/ActivityController.java` — query params: `after` (Long, optional), `before` (Long, optional), `page` (Integer, optional), `per_page` (Integer, optional). Delegate to `ActivityService`. Return `SyncResponse` as JSON with 200 OK
- [ ] T024 [US1] Add sync parameter validation in `ActivityController`: `after` and `before` must be non-negative when present; `after` must be strictly less than `before` when both present; `page` must be ≥ 1 when present; `per_page` must be 1–200 when present. Use Jakarta Bean Validation or manual validation. Reject with 400 `VALIDATION_ERROR` before calling Strava (FR-006)

**Checkpoint**: Sync endpoint fully functional. Athlete can sync
activities from Strava. Duplicate detection works. All error codes
mapped. Test independently with `POST /athlete/activities/sync`.

---

## Phase 4: User Story 2 — List Stored Activity Summaries (Priority: P2)

**Goal**: Athlete can query stored activity summaries by date range
with pagination. Response includes pagination metadata. Strava is never
called.

**Independent Test**: Pre-populate database with known records via
Testcontainers. Query with various date ranges and page params. Verify
ordering, filtering, pagination metadata. Verify zero Strava calls.

### Tests for User Story 2

- [ ] T025 [P] [US2] Write `ActivitySummaryRepositoryListTest` in `src/test/java/<base-package>/repository/ActivitySummaryRepositoryListTest.java` — Testcontainers PostgreSQL. Test: exclusive boundary filtering (`start_date > after AND start_date < before`), newest-first ordering, descending activity_id tiebreaker for equal start_dates, Spring Data Page results for pagination. Test display names include FR-009, FR-010
- [ ] T026 [P] [US2] Write list controller test in `src/test/java/<base-package>/controller/ActivityControllerListTest.java` — MockMvc. Test: valid params → 200 with `activities` array + `total_count`, `page`, `per_page`, `has_next` (FR-013); empty result → 200 with empty array (FR-011); `per_page=201` → 400 (FR-010); `per_page=0` → 400; `after=1000&before=500` → 400. Verify no Strava call header in any test (FR-012)
- [ ] T027 [US2] Write list integration test in `src/test/java/<base-package>/integration/ListIntegrationTest.java` — Testcontainers. Cover AC-2.1 (5 activities in range), AC-2.2 (newest-first), AC-2.3 (tiebreaker), AC-2.4 (page 2), AC-2.5 (default per_page=30), AC-2.6 (empty range), AC-2.7 (empty DB), AC-2.11 (boundary exclusion for after), AC-2.12 (boundary exclusion for before), AC-2.13 (pagination metadata has_next=true), AC-2.14 (last page has_next=false), AC-2.15 (empty DB metadata)

### Implementation for User Story 2

- [ ] T028 [P] [US2] Create DTO records in `src/main/java/<base-package>/dto/`: `ActivitySummaryDto` record (`long activityId`, `String startTime`, `Object summary`); `ActivityListResponse` record (`List<ActivitySummaryDto> activities`, `long totalCount`, `int page`, `int perPage`, `boolean hasNext`) per contracts/api.md
- [ ] T029 [US2] Add list query method to `ActivitySummaryRepository` — Spring Data JPA query finding summaries with `start_date > :after AND start_date < :before` (exclusive boundaries per Clarification Q1), ordered by `start_date DESC, activity_id DESC`, returning `Page<ActivitySummary>`. Handle optional after/before (omit clause if null). Convert epoch-second Long params to `Instant` before querying
- [ ] T030 [US2] Implement list logic in `ActivityService` — accept `after`, `before` (Long, optional), `page`, `perPage`. Convert epoch seconds to `Instant` via `Instant.ofEpochSecond()`. Query repository. Map `Page<ActivitySummary>` to `ActivityListResponse` with pagination metadata: `totalCount` from `page.getTotalElements()`, `hasNext` from `page.hasNext()`
- [ ] T031 [US2] Implement `GET /athlete/activities` in `ActivityController` — query params: `after`, `before`, `page`, `per_page`. Same validation as sync (non-negative, after < before, page ≥ 1, per_page 1–200). Return `ActivityListResponse` as JSON with 200 OK. This endpoint must never call Strava (FR-012)

**Checkpoint**: List endpoint fully functional. Athlete can query stored
activities by date range with pagination metadata. Independently
testable without Strava.

---

## Phase 5: User Story 3 — Get Activity Detail by ID (Priority: P3)

**Goal**: Athlete can fetch a single activity's detailed representation.
Local-first: returns stored detail instantly, or fetches from Strava
and caches it. Second request never calls Strava.

**Independent Test**: (a) Pre-populate a detail record and verify local
return without Strava call. (b) Use WireMock stub for fetch-and-store
path. Verify Strava called exactly once across two sequential requests.

### Tests for User Story 3

- [ ] T032 [P] [US3] Write `ActivityDetailRepositoryTest` in `src/test/java/<base-package>/repository/ActivityDetailRepositoryTest.java` — Testcontainers. Test: save and retrieve detail record, verify JSONB storage, verify start_date stored as UTC. Test display names include FR-015
- [ ] T033 [P] [US3] Write get-detail controller test in `src/test/java/<base-package>/controller/ActivityControllerDetailTest.java` — MockMvc. Test: valid ID cached → 200 with `{activity_id, start_time, detail}` (FR-015); `id=abc` → 400 (FR-018); `id=0` → 400 (FR-018); `id=-1` → 400 (FR-018). Verify Strava not called for cached or validation-error cases
- [ ] T034 [US3] Write get-detail integration test in `src/test/java/<base-package>/integration/DetailIntegrationTest.java` — Testcontainers + WireMock. Cover AC-3.1 (cache miss → fetch, store, return), AC-3.2 (cache hit → local return, 0 Strava calls), AC-3.3 (repeat request → Strava called 1 time total), AC-3.7 (Strava 404 → `ACTIVITY_NOT_FOUND`, nothing stored), AC-3.8 (401), AC-3.9 (429), AC-3.10 (502), AC-3.11 (timeout). Verify error responses do not contain token

### Implementation for User Story 3

- [ ] T035 [US3] Create `ActivityDetailRepository` in `src/main/java/<base-package>/repository/ActivityDetailRepository.java` — extends `JpaRepository<ActivityDetail, Long>`. Basic CRUD is sufficient (find by ID, save)
- [ ] T036 [P] [US3] Create `ActivityDetailResponse` DTO record in `src/main/java/<base-package>/dto/ActivityDetailResponse.java` — fields: `long activityId`, `String startTime`, `Object detail` per contracts/api.md
- [ ] T037 [US3] Implement get-detail logic in `ActivityService` — check `ActivityDetailRepository.findById(id)`. If present, return it (FR-015). If absent, call `StravaClient.getActivityDetail(id)`, parse JSON to extract `start_date`, save new `ActivityDetail` entity, return it (FR-016). On Strava failure, throw `StravaApiException` — nothing stored (FR-020)
- [ ] T038 [US3] Implement `GET /activities/{id}` in `ActivityController` — path param `id` (Long). Validate: must be numeric and positive (≥ 1); reject with 400 `VALIDATION_ERROR` if invalid (FR-018). Delegate to `ActivityService`. Return `ActivityDetailResponse` as JSON with 200 OK

**Checkpoint**: All three endpoints operational. Detail retrieval is
local-first with Strava fallback. Independently testable.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Startup validation tests, error security tests, database
failure tests, harness verification, coverage check.

- [ ] T039 [P] Write startup validation tests in `src/test/java/<base-package>/config/StartupValidationTest.java` — verify application refuses to start when `STRAVA_ACCESS_TOKEN` is missing or blank (EC-1, FR-024); verify application refuses to start when database URL is missing (EC-2, FR-025). Use `@SpringBootTest` with modified environment
- [ ] T040 [P] Write error response security tests in `src/test/java/<base-package>/exception/ErrorResponseSecurityTest.java` — for every error code in the error table, verify the response body does not contain the configured token value (EC-3) and does not contain a raw Strava response body (EC-4). Test display names include EC-3, EC-4
- [ ] T041 [P] Write database failure tests in `src/test/java/<base-package>/integration/DatabaseFailureTest.java` — simulate DB failure during sync → 500 `DATABASE_ERROR`, zero records stored (EC-5); DB failure during list → 500 `DATABASE_ERROR` (EC-6); DB failure during detail store → 500 `DATABASE_ERROR`, nothing stored (EC-7)
- [ ] T042 Run `.harness/run.sh` end-to-end and fix any gate failures. Verify: (1) Checkstyle reports zero violations, (2) SpotBugs+FindSecBugs reports zero high/medium findings, (3) secret scan finds no secrets in committed files, (4) all tests pass with JaCoCo ≥80% line coverage
- [ ] T043 Review all test names/display names to verify every FR-nnn and EC-n from the spec is covered by at least one test (Principle V traceability). Cross-reference against spec.md functional requirements FR-001 through FR-025 and edge cases EC-1 through EC-7
- [ ] T044 Run quickstart.md validation scenarios A through I manually against the running application to confirm end-to-end behavior

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 completion — BLOCKS
  all user stories
- **User Stories (Phases 3–5)**: All depend on Phase 2 completion.
  Can proceed in parallel or sequentially in priority order (P1→P2→P3)
- **Polish (Phase 6)**: Depends on all user stories being complete

### User Story Dependencies

- **US1 (Sync, P1)**: Depends on Phase 2 only. No dependency on US2
  or US3.
- **US2 (List, P2)**: Depends on Phase 2 only. Uses
  `ActivitySummaryRepository` (extended in US1 with sync method, but
  list query is independent). Can start in parallel with US1.
- **US3 (Detail, P3)**: Depends on Phase 2 only. Uses `StravaClient`
  (created in Phase 2) and `ActivityDetailRepository` (created in US3).
  Can start in parallel with US1 and US2.

### Within Each User Story

1. Tests written first (TDD — tests should fail initially)
2. Repository/data layer
3. Service layer
4. Controller/endpoint layer
5. Validation
6. Story checkpoint: all tests pass

### Parallel Opportunities

**Phase 1**: T002, T003, T004, T005, T007 can all run in parallel.
T006 (run.sh) depends on T004, T005 being decided but not on their
file content.

**Phase 2**: T009, T010, T011, T012 can run in parallel (independent
files). T013 depends on T011. T014 depends on T012, T013. T015
depends on T012. T016 depends on T014.

**Phases 3–5**: All three user stories can run in parallel after Phase 2.
Within each story, test tasks marked [P] can run in parallel.

---

## Parallel Example: User Story 1

```text
# Parallel: write all US1 tests simultaneously
T017: ActivitySummaryRepositoryTest (Testcontainers)
T018: ActivityControllerSyncTest (MockMvc)

# Sequential: integration test depends on understanding unit tests
T019: SyncIntegrationTest

# Parallel: repository and DTO
T020: ActivitySummaryRepository (sync method)
T021: SyncResponse DTO record

# Sequential: service → controller → validation
T022: ActivityService sync logic
T023: ActivityController sync endpoint
T024: Sync parameter validation
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: User Story 1 (Sync)
4. **STOP and VALIDATE**: Run sync tests, verify Strava stub works,
   check counts
5. Run `.harness/run.sh` — fix any gate failures early

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. US1 (Sync) → Athlete can sync and store activities (MVP)
3. US2 (List) → Athlete can query stored activities with pagination
4. US3 (Detail) → Athlete can drill into individual activities
5. Polish → All gates green, full traceability, quickstart validated

### Sequential (Single Developer)

1. Phase 1 → Phase 2 → Phase 3 (US1) → Phase 4 (US2) → Phase 5 (US3) → Phase 6
2. Each phase checkpoint verifies the increment works before moving on
3. US1 completion = deployable MVP

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks
- [Story] label maps task to specific user story for traceability
- Each user story is independently completable and testable
- Tests written first (TDD) — verify they fail before implementing
- All test display names must include requirement IDs (FR-nnn, EC-n)
- Jackson 3 uses `tools.jackson` packages — do not import `com.fasterxml.jackson` (except annotations)
- Spring Boot 4 starters: `spring-boot-starter-webmvc` (not `-web`), `spring-boot-starter-restclient`, `spring-boot-starter-flyway`
- Commit after each task or logical group
- Stop at any checkpoint to validate independently
