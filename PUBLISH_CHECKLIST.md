# Publish Checklist — query-builder-be & query-builder-ui

> **Delete this file when done.** It exists only as a portable continuation note so the
> publish work can resume from any machine. Tracked in git purely for sync; not part of
> the project documentation.

**Author:** Abdullah Almahmoud · [LinkedIn](https://sa.linkedin.com/in/asalmahmoud) · [GitHub @aalmahmoud](https://github.com/aalmahmoud)
**Branches that hold the legendary code:**
- BE: `feature/legendary-query-engine` → https://github.com/aalmahmoud/query-builder-be
- FE: `align-backend-contract` → https://github.com/aalmahmoud/query-builder-ui

---

## 1. Change the GitHub default branch on BOTH repos

GitHub currently defaults to `master` on both, but the legendary work lives on feature
branches. Without this step, every link in the LinkedIn post lands visitors on the
older, pre-legendary code.

**BE:** https://github.com/aalmahmoud/query-builder-be/settings/branches
→ Default branch → switch icon → pick `feature/legendary-query-engine` → **Update**.

**FE:** https://github.com/aalmahmoud/query-builder-ui/settings/branches
→ Default branch → switch icon → pick `align-backend-contract` → **Update**.

---

## 2. Set repo descriptions + topics

Both repos currently have `null` descriptions on GitHub. On each repo's main page,
click the ⚙ next to "About" and set:

**BE description:**
> A metadata-driven JSON query engine for Spring Boot — recursive AND/OR, projections, group-by aggregations, keyset cursors, saved queries. Includes a publishable library and an Angular reference UI.

**BE topics:** `spring-boot` `querydsl` `java` `jpa` `query-builder` `rest-api` `angular` `library`

**FE description:**
> Angular 21 admin UI for the generic-querydsl engine — recursive query builder, projection picker, aggregation panel, saved queries, all metadata-driven.

**FE topics:** `angular` `typescript` `material` `signals` `query-builder` `standalone-components`

---

## 3. Capture & commit screenshots

Both READMEs reference these. Until they exist on disk, GitHub will show
broken-image icons.

**Capture from the running UI** (`localhost:4200`) and save to `docs/screenshots/`
in **both** repos with these exact filenames:

| File | What to capture |
|---|---|
| `recursive-builder.png` | Users page, query builder open with top-level `AND` and a nested `OR` sub-group containing `Role Name = ADMIN` and `Email contains "system"`. Results visible underneath. |
| `aggregation-panel.png` | Users → **Aggregate** toggle on, Group by `Role Name`, two metrics (`COUNT`, `MAX · id`), result table populated. |
| `projection-picker.png` | Users → **Columns** menu open with 2–3 columns ticked, projected table visible underneath. |
| `saved-queries.png` | **Saved** menu open showing at least one saved query (e.g. "Active admins") in the list. |
| `demo.gif` *(optional)* | ~6-second screen recording: click "+ Group" → switch to OR → add a condition → Search. Sells the recursion. |

Commit them to **each** repo on its legendary branch:

```bash
# BE
mkdir -p docs/screenshots
cp ~/screenshots/*.png docs/screenshots/
git add docs/screenshots/
git commit -m "docs: add UI screenshots for README"
git push

# FE — same flow
```

---

## 4. (Optional) Rotate the test JWT + AES key

`src/test/resources/application-test.properties` in BE contains real-looking
secrets that are test-only (H2 in-memory, no blast radius). Secret-scanning bots
will flag them anyway. To silence the flags:

```bash
# generate fresh
NEW_JWT=$(openssl rand -base64 48)
NEW_AES=$(openssl rand -base64 32)
# edit src/test/resources/application-test.properties — replace the two values
git commit -am "chore: rotate test-only secrets"
git push
```

No history rewrite needed — these were never real secrets.

---

## 5. LinkedIn post — copy-paste ready

Best to post mid-morning Tue–Thu for engagement. Bare repo URLs render as link
previews on LinkedIn (don't shorten them).

```
🚀 Shipped something I'm proud of: a metadata-driven JSON query engine for Spring Boot + Angular.

Most enterprise apps grow a swamp of hand-written findByXAndYAndZOrW… methods — one per entity, one per combo. Then someone asks for "search by anything" and the team starts over.

So I built one engine that gives every entity the full toolset for free:

🔹 Recursive AND / OR groups — (A AND (B OR C)) straight from JSON, depth-capped
🔹 24 operations — string, numeric, date, IN, BETWEEN, IS_NULL… all type-aware
🔹 Self-describing /{entity}/metadata — the Angular UI builds every dropdown from this. Zero hard-coded field lists.
🔹 @FilterableFields allow-list — sensitive columns like password literally cannot be filtered, projected, or aggregated. Closes a security gap most query libraries leave wide open.
🔹 Sparse-fieldset projections — select: [...] returns flat rows
🔹 Group-by aggregations — COUNT/SUM/AVG/MIN/MAX, with filters applied first
🔹 Keyset (cursor) pagination — O(1) page-after-page on large tables
🔹 Saved queries, scoped per user

Stack: Spring Boot 3.5 · Java 21 · QueryDSL 5.1 · Flyway · JWT/RBAC · AES-256-GCM column encryption · Angular 21 standalone + Signals + Material.

The engine ships as a publishable library; the demo app (User / Role / Permission admin) is its reference consumer. Compared head-to-head with RSQL, Spring Data Specifications, and specification-arg-resolver, it solves at least four problems each of them leaves on the floor.

🔗 Backend: https://github.com/aalmahmoud/query-builder-be
🔗 Frontend: https://github.com/aalmahmoud/query-builder-ui

Feedback, PRs, and tough questions welcome — especially from anyone who's wrestled with the same swamp.

#SpringBoot #Java #Angular #QueryDSL #SoftwareEngineering #BackendDevelopment #OpenSource
```

---

## 6. After the post is live — delete this file

```bash
git rm PUBLISH_CHECKLIST.md
git commit -m "chore: drop publish checklist"
git push
```

---

## Quick state reference

- **Repos verified public:** ✅ (both, default branch `master`)
- **Secret audit:** ✅ no real secrets in either history (only test fixtures in BE)
- **READMEs:** ✅ rewritten on both legendary branches (feature-first, comparison table, screenshot placeholders, author block, v0.9-RC status footer)
- **Local working tree to never stage:** `src/main/resources/application.properties` (your local DB creds)
