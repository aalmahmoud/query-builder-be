# Authentication API

Base URL: `http://localhost:8080/auth`

The authentication controller handles JWT token generation. All other API endpoints (except Swagger and Actuator health) require a valid JWT token obtained from this endpoint.

## Login

Authenticates a user and returns a JWT token.

**Endpoint:** `POST /auth/login`  
**Access:** Public (no token required)

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin@system.com",
    "password": "admin123"
  }'
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "type": "Bearer",
  "username": "admin@system.com",
  "authorities": "ROLE_ADMIN,user:create,user:read,user:update,user:delete,role:create,..."
}
```

**Error — invalid credentials (401 Unauthorized):**
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Bad credentials",
  "timestamp": "2026-01-26T14:00:00"
}
```

---

## Seed Users

The following default users are created by the `V2__Seed_data.sql` migration:

| Email                | Password     | Role    |
|----------------------|--------------|---------|
| admin@system.com     | admin123     | ADMIN   |
| manager@system.com   | manager123   | MANAGER |
| user@system.com      | user123      | USER    |

---

## Using the Token

After login, include the token in the `Authorization` header for all subsequent requests:

```bash
# Store the token in a variable
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@system.com","password":"admin123"}' \
  | jq -r '.token')

# Use the token in subsequent requests
curl http://localhost:8080/user \
  -H "Authorization: Bearer $TOKEN"
```

---

## Authorization Rules

| Endpoint             | Required Role                  |
|----------------------|--------------------------------|
| `/auth/login`        | Public                         |
| `/user/**`           | ROLE_USER, ROLE_ADMIN, ROLE_MANAGER |
| `/role/**`           | ROLE_ADMIN, ROLE_MANAGER       |
| `/permission/**`     | ROLE_ADMIN                     |
| `/**/export/**`      | user:read, role:read, or permission:read |
| `/swagger-ui/**`     | Public                         |
| `/actuator/health`   | Public                         |
| `/actuator/info`     | Public                         |
