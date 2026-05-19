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

### 2. Configure database and JWT secret

Create a PostgreSQL database and set credentials via environment variables or directly in `application.properties`:

```bash
export DB_USERNAME=your_username
export DB_PASSWORD=your_password
```

**Required:** set a JWT signing secret of at least 32 bytes (256 bits, HS256 requirement).
The app refuses to start if this is missing or too short:

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
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
# Get a JWT access token + refresh token
RESP=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@system.com","password":"admin123"}')
TOKEN=$(echo "$RESP" | jq -r '.token')
REFRESH=$(echo "$RESP" | jq -r '.refreshToken')

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

# Refresh the access token (the refresh token is rotated on each call):
RESP=$(curl -s -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}")
TOKEN=$(echo "$RESP" | jq -r '.token')
REFRESH=$(echo "$RESP" | jq -r '.refreshToken')

# Logout (revokes the refresh token):
curl -X POST http://localhost:8080/auth/logout \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}"
```

### Rate limit

`POST /auth/login` is rate-limited to **5 attempts per minute per IP**. The 6th request
in the same minute returns `429 Too Many Requests` with a `Retry-After` header. Override:

```properties
security.login.rate-limit.capacity=10
security.login.rate-limit.refill-period-seconds=60
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
