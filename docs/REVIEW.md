# Forensic Review — querydslbuilder

Audience: the engineer who wrote it. Direct, no padding. Severity scale: **🔴 CRITICAL** (fix before any deploy), **🟠 HIGH** (fix soon, exploitable or load-bearing), **🟡 MEDIUM** (real but bounded), **🟢 LOW / nit** (polish). File:line references throughout.

---

## 0. Reconciliation status (2026-05-20)

> **The body of this review below describes the codebase BEFORE the Phase 1–5 hardening commits** (`5864431` → `15286f0`) and the engine extraction (`3eba1e0`). It has now been reconciled against the current code. Each finding carries a `> **STATUS**` line. File:line references in the *original* prose are pre-extraction; the STATUS lines give current locations (the engine moved to `generic-querydsl/src/main/java/querydsl/…`).
>
> **Tally: 52 FIXED · 0 PARTIAL · 1 OPEN (of 53).** (4.9, 5.9, 5.13 closed 2026-05-20; only 6.29 docs-process remains.)

| # | Finding | Status |
|---|---|---|
| 3.1 | Password leak via export | ✅ FIXED |
| 3.2 | NOT_STARTS_WITH/NOT_ENDS_WITH silently dropped | ✅ FIXED |
| 3.3 | Type-incompatible ops match all rows | ✅ FIXED |
| 3.4 | Hard-coded JWT secret default | ✅ FIXED |
| 3.5 | No method-level authz | ✅ FIXED |
| 3.6 | Lazy collection without transaction | ✅ FIXED |
| 4.1 | AuditorAware missing | ✅ FIXED |
| 4.2 | No login rate limiting | ✅ FIXED |
| 4.3 | 24h JWT, no refresh/revocation | ✅ FIXED (refresh infra added; access TTL default still 24h — tune via env) |
| 4.4 | Mappers do DB lookups / dead MapStruct dep | ✅ FIXED |
| 4.5 | N+1 on /user listing | ✅ FIXED |
| 4.6 | DCL singleton without volatile | ✅ FIXED |
| 4.7 | Timezone discarded on date parse | ✅ FIXED |
| 4.8 | generic-querydsl subproject didn't exist | ✅ FIXED |
| 4.9 | change-status bodyless toggle + no authz | ✅ FIXED (authz + absolute idempotent state) |
| 4.10 | No DataIntegrityViolation handler | ✅ FIXED |
| 5.1 | Inactive roles/permissions still effective | ✅ FIXED |
| 5.2 | Sort fields not whitelisted | ✅ FIXED |
| 5.3 | Empty NOT_IN inconsistency | ✅ FIXED |
| 5.4 | MAX_IN_VALUES=1000 too high | ✅ FIXED (→200) |
| 5.5 | convertValueToFieldType incomplete | ✅ FIXED |
| 5.6 | validateToken swallows all exceptions | ✅ FIXED |
| 5.7 | Hand-rolled mappers + unused MapStruct | ✅ FIXED (MapStruct dropped, hand-rolled kept) |
| 5.8 | DEBUG logging by default | ✅ FIXED |
| 5.9 | Page<…> serialization unstable | ✅ FIXED (PageResponse<T> record) |
| 5.10 | Export DTOs unvalidated | ✅ FIXED |
| 5.11 | Password no min length/strength | ✅ FIXED |
| 5.12 | Same DTO for create & update | ✅ FIXED (validation groups) |
| 5.13 | addUser returns void / 200 not 201 | ✅ FIXED (201 + Location + body) |
| 5.14 | CORS hard-coded to localhost | ✅ FIXED |
| 5.15 | CSP breaks Swagger UI | ✅ FIXED |
| 5.16 | No created_date indexes | ✅ FIXED (V4) |
| 5.17 | No FK ON DELETE on users.role_id | ✅ FIXED (V5) |
| 5.18 | nationalId PII plaintext | ✅ FIXED (V6 + AES-GCM) |
| 6.1–6.28 | LOW/nits | ✅ FIXED (see table in §6) |
| 6.29 | Docs maintenance burden | ❌ OPEN (process, not code) |

---

## 1. What this thing actually is

A Spring Boot service that ships **two products bundled together**:

1. **A reusable JSON-query engine** — `QueryPredicateBuilder` + `GenericQueryRepository` + `ComputedFieldHandlerRegistry`. Clients POST a `QueryRequest` JSON (conditions, sort, pagination) and get back a `Page<T>` filtered through QueryDSL predicates, with computed/virtual fields and dot-notation field navigation.
2. **A reference application** — JWT-auth'd User / Role / Permission CRUD with Excel & PDF export, demonstrating product (1) at scale of three entities.

The intent (per README, ARCHITECTURE.md, and the `generic-querydsl` reference in `settings.gradle:3`) is for product (1) to be extracted as a library / starter. **That extraction was never finished** — the subproject directory doesn't exist on disk, so the engine is fused to the demo, and anyone who wants to reuse it must copy-paste or fork.

## 2. Verdict

**As a portfolio / learning project:** ★★★★☆ (good). Clean layering, thoughtful caching, useful validation guards, decent docs. The query engine's plugin model for computed fields is actually elegant. You demonstrate competence with Spring Security, QueryDSL, JPA auditing, Flyway, OpenAPI, and the global exception handler pattern. Above what most "Spring Boot CRUD demos" deliver.

**As a production backend:** ★★☆☆☆ — not ready. One critical data-leak (export reflection), one critical correctness bug (silently-dropped query operations), no method-level authz despite enabling `@EnableMethodSecurity`, no audit trail wiring, ~1% test coverage of the core feature, hard-coded JWT secret default, no rate limiting / brute-force protection, several N+1 hotspots.

**As a reusable library:** ★★☆☆☆ — claim is undermined. The engine isn't extracted, the API surface (`QueryRequest`, `QueryOperation`, `ComputedFieldHandlerRegistry`) is fine in isolation but the package layout (`querydsl.query.*` mixed with `querydsl.model.User`) makes it impossible to depend on without dragging in the demo entities. The static singleton (`QueryPredicateBuilder.getInstance()`) is a smell that prevents library-style usage in multi-context apps.

What follows is the punch list, ordered by severity.

---

## 3. 🔴 CRITICAL findings

### 3.1 Password (BCrypt hash) is exfiltratable via the export endpoint
> **STATUS: ✅ FIXED** — Private-field reflection fallback removed; `ExportService` uses public getters only and validates every requested column against a per-entity allow-list (`export/ExportService.java:71-77`). `User` declares `@Exportable` listing permitted fields — `password` is excluded (`model/User.java:20-24`, `export/Exportable.java`). `selectedColumns` is capped at 100.

**`export/ExportService.java:151-153`** — `filterColumns` → `getFieldValue` falls back to:
```java
java.lang.reflect.Field field = entityClass.getDeclaredField(fieldName);
field.setAccessible(true);
return field.get(entity);
```
`getDeclaredField` resolves **private fields**, `setAccessible(true)` makes them readable. A USER-role authenticated request:

```json
POST /user/export/query
{ "format": "EXCEL",
  "selectedColumns": ["email","password"],
  "queryRequest": { "conditions": [{ "field":"isActive", "operation":"IS_TRUE" }] } }
```
returns an `.xlsx` with the BCrypt hash for every user in plaintext. Hashes aren't directly reversible but:
- They accelerate offline cracking,
- They confirm password reuse across systems,
- Their leakage is a reportable incident under GDPR / Saudi PDPL.

**Fix options:**
1. Per-entity allow-list of exportable columns (cleanest — drives `ExportRequest.selectedColumns` whitelist validation).
2. Hard-coded sensitive-field blacklist in `ExportService` (`password`, anything ending in `Hash`, `Secret`, etc.) — quick fix.
3. Remove the private-field fallback entirely; require a public getter. `User.password` has a public getter via Lombok `@Getter`, so this alone doesn't fix it — must combine with (1) or (2).

Also: the same reflection path is used for nested navigation (`role.permissions`) and would happily walk into `Role.permissions` returning a `HashSet<Permission>` rendered as `[Permission@abc, ...]` in Excel. UX-wise broken; security-wise mostly harmless but worth a separate test.

### 3.2 `NOT_STARTS_WITH` / `NOT_ENDS_WITH` (and their `_IGNORE_CASE` variants) are silently dropped
> **STATUS: ✅ FIXED** — All four variants handled in `buildOperationPredicate` via `.not()` (`generic-querydsl/…/query/QueryPredicateBuilder.java:567-580`). The `default` case now `throw new QueryException("Unsupported query operation…")` instead of returning null, so nothing fails open.

**`query/QueryOperation.java:31,33,35,37`** declares them. **`query/QueryPredicateBuilder.java:567-616`** does not handle them in `buildOperationPredicate`. They hit `default → log.warn → return null`. The caller at `query/QueryPredicateBuilder.java:131-136`:
```java
Predicate predicate = this.buildConditionPredicate(condition, entityClass);
if (predicate != null) booleanBuilder.and(predicate);
```
silently drops null predicates. **A query "WHERE name NOT_STARTS_WITH 'admin'" returns every row including admins**. README (`README.md:57`) advertises these operations. This is a correctness bug that fails open.

**Fix:** add the missing cases to the switch (or, better, change the dispatch so that an unsupported operation throws `QueryException` instead of returning null — see also §3.3).

### 3.3 Type-incompatible operations silently match all rows
> **STATUS: ✅ FIXED** — Helper builders now call `unsupportedTypeFor(...)` which throws `QueryException` instead of returning null (`generic-querydsl/…/query/QueryPredicateBuilder.java:614-619`, applied in `buildContainsPredicate`/`buildBetweenPredicate`/`buildGreaterThanPredicate` etc.). A type-mismatched condition now 400s rather than dropping.

Same swallowing pattern. `buildContainsPredicate` returns null when `fieldPath` isn't a `StringExpression` (`QueryPredicateBuilder.java:918-922`), `buildBetweenPredicate` returns null for non-comparables (`:1031-1037`), etc. So **`{"field":"id","operation":"CONTAINS","value":"42"}`** drops the condition and matches everyone. Combined with multi-condition AND, an attacker who can guess one valid field and one mismatched-type field gets a broader result set than intended.

**Fix:** every helper should `throw new QueryException("Operation X not supported on field type Y")` instead of `return null`. The `buildPredicate` loop should not have to filter nulls.

### 3.4 Hard-coded JWT secret default (anyone with the source can forge tokens)
> **STATUS: ✅ FIXED** — `jwt.secret=${JWT_SECRET}` with no fallback (`application.properties:50`); `JwtTokenProvider` `@PostConstruct` throws `IllegalStateException` if the secret is unset or under 32 bytes / 256 bits (`security/JwtTokenProvider.java:37-53`). Startup fails loudly.

**`application.properties:48`** and **`security/JwtTokenProvider.java:32`** both default `jwt.secret` to a fixed string. If `JWT_SECRET` env isn't set, every deployment of this code shares the same HS256 key. The defaults aren't even identical between the two files — the properties file says `…required-for-security`, the `@Value` says `…minimum-256-bits`. Whichever wins, the secret is public.

**Fix:** delete both defaults. Throw on startup if `JWT_SECRET` is unset (`@Value("${jwt.secret}")` with no default → BeanCreationException is fine). Add a sample value to a `.env.example` instead. Optionally, enforce a 256-bit minimum length by validating in a `@PostConstruct`.

### 3.5 No authorization beyond URL prefix — a USER can edit/disable an ADMIN
> **STATUS: ✅ FIXED** — `@PreAuthorize("hasRole('ADMIN')")` now guards every write: `UserService` addUser/updateUser/changeUserStatus/deleteUser (`service/UserService.java:40,60,103,113`) and `RoleService` addRole/updateRole/deleteRole (`service/RoleService.java:36,45,70`). Note the chosen policy is ADMIN-only for all writes (stricter than the review's suggested self-update carve-out).

`SecurityConfig.java:78` gates `/user/**` to `USER / ADMIN / MANAGER`. There is no method-level check. A user authenticated as `user@system.com` can call:
- `PUT /user/{id}/change-status` on the admin's id → admin is now inactive → admin can no longer log in.
- `PUT /user/{id}` on any other user → change their email, role, password.
- `DELETE /user/{id}` on anyone.
- `POST /user` to create a new ADMIN-role user (the role assignment is taken from the request body; no check that the caller is allowed to grant ADMIN).

`@EnableMethodSecurity(prePostEnabled = true)` is enabled at `SecurityConfig.java:43` but **no `@PreAuthorize` exists anywhere in the codebase** (`grep` confirms). The annotation is dead.

**Fix:** add `@PreAuthorize` to `UserService` write methods. At minimum:
```java
@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
public void updateUser(Long id, UserDto userDto) { … }
```
Also restrict role assignment: a non-ADMIN should not be able to set `userDto.roleId` to anything other than their own role or below.

### 3.6 `CustomUserDetailsService` traverses LAZY collections without a transaction
> **STATUS: ✅ FIXED** — `loadUserByUsername` is now `@Transactional(readOnly = true)` (`security/CustomUserDetailsService.java:33`) and `UserRepository.findByEmail` carries `@EntityGraph(attributePaths = {"role", "role.permissions"})` (`repository/UserRepository.java:37-38`), fetching in one query. Test profile sets `spring.jpa.open-in-view=false`.

**`security/CustomUserDetailsService.java:62-67`** dereferences `user.getRole().getName()` and `user.getRole().getPermissions().stream()` outside any `@Transactional` boundary. `Role.permissions` is `@ManyToMany(fetch = LAZY)` (`model/Role.java:29`). This only works today because Spring Boot enables OSIV (open-session-in-view) by default — and prints a warning at startup that you've been ignoring.

Real consequence: turning OSIV off (a standard prod-hardening step) breaks login with `LazyInitializationException`. Also costs N+1 queries per login (user → role → permissions).

**Fix:** add `@Transactional(readOnly = true)` to `loadUserByUsername`, and add a `@EntityGraph(attributePaths = {"role", "role.permissions"})` to `UserRepository.findByEmail` to fetch in one query.

---

## 4. 🟠 HIGH findings

### 4.1 `@EnableJpaAuditing` without `AuditorAware` — `createdBy`/`lastModifiedBy` always null
> **STATUS: ✅ FIXED** — `@EnableJpaAuditing(auditorAwareRef = "auditorProvider")` plus an `AuditorAware<String>` bean resolving the authenticated principal (falling back to "system") (`config/JpaAuditingConfig.java:24-40`). Covered by `AuditorAwareTest`.

**`config/JpaAuditingConfig.java:11`** enables auditing, but no `AuditorAware<String>` bean is registered. `BaseEntity.createdBy` and `BaseEntity.lastModifiedBy` (`model/BaseEntity.java:37-43`) will always be null. The seed migration even leaves them null. You have audit columns on every table costing storage and indexing surface for no value.

**Fix:**
```java
@Bean
public AuditorAware<String> auditorProvider() {
    return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .filter(Authentication::isAuthenticated)
        .map(Authentication::getName);
}
```

### 4.2 No rate limiting / brute force protection on `/auth/login`
> **STATUS: ✅ FIXED** — `LoginRateLimitFilter` (Bucket4j, per-IP, default 5/min, returns 429 + `Retry-After`) is registered before `UsernamePasswordAuthenticationFilter` (`security/LoginRateLimitFilter.java`, `config/SecurityConfig.java:111`). Covered by `LoginRateLimitFilterTest`.

The endpoint is `permitAll` and unmetered (`SecurityConfig.java:69`). Combined with the hard-coded seed credentials (`admin@system.com` / `admin123`), a botnet can credential-stuff at line speed. No login-failure counter, no account lockout, no Captcha, no IP throttle.

**Fix:** add Bucket4j or Resilience4j-rate-limiter as a filter, or use Spring Security's `LockedException` with a `failed_login_attempts` column on `users`. Also force a password change on first login for seeded users.

### 4.3 24-hour JWTs with no revocation, no refresh tokens
> **STATUS: ✅ FIXED** — Refresh-token infrastructure added: `RefreshToken` entity (SHA-256 hashed, V3 migration), `RefreshTokenService` (issue/validate/revoke/rotate), and `/auth/refresh` + `/auth/logout` endpoints (`controller/AuthController.java:68-95`). ⚠️ Residual: the *access* token default is still 24h (`application.properties:53`) — shorten via `JWT_EXPIRATION` env in prod.

**`application.properties:49`** sets 24h expiration. A stolen token is good for a full day. There is no JTI blacklist, no rotating keys, no refresh-token endpoint. Logging out a compromised user requires waiting up to 24h or deploying a new signing key (which logs everyone out).

**Fix:** short-lived access tokens (15 min) + refresh tokens (stored server-side, revocable), or a JTI blacklist backed by Redis. For a smaller surface area, just shorten to 15-60 min and accept re-auth UX.

### 4.4 Mappers do database lookups → tight coupling and broken testability
> **STATUS: ✅ FIXED** — Mappers take pre-resolved associations as parameters (`UserMapper.toEntity(dto, Role)`, `RoleMapper.toEntity(dto, Set<Permission>)`); no repository injection, no DB calls — resolution moved to the service layer. The dead MapStruct dependency was removed from `build.gradle`. Covered by `UserMapperTest`.

**`mapper/UserMapper.java:76-82`** and **`mapper/RoleMapper.java:80-88`** call `roleRepository.findById` / `permissionRepository.findById` inside `toEntity`. Consequences:
- N+1 queries on bulk creation flows.
- Unit-testing the mapper requires a real repository (or heavy mocking).
- The mapper throws `EntityNotFoundException`, leaking persistence concerns into the mapping layer.

**Fix:** move resolution into `UserService.addUser` / `RoleService.addRole`. The mapper should be a pure data transformation.

Bonus: `build.gradle:38` pulls in MapStruct + `mapstruct-processor` but no mapper uses MapStruct — dead dependency. Either generate mappers via MapStruct (`@Mapper(componentModel = "spring")`) or drop the dependency.

### 4.5 N+1 on `/user` listing
> **STATUS: ✅ FIXED** — `UserRepository.findAll(Pageable)` overridden with `@EntityGraph(attributePaths = "role")` (`repository/UserRepository.java:46-48`), so the listing fetches roles in one query.

`UserMapper.toUserResponseDto` (`mapper/UserMapper.java:52-54`) calls `user.getRole().getId()` and `user.getRole().getName()` inside the mapping loop. `role` is `@ManyToOne(fetch = LAZY)` (`model/User.java:32`). Each row in a 20-row page triggers a `SELECT FROM roles WHERE id = ?`. OSIV silently fires off 20 queries.

**Fix:** add `@EntityGraph(attributePaths = "role")` to `UserRepository.findAll(Pageable)`, or — for the QueryDSL path — use a custom fetch in `findAllByQueryRequest` via `JPAQuery.leftJoin(qUser.role).fetchJoin()`.

### 4.6 Static singleton + double-checked locking without `volatile`
> **STATUS: ✅ FIXED** — `instance` is now `volatile` and assigned once in a `@PostConstruct`; `getInstance()` throws `IllegalStateException` if null with the `applicationContext.getBean` fallback removed (`generic-querydsl/…/query/QueryPredicateBuilder.java:53,60-79`). (The deeper "drop the static singleton entirely" suggestion was not taken — the volatile-single-assignment pattern is now correct.)

**`query/QueryPredicateBuilder.java:41-78`**: `instance` is a static field assigned in both `setApplicationContext` (Spring init thread) and lazily in `getInstance()` under double-checked locking — but the field is not `volatile`. Pre-JMM-fix (Java 5+) this pattern is broken: another thread can see a non-null reference to a partially-constructed object. In practice it works because the assignment happens during Spring init before any web threads run, but the pattern is wrong and gives readers the wrong message.

**Fix:** make `instance` `volatile`, or — better — drop the lazy lookup entirely. The `applicationContext.getBean` fallback is never useful: by the time any HTTP request is served, `setApplicationContext` has already run. Just throw if `instance` is null and document that Spring must wire it.

Cleaner still: drop the static singleton, give `GenericQueryRepository` a constructor-injected helper via a `FragmentInterface` or a `RepositoryFactoryBeanCustomizer`. The static is a workaround for "interface default methods can't @Autowired" but Spring Data has proper extension points.

### 4.7 Timezone information is silently discarded when parsing dates
> **STATUS: ✅ FIXED** — A TZ indicator now triggers `OffsetDateTime.parse(...).withOffsetSameInstant(ZoneOffset.UTC)` (conversion, not stripping); TZ-less input is documented as UTC (`generic-querydsl/…/query/QueryPredicateBuilder.java:732-783`). `Instant`/`OffsetDateTime`/`ZonedDateTime` are now supported target types.

**`query/QueryPredicateBuilder.java:745-771`** `removeTimezoneIndicators` strips `Z`, `+HH:MM`, `-HH:MM` and parses the remainder as **local time**. A client in UTC sending `"2025-11-01T00:00:00Z"` is filtered as `2025-11-01T00:00:00` server-local — could be 5h off depending on JVM `user.timezone`. Same data interpreted differently between two replicas in different regions. **Data correctness bug.**

**Fix:** parse as `OffsetDateTime`/`ZonedDateTime` first if a TZ indicator is present, then convert to the target type. If the column is `LocalDateTime`, document that the API always interprets input as the server's TZ — or better, force UTC.

### 4.8 The `generic-querydsl` subproject in `settings.gradle` doesn't exist
> **STATUS: ✅ FIXED** — The engine was extracted into the real subproject (commit `3eba1e0`): `generic-querydsl/build.gradle` + full source tree (`QueryPredicateBuilder`, `GenericQueryRepository`, `GenericQuerydslAutoConfiguration`, etc.) now exist on disk. The library narrative is now true.

**`settings.gradle:3`** declares `include 'generic-querydsl'` with `projectDir = new File(settingsDir, 'generic-querydsl')`. The directory doesn't exist (verified). Gradle's behavior here is to fail evaluation or print a noisy warning depending on version. Either:
- Extract the engine into the subproject as originally planned (the right call for the library narrative).
- Or delete the `include` line.

Right now `settings.gradle` makes a false promise.

### 4.9 No method-level authorization audit + `changeUserStatus` is a toggle on PUT
> **STATUS: ✅ FIXED** (authz fixed earlier; race closed 2026-05-20) — `changeUserStatus` is `@PreAuthorize("hasRole('ADMIN')")` and now takes the **absolute** target state via `ChangeUserStatusRequest {@NotNull Boolean isActive}` instead of toggling, making it idempotent and race-free (`controller/UserController.java`, `service/UserService.java`, `dto/ChangeUserStatusRequest.java`). `AuthorizationTest` updated.

**`controller/UserController.java:68`** — `PUT /{id}/change-status` toggles `isActive` and returns 200. Concurrent requests race: two clicks → state ends where it started. Also it's a PUT with no body, which is semantically odd. Combined with §3.5 (no authz), this is the easiest one-shot privilege-tampering primitive in the app.

**Fix:** accept the target state in the body (`{"isActive": false}`), and require `@PreAuthorize("hasRole('ADMIN')")`.

### 4.10 Missing handler for `DataIntegrityViolationException`
> **STATUS: ✅ FIXED** — `@ExceptionHandler(DataIntegrityViolationException.class)` returns 409 CONFLICT with constraint-name-mapped friendly messages (`exception/GlobalExceptionHandler.java:198-273`). Covered by `DataIntegrityViolationHandlerTest`.

`GlobalExceptionHandler.java` doesn't catch JPA / Hibernate constraint violations. Examples that hit the generic `Exception` handler and become "An unexpected error occurred":
- Creating a Role with a duplicate `name` (UNIQUE constraint).
- Creating a User with a duplicate `email` or `nationalId`.
- Deleting a Role still referenced by `users.role_id` (no `ON DELETE` clause — `V1__Create_base_tables.sql:51`).

Clients get an opaque 500 instead of a useful 409 CONFLICT / 400 BAD REQUEST.

**Fix:** add `@ExceptionHandler(DataIntegrityViolationException.class)` returning 409 with a parseable message.

---

## 5. 🟡 MEDIUM findings

### 5.1 `isActive=false` permissions and roles are still effective
> **STATUS: ✅ FIXED** — `getAuthorities` returns empty when the role is inactive and filters out inactive permissions (`security/CustomUserDetailsService.java:60-76`).

`Permission.isActive` and `Role.isActive` exist in the schema (`model/Permission.java:30`, `model/Role.java:27`) but `CustomUserDetailsService.getAuthorities` (`security/CustomUserDetailsService.java:56-73`) does not filter them. A deactivated permission still grants access. Soft-delete is plumbing without a feature.

### 5.2 Sort fields are not whitelisted
> **STATUS: ✅ FIXED** — `GenericQueryService` validates each sort field against the entity's `@SortableFields` allow-list, throwing `QueryException` (400) otherwise (`generic-querydsl/…/service/GenericQueryService.java:176-190`); User/Role/Permission carry the annotation. Covered by `SortFieldWhitelistTest`.

`SortField` accepts any path matching `^[a-zA-Z0-9_.]+$` up to depth 5 (`QueryPredicateBuilder.java:225-238`). A client can sort by `role.permissions.id` which forces a join + DISTINCT-or-window-function and an unsuitable index. Combined with pagination this generates pathological SQL.

**Fix:** per-entity sortable-field allow-list in the service or via an annotation on the entity (`@Sortable`).

### 5.3 Empty `NOT_IN` returns null (matches nothing) — inconsistent with IN
> **STATUS: ✅ FIXED** — Both cases are now explicit: empty `IN` → `field.in([])` (matches nothing); empty `NOT_IN` → `Expressions.TRUE.isTrue()` (matches everything), each documented (`generic-querydsl/…/query/QueryPredicateBuilder.java:1052-1082`).

**`QueryPredicateBuilder.java:1132-1163`**:
- `IN` with empty list returns `field.in(List.of())` which is `WHERE 1=0` → matches nothing (correct).
- `NOT_IN` with empty list returns `null` → predicate is dropped → matches everything (correct **semantically** but inconsistent with the early-null pattern). Actually this one is right by accident, but only because `NOT_IN ∅ ≡ true` and dropping the predicate is `true`. Document this or normalize the behavior.

### 5.4 `MAX_IN_VALUES = 1000` is too generous
> **STATUS: ✅ FIXED** — Lowered to `MAX_IN_VALUES = 200` (`generic-querydsl/…/query/QueryPredicateBuilder.java:98-100`).

Many DBs (and JDBC drivers) have prepared-statement parameter limits. SQL Server caps at 2100; PostgreSQL Hibernate batching can choke earlier; large IN lists also defeat the planner. Realistic cap: 100-500. Document the trade-off or expose it as configuration.

### 5.5 `convertValueToFieldType` is incomplete
> **STATUS: ✅ FIXED** — Added `BigDecimal`, `BigInteger`, `UUID`, `Short`, `Byte`, `Instant`, `OffsetDateTime`, `ZonedDateTime`, `LocalTime`; enum lookup now falls back to case-insensitive matching (`generic-querydsl/…/query/QueryPredicateBuilder.java:657-705`).

**`QueryPredicateBuilder.java:632-691`** handles `String/Long/Integer/Double/Float/Boolean/LocalDate/LocalDateTime/Enum`. Missing: `BigDecimal`, `BigInteger`, `UUID`, `short`, `byte`, `Instant`, `OffsetDateTime`, `ZonedDateTime`, `LocalTime`. UUID is especially likely to bite (it's a common ID type in greenfield Spring projects). Enum lookup is case-sensitive (`Enum.valueOf` on `"admin"` for `Role.ADMIN` throws).

### 5.6 `JwtTokenProvider.validateToken` swallows all exceptions
> **STATUS: ✅ FIXED** — Exceptions are now differentiated: `ExpiredJwtException` at INFO, `SignatureException` at WARN, others lower; the duplicated dot-counter is gone (`security/JwtTokenProvider.java:126-153`). Covered by `JwtTokenProviderTest`.

**`security/JwtTokenProvider.java:106-118`** logs every failure as the same level with a weird inline dot-counter computed twice (`token.chars().filter(c -> c == '.').count()` evaluated twice). It treats malformed, expired, signature-mismatch, and unsupported-algorithm identically. For audit logs you want to distinguish — particularly signature mismatches (which suggest forgery) from expirations (normal).

### 5.7 Hand-rolled mappers + unused MapStruct dependency
> **STATUS: ✅ FIXED** — Decision made: MapStruct dependency dropped from `build.gradle`, hand-rolled mappers kept (and cleaned up per §4.4).

See §4.4. Decide one way or the other.

### 5.8 Logback `<logger name="querydsl" level="DEBUG"/>` on by default
> **STATUS: ✅ FIXED** — Default `querydsl` and `org.springframework.security` levels are INFO; DEBUG only under the `dev` profile and `prod` explicitly forces WARN (`logback-spring.xml:35-66`). `show-sql` belongs to dev config.

**`logback-spring.xml:34`** plus `<logger name="org.springframework.security" level="DEBUG"/>` (`:36`). DEBUG-level logs in the query path print conditions and values — including any PII inside `value` fields. The `prod` profile (`:54-60`) does override root to INFO but does NOT override the per-package levels (logback merges, doesn't replace). Verify the prod profile actually suppresses these.

Also `spring.jpa.show-sql=true` (`application.properties:13`) plus `spring.jpa.properties.hibernate.format_sql=true` — fine in dev, please don't ship this to prod stdout.

### 5.9 `Page<…ResponseDto>` serialization is unstable
> **STATUS: ✅ FIXED** (2026-05-20) — Added a `PageResponse<T>` record (`dto/PageResponse.java`) owning the JSON envelope (content/page/size/totalElements/totalPages/first/last/empty); all six paged endpoints across the three controllers now return `PageResponse.from(...)` instead of `Page<…>`. No more `PageImpl` on the wire.

Spring has been warning for ~3 years that serializing `PageImpl` directly is fragile across versions. Use `PagedModel` (Spring HATEOAS) or a project-local `PageResponse<T>` DTO.

### 5.10 `ExportRequest` / `ExportWithQueryRequest` have no validation
> **STATUS: ✅ FIXED** — Both now carry `@Pattern(regexp = "(?i)EXCEL|PDF")` on `format`; `ExportRequest` adds `@Size(max = 100)` on `selectedColumns` (`export/ExportRequest.java:19-24`, `export/ExportWithQueryRequest.java:18-20`).

**`export/ExportRequest.java`** and **`export/ExportWithQueryRequest.java`** are pure `@Data` — `format` can be null, `selectedColumns` can be null, `format = "BANANA"` defaults to PDF (because the code at `ExportService.java:40` does `"EXCEL".equalsIgnoreCase(request.getFormat())`). At minimum:
```java
@Pattern(regexp = "EXCEL|PDF") private String format;
@NotEmpty @Size(max = 50) private List<String> selectedColumns;
```
Better: make `format` an enum.

### 5.11 `UserDto.password` has no minimum length or strength rule
> **STATUS: ✅ FIXED** — `@NotBlank` + `@Size(min=8, max=128)` + `@Pattern` (≥1 letter, ≥1 digit), scoped to the `OnCreate` group (`dto/UserDto.java:44-50`).

**`dto/UserDto.java:37`** — `private String password;` with no constraints. 1-char passwords are accepted; BCrypt hashes them; login works. Trivial credentials at scale.

### 5.12 Same DTO for create & update
> **STATUS: ✅ FIXED** — Resolved via validation groups rather than DTO split: an `OnCreate` group + `@Validated({OnCreate.class, Default.class})` on `addUser` vs plain `@Valid` on `updateUser` lets password be required on create only (`dto/validation/OnCreate.java`, `controller/UserController.java:38,67`).

`UserDto`, `RoleDto`, `PermissionDto` are reused for POST and PUT. Cannot require password on create but forbid on update. Cannot validate that role assignment is permitted only on create vs update. Split into `UserCreateDto` + `UserUpdateDto` if you want correctness.

### 5.13 `UserService.addUser` returns `void` and the controller returns `200 OK` with empty body
> **STATUS: ✅ FIXED** (2026-05-20) — `addUser`/`addRole`/`addPermission` now return the created `…ResponseDto`; the three controllers respond `201 Created` with a `Location: /<entity>/{id}` header (via `ServletUriComponentsBuilder`) and the resource body.

Should be `201 Created` with a `Location: /user/{id}` header and the new resource body. Same for `/role`, `/permission`. Currently clients have to do an extra round trip to discover the new ID (or use `?` workaround like a second query call).

### 5.14 CORS allow-list is hard-coded to localhost
> **STATUS: ✅ FIXED** — Origins externalized to `cors.allowed-origins` (`config/SecurityConfig.java:59`), with `application-prod.properties` binding it to `${CORS_ALLOWED_ORIGINS:}` (deny-all by default).

**`SecurityConfig.java:115-119`** lists `localhost:3000/4200/5173/8080`. No production override mechanism. `setAllowCredentials(true)` (`:124`) plus a fixed list means the moment you deploy to a real domain, CORS breaks silently and you'll edit code instead of config. Move to `cors.allowed-origins=` in `application.properties` with a profile override.

### 5.15 CSP `default-src 'self'` on every response, including the Swagger UI
> **STATUS: ✅ FIXED** — CSP now permits `'unsafe-inline'` for script-src/style-src so Swagger UI loads, with a comment noting the trade-off for an API-only app (`config/SecurityConfig.java:117-127`).

**`SecurityConfig.java:103`** applies CSP globally. Swagger UI under `/swagger-ui/**` loads JS/CSS that may violate `default-src 'self'`. Test it. If it works for you today, it's because springdoc serves assets same-origin — but any future inline script will break.

### 5.16 No DB indexes on `created_date` / `last_modified_date`
> **STATUS: ✅ FIXED** — `V4__Add_created_date_indexes.sql` adds DESC indexes on `created_date` for users/roles/permissions.

Every controller defaults `@PageableDefault(sort = "createdDate", direction = DESC)`. With seed data of 3 users this is fine. With 1M rows you'll table-scan + sort. Add a B-tree index in a new Flyway migration:
```sql
CREATE INDEX idx_users_created_date ON users(created_date DESC);
CREATE INDEX idx_roles_created_date ON roles(created_date DESC);
CREATE INDEX idx_permissions_created_date ON permissions(created_date DESC);
```

### 5.17 No FK `ON DELETE` strategy on `users.role_id`
> **STATUS: ✅ FIXED** — `V5__User_role_fk_ondelete.sql` recreates the FK with `ON DELETE SET NULL`; the duplicate-key/FK errors are also now mapped to 409 (§4.10).

**`V1__Create_base_tables.sql:51`** — `CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES roles(id)`. No `ON DELETE`. Deleting a role used by any user fails with a vague FK error → mapped to 500 by the generic exception handler. Decide: `SET NULL` (users become role-less) or `RESTRICT` (deletion forbidden — current behavior, but error-handle it).

### 5.18 `nationalId` is PII in plaintext
> **STATUS: ✅ FIXED** — `nationalId` is `@Convert(converter = EncryptedStringConverter.class)` (AES-256-GCM via `EncryptionService`); `V6__Encrypt_national_id.java` encrypts existing rows and adds a deterministic hash column for uniqueness/lookup. Covered by `EncryptionServiceTest`.

**`model/User.java:30`**, indexed unique, no encryption. Saudi NIN given the `+966…` mobile defaults. Under Saudi PDPL this is regulated personal data. Either:
- Encrypt at rest (Hibernate `@Convert` with AES + KMS-managed key), or
- Hash for equality-only lookups (you only use `findByNationalId`, so an HMAC works), or
- Drop the field if not needed.

---

## 6. 🟢 LOW / nits

> **STATUS (all reconciled 2026-05-20):** 6.1–6.28 ✅ **FIXED** (mostly Phase 5 polish), 6.29 ❌ **OPEN**.
>
> Highlights: 6.3 `MAX_FIELD_PATH_DEPTH` → `MAX_FIELD_PATH_SEGMENTS`; 6.4 Q-entity lowercase fallback deleted; 6.5 `MAX_CONDITIONS` single-sourced; 6.6 deprecated `…WithProjection` removed; 6.7 untyped `ComputedFieldHandler` deleted; 6.8 switch logic extracted to `StringFieldOperationDispatcher` (used by all 4 handlers); 6.9 handlers default to `EQUALS` uniformly; 6.10 unsupported computed-field ops now throw; 6.11 `ObjectMapper` injected; 6.12 `SecurityConfig` error JSON now uses the `ErrorResponse` envelope; 6.13 `isActive` is nullable `Boolean` (no `=true` initializer); 6.15 `mobileNumber` `@Pattern` added; 6.16 email normalized/lowercased in service; 6.18 `autoSizeColumn` skipped above 5k rows; 6.19/6.24 migrated to **OpenPDF 2.0.3**; 6.20 export filename uses UTC; 6.22 `LOG_FILE` defaults to `./logs/`; 6.23 PostgreSQL pinned to **42.7.4**; 6.25 `@Tag` on all controllers; 6.26 **test suite added** (`QueryPredicateBuilderTest`, `AuthorizationTest`, `SortFieldWhitelistTest`, `ExportServiceTest`, `JwtTokenProviderTest`, `RefreshTokenServiceTest`, `EncryptionServiceTest`, `LoginRateLimitFilterTest`, `CustomUserDetailsServiceTest`, `UserMapperTest`, `DataIntegrityViolationHandlerTest`, `AuditorAwareTest`); 6.27 `@EqualsAndHashCode(onlyExplicitlyIncluded=true)` with business keys; 6.28 explicit `@EntityScan`/`@EnableJpaRepositories` on the main class.
> **6.17** (authorities CSV in body) and **6.21** (`actuator` `show-components=always`) were reviewed and kept as **intentional** design choices — verify they still match your prod posture.
> **6.29** docs maintenance is a process matter, not a code fix — and this very file is now one more doc to keep in sync.

| # | Where | Issue |
|---|---|---|
| 6.1 | `QueryPredicateBuilder.java:114` | Dot counter computed twice in same expression — cleanup. |
| 6.2 | `QueryPredicateBuilder.java:9` (`InvalidFieldException.java:10`) | Javadoc list has malformed `<li>` tag — indentation looks copy-pasted. |
| 6.3 | `QueryPredicateBuilder.java:330` | "depth" in `MAX_FIELD_PATH_DEPTH` actually means "segments". Misleading name. |
| 6.4 | `QueryPredicateBuilder.java:345-383` | Q-entity loader has camelCase + lowercase fallback. QueryDSL is consistent — kill the fallback. |
| 6.5 | `QueryRequest.java:48` & `QueryPredicateBuilder.MAX_CONDITIONS` | Same constant in two places. Centralize. |
| 6.6 | `GenericQueryRepository.java:87-108` | `findAllByQueryRequestWithProjection` is `@Deprecated` and does an unchecked cast. Delete it — nothing uses it. |
| 6.7 | `ComputedFieldHandlerRegistry.java` | Both `ComputedFieldHandler` and `TypedComputedFieldHandler` are supported "for backward compat" — this is a greenfield codebase, there is no compat to preserve. Delete the untyped variant. |
| 6.8 | All 4 computed handlers | Switch statements duplicated between `UserFullNameHandler`, `UserRoleNameHandler`, `UserPermissionHandler`, `RolePermissionNameHandler`. Extract a `StringFieldOperationDispatcher` helper. |
| 6.9 | `UserFullNameHandler.java:42-56` | Defaults to `CONTAINS_IGNORE_CASE` for missing operation. Other handlers default to `EQUALS`. Inconsistent surprise for callers. |
| 6.10 | `UserRoleNameHandler` and `UserPermissionHandler` | Missing `BETWEEN`, `GREATER_THAN`, etc. — they fall to `default → eq`, which silently lies. Throw instead. |
| 6.11 | `SecurityConfig.java:134-160` | Inline `new ObjectMapper()` per call. Use the Spring-managed one (`@Autowired`). |
| 6.12 | `SecurityConfig.java` error JSON | Different shape from `ErrorResponse`. Standardize. |
| 6.13 | `User.java:37`, `Role.java:27`, `Permission.java:30` | `Boolean isActive = true` initializer — beware Jackson deserializing `{"isActive": null}` setting it back to null. Use `@JsonProperty(defaultValue = "true")` or coerce in service. |
| 6.14 | `UserService.addUser:36` | Silently sets null password if not provided. Either require password or auto-generate + return one. |
| 6.15 | `UserDto.mobileNumber` | No `@Pattern` regex. E.164 validation would help. |
| 6.16 | `UserDto.email` | `@Email` passes a lot of nonsense. Lowercase + normalize before save. |
| 6.17 | `AuthController.java:33` | Returns plaintext authorities CSV in response body. Fine, but the same data is in the JWT — pick one. |
| 6.18 | `ExcelExporter.java:79-81` | `sheet.autoSizeColumn()` is O(N×M) and slow on large exports. For >10k rows, disable or batch. |
| 6.19 | `PdfExporter.java` | iText 2.1.7 (`build.gradle:33`) is from 2009. Successor is OpenPDF (LGPL/MPL fork). The original iText went commercial and 2.1.7 has known bugs (mostly unicode + memory). Migrate to OpenPDF. |
| 6.20 | `ExportService.java:43` | Filename has `LocalDate.now()` — TZ ambiguous. Use UTC explicitly. |
| 6.21 | `application.properties:30` | `management.endpoint.health.show-details=when-authorized` — combined with `actuator/health` being `permitAll`, anyone can probe; details only show when authenticated. Reasonable but `show-components=always` (`:31`) leaks component names unauthenticated. Tighten. |
| 6.22 | `logback-spring.xml:6` | `LOG_FILE` resolves to `${java.io.tmpdir}/spring.log` on Windows — that's `C:\Users\<you>\AppData\Local\Temp\spring.log`. Probably not where you want logs. Set explicitly per profile. |
| 6.23 | `build.gradle:36` | PostgreSQL 42.7.7 doesn't exist as of this writing; check the version. Latest stable is 42.7.4. |
| 6.24 | `build.gradle:33` | `com.lowagie:itext:2.1.7` — see 6.19. |
| 6.25 | `controller/AuthController.java` | No `@Tag` on User/Role/Permission controllers. OpenAPI groups them generically. |
| 6.26 | Tests | Only `ExceptionTest` + `GlobalExceptionHandlerTest`. **No tests for the query engine, computed fields, JWT, export, or any service.** This is the single biggest test-coverage gap. |
| 6.27 | `User.java` / `Role.java` / `Permission.java` | No `equals`/`hashCode` overrides — Lombok `@Getter/@Setter` only. JPA entities with auto-generated IDs need `equals` based on a stable business key, not the ID (which is null pre-persist). Hibernate's collection caching can misbehave. |
| 6.28 | `QuerydslbuilderApplication.java` | No `@EnableJpaRepositories` or `@EntityScan` explicit — relies on default package scanning from `querydsl.*`. Works today; fragile if anyone ever moves the main class. |
| 6.29 | Docs (`docs/`) | Six markdown files for ~50 Java files. Maintenance burden. Will drift. Consider generating endpoint docs from OpenAPI instead of hand-writing curl examples. |

---

## 7. Performance hotspots (ordered by impact)

1. **N+1 on `/user` listing** (§4.5). Fix with `@EntityGraph` or fetch joins. Biggest single win.
2. **N+1 in `loadUserByUsername`** (§3.6). Same fix.
3. **Missing index on `created_date`** (§5.16). Cheap migration, big payoff at scale.
4. **`autoSizeColumn` in Excel export** (6.18). Disable for large exports.
5. **`MAX_IN_VALUES=1000`** (§5.4). Lower to ~100 for plan stability.
6. **No HTTP caching** on read endpoints. `/permission` rarely changes — could send `Cache-Control: max-age=300` via response wrapper.

---

## 8. What I'd do in what order

If this is going to production:
1. **Patch §3.1 (password leak via export)** — same day. Hot-fix.
2. **Patch §3.5 (no method-level authz)** — same week. Add `@PreAuthorize` to all write operations, restrict role assignment to ADMIN-only.
3. **Patch §3.4 (hard-coded JWT default)** — same day. Set `JWT_SECRET` and remove the default.
4. **Patch §3.2 and §3.3 (silently dropped operations)** — same week. Convert "unsupported" returns to thrown `QueryException`.
5. **§4.1 wire `AuditorAware`** — same day. Otherwise `createdBy` is wasted storage.
6. **§4.2 add rate limiting on `/auth/login`** — same week. Bucket4j is two beans.
7. **§4.6 fix the DCL singleton + extract the engine into a proper module** — same month. Resolves §4.8 too.
8. **§5.16 + §4.5 indexes & fetch joins** — once any one table has >1000 rows.
9. **§6.26 actually test the query engine** — same quarter, build up before features.

If this is being published as a library:
1. **Extract `querydsl.query.*` + `querydsl.repository.GenericQueryRepository` into the `generic-querydsl` subproject** (§4.8). The demo (User/Role/Permission) becomes the example app.
2. **Drop the static singleton** (§4.6). The library should hand a `QueryPredicateBuilder` to repositories via a `RepositoryFactoryBeanCustomizer` so consumers don't depend on `getInstance()`.
3. **Document the operation matrix** — which `QueryOperation` works on which QueryDSL expression type. The current "silently null" behavior is unacceptable as a library contract.
4. **Computed-field SPI** — settle on `TypedComputedFieldHandler<T, Q>` only and version it. Delete the untyped sibling.

---

## 9. What's genuinely good

So this doesn't read as a hit-piece — credit where due:

- **Query engine design is sound.** The `QueryRequest`/`QueryCondition` shape is exactly what you want for a JSON-driven query API. Most attempts at this overshoot into Mongo-style nested AND/OR trees and end up un-cacheable; the flat AND-of-conditions list with explicit `QueryOperation` is a sweet spot.
- **Field-name regex + path-depth + IN-size + condition-count guards** at the engine boundary. Real defense in depth. Most generic-query libraries skip this.
- **Two-level reflection cache** is well-sized and thread-safe. Numbers in `docs/ADVANCED.md` look believable.
- **Computed-field plug-in via Spring `@Component` discovery** is the right Spring-idiomatic choice.
- **Flyway + `ddl-auto=validate`** — exactly the right posture.
- **Global exception handler with `ErrorResponse` DTO** is consistent.
- **JWT structure is clean** (subject + authorities claim, HS256), and the parsing API uses the new jjwt 0.12 `verifyWith()` builder — current best practice.
- **OpenAPI config** (`OpenApiConfig.java`) is non-trivial and well-done.
- **The 4 computed-field handlers** demonstrate the pattern clearly enough that a new contributor could add a 5th in 10 minutes.

The bones are there. Most of the findings above are the difference between "demo-quality Spring Boot" and "production-quality Spring Boot" — a journey every codebase makes, and yours is closer to the finish than most.
