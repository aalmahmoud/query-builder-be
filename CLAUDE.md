# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Build, run, test (Windows shell — use `gradlew.bat`; on POSIX use `./gradlew`):

```powershell
.\gradlew.bat build                  # full build (compiles QueryDSL Q-classes via annotation processor)
.\gradlew.bat bootRun                # run the Spring Boot app on :8080
.\gradlew.bat test                   # run all tests (JUnit 5)
.\gradlew.bat test --tests "querydsl.exception.GlobalExceptionHandlerTest"   # single test class
.\gradlew.bat test --tests "querydsl.exception.GlobalExceptionHandlerTest.methodName"  # single test method
.\gradlew.bat compileJava            # regenerate QueryDSL Q-classes only
```

Default DB credentials are pulled from env vars `DB_USERNAME`/`DB_PASSWORD` (and optionally `JWT_SECRET`), falling back to placeholders in `application.properties`. PostgreSQL must be running on `localhost:5432` with database `query_builder_db` before `bootRun`. Tests use H2 (`com.h2database:h2` is `testImplementation`).

Swagger UI: `http://localhost:8080/swagger-ui.html`. Default seeded login: `admin@system.com` / `admin123` (see `V2__Seed_data.sql`).

## Architecture

This is a single Gradle module (root project name `querydslbuilder`, package root `querydsl`). Note that `settings.gradle` also declares `include 'generic-querydsl'` pointing to a sibling directory that does not exist on disk — leave this alone unless the user is intentionally extracting the engine into a sub-module.

The system is a generic JSON-driven query layer over Spring Data JPA + QueryDSL. The key insight: instead of N hand-written `findBy…` methods per entity, every entity gets `query`, `count`, `exists`, and `export/query` endpoints that accept a `QueryRequest` JSON body and run through one shared predicate builder.

### Query pipeline

```
POST /<entity>/query  →  Controller  →  Service  →  GenericQueryService
   →  GenericQueryRepository.findAllByQueryRequest (default method)
   →  QueryPredicateBuilder.buildPredicate(request, entityClass)
       ├─ validateQueryRequest (size/regex/depth/IN-size/BETWEEN guards)
       ├─ for each QueryCondition:
       │   ├─ ComputedFieldHandlerRegistry — try typed handler (O(1)) then untyped (O(n))
       │   └─ otherwise navigateToField via reflection on Q-entity (cached)
       │       and dispatch on QueryOperation enum in a switch
       └─ combine with BooleanBuilder AND
   →  JpaRepository.findAll(predicate, pageable)  →  Mapper  →  ResponseDto
```

Critical pieces, by file:

- `query/QueryPredicateBuilder.java` — the engine. Two static `ConcurrentHashMap` caches (`Q_ENTITY_CACHE`, `FIELD_CACHE`) avoid repeated reflection. Hard-coded security limits: `MAX_CONDITIONS=50`, `MAX_FIELD_PATH_DEPTH=5`, `MAX_IN_VALUES=1000`, field names must match `^[a-zA-Z0-9_.]+$`. Date strings are parsed with manual ISO-8601 handling (timezone stripping + millisecond normalization) before being passed to QueryDSL.
- `repository/GenericQueryRepository.java` — `@NoRepositoryBean` interface with **default methods** (`findAllByQueryRequest`, `countByQueryRequest`, …WithAdditionalPredicates). Each concrete repo must override `getEntityClass()` so the engine can locate the matching Q-class. Default methods reach the predicate builder via `QueryPredicateBuilder.getInstance()` — a static accessor backed by `ApplicationContextAware` and double-checked locking, because default methods cannot use `@Autowired`. Do not remove this static singleton without replacing the lookup mechanism.
- `service/GenericQueryService.java` — thin orchestrator; also converts `QueryRequest.sortFields` into a Spring `Sort` and rebuilds the `Pageable`. If `sortFields` is empty, the controller's `@PageableDefault` sort wins.
- `query/computed/` — virtual fields. `TypedComputedFieldHandler<T, Q>` is preferred (O(1) registry lookup); `ComputedFieldHandler` is the legacy untyped variant kept for backward compat. The registry auto-discovers both via Spring `@Component` injection; computed fields are checked **before** regular field resolution, so a computed field shadows any same-named entity field. Built-ins: `fullName`, `roleName`, `permissionName` (User), `permissionName` (Role).
- `model/BaseEntity.java` — every entity extends this; JPA auditing fills `createdDate`/`lastModifiedDate`/`createdBy`/`lastModifiedBy`. `id` is `IDENTITY`-generated.
- `config/SecurityConfig.java` — stateless JWT, CSRF off, `@EnableMethodSecurity`. Endpoint authorization is wired by URL prefix: `/user/**` → USER/ADMIN/MANAGER, `/role/**` → ADMIN/MANAGER, `/permission/**` → ADMIN. Public: `/auth/login`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health`, `/actuator/info`. CORS allow-list is hard-coded to localhost dev ports (3000, 4200, 5173, 8080).
- `security/JwtAuthenticationFilter.java` + `JwtTokenProvider.java` — HS256, claims include comma-separated authorities (roles + permissions). Expiration via `jwt.expiration` (ms; default 24h).
- Schema is owned by Flyway (`spring.jpa.hibernate.ddl-auto=validate`) — never let Hibernate auto-DDL. Migrations live in `src/main/resources/db/migration/`.

### Adding things

- **New entity with full query support**: extend `BaseEntity`; create `XRepository extends JpaRepository<X, Long>, GenericQueryRepository<X, Long>` and override `getEntityClass()`; create service that calls `GenericQueryService` and `XRepository`; create controller mirroring `UserController` (`/query`, `/count`, `/exists`, `/export/query`); create request/response DTOs and a MapStruct mapper; add a Flyway `V{n}__…sql` migration. Update `SecurityConfig` if the new endpoint prefix needs different authorization.
- **New query operation**: add the enum value to `QueryOperation`, then add a `case` in the big `switch` inside `QueryPredicateBuilder.buildOperationPredicate` and a small helper builder method below it. Mind which QueryDSL expression interface the operation requires (`SimpleExpression`, `StringExpression`, `ComparableExpression`, `BooleanExpression`) — the helpers return `null` for unsupported types, which silently drops the condition.
- **New computed field**: implement `TypedComputedFieldHandler<Entity, QEntity>` and annotate `@Component`. It is auto-registered. The `getFieldName()` you return becomes the value clients send in `QueryCondition.field`.

### Things that bite

- Q-classes are generated into `build/generated/sources/querydsl/java` (added to the main source set in `build.gradle`). After adding/renaming an entity field, run `compileJava` or `build` before referencing the new Q-field.
- `QueryPredicateBuilder` uses **static** caches and a static singleton — they survive across requests but reset on JVM restart. Don't reach into them from tests; if a test needs a clean state, restart the Spring context.
- `findAllByQueryRequestWithProjection` on `GenericQueryRepository` is `@Deprecated` and performs an unchecked cast — prefer adding a MapStruct mapper instead.
- The `Q-entity` loader tries camelCase field first (`QUser.user`) then falls back to all-lowercase. Stay on standard QueryDSL naming so this fallback never triggers.
