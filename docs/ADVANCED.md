# Advanced Topics

## Table of Contents

1. [Caching](#caching)
2. [Computed Fields](#computed-fields)
3. [Projections & DTOs](#projections--dtos)
4. [Security Internals](#security-internals)
5. [Performance Optimization](#performance-optimization)

---

## Caching

The QueryDSL system uses two levels of in-memory caching for optimal performance.

### Q-Entity Cache

Stores compiled QueryDSL Q-class instances. Key: entity class; Value: Q-entity instance.

```java
private static final Map<Class<?>, Object> Q_ENTITY_CACHE = new ConcurrentHashMap<>(32);
```

- First query: ~150ms (reflection + cache population)
- Subsequent: ~10ms (cached) — **15x faster**

### Field Lookup Cache

Stores reflected `Field` objects for dot-notation field navigation.

```java
private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>(128);
```

- First lookup: ~50ms
- Subsequent: ~1ms — **50x faster**

### Cache Characteristics

- Permanent (no TTL), thread-safe (`ConcurrentHashMap`)
- Q-Entity cache: ~10-20 entries, ~20KB total
- Field cache: ~100-200 entries, ~50-100KB total
- No eviction needed — memory impact is negligible

---

## Computed Fields

Computed fields allow virtual field aliases that resolve to complex expressions at query time. They are Spring `@Component` beans auto-discovered via classpath scanning.

### Architecture

```
QueryCondition(field="fullName") 
  → ComputedFieldRegistry.findHandler(User.class, "fullName")
  → UserFullNameHandler.buildPredicate(...)
  → concat(firstName, ' ', lastName).containsIgnoreCase(value)
```

### Built-in handlers

| Handler | Entity | Field | Expression |
|---------|--------|-------|-----------|
| `UserFullNameHandler` | User | `fullName` | `concat(firstName, ' ', lastName)` |
| `UserRoleNameHandler` | User | `roleName` | `role.name` |
| `UserPermissionHandler` | User | `permissionName` | `role.permissions.any().name` |
| `RolePermissionNameHandler` | Role | `permissionName` | `permissions.any().name` |

### Creating a custom handler

```java
@Component
public class UserEmailDomainHandler implements TypedComputedFieldHandler<User, QUser> {

    @Override
    public Class<User> getEntityClass() { return User.class; }

    @Override
    public String getFieldName() { return "emailDomain"; }

    @Override
    public Predicate buildPredicate(QUser qUser, QueryCondition condition) {
        StringExpression domain = Expressions.stringTemplate(
            "substring({0}, locate('@', {0}) + 1)", qUser.email);
        String value = condition.getValue().toString();
        return switch (condition.getEffectiveOperation()) {
            case EQUALS -> domain.eq(value);
            case CONTAINS_IGNORE_CASE -> domain.containsIgnoreCase(value);
            default -> domain.eq(value);
        };
    }
}
```

---

## Projections & DTOs

### Standard approach: Entity → DTO mapping

```java
public Page<UserResponseDto> getAllUsersByQueryRequest(Pageable pageable, QueryRequest request) {
    return genericQueryService.findAllByQueryRequest(userRepository, request, pageable)
            .map(userMapper::toUserResponseDto);
}
```

### Lightweight summary DTOs

Create a summary mapper for specific use cases:

```java
private UserSummaryDto toSummary(User user) {
    UserSummaryDto dto = new UserSummaryDto();
    dto.setId(user.getId());
    dto.setFullName(user.getFirstName() + " " + user.getLastName());
    dto.setEmail(user.getEmail());
    dto.setRoleName(user.getRole() != null ? user.getRole().getName() : null);
    return dto;
}
```

### QueryDSL Projections (advanced)

For high-performance queries selecting only specific columns:

```java
QUser user = QUser.user;
jpaQueryFactory()
    .select(Projections.constructor(UserSummaryDto.class,
        user.id, user.firstName, user.lastName, user.email, user.role.name))
    .from(user)
    .where(predicate)
    .offset(pageable.getOffset())
    .limit(pageable.getPageSize())
    .fetch();
```

---

## Security Internals

### Authentication flow

```
POST /auth/login (username, password)
  → AuthController
  → AuthenticationManager.authenticate()
  → CustomUserDetailsService.loadUserByUsername()
  → BCryptPasswordEncoder.matches()
  → JwtTokenProvider.generateToken()
  → Response: { token, type, username, authorities }
```

### Request authentication flow

```
Request with Authorization: Bearer <token>
  → JwtAuthenticationFilter (OncePerRequestFilter)
  → JwtTokenProvider.validateToken()
  → JwtTokenProvider.getUsernameFromToken()
  → CustomUserDetailsService.loadUserByUsername()
  → SecurityContextHolder.setAuthentication()
  → Controller
```

### JWT structure

- Algorithm: HS256
- Subject: username (email)
- Claims: authorities (comma-separated roles + permissions)
- Expiration: configurable via `jwt.expiration` (default 24h)

### Authorization

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) {
    http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/auth/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
        .requestMatchers("/actuator/health").permitAll()
        .requestMatchers("/user/**").hasAnyRole("USER", "ADMIN", "MANAGER")
        .requestMatchers("/role/**").hasAnyRole("ADMIN", "MANAGER")
        .requestMatchers("/permission/**").hasRole("ADMIN")
        .anyRequest().authenticated()
    );
}
```

### Password storage

All passwords are hashed with BCrypt before storage. The `UserService.addUser()` and `updateUser()` methods call `passwordEncoder.encode()`. The mapper never touches the password field directly.

---

## Performance Optimization

### Performance metrics

| Operation | First call | Cached | Improvement |
|-----------|-----------|--------|-------------|
| Q-entity load | ~150ms | ~10ms | 15x |
| Field lookup | ~50ms | ~1ms | 50x |
| Query execution | Depends on DB | Same | — |

### Recommendations

1. **Database indexes** — add indexes on frequently queried fields
2. **Pagination** — always use pagination; configure max page size
3. **Condition limits** — max 50 conditions enforced by validation
4. **Field depth limits** — max 5 levels of dot-notation nesting
5. **DTOs** — return only needed fields via mappers
6. **Connection pool** — HikariCP is the default; tune `maximum-pool-size` for your workload

### Query security limits

| Limit | Value | Purpose |
|-------|-------|---------|
| `MAX_CONDITIONS` | 50 | Prevent resource-exhaustion queries |
| `MAX_FIELD_PATH_DEPTH` | 5 | Prevent deep-nesting attacks |
| `max values (IN)` | 1000 | Limit array size |

---

**See also:** [Architecture](ARCHITECTURE.md) | [User Guide](USER_GUIDE.md) | [API Reference](API_REFERENCE.md)
