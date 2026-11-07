# Research: Strava Activity Mirror

**Date**: 2026-11-07 | **Plan**: [plan.md](plan.md)

## R-1: Spring Boot 4 Starters and Migration

**Decision**: Use Spring Boot 4.0.4 with per-technology starters.

**Rationale**: Boot 4 renamed and split several starters. The
catch-all `spring-boot-starter-test` is now
`spring-boot-starter-test-classic` and deprecated. Per-technology
starters are the recommended approach.

**Starter mapping** (groupId: `org.springframework.boot`):

| Purpose               | Boot 3 artifact                    | Boot 4 artifact                          |
|-----------------------|------------------------------------|------------------------------------------|
| Web MVC (REST)        | `spring-boot-starter-web`          | `spring-boot-starter-webmvc`             |
| RestClient            | (in spring-web)                    | `spring-boot-starter-restclient`         |
| Spring Data JPA       | `spring-boot-starter-data-jpa`     | `spring-boot-starter-data-jpa`           |
| Flyway                | `flyway-core`                      | `spring-boot-starter-flyway`             |
| Validation            | `spring-boot-starter-validation`   | `spring-boot-starter-validation`         |
| RestClient test       | N/A                                | `spring-boot-starter-restclient-test`    |
| Flyway test           | N/A                                | `spring-boot-starter-flyway-test`        |
| WebMVC test           | (in starter-test)                  | `spring-boot-starter-webmvc-test`        |
| JPA test              | (in starter-test)                  | `spring-boot-starter-data-jpa-test`      |

**Alternatives considered**: Using the deprecated
`spring-boot-starter-test-classic` — rejected per constitution
Principle I (simplicity) and user requirement to avoid deprecated
starters.

## R-2: Jackson 3 Package Changes

**Decision**: Use Jackson 3 with `tools.jackson` packages.

**Rationale**: Jackson 3 (managed by Boot 4 BOM) moved from
`com.fasterxml.jackson` to `tools.jackson`. Annotations retain
`com.fasterxml.jackson.annotation` for backward compatibility.

**Key renames**:
- `@JsonComponent` → `@JacksonComponent`
- `@JsonMixin` → `@JacksonMixin`
- `Jackson2ObjectMapperBuilderCustomizer` → `JsonMapperBuilderCustomizer`
- Properties: `spring.jackson.json.read/write.*`

**Alternatives considered**: Pinning Jackson 2 — rejected, as Boot 4
manages Jackson 3 and mixing versions causes classpath conflicts.

## R-3: RestClient Timeout Configuration

**Decision**: Configure RestClient with `ClientHttpRequestFactoryBuilder`
for programmatic timeout control.

**Rationale**: The dedicated `spring-boot-starter-restclient` provides
auto-configuration. Timeouts can be set via:
- Properties: `spring.http.client.connect-timeout=3s`,
  `spring.http.client.read-timeout=10s`
- Programmatic: `ClientHttpRequestFactoryBuilder` on the
  `RestClient.Builder` for explicit control per-client.

Programmatic configuration is preferred because the Strava client is
the only HTTP client and the timeouts are constitutional requirements
(Principle VII) that must not be accidentally overridden.

**Alternatives considered**: Using `spring.http.client.*` properties —
rejected because they apply globally and can't distinguish between
different clients if more are added later.

## R-4: Strava API Parameter Verification

**Decision**: Use parameter names exactly as in Strava's swagger.

**Verified from** `https://developers.strava.com/swagger/swagger.json`:

| Endpoint                  | Parameter          | Type    | Default | Max          |
|---------------------------|--------------------|---------|---------|--------------|
| GET /athlete/activities   | `before`           | integer | —       | —            |
| GET /athlete/activities   | `after`            | integer | —       | —            |
| GET /athlete/activities   | `page`             | integer | 1       | —            |
| GET /athlete/activities   | `per_page`         | integer | 30      | Not in swagger |
| GET /activities/{id}      | `id` (path)        | int64   | —       | —            |
| GET /activities/{id}      | `include_all_efforts` | boolean | —    | —            |

**Notes**:
- The swagger does not specify a maximum for `per_page`. The 200 limit
  is documented in Strava's API docs outside the swagger. We enforce
  max 200 per the spec (FR-006, FR-010).
- `include_all_efforts` exists but is explicitly out of scope.
- Auth: OAuth 2.0 Bearer token in Authorization header.

## R-5: Testcontainers and WireMock

**Decision**: Use Testcontainers 2.0 (managed by Boot 4) for PostgreSQL
and WireMock for Strava stub.

**Rationale**: Boot 4 manages Testcontainers 2.0 with
`@ServiceConnection` support. WireMock is the standard HTTP stub
library and integrates well with JUnit 5.

**Dependencies**:
- `org.testcontainers:postgresql` (managed version, test scope)
- `org.wiremock:wiremock-standalone` (test scope)

## R-6: Harness Tool Versions

**Decision**: Use the following plugin/tool versions, all confirmed
compatible with Java 21 and Spring Boot 4.0.4.

| Tool              | Version | Plugin artifact                          |
|-------------------|---------|------------------------------------------|
| Checkstyle        | 10.21   | `maven-checkstyle-plugin:3.6.0`         |
| SpotBugs          | 4.9.1   | `spotbugs-maven-plugin:4.9.1.0`         |
| Find Security Bugs| 1.13.0  | `com.h3xstream.findsecbugs:findsecbugs-plugin` |
| JaCoCo            | 0.8.12  | `jacoco-maven-plugin:0.8.12`            |
| Gitleaks          | 8.21    | CLI binary (not a Maven plugin)          |

**Alternatives considered**: Spotless instead of Checkstyle — both are
viable. Checkstyle is chosen for its widely understood configuration
format and Maven plugin maturity.

## R-7: Key Framework Versions (Boot 4.0.4 BOM-managed)

| Dependency          | Version  |
|---------------------|----------|
| Spring Framework    | 7.0      |
| Hibernate ORM       | 7.2      |
| Hibernate Validator  | 9.0      |
| Flyway              | 11.11    |
| Testcontainers      | 2.0      |
| Mockito             | 5.20     |
| JUnit 5             | 5.12     |
| Jakarta Validation  | 3.1      |
| Jakarta Persistence | 3.2      |
| Tomcat              | 11.0     |
