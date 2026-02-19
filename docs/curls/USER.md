# User API

Base URL: `http://localhost:8080/user`  
**Required Role:** ROLE_USER, ROLE_ADMIN, or ROLE_MANAGER

> All requests require a valid JWT token. See [AUTH.md](AUTH.md) for login instructions.

```bash
# Get a token first
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@system.com","password":"admin123"}' \
  | jq -r '.token')
```

---

## Create User

**Endpoint:** `POST /user`

```bash
curl -X POST http://localhost:8080/user \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "mobileNumber": "+966501234567",
    "nationalId": "2000000001",
    "password": "securePassword123",
    "roleId": 1,
    "isActive": true
  }'
```

**Response:** `200 OK` (empty body)

---

## Get All Users (Paginated)

**Endpoint:** `GET /user`

```bash
# Default pagination (page 0, size 20, sorted by createdDate DESC)
curl http://localhost:8080/user \
  -H "Authorization: Bearer $TOKEN"

# Custom pagination
curl "http://localhost:8080/user?page=0&size=10&sort=firstName,asc" \
  -H "Authorization: Bearer $TOKEN"

# Filter by role ID
curl "http://localhost:8080/user?roleId=1" \
  -H "Authorization: Bearer $TOKEN"
```

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": 1,
      "firstName": "System",
      "lastName": "Admin",
      "email": "admin@system.com",
      "mobileNumber": "+966500000001",
      "nationalId": "1000000001",
      "roleId": 1,
      "roleName": "ADMIN",
      "isActive": true,
      "createdDate": "2026-01-26T14:00:00",
      "lastModifiedDate": null,
      "createdBy": null,
      "lastModifiedBy": null
    }
  ],
  "totalElements": 3,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

---

## Get User by ID

**Endpoint:** `GET /user/{id}`

```bash
curl http://localhost:8080/user/1 \
  -H "Authorization: Bearer $TOKEN"
```

---

## Update User

**Endpoint:** `PUT /user/{id}`

```bash
curl -X PUT http://localhost:8080/user/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Updated",
    "lastName": "Admin",
    "email": "admin@system.com",
    "nationalId": "1000000001",
    "roleId": 1,
    "isActive": true
  }'
```

---

## Change User Status (Toggle Active/Inactive)

**Endpoint:** `PUT /user/{id}/change-status`

```bash
curl -X PUT http://localhost:8080/user/1/change-status \
  -H "Authorization: Bearer $TOKEN"
```

---

## Delete User

**Endpoint:** `DELETE /user/{id}`

```bash
curl -X DELETE http://localhost:8080/user/4 \
  -H "Authorization: Bearer $TOKEN"
```

---

## Query Users (Generic QueryDSL)

**Endpoint:** `POST /user/query`

The query endpoint accepts a structured request body with conditions and sort fields. This is the core feature of the Generic QueryDSL system.

### Simple field search

```bash
# Find users by first name
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "firstName", "operation": "CONTAINS_IGNORE_CASE", "value": "john" }
    ]
  }'
```

### Multiple conditions (AND)

```bash
# Find active users with a specific role
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "isActive", "operation": "IS_TRUE" },
      { "field": "role.id", "operation": "EQUALS", "value": "1" }
    ],
    "sortFields": [
      { "field": "createdDate", "direction": "DESC" }
    ]
  }'
```

### Nested field search (dot-notation)

The QueryDSL system supports dot-notation for navigating entity relationships:

```bash
# Search by role name (dot-notation)
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "role.name", "operation": "EQUALS", "value": "ADMIN" }
    ]
  }'

# Search by role's permission name (traverses ManyToMany)
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "role.permissions.name", "operation": "EQUALS", "value": "user:create" }
    ]
  }'
```

### Computed field search

Computed fields are virtual aliases that simplify common nested queries:

```bash
# Search by full name (computed: firstName + lastName)
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "fullName", "operation": "CONTAINS_IGNORE_CASE", "value": "system admin" }
    ]
  }'

# Search by role name (computed alias for role.name)
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "roleName", "operation": "IN", "values": ["ADMIN", "MANAGER"] }
    ]
  }'

# Search by permission name (computed alias for role.permissions.name)
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "permissionName", "operation": "EQUALS", "value": "user:delete" }
    ]
  }'
```

### Date range search

```bash
# Find users created in the last month
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      {
        "field": "createdDate",
        "operation": "BETWEEN",
        "startValue": "2026-01-01T00:00:00",
        "endValue": "2026-01-31T23:59:59"
      }
    ]
  }'
```

### IN operation

```bash
# Find users by multiple emails
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      {
        "field": "email",
        "operation": "IN",
        "values": ["admin@system.com", "manager@system.com"]
      }
    ]
  }'
```

### Pagination with query

```bash
curl -X POST "http://localhost:8080/user/query?page=0&size=5&sort=lastName,asc" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "isActive", "operation": "IS_TRUE" }
    ]
  }'
```

---

## Count Users by Query

**Endpoint:** `POST /user/count`

```bash
curl -X POST http://localhost:8080/user/count \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "isActive", "operation": "IS_TRUE" }
    ]
  }'
```

**Response:** `3` (number)

---

## Check if Users Exist

**Endpoint:** `POST /user/exists`

```bash
curl -X POST http://localhost:8080/user/exists \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "email", "operation": "EQUALS", "value": "admin@system.com" }
    ]
  }'
```

**Response:** `true` or `false`

---

## Export Users

**Endpoint:** `POST /user/export/query`

Uses the full Generic QueryDSL system for filtering:

```bash
# Export active admins to Excel
curl -X POST http://localhost:8080/user/export/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "queryRequest": {
      "conditions": [
        { "field": "isActive", "operation": "IS_TRUE" },
        { "field": "roleName", "operation": "EQUALS", "value": "ADMIN" }
      ]
    },
    "selectedColumns": ["firstName", "lastName", "email", "mobileNumber", "nationalId"],
    "format": "EXCEL",
    "friendlyHeaders": {
      "firstName": "First Name",
      "lastName": "Last Name",
      "email": "Email",
      "mobileNumber": "Mobile",
      "nationalId": "National ID"
    }
  }' \
  -o users_admin_export.xlsx
```

---

## Available Search Fields

### Direct fields
| Field           | Type          | Operations                     |
|-----------------|---------------|--------------------------------|
| `id`            | Long          | EQUALS, IN, GREATER_THAN, ...  |
| `firstName`     | String        | All string operations          |
| `lastName`      | String        | All string operations          |
| `email`         | String        | All string operations          |
| `mobileNumber`  | String        | All string operations          |
| `nationalId`    | String        | All string operations          |
| `isActive`      | Boolean       | IS_TRUE, IS_FALSE, EQUALS      |
| `createdDate`   | LocalDateTime | BETWEEN, GREATER_THAN, ...     |

### Nested fields (dot-notation)
| Field                     | Description                          |
|---------------------------|--------------------------------------|
| `role.id`                 | Role ID                              |
| `role.name`               | Role name                            |
| `role.isActive`           | Whether the role is active           |
| `role.permissions.name`   | Permission name (traverses ManyToMany) |
| `role.permissions.resource`| Permission resource                 |
| `role.permissions.action` | Permission action                    |

### Computed fields (aliases)
| Field            | Maps to                        | Description                          |
|------------------|--------------------------------|--------------------------------------|
| `fullName`       | `concat(firstName, ' ', lastName)` | Search by combined full name     |
| `roleName`       | `role.name`                    | Shorthand for the user's role name   |
| `permissionName` | `role.permissions.any().name`  | Search by any assigned permission    |
