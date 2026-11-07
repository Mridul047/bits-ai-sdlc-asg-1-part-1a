# Feature Specification: Strava Activity Mirror

**Feature Directory**: `specs/001-strava-activity-mirror`

**Created**: 2026-11-07

**Status**: Draft

**Input**: Mirror a single athlete's Strava activities into a local
Postgres database and serve them locally, with manual sync, date-range
listing, and local-first detail retrieval.

## User Scenarios & Testing

### User Story 1 — Sync Activity Summaries from Strava (Priority: P1)

The athlete triggers a one-shot sync to pull activity summaries from
Strava into the local database. The sync accepts the same inputs as
Strava's "get logged-in athlete activities" endpoint: `after` and
`before` (epoch timestamps), `page`, and `per_page`. One sync request
makes exactly one Strava API call. The athlete repeats syncs manually
for additional pages.

Each returned activity is stored as a summary record containing the
Strava activity ID (unique), the start time in UTC (ISO-8601, e.g.
`2018-05-02T12:15:09Z`), and the complete summary JSON exactly as
Strava returned it. Activities already in the database are skipped, not
updated. The response reports how many activities Strava returned, how
many were newly stored, and how many were skipped.

**Why this priority**: This is the foundational capability — without
syncing, no data enters the system and no other story can function.

**Independent Test**: Can be fully tested by sending a sync request
against a stubbed Strava and verifying the database contains the
expected records with the correct counts.

**Acceptance Scenarios**:

1. **Given** the database is empty and Strava returns 3 activities,
   **When** the athlete sends a sync request with valid parameters,
   **Then** the response status is 200, the body reports
   `total=3, stored=3, skipped=0`, all 3 summary records are in the
   database, and Strava was called exactly 1 time.

2. **Given** the database contains 3 activities from a previous sync
   and Strava returns those same 3 activities,
   **When** the athlete sends the same sync request again,
   **Then** the response status is 200, the body reports
   `total=3, stored=0, skipped=3`, no new records are created, and
   Strava was called exactly 1 time.

3. **Given** the database contains 2 activities and Strava returns 5
   (the 2 existing plus 3 new),
   **When** the athlete sends a sync request,
   **Then** the response status is 200, the body reports
   `total=5, stored=3, skipped=2`, and Strava was called exactly
   1 time.

4. **Given** Strava returns 0 activities for the requested range,
   **When** the athlete sends a sync request,
   **Then** the response status is 200, the body reports
   `total=0, stored=0, skipped=0`, and Strava was called exactly
   1 time.

5. **Given** the athlete sends `after=abc` (non-numeric),
   **When** the request is processed,
   **Then** the response status is 400 with code `VALIDATION_ERROR`,
   a human-readable message describes the problem, and Strava was
   called exactly 0 times.

6. **Given** the athlete sends `after=-1` (negative),
   **When** the request is processed,
   **Then** the response status is 400 with code `VALIDATION_ERROR`,
   and Strava was called exactly 0 times.

7. **Given** the athlete sends `after=1000` and `before=500`
   (after not earlier than before),
   **When** the request is processed,
   **Then** the response status is 400 with code `VALIDATION_ERROR`,
   and Strava was called exactly 0 times.

8. **Given** the athlete sends `after=1000` and `before=1000`
   (equal values),
   **When** the request is processed,
   **Then** the response status is 400 with code `VALIDATION_ERROR`,
   and Strava was called exactly 0 times.

9. **Given** the athlete sends `page=0`,
   **When** the request is processed,
   **Then** the response status is 400 with code `VALIDATION_ERROR`,
   and Strava was called exactly 0 times.

10. **Given** the athlete sends `per_page=-1`,
    **When** the request is processed,
    **Then** the response status is 400 with code `VALIDATION_ERROR`,
    and Strava was called exactly 0 times.

11. **Given** Strava rejects the token (HTTP 401),
    **When** the athlete sends a valid sync request,
    **Then** the response status is 401 with code `STRAVA_AUTH_ERROR`,
    zero activities are stored, the response body does not contain the
    token, and Strava was called exactly 1 time.

12. **Given** Strava rate-limits the request (HTTP 429),
    **When** the athlete sends a valid sync request,
    **Then** the response status is 429 with code
    `STRAVA_RATE_LIMITED`, zero activities are stored, and Strava was
    called exactly 1 time.

13. **Given** Strava returns a server error (HTTP 5xx),
    **When** the athlete sends a valid sync request,
    **Then** the response status is 502 with code
    `STRAVA_SERVER_ERROR`, zero activities are stored, and Strava was
    called exactly 1 time.

14. **Given** Strava does not respond within the timeout,
    **When** the athlete sends a valid sync request,
    **Then** the response status is 504 with code `STRAVA_TIMEOUT`,
    zero activities are stored, and Strava was called exactly 1 time
    (the timed-out attempt).

---

### User Story 2 — List Stored Activity Summaries (Priority: P2)

The athlete queries stored activity summaries using the same parameter
shape as sync: `after`, `before`, `page`, `per_page`. The response is
built entirely from the local database. Strava is never called.

Results are filtered by stored start time, ordered newest-first, with a
stable tiebreaker (descending activity ID) for equal start times. Each
item includes the activity ID, start time, and stored summary JSON.
Paging defaults to page 1 with 30 items per page; the maximum
`per_page` is 200.

**Why this priority**: Querying stored data is the primary read path
and the prerequisite for all downstream features (e.g. AI insights).

**Independent Test**: Can be fully tested by pre-populating the
database with known summary records and verifying query results without
any Strava stub.

**Acceptance Scenarios**:

1. **Given** the database contains 5 activity summaries spanning
   2024-01-01 to 2024-01-31,
   **When** the athlete requests a list with `after` and `before`
   covering that range,
   **Then** the response status is 200, all 5 activities are returned
   newest-first, each including activity ID, start time, and summary
   JSON, and Strava was called exactly 0 times.

2. **Given** the database contains activities with start times
   T1 < T2 < T3,
   **When** the athlete requests a list without date filters,
   **Then** the activities appear in order T3, T2, T1.

3. **Given** the database contains 2 activities with identical start
   times but Strava IDs 100 and 200,
   **When** the athlete requests a list,
   **Then** the activity with ID 200 appears before ID 100
   (stable descending-ID tiebreaker), and Strava was called exactly
   0 times.

4. **Given** the database contains 5 activities,
   **When** the athlete requests `page=2` with `per_page=2`,
   **Then** exactly 2 activities are returned (the 3rd and 4th
   newest), and Strava was called exactly 0 times.

5. **Given** the database contains 50 activities and no `page` or
   `per_page` is specified,
   **When** the athlete requests a list,
   **Then** exactly 30 activities are returned (the default page size).

6. **Given** the database contains activities but none fall within the
   requested date range,
   **When** the athlete requests a list,
   **Then** the response status is 200 with an empty activities list,
   not an error.

7. **Given** the database is empty,
   **When** the athlete requests a list,
   **Then** the response status is 200 with an empty activities list.

8. **Given** the athlete sends `per_page=201`,
   **When** the request is processed,
   **Then** the response status is 400 with code `VALIDATION_ERROR`.

9. **Given** the athlete sends `per_page=0`,
   **When** the request is processed,
   **Then** the response status is 400 with code `VALIDATION_ERROR`.

10. **Given** the athlete sends `after=1000` and `before=500`,
    **When** the request is processed,
    **Then** the response status is 400 with code `VALIDATION_ERROR`,
    and Strava was called exactly 0 times.

---

### User Story 3 — Get Activity Detail by ID (Priority: P3)

The athlete requests one activity's detailed representation by its
Strava ID. If a detailed record for that ID is already stored locally,
it is returned without calling Strava. Otherwise the service fetches the
detail from Strava, stores it (activity ID, UTC start time, complete
detail JSON as returned), and returns it. Subsequent requests for the
same ID are served from local storage.

**Why this priority**: Enables drill-down into individual activities and
is the primary mechanism for obtaining detailed Strava data. Depends on
sync for discovery but can also be called with a known ID directly.

**Independent Test**: Can be tested by (a) pre-populating a detail
record and verifying it is returned without a Strava call, and (b)
using a Strava stub for the fetch-and-store path.

**Acceptance Scenarios**:

1. **Given** no detail record for activity 12345 exists locally and
   Strava returns the detail for 12345,
   **When** the athlete requests activity 12345,
   **Then** the response status is 200, the response includes activity
   ID, start time, and complete detail JSON, the detail is stored in
   the database, and Strava was called exactly 1 time.

2. **Given** a detail record for activity 12345 is already stored,
   **When** the athlete requests activity 12345,
   **Then** the response status is 200, the stored detail is returned,
   and Strava was called exactly 0 times.

3. **Given** no detail record for activity 12345 exists,
   **When** the athlete requests activity 12345 twice in sequence,
   **Then** both responses return the detail successfully, and Strava
   was called exactly 1 time total (the second request is served
   locally).

4. **Given** the athlete requests activity ID "abc" (non-numeric),
   **When** the request is processed,
   **Then** the response status is 400 with code `VALIDATION_ERROR`,
   and Strava was called exactly 0 times.

5. **Given** the athlete requests activity ID 0 (non-positive),
   **When** the request is processed,
   **Then** the response status is 400 with code `VALIDATION_ERROR`,
   and Strava was called exactly 0 times.

6. **Given** the athlete requests activity ID -1 (negative),
   **When** the request is processed,
   **Then** the response status is 400 with code `VALIDATION_ERROR`,
   and Strava was called exactly 0 times.

7. **Given** no detail record exists and Strava returns 404 for
   activity 99999,
   **When** the athlete requests activity 99999,
   **Then** the response status is 404 with code
   `ACTIVITY_NOT_FOUND`, no record is stored, and Strava was called
   exactly 1 time.

8. **Given** the configured Strava token is invalid and no detail
   exists locally,
   **When** the athlete requests an activity,
   **Then** the response status is 401 with code `STRAVA_AUTH_ERROR`,
   no record is stored, and Strava was called exactly 1 time.

9. **Given** Strava rate-limits the request and no detail exists,
   **When** the athlete requests an activity,
   **Then** the response status is 429 with code
   `STRAVA_RATE_LIMITED`, no record is stored, and Strava was called
   exactly 1 time.

10. **Given** Strava returns a server error and no detail exists,
    **When** the athlete requests an activity,
    **Then** the response status is 502 with code
    `STRAVA_SERVER_ERROR`, no record is stored, and Strava was called
    exactly 1 time.

11. **Given** Strava times out and no detail exists locally,
    **When** the athlete requests an activity,
    **Then** the response status is 504 with code `STRAVA_TIMEOUT`,
    no record is stored, and Strava was called exactly 1 time.

---

### Edge Cases

**EC-1: Missing Strava token at startup**
**Given** the Strava access token environment variable is not set,
**When** the service starts,
**Then** it refuses to start and outputs a clear error message naming
the missing variable.

**EC-2: Missing database settings at startup**
**Given** the database connection environment variables are not set,
**When** the service starts,
**Then** it refuses to start and outputs a clear error message naming
the missing settings.

**EC-3: Error response never contains the token**
**Given** any error condition occurs on any endpoint,
**When** the error response is returned,
**Then** the JSON body and any logged output do not contain the Strava
access token value.

**EC-4: Error response never contains raw Strava body**
**Given** a Strava API error occurs,
**When** the error response is returned,
**Then** the JSON body does not contain Strava's raw response body.

**EC-5: Database failure during sync**
**Given** Strava returns activities successfully but the database write
fails,
**When** the sync request completes,
**Then** the response status is 500 with code `DATABASE_ERROR`, and
zero partial records are stored (transaction rolled back).

**EC-6: Database failure during list**
**Given** the database is unreachable,
**When** the athlete requests a list,
**Then** the response status is 500 with code `DATABASE_ERROR`.

**EC-7: Database failure during get-detail store**
**Given** Strava returns the detail successfully but the database write
fails,
**When** the get-detail request completes,
**Then** the response status is 500 with code `DATABASE_ERROR`, and
no partial record is stored.

## Requirements

### Functional Requirements

**Sync Capability**

- **FR-001**: System MUST accept sync requests with optional `after`,
  `before` (epoch timestamps), `page`, and `per_page` parameters.
- **FR-002**: Each sync request MUST make exactly one call to the
  Strava API, passing through the provided parameters.
- **FR-003**: Each activity returned by Strava MUST be stored as a
  summary record containing the Strava activity ID (unique), the start
  time in UTC (ISO-8601), and the complete summary JSON exactly as
  Strava returned it.
- **FR-004**: An activity whose Strava ID already exists in the
  database MUST be left unchanged and counted as skipped.
- **FR-005**: The sync response MUST report: total activities returned
  by Strava, count of newly stored activities, and count of skipped
  activities.
- **FR-006**: Invalid inputs (non-numeric values, negative epoch
  timestamps, `after` not strictly earlier than `before`, non-positive
  `page`, non-positive or out-of-range `per_page`) MUST be rejected
  with a validation error before any Strava call.
- **FR-007**: If the Strava API call fails for any reason, zero
  activities MUST be stored for that sync request and the failure MUST
  be reported with a distinct error code.

**List Capability**

- **FR-008**: System MUST accept list requests with optional `after`,
  `before` (epoch timestamps), `page`, and `per_page` parameters.
- **FR-009**: List results MUST be filtered by stored start time and
  ordered newest-first, with descending activity ID as a stable
  tiebreaker for equal start times.
- **FR-010**: List paging MUST default to page 1 with 30 items per
  page; `per_page` MUST NOT exceed 200. Invalid values MUST be
  rejected with a validation error.
- **FR-011**: A list request matching no activities MUST return HTTP
  200 with an empty result set, not an error.
- **FR-012**: List requests MUST NOT call the Strava API under any
  circumstances.
- **FR-013**: Each returned list item MUST include the activity ID,
  start time, and stored summary JSON.

**Get-Detail Capability**

- **FR-014**: System MUST accept get-detail requests with a single
  activity ID path parameter.
- **FR-015**: If a detailed record for the requested ID is stored
  locally, it MUST be returned without calling Strava.
- **FR-016**: If no detailed record exists, the system MUST fetch the
  detail from Strava, store it (activity ID, UTC start time, complete
  detail JSON as returned), and return it.
- **FR-017**: A subsequent get-detail request for the same ID MUST be
  served from local storage; Strava MUST be called at most once per
  activity for detail retrieval.
- **FR-018**: A non-numeric or non-positive activity ID MUST be
  rejected with a validation error without calling Strava.
- **FR-019**: If Strava reports the activity does not exist (HTTP 404),
  nothing MUST be stored and a distinct `ACTIVITY_NOT_FOUND` error
  MUST be returned.
- **FR-020**: If Strava fails (server error, timeout, rate limit, or
  auth error), nothing MUST be stored and the failure MUST be reported
  with a distinct error code.

**Error Handling**

- **FR-021**: All error responses MUST be JSON containing a
  machine-readable `code` field and a human-readable `message` field.
- **FR-022**: Error responses MUST NOT contain the Strava access token
  or Strava's raw response body.
- **FR-023**: The system MUST return distinct error codes for each of
  the following conditions:

  | Condition                          | Error Code             | HTTP Status |
  |------------------------------------|------------------------|-------------|
  | Invalid input                      | `VALIDATION_ERROR`     | 400         |
  | Strava token expired/invalid       | `STRAVA_AUTH_ERROR`    | 401         |
  | Activity not found on Strava       | `ACTIVITY_NOT_FOUND`   | 404         |
  | Strava rate limiting               | `STRAVA_RATE_LIMITED`  | 429         |
  | Strava server error                | `STRAVA_SERVER_ERROR`  | 502         |
  | Strava timeout                     | `STRAVA_TIMEOUT`       | 504         |
  | Local database failure             | `DATABASE_ERROR`       | 500         |

**Startup**

- **FR-024**: The service MUST refuse to start if the Strava access
  token environment variable is missing or empty, with a clear error
  message.
- **FR-025**: The service MUST refuse to start if database connection
  environment variables are missing, with a clear error message.

### Key Entities

- **Activity Summary**: A synced activity's summary data. Contains the
  Strava activity ID (unique identifier), the activity start time in
  UTC (ISO-8601), and the complete Strava summary JSON stored as-is.
  Created by the sync capability. Immutable after creation.

- **Activity Detail**: A fetched activity's detailed data. Contains the
  Strava activity ID (unique identifier), the activity start time in
  UTC (ISO-8601), and the complete Strava detail JSON stored as-is.
  Created by the get-detail capability on first access. Immutable after
  creation.

An activity may have a summary record, a detail record, or both.
The list capability returns only summary records. The get-detail
capability checks only for detail records; a summary-only activity
still triggers a Strava fetch for the detail.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Every synced activity is retrievable from local storage
  without any subsequent call to Strava.
- **SC-002**: Syncing the same set of activities any number of times
  produces zero duplicate records in the database.
- **SC-003**: Activity details are fetched from Strava at most once per
  activity; all repeat accesses are served from local storage.
- **SC-004**: Every documented error condition produces a distinct,
  machine-readable error code that the athlete can programmatically
  distinguish.
- **SC-005**: The Strava access token is never exposed to the athlete
  through any response body, error message, or log output.
- **SC-006**: The service cannot enter an operational state when
  required credentials or database settings are missing.

## Assumptions

- **Paging defaults**: Sync parameters follow Strava's conventions
  (page starts at 1, per_page defaults to 30, maximum 200). List
  uses the same defaults and limits for local queries.
- **Immutable activities**: Synced activities are assumed not to change
  on Strava after they are recorded. The system never updates an
  existing summary or detail record.
- **Single-athlete model**: Only one athlete's token is configured. The
  system does not partition data by athlete.
- **Epoch timestamps**: `after` and `before` are Unix epoch seconds
  (non-negative integers). When both are provided, `after` MUST be
  strictly less than `before`.
- **Tiebreaker ordering**: When multiple activities share the same
  start time, descending Strava activity ID is used as the tiebreaker
  to guarantee deterministic ordering.
- **Transactional writes**: A sync that stores multiple activities
  either commits all new records or none (atomic transaction).

## Out of Scope

- User interface or dashboard.
- The `include_all_efforts` option on activity detail.
- Automatic paging or scheduled/recurring sync.
- Updating already-stored summary or detail records.
- Deleting records that were removed on Strava.
- Token generation, refresh, or storage.
- Multiple athletes or athlete switching.
- Authentication on the service's own API.
- Any write-back to Strava.
