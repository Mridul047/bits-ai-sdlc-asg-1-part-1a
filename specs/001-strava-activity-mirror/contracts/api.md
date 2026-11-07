# API Contracts: Strava Activity Mirror

**Spec**: [../spec.md](../spec.md) | **Data Model**: [../data-model.md](../data-model.md)

## Base URL

`http://localhost:8080`

## Common Error Response

All error responses share this shape (FR-021, FR-022):

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Human-readable description of the problem"
}
```

The `code` field is always one of the values in the error code table.
The response body MUST NOT contain the Strava access token or Strava's
raw response body.

### Error Code Table (FR-023)

| Code                 | HTTP Status | Condition                        |
|----------------------|-------------|----------------------------------|
| `VALIDATION_ERROR`   | 400         | Invalid input parameters         |
| `STRAVA_AUTH_ERROR`  | 401         | Strava rejected the token        |
| `ACTIVITY_NOT_FOUND` | 404         | Activity does not exist on Strava|
| `STRAVA_RATE_LIMITED`| 429         | Strava rate-limiting the request |
| `DATABASE_ERROR`     | 500         | Local database failure           |
| `STRAVA_SERVER_ERROR`| 502         | Strava returned 5xx              |
| `STRAVA_TIMEOUT`     | 504         | Strava did not respond in time   |

---

## POST /athlete/activities/sync

Syncs activity summaries from Strava into the local database.
Makes exactly one call to Strava per request (FR-002).

### Query Parameters

| Name       | Type    | Required | Default | Constraints                  |
|------------|---------|----------|---------|------------------------------|
| `after`    | integer | No       | —       | Non-negative epoch seconds   |
| `before`   | integer | No       | —       | Non-negative epoch seconds   |
| `page`     | integer | No       | 1       | Positive integer (≥ 1)       |
| `per_page` | integer | No       | 30      | Positive integer, 1–200      |

When both `after` and `before` are provided, `after` MUST be strictly
less than `before` (exclusive boundaries).

### Success Response — 200 OK

```json
{
  "total": 5,
  "stored": 3,
  "skipped": 2
}
```

| Field     | Type | Description                              |
|-----------|------|------------------------------------------|
| `total`   | int  | Activities returned by Strava            |
| `stored`  | int  | Newly inserted into the database         |
| `skipped` | int  | Already existed, left unchanged          |

### Error Responses

- **400** `VALIDATION_ERROR` — invalid parameters (Strava not called)
- **401** `STRAVA_AUTH_ERROR` — Strava rejected the token
- **429** `STRAVA_RATE_LIMITED` — Strava rate-limiting
- **500** `DATABASE_ERROR` — database write failed (nothing stored)
- **502** `STRAVA_SERVER_ERROR` — Strava returned 5xx
- **504** `STRAVA_TIMEOUT` — Strava did not respond

---

## GET /athlete/activities

Lists stored activity summaries from the local database.
Never calls Strava (FR-012).

### Query Parameters

| Name       | Type    | Required | Default | Constraints                  |
|------------|---------|----------|---------|------------------------------|
| `after`    | integer | No       | —       | Non-negative epoch seconds   |
| `before`   | integer | No       | —       | Non-negative epoch seconds   |
| `page`     | integer | No       | 1       | Positive integer (≥ 1)       |
| `per_page` | integer | No       | 30      | Positive integer, 1–200      |

Filtering uses exclusive boundaries:
`start_date > after AND start_date < before`.

### Success Response — 200 OK

```json
{
  "activities": [
    {
      "activity_id": 12345678,
      "start_time": "2024-01-15T08:30:00Z",
      "summary": { /* complete Strava summary object */ }
    }
  ],
  "total_count": 42,
  "page": 1,
  "per_page": 30,
  "has_next": true
}
```

| Field         | Type    | Description                               |
|---------------|---------|-------------------------------------------|
| `activities`  | array   | Activity items, newest-first              |
| `total_count` | int     | Total matching activities across all pages|
| `page`        | int     | Current page number                       |
| `per_page`    | int     | Page size used                            |
| `has_next`    | boolean | Whether more pages exist                  |

Each activity item:

| Field         | Type   | Description                        |
|---------------|--------|------------------------------------|
| `activity_id` | long   | Strava activity ID                 |
| `start_time`  | string | ISO-8601 UTC timestamp             |
| `summary`     | object | Complete Strava summary JSON as-is |

Ordering: newest `start_time` first; ties broken by descending
`activity_id` (FR-009).

### Error Responses

- **400** `VALIDATION_ERROR` — invalid parameters
- **500** `DATABASE_ERROR` — database read failed

---

## GET /activities/{id}

Gets a single activity's detailed representation. Local-first: returns
stored detail if available, otherwise fetches from Strava and stores it
(FR-015, FR-016).

### Path Parameters

| Name | Type | Required | Constraints            |
|------|------|----------|------------------------|
| `id` | long | Yes      | Positive integer (≥ 1) |

### Success Response — 200 OK

```json
{
  "activity_id": 12345678,
  "start_time": "2024-01-15T08:30:00Z",
  "detail": { /* complete Strava detail object */ }
}
```

| Field         | Type   | Description                       |
|---------------|--------|-----------------------------------|
| `activity_id` | long   | Strava activity ID                |
| `start_time`  | string | ISO-8601 UTC timestamp            |
| `detail`      | object | Complete Strava detail JSON as-is |

### Error Responses

- **400** `VALIDATION_ERROR` — non-numeric or non-positive ID
- **401** `STRAVA_AUTH_ERROR` — Strava rejected the token
- **404** `ACTIVITY_NOT_FOUND` — activity not found on Strava
- **429** `STRAVA_RATE_LIMITED` — Strava rate-limiting
- **500** `DATABASE_ERROR` — database failure
- **502** `STRAVA_SERVER_ERROR` — Strava returned 5xx
- **504** `STRAVA_TIMEOUT` — Strava did not respond

---

## Strava API Calls (outbound)

### GET https://www.strava.com/api/v3/athlete/activities

Called by the sync endpoint. Parameters passed through as-is.

**Headers**: `Authorization: Bearer {token}`

### GET https://www.strava.com/api/v3/activities/{id}

Called by the get-detail endpoint when no local detail exists.
The `include_all_efforts` parameter is NOT sent (out of scope).

**Headers**: `Authorization: Bearer {token}`

**Client configuration** (Principle VII):
- Connect timeout: 3 seconds
- Read timeout: 10 seconds
