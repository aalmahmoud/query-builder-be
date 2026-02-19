# Getting Started

## Prerequisites

- Java 21+
- PostgreSQL
- Gradle 8.x

## Setup

### 1. Clone and build

```bash
git clone <repository-url>
cd querydslbuilder
./gradlew build
```

### 2. Configure database

Create a PostgreSQL database and set credentials via environment variables or directly in `application.properties`:

```bash
export DB_USERNAME=your_username
export DB_PASSWORD=your_password
```

Or edit `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/query_builder
spring.datasource.username=${DB_USERNAME:your_username}
spring.datasource.password=${DB_PASSWORD:your_password}
```

### 3. Run

```bash
./gradlew bootRun
```

Flyway automatically creates all tables and seeds initial data on first run.

## Seed Data

The `V2__Seed_data.sql` migration creates default data so you can start testing immediately:

**Permissions** (12 total): CRUD for `user`, `role`, and `permission` resources.

**Roles:**

| Role | Permissions |
|------|------------|
| ADMIN | All 12 permissions |
| MANAGER | All user + role permissions, permission:read |
| USER | Read-only (user:read, role:read, permission:read) |

**Users:**

| Email | Password | Role |
|-------|----------|------|
| admin@system.com | admin123 | ADMIN |
| manager@system.com | manager123 | MANAGER |
| user@system.com | user123 | USER |

## First Login

```bash
# Get a JWT token
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@system.com","password":"admin123"}' \
  | jq -r '.token')

# Test: list users
curl http://localhost:8080/user \
  -H "Authorization: Bearer $TOKEN"

# Test: query active users
curl -X POST http://localhost:8080/user/query \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "conditions": [
      {"field": "isActive", "operation": "IS_TRUE"}
    ]
  }'
```

## Available Endpoints

| URL | Description |
|-----|-------------|
| `http://localhost:8080/swagger-ui.html` | Swagger UI (no auth required) |
| `http://localhost:8080/actuator/health` | Health check (no auth required) |
| `http://localhost:8080/auth/login` | Login (no auth required) |
| `http://localhost:8080/user/**` | User API (ROLE_USER, ROLE_ADMIN, ROLE_MANAGER) |
| `http://localhost:8080/role/**` | Role API (ROLE_ADMIN, ROLE_MANAGER) |
| `http://localhost:8080/permission/**` | Permission API (ROLE_ADMIN only) |

## Database Schema

All entities extend `BaseEntity` with JPA auditing:

- `id` (Long) — auto-generated primary key
- `createdDate` (LocalDateTime) — set on creation
- `lastModifiedDate` (LocalDateTime) — updated on modification
- `createdBy` / `lastModifiedBy` (String) — audit trail

Tables: `users`, `roles`, `permissions`, `role_permissions` (join table).

## Next Steps

- [User Guide](USER_GUIDE.md) — Query examples and best practices
- [Curl Examples](curls/AUTH.md) — Ready-to-run curl commands
- [API Reference](API_REFERENCE.md) — Full endpoint documentation
