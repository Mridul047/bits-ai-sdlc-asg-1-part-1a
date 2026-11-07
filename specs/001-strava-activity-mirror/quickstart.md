# Quickstart Validation Guide: Strava Activity Mirror

**Spec**: [spec.md](spec.md) | **Contracts**: [contracts/api.md](contracts/api.md)

## Prerequisites

- Java 21 (JDK)
- Maven 3.9+
- Docker and Docker Compose (for PostgreSQL)
- A valid Strava API access token (for manual testing only; automated
  tests use stubs)

## 1. Start the Database

```bash
docker-compose up -d
```

Requires environment variables (see below). The database starts on
the port specified in the compose file. No hard-coded credentials.

## 2. Set Environment Variables

```bash
export STRAVA_ACCESS_TOKEN=<your-token>
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/stravamirror
export SPRING_DATASOURCE_USERNAME=<db-user>
export SPRING_DATASOURCE_PASSWORD=<db-password>
```

The application refuses to start if `STRAVA_ACCESS_TOKEN` or database
settings are missing (FR-024, FR-025).

## 3. Build and Run

```bash
mvn clean package -DskipTests
java -jar target/strava-activity-mirror-*.jar
```

The service starts on `http://localhost:8080`.

## 4. Run the Harness (all gates)

```bash
.harness/run.sh
```

Gates run in order: lint → security → secret-scan → tests (with
JaCoCo ≥80%). Exits non-zero on any failure. Evidence written to
`.harness/evidence/`.

## 5. Validation Scenarios

### Scenario A: Startup validation (EC-1, EC-2)

1. Unset `STRAVA_ACCESS_TOKEN` and attempt to start the app.
2. **Expected**: Application refuses to start with a clear error
   message naming the missing variable.
3. Repeat with missing database URL.

### Scenario B: Sync activities (FR-001 → FR-007)

```bash
curl -X POST "http://localhost:8080/athlete/activities/sync?after=1704067200&before=1706745600&page=1&per_page=30"
```

**Expected**: 200 OK with `{"total": N, "stored": N, "skipped": 0}`
on first sync. Repeat the same request — `stored` should be 0 and
`skipped` should equal `total`.

### Scenario C: Sync validation error (FR-006)

```bash
curl -X POST "http://localhost:8080/athlete/activities/sync?after=-1"
```

**Expected**: 400 with `{"code": "VALIDATION_ERROR", "message": "..."}`.
Strava is not called.

### Scenario D: List activities (FR-008 → FR-013)

```bash
curl "http://localhost:8080/athlete/activities?page=1&per_page=10"
```

**Expected**: 200 OK with activities array (newest first), plus
`total_count`, `page`, `per_page`, `has_next` pagination fields.
Strava is never called.

### Scenario E: List boundary exclusion

```bash
# After syncing, find an activity's exact epoch start time and query
# with after=<exact_time>. That activity should NOT appear (exclusive).
```

### Scenario F: Get activity detail — cache miss (FR-014 → FR-017)

```bash
curl "http://localhost:8080/activities/12345678"
```

**Expected** (first call): 200 OK with detail JSON. Strava called once.
**Expected** (second call): 200 OK with same detail. Strava NOT called.

### Scenario G: Get activity — not found (FR-019)

```bash
curl "http://localhost:8080/activities/999999999"
```

**Expected**: 404 with `{"code": "ACTIVITY_NOT_FOUND", "message": "..."}`.

### Scenario H: Invalid activity ID (FR-018)

```bash
curl "http://localhost:8080/activities/0"
```

**Expected**: 400 with `{"code": "VALIDATION_ERROR", "message": "..."}`.

### Scenario I: Error response security (EC-3, EC-4)

For any error response, verify:
- The JSON body does not contain the `STRAVA_ACCESS_TOKEN` value.
- The JSON body does not contain Strava's raw response body.

## 6. Automated Test Run

```bash
mvn verify
```

All tests run with:
- WireMock stubbing Strava (no real API calls)
- Testcontainers PostgreSQL (no external database needed)
- Test names/display names include requirement IDs (FR-nnn, EC-n)
- JaCoCo coverage report at `target/site/jacoco/index.html`
