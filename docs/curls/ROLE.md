# Role API

Base URL: `http://localhost:8080/role`  
**Required Role:** ROLE_ADMIN or ROLE_MANAGER

> All requests require a valid JWT token. See [AUTH.md](AUTH.md) for login instructions.

```bash
# Get a token first
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@system.com","password":"admin123"}' \
  | jq -r '.token')
```

---

## Create Role

**Endpoint:** `POST /role`

```bash
curl -X POST http://localhost:8080/role \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "SUPERVISOR",
    "description": "Supervisor with limited management access",
    "isActive": true,
    "permissionIds": [2, 6, 10]
  }'
```

**Response:** `200 OK` (empty body)

The `permissionIds` field assigns permissions to the role. Use the permission IDs from the database. The seed data creates permissions with IDs 1–12.

---

## Get All Roles (Paginated)

**Endpoint:** `GET /role`

```bash
# Default pagination
curl http://localhost:8080/role \
  -H "Authorization: Bearer $TOKEN"

# Custom pagination and sorting
curl "http://localhost:8080/role?page=0&size=10&sort=name,asc" \
  -H "Authorization: Bearer $TOKEN"
```

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": 1,
      "name": "ADMIN",
      "description": "Full system access with all permissions",
      "isActive": true,
      "permissionIds": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
      "permissionNames": ["user:create", "user:read", "user:update", "user:delete", "role:create", "..."],
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

## Get Role by ID

**Endpoint:** `GET /role/{id}`

```bash
curl http://localhost:8080/role/1 \
  -H "Authorization: Bearer $TOKEN"
```

---

## Update Role

**Endpoint:** `PUT /role/{id}`

```bash
curl -X PUT http://localhost:8080/role/2 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "MANAGER",
    "description": "Updated manager description",
    "isActive": true,
    "permissionIds": [1, 2, 3, 4, 5, 6, 7, 8, 10]
  }'
```

---

## Delete Role

**Endpoint:** `DELETE /role/{id}`

```bash
curl -X DELETE http://localhost:8080/role/4 \
  -H "Authorization: Bearer $TOKEN"
```

---

## Query Roles (Generic QueryDSL)

**Endpoint:** `POST /role/query`

### Search by role name

```bash
curl -X POST http://localhost:8080/role/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "name", "operation": "CONTAINS_IGNORE_CASE", "value": "admin" }
    ]
  }'
```

### Search by active status

```bash
curl -X POST http://localhost:8080/role/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "isActive", "operation": "IS_TRUE" }
    ],
    "sortFields": [
      { "field": "name", "direction": "ASC" }
    ]
  }'
```

### Search by permission name (computed field)

Find roles that include a specific permission:

```bash
# Roles with "user:delete" permission
curl -X POST http://localhost:8080/role/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "permissionName", "operation": "EQUALS", "value": "user:delete" }
    ]
  }'
```

### Search by permission name (dot-notation)

```bash
# Same result using dot-notation
curl -X POST http://localhost:8080/role/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "permissions.name", "operation": "CONTAINS", "value": "user:" }
    ]
  }'
```

### Search by multiple role names (IN)

```bash
curl -X POST http://localhost:8080/role/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "name", "operation": "IN", "values": ["ADMIN", "MANAGER"] }
    ]
  }'
```

---

## Count Roles by Query

**Endpoint:** `POST /role/count`

```bash
curl -X POST http://localhost:8080/role/count \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "isActive", "operation": "IS_TRUE" }
    ]
  }'
```

---

## Check if Roles Exist

**Endpoint:** `POST /role/exists`

```bash
curl -X POST http://localhost:8080/role/exists \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "name", "operation": "EQUALS", "value": "SUPERVISOR" }
    ]
  }'
```

---

## Export Roles (Generic QueryDSL)

**Endpoint:** `POST /role/export/query`

```bash
curl -X POST http://localhost:8080/role/export/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "queryRequest": {
      "conditions": [
        { "field": "isActive", "operation": "IS_TRUE" }
      ]
    },
    "selectedColumns": ["name", "description"],
    "format": "EXCEL",
    "friendlyHeaders": {
      "name": "Role Name",
      "description": "Description"
    }
  }' \
  -o roles_export.xlsx
```

---

## Available Search Fields

### Direct fields
| Field         | Type          | Operations                     |
|---------------|---------------|--------------------------------|
| `id`          | Long          | EQUALS, IN, GREATER_THAN, ...  |
| `name`        | String        | All string operations          |
| `description` | String        | All string operations          |
| `isActive`    | Boolean       | IS_TRUE, IS_FALSE, EQUALS      |
| `createdDate` | LocalDateTime | BETWEEN, GREATER_THAN, ...     |

### Nested fields (dot-notation)
| Field                  | Description                              |
|------------------------|------------------------------------------|
| `permissions.id`       | Permission ID                            |
| `permissions.name`     | Permission name (traverses ManyToMany)   |
| `permissions.resource` | Permission resource                      |
| `permissions.action`   | Permission action                        |

### Computed fields (aliases)
| Field            | Maps to                       | Description                          |
|------------------|-------------------------------|--------------------------------------|
| `permissionName` | `permissions.any().name`      | Search by any assigned permission    |
