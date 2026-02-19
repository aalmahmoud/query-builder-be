# Documentation Structure

## File Tree

```
querydslbuilder/
├── README.md                       # Overview, quick start, requirements
└── docs/
    ├── GETTING_STARTED.md          # Setup, database, seed data, first login
    ├── USER_GUIDE.md               # Queries, computed fields, export examples
    ├── API_REFERENCE.md            # All endpoints, request/response formats
    ├── ADVANCED.md                 # Caching, computed field internals, security
    ├── ARCHITECTURE.md             # Layers, data flow, design patterns
    ├── DOCUMENTATION_STRUCTURE.md  # This file
    └── curls/
        ├── AUTH.md                 # Authentication curl examples
        ├── USER.md                 # User endpoint curl examples
        ├── ROLE.md                 # Role endpoint curl examples
        └── PERMISSION.md          # Permission endpoint curl examples
```

## Navigation

```
README.md
 ├── Getting Started    → docs/GETTING_STARTED.md
 ├── User Guide         → docs/USER_GUIDE.md
 ├── API Reference      → docs/API_REFERENCE.md
 ├── Advanced Topics    → docs/ADVANCED.md
 ├── Architecture       → docs/ARCHITECTURE.md
 └── Curl Examples      → docs/curls/
       ├── AUTH.md
       ├── USER.md
       ├── ROLE.md
       └── PERMISSION.md
```

## Purpose of each file

| File | Audience | Focus |
|------|----------|-------|
| README.md | Everyone | First impression, features, quick start |
| GETTING_STARTED.md | New users | Setup, database, seed data, first login |
| USER_GUIDE.md | Developers | Query examples, computed fields, export |
| API_REFERENCE.md | API consumers | Endpoints, DTOs, error formats |
| ADVANCED.md | Advanced users | Caching, custom handlers, security internals |
| ARCHITECTURE.md | Architects/contributors | Layers, patterns, extension points |
| curls/*.md | API testers | Ready-to-run curl commands per controller |
