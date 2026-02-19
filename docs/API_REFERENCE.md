# API Reference

## Authentication

All endpoints except `/auth/login`, Swagger UI, and Actuator health require a JWT token in the `Authorization` header:

```
Authorization: Bearer <token>
```

### POST /auth/login

Returns a JWT token. No auth required.

**Request:**
```json
{"username": "admin@system.com", "password": "admin123"}
```

**Response (200):**
```json
{
  "token": "eyJhbG...",
  "type": "Bearer",
  "username": "admin@system.com",
  "authorities": "ROLE_ADMIN,user:create,user:read,..."
}
```

---

## Query Components

### QueryRequest

```json
{
  "conditions": [ ... ],
  "sortFields": [ ... ]
}
```

| Field | Type | Constraints |
|-------|------|-------------|
| `conditions` | `QueryCondition[]` | Max 50 |
| `sortFields` | `SortField[]` | Max 10 |

### QueryCondition

```json
{"field": "firstName", "operation": "CONTAINS_IGNORE_CASE", "value": "john"}
```

| Field | Type | Description |
|-------|------|-------------|
| `field` | String (required) | Field name, dot-notation, or computed field |
| `operation` | QueryOperation | Defaults to `EQUALS` if omitted |
| `value` | Object | For most operations |
| `values` | Object[] | For `IN` / `NOT_IN` (max 1000) |
| `startValue` | Object | For `BETWEEN` / `NOT_BETWEEN` |
| `endValue` | Object | For `BETWEEN` / `NOT_BETWEEN` |

### QueryOperation

| Operation | Applies to | Notes |
|-----------|-----------|-------|
| `EQUALS` / `NOT_EQUALS` | All types | |
| `CONTAINS` / `NOT_CONTAINS` | String | Case-sensitive |
| `CONTAINS_IGNORE_CASE` / `NOT_CONTAINS_IGNORE_CASE` | String | |
| `STARTS_WITH` / `STARTS_WITH_IGNORE_CASE` | String | |
| `ENDS_WITH` / `ENDS_WITH_IGNORE_CASE` | String | |
| `BETWEEN` / `NOT_BETWEEN` | Date, Number | Requires `startValue` + `endValue` |
| `GREATER_THAN` / `GREATER_THAN_OR_EQUAL` | Date, Number | |
| `LESS_THAN` / `LESS_THAN_OR_EQUAL` | Date, Number | |
| `IN` / `NOT_IN` | All types | Requires `values` array |
| `IS_NULL` / `IS_NOT_NULL` | All types | No value needed |
| `IS_TRUE` / `IS_FALSE` | Boolean | No value needed |

### SortField

```json
{"field": "createdDate", "direction": "DESC"}
```

---

## Endpoint Pattern

Every entity (User, Role, Permission) follows the same endpoint pattern:

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/{entity}` | Create |
| `GET` | `/{entity}` | List all (paginated) |
| `GET` | `/{entity}/{id}` | Get by ID |
| `PUT` | `/{entity}/{id}` | Update |
| `DELETE` | `/{entity}/{id}` | Delete |
| `POST` | `/{entity}/query` | Dynamic query (paginated) |
| `POST` | `/{entity}/count` | Count matching |
| `POST` | `/{entity}/exists` | Check existence |
| `POST` | `/{entity}/export/query` | Export to Excel/PDF |

Additionally, User has:
| `PUT` | `/user/{id}/change-status` | Toggle active/inactive |

### Authorization

| Endpoint | Required Role |
|----------|--------------|
| `/auth/login` | Public |
| `/user/**` | ROLE_USER, ROLE_ADMIN, ROLE_MANAGER |
| `/role/**` | ROLE_ADMIN, ROLE_MANAGER |
| `/permission/**` | ROLE_ADMIN |
| `/swagger-ui/**`, `/actuator/health` | Public |

---

## User Endpoints

### POST /user
Create a user.

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "mobileNumber": "+966501234567",
  "nationalId": "2000000001",
  "password": "securePassword",
  "roleId": 1,
  "isActive": true
}
```

### GET /user?page=0&size=20&sort=createdDate,desc&roleId=1
List users with optional role filter.

### POST /user/query?page=0&size=20
Query users. Body: `QueryRequest`. Response: `Page<UserResponseDto>`.

### POST /user/export/query
Export matching users. Body:

```json
{
  "queryRequest": {"conditions": [...]},
  "selectedColumns": ["firstName", "lastName", "email"],
  "format": "EXCEL",
  "friendlyHeaders": {"firstName": "First Name", "lastName": "Last Name", "email": "Email"}
}
```

Response: file download.

---

## Role Endpoints

### POST /role

```json
{
  "name": "SUPERVISOR",
  "description": "Supervisor role",
  "isActive": true,
  "permissionIds": [2, 6, 10]
}
```

### GET /role/{id} Response

```json
{
  "id": 1,
  "name": "ADMIN",
  "description": "Full system access",
  "isActive": true,
  "permissionIds": [1, 2, 3, ...],
  "permissionNames": ["user:create", "user:read", ...],
  "createdDate": "2026-01-26T14:00:00"
}
```

---

## Permission Endpoints

### POST /permission

```json
{
  "name": "report:generate",
  "resource": "report",
  "action": "generate",
  "description": "Generate reports",
  "isActive": true
}
```

---

## Computed Fields

Virtual field aliases resolved by the query engine:

| Entity | Field | Maps to |
|--------|-------|---------|
| User | `fullName` | `concat(firstName, ' ', lastName)` |
| User | `roleName` | `role.name` |
| User | `permissionName` | `role.permissions.any().name` |
| Role | `permissionName` | `permissions.any().name` |

Usage: `{"field": "fullName", "operation": "CONTAINS_IGNORE_CASE", "value": "john doe"}`

---

## Error Responses

All errors follow this format:

```json
{
  "timestamp": "2026-01-26T14:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "User not found with id: 999",
  "path": "/user/999"
}
```

Validation errors include field details:

```json
{
  "timestamp": "2026-01-26T14:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/user",
  "validationErrors": [
    {"field": "email", "message": "Email must be valid", "rejectedValue": "not-an-email"}
  ]
}
```

---

## Type Conversion

Values in conditions are auto-converted to match the field type:

| Field type | Input | Converted to |
|-----------|-------|-------------|
| Enum | `"ADMIN"` | Enum constant |
| Long | `"123"` | `123L` |
| Boolean | `"true"` | `true` |
| LocalDateTime | `"2026-01-01T00:00:00"` | `LocalDateTime` |
| LocalDate | `"2026-01-01"` | `LocalDate` |

---

## Implementation Guide

### Adding a new entity to the QueryDSL system

1. **Entity** — extend `BaseEntity`
2. **Repository** — extend `GenericQueryRepository<T, Long>`, implement `getEntityClass()`
3. **Service** — inject `GenericQueryService`, delegate query/count/exists
4. **Controller** — add `/query`, `/count`, `/exists`, `/export/query` endpoints
5. **DTOs + Mapper** — request DTO, response DTO, mapper component
6. **Computed fields** (optional) — implement `TypedComputedFieldHandler`

---

**See also:** [Curl Examples](curls/) | [User Guide](USER_GUIDE.md) | [Advanced Topics](ADVANCED.md)
