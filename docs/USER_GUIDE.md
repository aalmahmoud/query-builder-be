# User Guide

Complete guide to querying, computed fields, and exporting data.

> All examples assume you have a JWT token. See [Getting Started](GETTING_STARTED.md) or [Auth Curls](curls/AUTH.md).

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@system.com","password":"admin123"}' \
  | jq -r '.token')
```

---

## Basic Queries

### Active users

```bash
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

### Users by role ID

```bash
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      {"field": "role.id", "operation": "EQUALS", "value": "1"}
    ]
  }'
```

### Case-insensitive name search

```bash
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      {"field": "firstName", "operation": "CONTAINS_IGNORE_CASE", "value": "john"}
    ]
  }'
```

### Date range

```bash
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      {
        "field": "createdDate",
        "operation": "BETWEEN",
        "startValue": "2026-01-01T00:00:00",
        "endValue": "2026-12-31T23:59:59"
      }
    ]
  }'
```

### IN operation

```bash
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      {"field": "email", "operation": "IN", "values": ["admin@system.com", "manager@system.com"]}
    ]
  }'
```

### Default operation

If you omit `operation`, it defaults to `EQUALS`:

```json
{"field": "isActive", "value": true}
// same as
{"field": "isActive", "operation": "EQUALS", "value": true}
```

---

## Nested Field Navigation (Dot Notation)

Navigate entity relationships with `.` separator:

```bash
# Users with ADMIN role (User → Role.name)
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      {"field": "role.name", "operation": "EQUALS", "value": "ADMIN"}
    ]
  }'

# Users whose role has "user:create" permission (User → Role → Permission.name)
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      {"field": "role.permissions.name", "operation": "EQUALS", "value": "user:create"}
    ]
  }'

# Roles that have any "user:" permissions (Role → Permission.name)
curl -X POST http://localhost:8080/role/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      {"field": "permissions.name", "operation": "STARTS_WITH", "value": "user:"}
    ]
  }'
```

Collection relationships (ManyToMany) are automatically traversed with QueryDSL's `any()`.

---

## Computed Fields

Computed fields are virtual aliases that simplify common queries. They are registered as Spring components and resolved automatically by the query engine.

### User entity computed fields

| Field | Maps to | Description |
|-------|---------|-------------|
| `fullName` | `concat(firstName, ' ', lastName)` | Search by combined name |
| `roleName` | `role.name` | Shorthand for role name |
| `permissionName` | `role.permissions.any().name` | Search by any permission |

### Role entity computed fields

| Field | Maps to | Description |
|-------|---------|-------------|
| `permissionName` | `permissions.any().name` | Search by any permission |

### Examples

```bash
# Search by full name (computed)
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      {"field": "fullName", "operation": "CONTAINS_IGNORE_CASE", "value": "system admin"}
    ]
  }'

# Users with ADMIN or MANAGER role (computed)
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      {"field": "roleName", "operation": "IN", "values": ["ADMIN", "MANAGER"]}
    ]
  }'

# Users who can delete users (computed)
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      {"field": "permissionName", "operation": "EQUALS", "value": "user:delete"}
    ]
  }'
```

### Creating your own computed fields

Implement `TypedComputedFieldHandler<Entity, QEntity>` and annotate with `@Component`:

```java
@Component
public class MyHandler implements TypedComputedFieldHandler<User, QUser> {
    public Class<User> getEntityClass() { return User.class; }
    public String getFieldName() { return "myField"; }
    public Predicate buildPredicate(QUser q, QueryCondition c) {
        // build and return predicate
    }
}
```

It will be auto-discovered and registered.

---

## Complex Queries

### Multiple conditions with sorting

All conditions are combined with AND:

```bash
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      {"field": "isActive", "operation": "IS_TRUE"},
      {"field": "roleName", "operation": "EQUALS", "value": "ADMIN"},
      {"field": "createdDate", "operation": "GREATER_THAN", "value": "2026-01-01T00:00:00"}
    ],
    "sortFields": [
      {"field": "createdDate", "direction": "DESC"},
      {"field": "lastName", "direction": "ASC"}
    ]
  }'
```

### Count and exists

```bash
# Count active users
curl -X POST http://localhost:8080/user/count \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"conditions": [{"field": "isActive", "operation": "IS_TRUE"}]}'

# Check if email exists
curl -X POST http://localhost:8080/user/exists \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"conditions": [{"field": "email", "operation": "EQUALS", "value": "admin@system.com"}]}'
```

---

## Export

### Export to Excel

```bash
curl -X POST http://localhost:8080/user/export/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "queryRequest": {
      "conditions": [{"field": "isActive", "operation": "IS_TRUE"}]
    },
    "selectedColumns": ["firstName", "lastName", "email"],
    "format": "EXCEL",
    "friendlyHeaders": {
      "firstName": "First Name",
      "lastName": "Last Name",
      "email": "Email Address"
    }
  }' -o users.xlsx
```

### Export to PDF

```bash
curl -X POST http://localhost:8080/role/export/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "queryRequest": {
      "conditions": [{"field": "isActive", "operation": "IS_TRUE"}]
    },
    "selectedColumns": ["name", "description"],
    "format": "PDF",
    "friendlyHeaders": {"name": "Role Name", "description": "Description"}
  }' -o roles.pdf
```

---

## Queryable Fields Reference

### User

| Direct fields | Nested fields | Computed fields |
|--------------|---------------|-----------------|
| `firstName`, `lastName`, `email` | `role.id`, `role.name` | `fullName` |
| `mobileNumber`, `nationalId` | `role.isActive` | `roleName` |
| `isActive`, `createdDate` | `role.permissions.name` | `permissionName` |
| `lastModifiedDate`, `createdBy` | `role.permissions.resource` | |

### Role

| Direct fields | Nested fields | Computed fields |
|--------------|---------------|-----------------|
| `name`, `description`, `isActive` | `permissions.id` | `permissionName` |
| `createdDate`, `lastModifiedDate` | `permissions.name`, `permissions.resource` | |

### Permission

| Direct fields |
|--------------|
| `name`, `resource`, `action`, `description`, `isActive` |
| `createdDate`, `lastModifiedDate`, `createdBy`, `lastModifiedBy` |

---

## Best Practices

1. Use computed fields for common cross-entity queries
2. Combine multiple conditions for precise filtering
3. Always paginate large result sets (`?page=0&size=20`)
4. Add sorting for consistent results
5. Use `CONTAINS_IGNORE_CASE` for user-facing search
6. Use the `/count` endpoint before large exports

---

**See also:** [Curl Examples](curls/) | [API Reference](API_REFERENCE.md) | [Advanced Topics](ADVANCED.md)
