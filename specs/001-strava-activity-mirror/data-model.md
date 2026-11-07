# Data Model: Strava Activity Mirror

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

## Entities

### activity_summaries

Stores synced activity summary data from Strava's
GET /athlete/activities endpoint.

| Column       | Type          | Constraints           | Description                                    |
|--------------|---------------|-----------------------|------------------------------------------------|
| activity_id  | BIGINT        | PRIMARY KEY           | Strava activity ID, unique identifier          |
| start_date   | TIMESTAMPTZ   | NOT NULL              | Activity start time in UTC (from Strava's `start_date` field, ISO-8601) |
| summary_json | JSONB         | NOT NULL              | Complete Strava summary object stored as-is    |

**Indexes**:
- `idx_summaries_start_date` on `start_date DESC` — supports the
  list query's newest-first ordering and date-range filtering.

**Lifecycle**: Created by the sync capability. Immutable after
creation — never updated or deleted.

### activity_details

Stores fetched activity detail data from Strava's
GET /activities/{id} endpoint.

| Column      | Type          | Constraints           | Description                                    |
|-------------|---------------|-----------------------|------------------------------------------------|
| activity_id | BIGINT        | PRIMARY KEY           | Strava activity ID, unique identifier          |
| start_date  | TIMESTAMPTZ   | NOT NULL              | Activity start time in UTC (from detail's `start_date` field) |
| detail_json | JSONB         | NOT NULL              | Complete Strava detail object stored as-is     |

**Lifecycle**: Created by the get-detail capability on first access.
Immutable after creation — never updated or deleted.

## Relationships

- An activity may have a row in `activity_summaries`, a row in
  `activity_details`, or both. There is no foreign key between them;
  they are independent storage of different Strava representations.
- The list capability queries only `activity_summaries`.
- The get-detail capability queries only `activity_details`.

## Identity & Uniqueness

- `activity_id` is the Strava-assigned identifier (Long/BIGINT).
  It is the sole primary key in both tables.
- Sync uses `INSERT ... ON CONFLICT (activity_id) DO NOTHING` to
  atomically skip duplicates and derive inserted/skipped counts from
  the row count returned by the database.

## Data Volume Assumptions

- Single athlete, manual sync. Expected scale: hundreds to low
  thousands of activities.
- JSONB column size: a typical Strava summary is 1–3 KB; a detail
  is 5–20 KB.

## Flyway Migration Plan

### V1__create_activity_tables.sql

```sql
CREATE TABLE activity_summaries (
    activity_id BIGINT PRIMARY KEY,
    start_date  TIMESTAMPTZ NOT NULL,
    summary_json JSONB NOT NULL
);

CREATE INDEX idx_summaries_start_date
    ON activity_summaries (start_date DESC);

CREATE TABLE activity_details (
    activity_id BIGINT PRIMARY KEY,
    start_date  TIMESTAMPTZ NOT NULL,
    detail_json JSONB NOT NULL
);
```

## Timestamp Handling

- Strava returns `start_date` as an ISO-8601 string
  (e.g. `2018-05-02T12:15:09Z`). This is parsed and stored as
  `TIMESTAMPTZ` in UTC.
- Query parameters `after` and `before` are Unix epoch seconds
  (integers). The service converts them to `Instant` / UTC timestamps
  for database filtering.
- Filtering uses exclusive boundaries:
  `start_date > :after AND start_date < :before`.
