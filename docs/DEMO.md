# Demo Walkthrough — the legendary query builder

A guided tour of the advanced query engine and its Angular front end. Every API
example below was run against the seeded database and works as shown.

- Backend: `query-builder-be` (branch `feature/legendary-query-engine`)
- Frontend: `query-builder-ui` (branch `align-backend-contract`)
- Contract reference: [`docs/CONTRACT.md`](CONTRACT.md)

---

## 0. Start both apps

**Backend** (Postgres on `localhost:5432`, secrets in `application.properties` or env — see
[GETTING_STARTED](GETTING_STARTED.md)):

```bash
cd query-builder-be
./gradlew bootRun           # http://localhost:8080  (Swagger: /swagger-ui.html)
```

**Frontend**:

```bash
cd query-builder-ui
npm install && npm start    # http://localhost:4200
```

Log in with the seeded admin: **`admin@system.com` / `admin123`**.

For the API examples, grab a token once:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin@system.com","password":"admin123"}' | jq -r .token)
AUTH="Authorization: Bearer $TOKEN"
```

---

## 1. Self-describing metadata (the moat)

**UI:** open **Users**. The query builder's field and operation lists are populated
from the backend — nothing is hard-coded in the front end.

**API:** `GET /user/metadata`

```bash
curl -s http://localhost:8080/user/metadata -H "$AUTH" | jq '.fields[] | {name,type,sortable,filterable}'
```

Every field carries its logical type, valid operations, and sortable/filterable/computed
flags. Note `password` and `nationalId` are **absent** — they're not in the entity's
`@FilterableFields` allow-list, so the engine never advertises (or accepts) them.

---

## 2. Recursive AND / OR groups

The headline feature: nested boolean logic, e.g. **active users who are admins _or_
whose email contains "system"**.

**UI:** in the Users filter builder:
1. Top group logic = **AND**, add condition `Active · is true`.
2. Click **+ Group**, set the sub-group to **OR**.
3. In the OR group add `Role Name · equals · ADMIN` and `Email · contains · system`.
4. **Search**.

**API:** `POST /user/query`

```bash
curl -s -X POST "http://localhost:8080/user/query?size=5" -H "$AUTH" -H 'Content-Type: application/json' -d '{
  "logic":"AND",
  "conditions":[{"field":"isActive","operation":"IS_TRUE"}],
  "groups":[{"logic":"OR","conditions":[
    {"field":"roleName","operation":"EQUALS","value":"ADMIN"},
    {"field":"email","operation":"CONTAINS_IGNORE_CASE","value":"system"}]}]
}' | jq '{total:.totalElements, emails:[.content[].email]}'
```

A flat v1 body (`{"conditions":[...]}`) still works unchanged — it's just a top-level
AND group.

---

## 3. Security — the filter allow-list

Filtering by a sensitive column is rejected, so it can't be used as a boolean oracle:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/user/exists \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"conditions":[{"field":"password","operation":"STARTS_WITH","value":"$2a$"}]}'
# → 400   "Field 'password' is not filterable on entity User"
```

---

## 4. Projections (sparse fieldsets)

Return only the columns you ask for, as flat rows.

**UI:** Users → **Columns** menu → tick `Email`, `Role Name`. The table switches to a
projected view showing just those fields.

**API:** add `select` to the query body:

```bash
curl -s -X POST "http://localhost:8080/user/query?size=3" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"select":["id","email","role.name"],"sortFields":[{"field":"id","direction":"ASC"}]}' | jq '.content'
# → [ {"id":1,"email":"admin@system.com","role.name":"ADMIN"}, ... ]
```

---

## 5. Aggregations (group-by + metrics)

Turn the query tool into a reporting tool.

**UI:** Users → **Aggregate** toggle. Set **Group by** = `Role Name`, add metrics
`COUNT` and `MAX · id`, then **Run**. The current filter (if any) is applied first.

**API:** `POST /user/aggregate`

```bash
curl -s -X POST http://localhost:8080/user/aggregate -H "$AUTH" -H 'Content-Type: application/json' -d '{
  "groupBy":["role.name"],
  "metrics":[{"fn":"COUNT","alias":"total"},{"fn":"MAX","field":"id","alias":"maxId"}]
}' | jq '.rows'
# → [ {"group":{"role.name":"ADMIN"},"metrics":{"total":1,"maxId":1}}, ... ]
```

`SUM/AVG/MIN/MAX` require a numeric field; `COUNT` with no field counts rows. Group-by
and metric fields obey the same allow-list as filtering.

---

## 6. Keyset (cursor) pagination

Stable, cheap pagination for large tables — sorted by `id DESC` (newest first).

**API:** `POST /user/query/cursor`

```bash
# page 1
P1=$(curl -s -X POST http://localhost:8080/user/query/cursor -H "$AUTH" -H 'Content-Type: application/json' -d '{"size":2}')
echo "$P1" | jq '{emails:[.content[].email], hasNext, nextCursor}'

# page 2 — feed nextCursor back
CUR=$(echo "$P1" | jq -r .nextCursor)
curl -s -X POST http://localhost:8080/user/query/cursor -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"size\":2,\"cursor\":\"$CUR\"}" | jq '{emails:[.content[].email], hasNext}'
```

`nextCursor` is an opaque token; pass it back until `hasNext` is `false`.

---

## 7. Saved queries

Persist and reuse named queries, scoped to the logged-in user.

**UI:** build any filter, open the **Saved** menu, type a name → **save**. It appears in
the list; click it to load it back into the builder and run it; trash-icon to delete.

**API:** `/user/saved-queries`

```bash
# create
curl -s -X POST http://localhost:8080/user/saved-queries -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Active admins","queryRequest":{"conditions":[{"field":"isActive","operation":"IS_TRUE"}]}}' | jq '{id,name}'

# list (only yours)
curl -s http://localhost:8080/user/saved-queries -H "$AUTH" | jq '.[] | {id,name}'

# delete
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE http://localhost:8080/user/saved-queries/1 -H "$AUTH"   # → 204
```

---

## 8. Same surface on every entity

Everything above works identically for **Roles** (`/role/...`) and **Permissions**
(`/permission/...`) — the engine is generic and the UI pages are metadata-driven, so
each entity gets the full builder/projection/aggregation/saved-query toolset for free.

---

## Suggested 3-minute demo path (UI)

1. **Users** → show the builder is metadata-driven; build a nested **AND/OR** query → Search.
2. **Aggregate** toggle → group by Role, COUNT → Run.
3. **Columns** → pick a few fields → show the projected table.
4. **Saved** → save the current query, reload it.
5. Switch to **Roles** → same toolset, different entity.
6. (Optional) Swagger UI → show `/user/metadata` and the rejected `password` filter (400).
