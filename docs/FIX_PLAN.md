# Fix Plan — querydslbuilder

Companion to `REVIEW.md`. Plan to land **all** findings (Critical → nit) under the constraint of **no wire-format breaking changes** (split DTOs, response-code changes, endpoint shape changes are deferred to a future v2). Library extraction included.

## Ground rules

- **Branching:** one branch per phase, PR-sized. Phase 1 (security) ships first and alone — no other refactors mixed in.
- **Tests:** every fix gets a regression test that would have caught the original bug. No comprehensive new test suite, but coverage will grow organically as fixes land.
- **Deps:** new dependencies allowed (Bucket4j, OpenPDF). No Testcontainers — H2 stays as the test DB.
- **No wire-format breaks:** internal behavior changes (e.g., adding `@PreAuthorize` that returns 403 to a previously-allowed call) are fine; changing response codes, DTO shapes, or endpoint paths is not. Items needing wire breaks are deferred to **§Phase 6 (future v2)**.
- **Package stability under library extraction:** `querydsl.query.*` packages stay the same name even after moving to the subproject; only the Gradle module boundary changes. Downstream imports keep working.

---

## Phase 1 — 🔴 Critical security (hot-fix branch, ship alone)

**Goal:** unblock production deploy. ~2-3 days. Single PR `security/critical-1`.

| # | File:line | Change | Test |
|---|---|---|---|
| 1.1 | `export/ExportService.java:115-161` | Remove private-field reflection fallback. Only call public getters. Then layer on a per-entity exportable-field whitelist registry (`ExportableFieldRegistry`) populated by a marker on entity classes (`@Exportable(fields = {...})`) or a service-level allow-list passed by each controller. | `ExportServiceTest`: requesting `selectedColumns: ["password"]` returns empty / 400. |
| 1.2 | `query/QueryPredicateBuilder.java:567-616` | Add missing switch cases: `NOT_STARTS_WITH`, `NOT_STARTS_WITH_IGNORE_CASE`, `NOT_ENDS_WITH`, `NOT_ENDS_WITH_IGNORE_CASE`. Each calls the positive predicate then `.not()`. | `QueryPredicateBuilderTest`: each new operation builds a correct predicate against `QUser.firstName`. |
| 1.3 | `query/QueryPredicateBuilder.java:567-616` and helpers `:888-1217` | Replace silent-null returns with `throw new QueryException("Operation X not supported on field type of Y")`. Update `buildPredicate` loop at `:131-136` to not filter nulls anymore. | Test: `CONTAINS` on numeric `id` → 400 with informative message, not "match all". |
| 1.4 | `application.properties:48` and `security/JwtTokenProvider.java:32` | Remove both default values. Add startup validation: secret must be ≥ 32 bytes (256 bits HS256). Throw `IllegalStateException` on bean init if missing or too short. Document `JWT_SECRET` in `docs/GETTING_STARTED.md` as required. | Boot test asserts startup fails if `JWT_SECRET` unset. |
| 1.5 | `service/UserService.java`, `service/RoleService.java`, `service/PermissionService.java` | Add `@PreAuthorize` to all write methods. Rules: `addUser/updateUser/deleteUser/changeUserStatus` → `hasRole('ADMIN') or (#id == authentication.principal.id and only profile fields)`. Role assignment in `addUser`/`updateUser` → restrict to ADMIN. `addRole/updateRole/deleteRole` → `hasRole('ADMIN')` (currently MANAGER can too — narrow it). `addPermission/updatePermission/deletePermission` → `hasRole('ADMIN')` (already URL-gated, belt-and-braces). | Test per method: USER calling `updateUser(otherId, ...)` → 403. ADMIN → 200. |
| 1.6 | `security/CustomUserDetailsService.java:32` | Add `@Transactional(readOnly = true)`. Add `@EntityGraph(attributePaths = {"role", "role.permissions"})` to `UserRepository.findByEmail` (new method) and call that here. | Test: disable OSIV (`spring.jpa.open-in-view=false` in `application-test.properties`), assert login still works. |

**Phase 1 done = critical findings closed.** Merge, deploy.

---

## Phase 2 — Library extraction (architectural, alone)

**Goal:** make the `generic-querydsl` claim real. ~3-5 days. Single PR `refactor/library-extraction`. No behavior changes — pure file moves and module split.

### 2.1 Subproject layout

```
querydslbuilder/                 (root, becomes the demo app)
  build.gradle                   ← depends on :generic-querydsl
  settings.gradle                ← already includes :generic-querydsl
  src/main/java/querydsl/
    QuerydslbuilderApplication.java
    config/      controller/     dto/         mapper/
    model/       service/        repository/  security/
    export/      exception/      query/computed/  ← demo computed handlers stay here
generic-querydsl/                (the library)
  build.gradle                   ← Spring Boot starter + QueryDSL
  src/main/java/querydsl/
    query/                       ← QueryRequest, QueryCondition, QueryOperation, SortField, InvalidFieldException, QueryPredicateBuilder
    query/computed/              ← TypedComputedFieldHandler, ComputedFieldHandlerRegistry
    repository/GenericQueryRepository.java
    service/GenericQueryService.java
    exception/QueryException.java   ← used by the engine
  src/main/resources/META-INF/spring/
    org.springframework.boot.autoconfigure.AutoConfiguration.imports
                                 ← auto-config for QueryPredicateBuilder, registry, service
```

### 2.2 Steps

1. Create `generic-querydsl/build.gradle` declaring it as a Spring Boot library (not bootJar, no @SpringBootApplication). Mirror the QueryDSL annotation processor wiring from the root `build.gradle:50-68` — the library generates its own Q-classes is irrelevant; consumers generate Q-classes for their entities.
2. Move the files listed in 2.1 from `src/main/java/querydsl/...` to `generic-querydsl/src/main/java/querydsl/...`. **Package names stay identical** — no import churn in the demo.
3. Delete `ComputedFieldHandler.java` (untyped variant, §6.7). Only `TypedComputedFieldHandler` is kept.
4. Drop the `ComputedFieldHandler` references in `ComputedFieldHandlerRegistry.java:39-62` (the `setHandlers` autowire).
5. Create `GenericQuerydslAutoConfiguration` exposing `QueryPredicateBuilder`, `ComputedFieldHandlerRegistry`, `GenericQueryService` as `@Bean`s. Use `@ConditionalOnMissingBean` so consumers can override.
6. Root `build.gradle` adds `implementation project(':generic-querydsl')`.
7. Delete `untyped` handler interface from the registry; reduce `buildPredicate` to typed-only path (§6.7 closed simultaneously).

### 2.3 Kill the static singleton (§4.6)

`QueryPredicateBuilder.getInstance()` and `setApplicationContext` get deleted. `GenericQueryRepository` default methods need access to the builder. Two options:

**Option A (clean, recommended):** Spring Data custom base repository.
- Create `GenericQueryRepositoryImpl<T, ID>` as the implementation backed by a constructor-injected `QueryPredicateBuilder`.
- Register via a custom `RepositoryFactoryBean`: `EnableJpaRepositories(repositoryBaseClass = GenericQueryRepositoryImpl.class)` in `GenericQuerydslAutoConfiguration`.
- The default methods on the interface become abstract / no-op; the implementation does the work with proper DI.

**Option B (minimal):** keep `getInstance()`, mark the field `volatile`, drop the lazy `applicationContext.getBean` fallback (which is unreachable in practice). This is the lazy version of the fix.

→ **Use Option A** since we're doing extraction anyway. Sets a clean precedent for the library.

### 2.4 Settings.gradle

Remove no-op; `include 'generic-querydsl'` is now real. Verify `gradlew build` from a clean checkout works.

### 2.5 README

Add a snippet showing how a third-party Spring Boot project adds the dependency and gets the engine wired automatically.

**Phase 2 done = §4.6, §4.8, §6.6, §6.7 closed.**

---

## Phase 3 — 🟠 High-severity functional & operational

**Goal:** correctness + observability. ~1 week. Single PR `feat/operational-hardening`.

| # | Finding | Change | Test |
|---|---|---|---|
| 3.1 | §4.1 `AuditorAware` missing | Add `SecurityAuditorAware` bean returning the authenticated principal name (or `"system"` for unauthenticated paths like Flyway). Register in `JpaAuditingConfig`. | Test: create a User via authenticated request, assert `createdBy = "admin@system.com"`. |
| 3.2 | §4.2 No rate limiting on `/auth/login` | Add Bucket4j as a Spring filter ahead of `/auth/login`. 5 attempts per minute per IP, sliding window. Returns 429 with `Retry-After`. Add config in `application.properties`. | Test: 6th request within 60s returns 429. |
| 3.3 | §4.3 24h JWT, no revocation | Reduce default `jwt.expiration` to 900_000 (15 min). Add `jwt.refresh-expiration=604800000` (7 days) and a `/auth/refresh` endpoint that takes a refresh token. **Note: adding `/auth/refresh` is additive — not breaking.** Store refresh tokens hashed in a new `refresh_tokens` table (Flyway `V3__Create_refresh_tokens.sql`). On logout, delete the row → revocation. | Test: refresh flow happy path; revoked refresh token → 401. |
| 3.4 | §4.4 Mapper does DB lookups | Move role/permission resolution from `UserMapper` / `RoleMapper` into `UserService` / `RoleService`. Mappers become pure transformation. Delete `mapstruct` deps from `build.gradle:38,41` — they're unused; or generate MapStruct mappers (decision: **delete deps** since we already have hand-rolled ones working). | Mapper test runs without any repository mocks. |
| 3.5 | §4.5 N+1 on `/user` listing | Add `@EntityGraph(attributePaths = "role")` to `UserRepository.findAll(Pageable)` (override). For the QueryDSL path, override `findAllByQueryRequest` in `UserRepository` with an explicit `fetchJoin()` — accept some duplication for clarity. | Test with N+1-detector (`@DataJpaTest` + Hibernate statistics): one query per page. |
| 3.6 | §4.7 Timezone stripping | Replace `removeTimezoneIndicators` (`QueryPredicateBuilder.java:745-771`) with proper parsing: try `OffsetDateTime.parse`, convert to UTC, then to `LocalDateTime` if the target is `LocalDateTime`. Document that the engine assumes columns are UTC. | Test: `"2025-11-01T00:00:00+03:00"` parses to UTC equivalent. |
| 3.7 | §4.9 `changeUserStatus` race & semantics | **Keep toggle (no wire change)**, add `@PreAuthorize("hasRole('ADMIN')")`, document the race in Javadoc. Track as v2 item. | Test: USER role calling endpoint → 403. |
| 3.8 | §4.10 Missing `DataIntegrityViolationException` handler | Add `@ExceptionHandler(DataIntegrityViolationException.class)` in `GlobalExceptionHandler` → 409 CONFLICT with a parseable message. Inspect `getMostSpecificCause()` to extract the violated constraint name; map common ones (`users_email_key`, `roles_name_key`) to friendly messages. | Test: create two users with same email → 409, not 500. |

---

## Phase 4 — 🟡 Medium hardening

**Goal:** robustness and policy. ~1 week. Single PR `feat/medium-hardening`.

| # | Finding | Change |
|---|---|---|
| 4.1 | §5.1 `isActive=false` permissions still effective | In `CustomUserDetailsService.getAuthorities`, filter `permission.isActive` and check `user.role.isActive`. If role inactive → throw `DisabledException` ("Role is inactive"). |
| 4.2 | §5.2 Sort fields unbounded | Add `@SortableFields({"createdDate", "email", "isActive", "role.name"})` annotation to entities. `GenericQueryService.applySortingFromQueryRequest` (`:168-190`) reads the annotation, rejects unlisted fields with 400. Default whitelist = `["id", "createdDate"]` if annotation absent. |
| 4.3 | §5.3 Empty `NOT_IN` inconsistent | `buildNotInPredicate` with empty list returns `Expressions.TRUE` (always true) — explicit instead of accidentally-null. Document the semantics. |
| 4.4 | §5.4 `MAX_IN_VALUES` too high | Lower to 200. Make it configurable via `app.query.max-in-values`. |
| 4.5 | §5.5 Incomplete type conversion | Add `BigDecimal`, `BigInteger`, `UUID`, `Instant`, `OffsetDateTime`, `ZonedDateTime`, `LocalTime`. Enum lookup: try exact match first, then case-insensitive fallback. Split into a strategy table (Map<Class, Function<String,Object>>) — easier to extend. |
| 4.6 | §5.6 `validateToken` swallows exceptions | Catch specific JJWT exceptions: `ExpiredJwtException`, `SignatureException`, `MalformedJwtException`, `UnsupportedJwtException`. Log severity matters: expired → INFO, signature mismatch → WARN (possible forgery attempt), malformed → DEBUG. |
| 4.7 | §5.8 Debug logging in prod | In `logback-spring.xml`, move the `querydsl` and `org.springframework.security` DEBUG loggers under the `dev` profile. `prod` profile gets INFO for everything. `spring.jpa.show-sql=false` and add `application-prod.properties` to override. |
| 4.8 | §5.9 `Page<DTO>` serialization | Introduce `dto/PageResponse<T>` wrapper (`content`, `page`, `size`, `totalElements`, `totalPages`, `last`). All controllers return `PageResponse<DTO>` instead of `Page<DTO>`. **No wire change** — `Page`'s JSON has these same fields by default, so the wire payload stays compatible. Just makes the type stable across Spring upgrades. |
| 4.9 | §5.10 Export validation | `ExportRequest` and `ExportWithQueryRequest`: add `@Size(min=1, max=100) List<String> selectedColumns`, `@Pattern(regexp="EXCEL\|PDF\|excel\|pdf") String format`. **Null `format` stays defaulting to PDF** (compat), but a non-null invalid value rejects with 400. Add `@Valid` enforcement check. |
| 4.10 | §5.11 Password strength | Add `@Size(min=8, max=128)` and `@Pattern` (at least one digit + one letter) to `UserDto.password` for create flow only. Implementation: introduce a `@PasswordRule` validation group, apply only on POST. Existing-user updates that don't include password still work. |
| 4.11 | §5.14 CORS hard-coded | Move `allowed-origins` to `application.properties` (`cors.allowed-origins=http://localhost:3000,...`). `application-prod.properties` overrides empty (deny by default in prod until explicitly set). |
| 4.12 | §5.15 CSP breaks Swagger | Test in browser; if Swagger loads inline scripts, switch to `default-src 'self' 'unsafe-inline'` only for `/swagger-ui/**` and `/v3/api-docs/**` paths via a path-specific header. Or simpler: skip CSP entirely on those paths. |
| 4.13 | §5.16 No `created_date` index | Flyway `V4__Add_created_date_indexes.sql` adds B-tree indexes on `users.created_date DESC`, `roles.created_date DESC`, `permissions.created_date DESC`. |
| 4.14 | §5.17 FK `ON DELETE` strategy | Flyway `V5__Add_fk_ondelete.sql`: change `fk_user_role` to `ON DELETE SET NULL` (so deleting a role doesn't 500-fail). Service layer: `deleteRole` checks user count first, throws domain error if any. |
| 4.15 | §5.18 `nationalId` PII | Add Hibernate `@Convert` with AES-GCM converter for `User.nationalId`. Key from `app.encryption.key` env var, decrypted on read. Index becomes useless for direct equality — replace with HMAC-based deterministic lookup column `national_id_hash` (queryable) keeping `national_id` encrypted (display-only). Flyway `V6__Encrypt_national_id.sql` adds the hash column and backfills. **This is the heaviest item in Phase 4 — could split into its own PR.** |

---

## Phase 5 — 🟢 LOW / nits cleanup

**Goal:** polish. ~3-4 days. One PR `chore/cleanup-nits`. Mostly mechanical.

| # | Finding | Action |
|---|---|---|
| 5.1 | §6.1 Dot counter twice | One-line cleanup in `JwtTokenProvider.java:114`. |
| 5.2 | §6.2 Javadoc malformed `<li>` | Fix `InvalidFieldException.java:9-10`. |
| 5.3 | §6.3 "depth" misnamed | Rename `MAX_FIELD_PATH_DEPTH` → `MAX_FIELD_PATH_SEGMENTS`. |
| 5.4 | §6.4 Q-entity lowercase fallback | Delete the fallback in `QueryPredicateBuilder.loadQEntity:363-378`. Standard QueryDSL only. |
| 5.5 | §6.5 `MAX_CONDITIONS` duplicated | Define once in `QueryPredicateBuilder` as `public static final`. Reference from `@Size` via SpEL or just keep numeric duplication with a comment pointing at the source of truth. (Bean validation annotations require compile-time constants → use the constant.) |
| 5.6 | §6.6 `findAllByQueryRequestWithProjection` `@Deprecated` | Delete. Nothing references it. |
| 5.7 | §6.7 Untyped `ComputedFieldHandler` | Already deleted in Phase 2.3. |
| 5.8 | §6.8 Duplicated switches in handlers | Extract `StringFieldOperationDispatcher` with `dispatch(StringExpression expr, QueryCondition cond, QueryOperation default)`. Each handler shrinks to one line. |
| 5.9 | §6.9 `UserFullNameHandler` default `CONTAINS_IGNORE_CASE` | Change to `EQUALS` for consistency with siblings. (Behavior change for callers omitting `operation` — document, but it's the safer/more-predictable default.) |
| 5.10 | §6.10 Handler default→eq silently | After 5.8 dispatcher exists, throw `QueryException("Operation X not supported for field Y")`. |
| 5.11 | §6.11 `new ObjectMapper()` in SecurityConfig | Inject the Spring-managed `ObjectMapper` bean. |
| 5.12 | §6.12 Error JSON shape inconsistent | Use `ErrorResponse` in the two `AuthenticationEntryPoint`/`AccessDeniedHandler` lambdas. |
| 5.13 | §6.13 `isActive = true` initializer | Move default to service-layer (`if (dto.getIsActive() == null) entity.setIsActive(true);`). Remove field initializer to avoid Jackson surprise. |
| 5.14 | §6.14 Silent null password | `UserService.addUser` throws `ValidationException("password is required")` if null/blank. (Wire-internal change — DTO already accepts null today, but server rejects properly.) |
| 5.15 | §6.15 `mobileNumber` no `@Pattern` | Add `@Pattern(regexp = "\\+?[0-9 -]{6,20}")` — permissive E.164-ish. |
| 5.16 | §6.16 Email not normalized | Lowercase email in `UserService.addUser`/`updateUser` before save. |
| 5.17 | §6.17 Authorities CSV in login response | Keep — useful for frontends that want to render UI based on authorities without decoding JWT. Document. (No action — false alarm in REVIEW.) |
| 5.18 | §6.18 `autoSizeColumn` slow | Add `if (data.size() < 10_000) sheet.autoSizeColumn(...)` guard, otherwise set fixed width. |
| 5.19 | §6.19 iText 2.1.7 → OpenPDF | Replace `com.lowagie:itext:2.1.7` with `com.github.librepdf:openpdf:2.0.3` in `build.gradle`. API is largely drop-in; verify imports in `PdfExporter.java`. |
| 5.20 | §6.20 Filename uses local date | `LocalDate.now(ZoneOffset.UTC)` in `ExportService.java:43`. |
| 5.21 | §6.21 Health detail leak | `application-prod.properties`: `management.endpoint.health.show-components=when-authorized` (not `always`). |
| 5.22 | §6.22 `LOG_FILE` defaults to temp dir | Set `LOG_PATH=./logs` in `application.properties` (already gitignored by `logs/` entry). |
| 5.23 | §6.23 PostgreSQL version | Update to current stable 42.7.5 (or whatever's latest at the time). |
| 5.24 | §6.24 iText | Folded into 5.19. |
| 5.25 | §6.25 Missing `@Tag` | Add `@Tag(name = "Users")`, `@Tag(name = "Roles")`, `@Tag(name = "Permissions")` to the three controllers. |
| 5.26 | §6.26 Tests | Covered by per-fix regression tests in Phases 1–5. |
| 5.27 | §6.27 Entity `equals`/`hashCode` | Add Lombok `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` to `BaseEntity` + `@EqualsAndHashCode.Include` on a stable business key per entity (`User.email`, `Role.name`, `Permission.name`). |
| 5.28 | §6.28 Implicit package scan | Add explicit `@EntityScan("querydsl.model")` and `@EnableJpaRepositories("querydsl.repository")` to `QuerydslbuilderApplication`. Necessary anyway after Phase 2 (the library has its own scan via auto-config). |
| 5.29 | §6.29 Doc maintenance | No action this phase. Track for v2. |

---

## Phase 6 — Deferred to future v2 (breaking changes)

Documented so they don't get lost. Not addressed in Phases 1-5.

| # | Finding | Reason deferred |
|---|---|---|
| 6.1 | §5.12 Split create/update DTOs | Wire-breaking. v2 introduces `UserCreateRequest` / `UserUpdateRequest`. |
| 6.2 | §5.13 `POST /user` → 201 Created with `Location` | Wire-breaking. |
| 6.3 | §4.9 `changeUserStatus` accepts a body | Wire-breaking (current is body-less PUT). v2 sends `{"isActive": false}`. |
| 6.4 | Pagination response wrapper rename | If §4.8 PageResponse adopts a different shape, that's v2-only. The compat shim in Phase 4 keeps the wire payload identical. |
| 6.5 | Endpoint plurals | `/user` → `/users`, `/role` → `/roles`, `/permission` → `/permissions` (REST convention). v2. |

---

## Risk + sequencing summary

| Phase | PR | Risk | Blocking? | Effort |
|---|---|---|---|---|
| 1 | `security/critical-1` | Low (additive + bug fixes) | Yes — deploy blocker | 2-3 days |
| 2 | `refactor/library-extraction` | Medium (large refactor, no behavior change) | No, but precedes 4.6 cleanup | 3-5 days |
| 3 | `feat/operational-hardening` | Low-medium | No | 1 week |
| 4 | `feat/medium-hardening` | Medium (encryption change in 4.15 is heaviest) | No — consider splitting 4.15 out | 1 week |
| 5 | `chore/cleanup-nits` | Low | No | 3-4 days |
| **Total** | **5 PRs** | | | **~3-4 weeks** |

---

## What I'd want to know before starting

A handful of decisions that affect implementation details — none of them blockers, but worth choosing now:

1. **Sort-field whitelist mechanism (Phase 4.2):** annotation on entity vs. service config bean vs. property file? My default is the annotation.
2. **Encryption KMS (Phase 4.15):** env-var key (simple, what the plan assumes) vs. AWS KMS / Vault / Azure Key Vault (production-grade). Affects whether 4.15 fits in this scope.
3. **Refresh-token storage (Phase 3.3):** SQL table (what I'm assuming) vs. Redis (lower latency, requires infra). Affects deps.
4. **Phase 4.15 — keep it in scope or punt?** It's by far the biggest item in Phase 4 and could double the phase's duration.

Want me to start on Phase 1 now, or would you like to adjust the plan first?
