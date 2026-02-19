# Generic QueryDSL Builder

A generic QueryDSL system for Spring Boot that enables dynamic querying through JSON request bodies instead of URL parameters. Includes JWT authentication, role-based access control, and Excel/PDF export.

## Features

- **Dynamic Queries** — Send complex queries through JSON request body with 20+ operations
- **Nested Fields** — Query through relationships using dot notation (`role.name`, `role.permissions.name`)
- **Computed Fields** — Virtual field aliases (`fullName`, `roleName`, `permissionName`)
- **Type Safe** — Compile-time safe with QueryDSL, works with any JPA entity
- **Export** — Excel and PDF export with column selection and friendly headers
- **JWT Auth** — Stateless authentication with role and permission-based authorization
- **Validation** — Input validation on all DTOs and query conditions
- **Error Handling** — Standardized error responses with global exception handler

## Quick Start

```bash
# 1. Login to get a JWT token
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@system.com","password":"admin123"}' \
  | jq -r '.token')

# 2. Query active users
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      {"field": "isActive", "operation": "IS_TRUE"}
    ],
    "sortFields": [
      {"field": "createdDate", "direction": "DESC"}
    ]
  }'
```

## Documentation

- **[Getting Started](docs/GETTING_STARTED.md)** — Setup, database, seed data, first login
- **[User Guide](docs/USER_GUIDE.md)** — Query examples, computed fields, export
- **[API Reference](docs/API_REFERENCE.md)** — All endpoints, request/response formats
- **[Advanced Topics](docs/ADVANCED.md)** — Caching, projections, security internals
- **[Architecture](docs/ARCHITECTURE.md)** — System design, data flow, extension points
- **[Curl Examples](docs/curls/)** — Per-controller curl commands:
  [Auth](docs/curls/AUTH.md) |
  [User](docs/curls/USER.md) |
  [Role](docs/curls/ROLE.md) |
  [Permission](docs/curls/PERMISSION.md)

## Supported Operations

| Category | Operations |
|----------|-----------|
| Equality | `EQUALS`, `NOT_EQUALS` |
| String | `CONTAINS`, `CONTAINS_IGNORE_CASE`, `STARTS_WITH`, `ENDS_WITH` (+ negations, + ignore-case variants) |
| Range | `BETWEEN`, `NOT_BETWEEN`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN`, `LESS_THAN_OR_EQUAL` |
| Collection | `IN`, `NOT_IN` |
| Null | `IS_NULL`, `IS_NOT_NULL` |
| Boolean | `IS_TRUE`, `IS_FALSE` |

## Example Entities

The project includes a working User/Role/Permission management system:

- **User** — CRUD, query, export; relates to Role (ManyToOne)
- **Role** — CRUD, query, export; relates to Permission (ManyToMany)
- **Permission** — CRUD, query, export; resource:action naming (e.g. `user:create`)

## Requirements

- Java 21+
- Spring Boot 3.x
- PostgreSQL
- Gradle 8.x

## License

Apache 2.0
