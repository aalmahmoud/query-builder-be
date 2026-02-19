# Architecture

## Overview

The Generic QueryDSL Builder provides a reusable, type-safe way to build dynamic queries using JSON request bodies. It is layered on top of Spring Data JPA and QueryDSL, with JWT authentication and role-based access control.

---

## Layers

```
┌─────────────────────────────────────────────────┐
│  Security Layer (JWT Filter, Authorization)     │
├─────────────────────────────────────────────────┤
│  Controller Layer (REST endpoints)              │
├─────────────────────────────────────────────────┤
│  Service Layer (business logic, orchestration)  │
├─────────────────────────────────────────────────┤
│  Query Engine                                   │
│  ┌───────────────────┐ ┌─────────────────────┐  │
│  │ GenericQueryService│ │ QueryPredicateBuilder│  │
│  └───────────────────┘ └─────────────────────┘  │
│  ┌───────────────────────────────────────────┐  │
│  │ Computed Field Registry + Handlers        │  │
│  └───────────────────────────────────────────┘  │
├─────────────────────────────────────────────────┤
│  Repository Layer (JPA + QueryDSL)              │
├─────────────────────────────────────────────────┤
│  Database (PostgreSQL, Flyway migrations)       │
└─────────────────────────────────────────────────┘
```

---

### Security Layer

| Component | Responsibility |
|-----------|---------------|
| `SecurityConfig` | Endpoint authorization rules, CORS, CSRF, filter chain |
| `JwtAuthenticationFilter` | Extracts and validates JWT from request header |
| `JwtTokenProvider` | Token generation, validation, claims extraction |
| `CustomUserDetailsService` | Loads user + roles + permissions from database |
| `BCryptPasswordEncoder` | Password hashing |

### Controller Layer

Thin controllers that handle HTTP concerns (validation, response codes, pagination) and delegate to services.

| Controller | Endpoints |
|-----------|-----------|
| `AuthController` | `POST /auth/login` |
| `UserController` | CRUD + `/query`, `/count`, `/exists`, `/export/query` |
| `RoleController` | CRUD + `/query`, `/count`, `/exists`, `/export/query` |
| `PermissionController` | CRUD + `/query`, `/count`, `/exists`, `/export/query` |

### Service Layer

Business logic: entity lifecycle, password hashing, DTO mapping, query delegation.

| Service | Responsibility |
|---------|---------------|
| `UserService` | User CRUD, password encoding, status toggle |
| `RoleService` | Role CRUD with permission resolution |
| `PermissionService` | Permission CRUD |
| `GenericQueryService` | Reusable query/count/exists via `QueryPredicateBuilder` |
| `ExportService` | Excel (POI) and PDF (iText) export with response building |

### Query Engine

| Component | Responsibility |
|-----------|---------------|
| `QueryPredicateBuilder` | Builds QueryDSL `Predicate` from `QueryRequest` |
| `GenericQueryService` | Executes queries against any `GenericQueryRepository` |
| `ComputedFieldRegistry` | Discovers and stores computed field handlers per entity |
| `TypedComputedFieldHandler` | Interface for custom virtual fields |
| 4 built-in handlers | `fullName`, `roleName`, `permissionName` (User), `permissionName` (Role) |

### Repository Layer

| Component | Responsibility |
|-----------|---------------|
| `GenericQueryRepository<T, ID>` | Base interface requiring `getEntityClass()` |
| `UserRepository`, `RoleRepository`, `PermissionRepository` | Extend `GenericQueryRepository` |

### Database

- **PostgreSQL** with **Flyway** migrations
- `V1__Create_base_tables.sql` — schema (users, roles, permissions, role_permissions)
- `V2__Seed_data.sql` — default data (3 roles, 12 permissions, 3 users)
- All entities extend `BaseEntity` with JPA auditing (id, createdDate, lastModifiedDate, createdBy, lastModifiedBy)

---

## Data Flow

### Query request

```
HTTP POST /user/query (JSON body)
  → JwtAuthenticationFilter (validate token)
  → UserController.getAllUsersByQueryRequest()
  → UserService.getAllUsersByQueryRequest()
  → GenericQueryService.findAllByQueryRequest()
  → QueryPredicateBuilder.buildPredicate()
      ├─ For each condition:
      │   ├─ Check ComputedFieldRegistry (computed field?)
      │   │   └─ Yes → handler.buildPredicate()
      │   └─ No → resolve field via reflection (with caching)
      │       └─ Build predicate based on operation type
      └─ Combine all predicates with BooleanBuilder (AND)
  → JPA query execution
  → Entity results → UserMapper → UserResponseDto
  → Paginated JSON response
```

### Export request

```
HTTP POST /user/export/query (JSON body)
  → UserController → UserService (query, get entities)
  → ExportService.buildExportResponse(entities, request, "users")
      ├─ EXCEL → GenericExcelExporter → byte[]
      └─ PDF   → GenericPdfExporter   → byte[]
  → ResponseEntity with Content-Disposition header → file download
```

---

## Design Patterns

| Pattern | Usage |
|---------|-------|
| **Generic Repository** | `GenericQueryRepository<T, ID>` — one interface for all entities |
| **Builder** | `QueryPredicateBuilder.buildPredicate()` — converts JSON conditions to QueryDSL predicates |
| **Strategy** | `QueryOperation` enum + switch dispatch in predicate builder |
| **Registry** | `ComputedFieldRegistry` — auto-discovers `@Component` handlers at startup |
| **Template Method** | `TypedComputedFieldHandler` — each handler implements `buildPredicate()` |
| **DTO Mapping** | Entity → ResponseDto (never expose entities directly) |

---

## Extension Points

### Adding a new entity

1. Create entity extending `BaseEntity`
2. Create repository extending `GenericQueryRepository`
3. Create service injecting `GenericQueryService`
4. Create controller with the standard endpoint pattern
5. Create DTOs (request, response) and mapper
6. Add Flyway migration for the table

### Adding a new query operation

1. Add constant to `QueryOperation` enum
2. Handle it in `QueryPredicateBuilder` switch statement

### Adding a computed field

1. Create a class implementing `TypedComputedFieldHandler`
2. Annotate with `@Component`
3. It is auto-registered by `ComputedFieldRegistry`

---

**See also:** [Advanced Topics](ADVANCED.md) | [API Reference](API_REFERENCE.md) | [User Guide](USER_GUIDE.md)
