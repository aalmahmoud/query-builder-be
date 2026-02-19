# Permission API

Base URL: `http://localhost:8080/permission`  
**Required Role:** ROLE_ADMIN only

> All requests require a valid JWT token. See [AUTH.md](AUTH.md) for login instructions.

```bash
# Get a token first (must be ADMIN)
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@system.com","password":"admin123"}' \
  | jq -r '.token')
```

---

## Create Permission

**Endpoint:** `POST /permission`

```bash
curl -X POST http://localhost:8080/permission \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "report:generate",
    "resource": "report",
    "action": "generate",
    "description": "Generate system reports",
    "isActive": true
  }'
```

**Response:** `200 OK` (empty body)

The `resource` and `action` fields follow the `resource:action` naming convention (e.g., `user:create`, `role:read`). This makes it easy to group and query permissions by resource or action.

---

## Get All Permissions (Paginated)

**Endpoint:** `GET /permission`

```bash
# Default pagination
curl http://localhost:8080/permission \
  -H "Authorization: Bearer $TOKEN"

# Custom pagination and sorting
curl "http://localhost:8080/permission?page=0&size=10&sort=resource,asc" \
  -H "Authorization: Bearer $TOKEN"
```

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": 1,
      "name": "user:create",
      "resource": "user",
      "action": "create",
      "description": "Create new users",
      "isActive": true,
      "createdDate": "2026-01-26T14:00:00",
      "lastModifiedDate": null,
      "createdBy": null,
      "lastModifiedBy": null
    }
  ],
  "totalElements": 12,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

---

## Get Permission by ID

**Endpoint:** `GET /permission/{id}`

```bash
curl http://localhost:8080/permission/1 \
  -H "Authorization: Bearer $TOKEN"
```

---

## Update Permission

**Endpoint:** `PUT /permission/{id}`

```bash
curl -X PUT http://localhost:8080/permission/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "user:create",
    "resource": "user",
    "action": "create",
    "description": "Create new users in the system",
    "isActive": true
  }'
```

---

## Delete Permission

**Endpoint:** `DELETE /permission/{id}`

```bash
curl -X DELETE http://localhost:8080/permission/13 \
  -H "Authorization: Bearer $TOKEN"
```

---

## Query Permissions (Generic QueryDSL)

**Endpoint:** `POST /permission/query`

### Search by resource

```bash
# All permissions for the "user" resource
curl -X POST http://localhost:8080/permission/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "resource", "operation": "EQUALS", "value": "user" }
    ]
  }'
```

### Search by action

```bash
# All "delete" permissions across resources
curl -X POST http://localhost:8080/permission/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "action", "operation": "EQUALS", "value": "delete" }
    ]
  }'
```

### Search by name pattern

```bash
# All permissions that start with "user:"
curl -X POST http://localhost:8080/permission/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "name", "operation": "STARTS_WITH", "value": "user:" }
    ]
  }'
```

### Combined search with sorting

```bash
# Active permissions for "role" resource, sorted by action
curl -X POST http://localhost:8080/permission/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "resource", "operation": "EQUALS", "value": "role" },
      { "field": "isActive", "operation": "IS_TRUE" }
    ],
    "sortFields": [
      { "field": "action", "direction": "ASC" }
    ]
  }'
```

### Search by multiple resources (IN)

```bash
curl -X POST http://localhost:8080/permission/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "resource", "operation": "IN", "values": ["user", "role"] }
    ]
  }'
```

### Date range search

```bash
curl -X POST http://localhost:8080/permission/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      {
        "field": "createdDate",
        "operation": "GREATER_THAN_OR_EQUAL",
        "value": "2026-01-01T00:00:00"
      }
    ]
  }'
```

---

## Count Permissions by Query

**Endpoint:** `POST /permission/count`

```bash
# Count active permissions for "user" resource
curl -X POST http://localhost:8080/permission/count \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "resource", "operation": "EQUALS", "value": "user" },
      { "field": "isActive", "operation": "IS_TRUE" }
    ]
  }'
```

**Response:** `4` (number)

---

## Check if Permissions Exist

**Endpoint:** `POST /permission/exists`

```bash
curl -X POST http://localhost:8080/permission/exists \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      { "field": "name", "operation": "EQUALS", "value": "report:generate" }
    ]
  }'
```

---

## Export Permissions (Generic QueryDSL)

**Endpoint:** `POST /permission/export/query`

```bash
# Export all active permissions to Excel
curl -X POST http://localhost:8080/permission/export/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "queryRequest": {
      "conditions": [
        { "field": "isActive", "operation": "IS_TRUE" }
      ]
    },
    "selectedColumns": ["name", "resource", "action", "description"],
    "format": "EXCEL",
    "friendlyHeaders": {
      "name": "Permission Name",
      "resource": "Resource",
      "action": "Action",
      "description": "Description"
    }
  }' \
  -o permissions_export.xlsx

# Export to PDF
curl -X POST http://localhost:8080/permission/export/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "queryRequest": {
      "conditions": [
        { "field": "resource", "operation": "IN", "values": ["user", "role"] }
      ]
    },
    "selectedColumns": ["name", "resource", "action"],
    "format": "PDF",
    "friendlyHeaders": {
      "name": "Permission",
      "resource": "Resource",
      "action": "Action"
    }
  }' \
  -o permissions_export.pdf
```

---

## Available Search Fields

### Direct fields
| Field         | Type          | Operations                     |
|---------------|---------------|--------------------------------|
| `id`          | Long          | EQUALS, IN, GREATER_THAN, ...  |
| `name`        | String        | All string operations          |
| `resource`    | String        | All string operations          |
| `action`      | String        | All string operations          |
| `description` | String        | All string operations          |
| `isActive`    | Boolean       | IS_TRUE, IS_FALSE, EQUALS      |
| `createdDate` | LocalDateTime | BETWEEN, GREATER_THAN, ...     |

### Supported Operations Reference

| Operation                   | Applicable To      | Example Value         |
|-----------------------------|--------------------|-----------------------|
| `EQUALS`                    | All types          | `"admin"`             |
| `NOT_EQUALS`                | All types          | `"admin"`             |
| `CONTAINS`                  | String             | `"user"`              |
| `NOT_CONTAINS`              | String             | `"user"`              |
| `CONTAINS_IGNORE_CASE`      | String             | `"User"`              |
| `NOT_CONTAINS_IGNORE_CASE`  | String             | `"User"`              |
| `STARTS_WITH`               | String             | `"user:"`             |
| `STARTS_WITH_IGNORE_CASE`   | String             | `"User:"`             |
| `ENDS_WITH`                 | String             | `":read"`             |
| `ENDS_WITH_IGNORE_CASE`     | String             | `":Read"`             |
| `BETWEEN`                   | Date, Number       | startValue + endValue |
| `NOT_BETWEEN`               | Date, Number       | startValue + endValue |
| `GREATER_THAN`              | Date, Number       | `"2026-01-01T00:00"`  |
| `GREATER_THAN_OR_EQUAL`     | Date, Number       | `"2026-01-01T00:00"`  |
| `LESS_THAN`                 | Date, Number       | `"2026-12-31T23:59"`  |
| `LESS_THAN_OR_EQUAL`        | Date, Number       | `"2026-12-31T23:59"`  |
| `IN`                        | All types          | values array          |
| `NOT_IN`                    | All types          | values array          |
| `IS_NULL`                   | All types          | (no value needed)     |
| `IS_NOT_NULL`               | All types          | (no value needed)     |
| `IS_TRUE`                   | Boolean            | (no value needed)     |
| `IS_FALSE`                  | Boolean            | (no value needed)     |
