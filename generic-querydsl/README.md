# generic-querydsl

A JSON-driven query engine for Spring Data JPA. Instead of writing N hand-rolled
`findByX` / `findByXAndY` methods per entity, every entity gets `query`, `count`,
and `exists` capabilities driven by a single `QueryRequest` JSON payload. That
payload is compiled into a QueryDSL `Predicate` by one shared, reflection-cached
predicate builder, so adding filtering to a new entity is a matter of declaring a
repository interface — not writing more query methods.

## Install

Gradle (`build.gradle`):

```groovy
dependencies {
    implementation 'com.example.querydsl:generic-querydsl:1.0.0'
}
```

Requirements:

- **Java 21**
- **Spring Boot 3.5.x** (Spring Data JPA + QueryDSL 5.1.0, jakarta variant)

The library ships a Spring Boot auto-configuration registered via
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
It component-scans its own packages, so you do **not** need to add it to your
`@SpringBootApplication(scanBasePackages = ...)`. Just put it on the classpath.

## Quick start — make an entity queryable

### 1. Define a JPA entity

```java
@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private BigDecimal price;
    @ManyToOne private Category category;
    // getters / setters
}
```

QueryDSL `Q`-classes are **not** shipped by this library — the engine resolves the
`Q`-class for your entity at runtime. Your own build must generate them with the
QueryDSL annotation processor:

```groovy
annotationProcessor 'com.querydsl:querydsl-apt:5.1.0:jakarta'
annotationProcessor 'jakarta.persistence:jakarta.persistence-api'
```

After adding/renaming entity fields, rerun your build so the `Q`-classes regenerate.

### 2. Declare a repository

Extend both `JpaRepository` and `GenericQueryRepository`, and override
`getEntityClass()` so the engine can locate the matching `Q`-class:

```java
@Repository
public interface ProductRepository
        extends JpaRepository<Product, Long>, GenericQueryRepository<Product, Long> {

    @Override
    default Class<Product> getEntityClass() {
        return Product.class;
    }
}
```

That single interface gives you `findAllByQueryRequest(QueryRequest, Pageable)`,
`countByQueryRequest(...)`, `existsByQueryRequest(...)`, and the
`...WithAdditionalPredicates(...)` variants (which combine your `QueryRequest`
with a hand-built `BooleanBuilder` for business rules).

### 3. Wire it into a service / controller

`GenericQueryService` is an auto-registered `@Service`. Inject it and pass your
repository in:

```java
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository repository;
    private final GenericQueryService queryService;

    @PostMapping("/query")
    public Page<Product> query(@Valid @RequestBody QueryRequest request,
                               @PageableDefault Pageable pageable) {
        return queryService.findAllByQueryRequest(repository, request, pageable);
    }

    @PostMapping("/count")
    public long count(@Valid @RequestBody QueryRequest request) {
        return queryService.countByQueryRequest(repository, request);
    }

    @PostMapping("/exists")
    public boolean exists(@Valid @RequestBody QueryRequest request) {
        return queryService.existsByQueryRequest(repository, request);
    }
}
```

> Map entities to response DTOs (e.g. with MapStruct) before returning them — the
> samples above return entities for brevity only.

## The QueryRequest contract

A `QueryRequest` is a list of `conditions` (AND-combined) plus optional `sortFields`:

```json
{
  "conditions": [
    { "field": "name",          "operation": "CONTAINS_IGNORE_CASE", "value": "phone" },
    { "field": "price",         "operation": "BETWEEN", "startValue": 100, "endValue": 500 },
    { "field": "category.name", "operation": "IN", "values": ["Electronics", "Audio"] },
    { "field": "isActive",      "operation": "IS_TRUE" }
  ],
  "sortFields": [
    { "field": "price", "direction": "DESC" },
    { "field": "id",    "direction": "ASC" }
  ]
}
```

Which `QueryCondition` payload field to set depends on the operation:

- `value` — single-value operations (EQUALS, CONTAINS, GREATER_THAN, …)
- `values` — list-membership operations (IN, NOT_IN)
- `startValue` + `endValue` — range operations (BETWEEN, NOT_BETWEEN)
- nothing — unary operations (IS_NULL, IS_NOT_NULL, IS_TRUE, IS_FALSE)

If `operation` is omitted it defaults to `EQUALS`.

**Supported `operation` values** (from `QueryOperation`):

```
EQUALS                 NOT_EQUALS
CONTAINS               NOT_CONTAINS
CONTAINS_IGNORE_CASE   NOT_CONTAINS_IGNORE_CASE
STARTS_WITH            NOT_STARTS_WITH
STARTS_WITH_IGNORE_CASE NOT_STARTS_WITH_IGNORE_CASE
ENDS_WITH              NOT_ENDS_WITH
ENDS_WITH_IGNORE_CASE  NOT_ENDS_WITH_IGNORE_CASE
BETWEEN                NOT_BETWEEN
GREATER_THAN           GREATER_THAN_OR_EQUAL
LESS_THAN              LESS_THAN_OR_EQUAL
IN                     NOT_IN
IS_NULL                IS_NOT_NULL
IS_TRUE                IS_FALSE
```

**Field paths** support dot-notation to traverse relationships, e.g.
`category.name` or `category.parent.name`.

**Security guards** (enforced by the engine; violations yield a 400-style error):

- Max **50** conditions per request
- Max field-path depth of **5** segments (e.g. `a.b.c.d.e`)
- Max **200** values in an `IN` / `NOT_IN` list
- Field names must match `^[a-zA-Z0-9_.]+$`
- Max **10** sort fields per request

## Sortable fields

Sorting is allow-listed per entity via the `@SortableFields` annotation. A sort
request for any field not on the list is rejected:

```java
@Entity
@SortableFields({ "id", "name", "price", "createdDate", "category.name" })
public class Product { /* ... */ }
```

If an entity has **no** `@SortableFields` annotation, the engine falls back to a
conservative default allow-list of `id` and `createdDate` only. (Filtering via
`conditions` is unaffected by this annotation — it only governs `sortFields`.)

## Computed / virtual fields (optional)

To expose a field that isn't a plain entity column (a concatenation, a derived
flag, a join-spanning alias), implement `TypedComputedFieldHandler<T, Q>` and
annotate it `@Component`. It is auto-discovered. The `getFieldName()` you return
is the value clients put in `QueryCondition.field`, and a computed field shadows
any same-named real field.

```java
@Component
public class ProductFullLabelHandler
        implements TypedComputedFieldHandler<Product, QProduct> {

    @Override public Class<Product> getEntityClass() { return Product.class; }

    @Override public String getFieldName() { return "fullLabel"; }

    @Override
    public Predicate buildPredicate(QProduct q, QueryCondition condition) {
        return q.category.name.concat(" / ").concat(q.name)
                .containsIgnoreCase(String.valueOf(condition.getValue()));
    }
}
```

Clients then send `{ "field": "fullLabel", "operation": "CONTAINS", "value": "audio" }`.

## Known limitation: single engine instance per context

The repository default methods reach the engine through a static singleton,
`QueryPredicateBuilder.getInstance()`. The backing field is `volatile` and is
written exactly once at startup (during the engine bean's `@PostConstruct`), so
for a standard single-`ApplicationContext` Spring Boot application this is correct
and thread-safe — every HTTP request thread observes the fully-constructed engine.

The design assumes **one engine instance per JVM / Spring context**. It is fine
for normal Boot apps, but it is **not** designed for scenarios that run multiple
independent Spring contexts in the same JVM (e.g. several isolated child contexts
each expecting their own engine), since they would share the one static singleton.
