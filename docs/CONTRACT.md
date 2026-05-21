# Query Engine Contract — v2 (frozen)

This is the **single source of truth** for the BE↔FE contract of the legendary query
engine. Both `query-builder-be` and `query-builder-ui` build against this document.
All shapes are backward compatible with v1 (a flat `conditions[]` query still works).

> **Convention:** field names are `camelCase` JSON. All request bodies are JSON. All
> paginated responses use the `PageResponse<T>` envelope.

---

## 1. QueryCondition (leaf predicate) — unchanged from v1

```jsonc
{
  "field": "email",              // dot-notation path allowed: "role.name"; or a computed field
  "operation": "CONTAINS_IGNORE_CASE",
  "value": "john",               // single-value ops
  "values": ["A", "B"],          // IN / NOT_IN
  "startValue": "2026-01-01",    // BETWEEN / NOT_BETWEEN
  "endValue":   "2026-12-31"
}
```

`operation` defaults to `EQUALS` if omitted.

### QueryOperation (full set)
```
EQUALS, NOT_EQUALS,
CONTAINS, NOT_CONTAINS, CONTAINS_IGNORE_CASE, NOT_CONTAINS_IGNORE_CASE,
STARTS_WITH, NOT_STARTS_WITH, STARTS_WITH_IGNORE_CASE, NOT_STARTS_WITH_IGNORE_CASE,
ENDS_WITH, NOT_ENDS_WITH, ENDS_WITH_IGNORE_CASE, NOT_ENDS_WITH_IGNORE_CASE,
BETWEEN, NOT_BETWEEN,
GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL,
IN, NOT_IN,
IS_NULL, IS_NOT_NULL,
IS_TRUE, IS_FALSE
```

---

## 2. QueryGroup (NEW — recursive AND/OR)

A group combines its own `conditions` **and** its nested `groups` using `logic`.

```jsonc
{
  "logic": "AND",                // "AND" | "OR"  (default "AND")
  "conditions": [ /* QueryCondition[] */ ],
  "groups": [ /* QueryGroup[] (recursive) */ ]
}
```

**Semantics:** `predicate(group) = reduce(logic, [pred(c) for c in conditions] + [predicate(g) for g in groups])`.
An empty group (no conditions, no groups) contributes no predicate (matches all).

---

## 3. QueryRequest v2

The request **is itself a top-level group**, plus sorting / projection / pagination hints.

```jsonc
{
  "logic": "AND",                // optional, default "AND"
  "conditions": [ /* QueryCondition[] */ ],
  "groups": [ /* QueryGroup[] */ ],
  "sortFields": [ { "field": "createdDate", "direction": "DESC" } ],
  "select": ["id", "email", "role.name"]   // optional projection (see §6); null/[] = full DTO
}
```

### Backward compatibility (MUST hold)
- A v1 body `{ "conditions": [...], "sortFields": [...] }` is a valid v2 request: it is the
  top-level group with implicit `logic="AND"`, no nested `groups`, no `select`.
- `groups`, `logic`, `select` are all optional and additive.

### Example — `(status=ACTIVE) AND (role=ADMIN OR age>30)`
```jsonc
{
  "logic": "AND",
  "conditions": [ { "field": "isActive", "operation": "IS_TRUE" } ],
  "groups": [
    { "logic": "OR", "conditions": [
        { "field": "role.name", "operation": "EQUALS", "value": "ADMIN" },
        { "field": "age", "operation": "GREATER_THAN", "value": 30 } ] }
  ]
}
```

### Validation limits (across the whole tree)
- Total conditions (all groups, recursively) ≤ `MAX_CONDITIONS` (50).
- Group nesting depth ≤ `MAX_GROUP_DEPTH` (5).
- Field path segments ≤ 5; field name matches `^[a-zA-Z0-9_.]+$`.
- IN/NOT_IN values ≤ `MAX_IN_VALUES` (200).
- `sortFields` ≤ 10.

---

## 4. SortField — unchanged
```jsonc
{ "field": "createdDate", "direction": "ASC" }   // direction: "ASC" | "DESC"
```

## 5. PageResponse<T> — unchanged
```jsonc
{ "content": [ /* T[] */ ], "page": 0, "size": 20, "totalElements": 42,
  "totalPages": 3, "first": true, "last": false, "empty": false }
```

---

## 6. Projections (sparse fieldsets)

When `QueryRequest.select` is non-empty, `POST /{entity}/query` returns rows as
**flat maps** keyed by the requested field paths instead of full DTOs:

`PageResponse<Map<String,Object>>`, e.g.:
```jsonc
{ "content": [ { "id": 1, "email": "a@x.com", "role.name": "ADMIN" } ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "first": true, "last": true, "empty": false }
```
- Only `filterable`/selectable fields may be selected (same allow-list as filtering, §9).
- Keys are the exact `select` strings (dot paths preserved).

---

## 7. Aggregations

`POST /{entity}/aggregate`

```jsonc
{
  "filter": { /* a QueryRequest group: logic/conditions/groups — applied before grouping */ },
  "groupBy": ["role.name"],                 // 0..n field paths (0 = whole-set aggregate)
  "metrics": [
    { "fn": "COUNT", "field": null,  "alias": "total" },
    { "fn": "AVG",   "field": "age", "alias": "avgAge" }
  ]
}
```
`fn` ∈ `COUNT | SUM | AVG | MIN | MAX`. `field` is required for all except `COUNT` (where it may be null = count rows).

**Response:** `AggregationResult`
```jsonc
{
  "rows": [
    { "group": { "role.name": "ADMIN" }, "metrics": { "total": 3, "avgAge": 41.5 } },
    { "group": { "role.name": "USER"  }, "metrics": { "total": 12, "avgAge": 29.0 } }
  ]
}
```
- Only `filterable` fields may be grouped by; only numeric fields may be SUM/AVG/MIN/MAX'd.

---

## 8. Cursor (keyset) pagination — optional, opt-in

For large datasets, `POST /{entity}/query/cursor`:

```jsonc
{ "query": { /* QueryRequest */ }, "cursor": null, "size": 20 }
```
- Sort is fixed to `id DESC` (the unique, monotonic PK — a stable keyset with no timestamp-precision pitfalls). `query.sortFields` is ignored on this endpoint.
- Response: `CursorPage<T>`:
```jsonc
{ "content": [ /* T[] */ ], "nextCursor": "eyJjZCI6...", "hasNext": true }
```
- `nextCursor` is an opaque base64 token; pass it back as `cursor` for the next page. `null` cursor = first page.

---

## 9. Field metadata — `GET /{entity}/metadata`

Drives a self-describing FE (no hardcoded field lists).

```jsonc
{
  "entity": "user",
  "fields": [
    { "name": "email", "label": "Email", "type": "string",
      "operations": ["EQUALS","NOT_EQUALS","CONTAINS","CONTAINS_IGNORE_CASE","STARTS_WITH","ENDS_WITH","IN","NOT_IN","IS_NULL","IS_NOT_NULL"],
      "sortable": true, "filterable": true, "computed": false },
    { "name": "isActive", "label": "Active", "type": "boolean",
      "operations": ["EQUALS","IS_TRUE","IS_FALSE","IS_NULL","IS_NOT_NULL"],
      "sortable": true, "filterable": true, "computed": false },
    { "name": "role.name", "label": "Role", "type": "string", "operations": ["..."],
      "sortable": false, "filterable": true, "computed": false },
    { "name": "fullName", "label": "Full Name", "type": "string", "operations": ["..."],
      "sortable": false, "filterable": true, "computed": true }
  ]
}
```

### Type → default operation map (BE authoritative; FE may mirror for offline)
- `string`  → EQUALS, NOT_EQUALS, CONTAINS(+IGNORE_CASE), STARTS_WITH/ENDS_WITH(+IGNORE_CASE & negations), IN, NOT_IN, IS_NULL, IS_NOT_NULL
- `number`  → EQUALS, NOT_EQUALS, GREATER_THAN(_OR_EQUAL), LESS_THAN(_OR_EQUAL), BETWEEN, NOT_BETWEEN, IN, NOT_IN, IS_NULL, IS_NOT_NULL
- `date`/`datetime` → EQUALS, NOT_EQUALS, GREATER_THAN(_OR_EQUAL), LESS_THAN(_OR_EQUAL), BETWEEN, NOT_BETWEEN, IS_NULL, IS_NOT_NULL
- `boolean` → EQUALS, IS_TRUE, IS_FALSE, IS_NULL, IS_NOT_NULL
- `enum`    → EQUALS, NOT_EQUALS, IN, NOT_IN, IS_NULL, IS_NOT_NULL (+ `enumValues`)
- `uuid`    → EQUALS, NOT_EQUALS, IN, NOT_IN, IS_NULL, IS_NOT_NULL

### Security allow-lists
- **Filtering & projection & group-by** are restricted by `@FilterableFields` on the entity.
  If present, only listed fields (+ computed) are filterable; everything else is rejected
  (400). Sensitive columns (e.g. `password`) are excluded — closes the boolean-oracle hole.
- **Sorting** restricted by `@SortableFields` (existing).
- `metadata.fields` lists only filterable/sortable fields, so the FE never offers a field
  the BE will reject.

---

## 10. Saved queries — `/{entity}/saved-queries`

Persisted, named QueryRequests for reuse.

- `GET    /{entity}/saved-queries`           → `SavedQuery[]`
- `POST   /{entity}/saved-queries`           → create `{ name, queryRequest }` → 201 + body
- `DELETE /{entity}/saved-queries/{id}`      → 204

```jsonc
// SavedQuery
{ "id": 7, "entity": "user", "name": "Active admins",
  "queryRequest": { /* QueryRequest v2 */ },
  "createdBy": "admin@system.com", "createdDate": "2026-05-21T10:00:00" }
```
Scoped per-entity and per-owner (createdBy). ADMIN/MANAGER/USER may manage their own.

---

## 11. Endpoint summary (per entity: user, role, permission)

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/{entity}/query` | QueryRequest v2 | `PageResponse<DTO>` or `PageResponse<Map>` (if `select`) |
| POST | `/{entity}/query/cursor` | `{query,cursor,size}` | `CursorPage<DTO>` |
| POST | `/{entity}/count` | QueryRequest v2 | `long` |
| POST | `/{entity}/exists` | QueryRequest v2 | `boolean` |
| POST | `/{entity}/aggregate` | AggregationRequest | `AggregationResult` |
| GET  | `/{entity}/metadata` | — | `EntityMetadata` |
| GET  | `/{entity}/saved-queries` | — | `SavedQuery[]` |
| POST | `/{entity}/saved-queries` | `{name,queryRequest}` | `SavedQuery` (201) |
| DELETE | `/{entity}/saved-queries/{id}` | — | 204 |
| POST | `/{entity}/export/query` | ExportWithQueryRequest | file |

CRUD (`POST/GET/PUT/DELETE /{entity}`, `PUT /user/{id}/change-status`) unchanged.

---

## 12. Versioning note
v2 is additive and backward compatible. The engine accepts v1 bodies unchanged. FE should
prefer `/metadata` to discover fields/operations rather than hardcoding them.
