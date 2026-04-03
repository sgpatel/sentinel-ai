# Phase 1 Dependency-Ordered Sprint Board

Planning window: Weeks 1-4 (two 2-week sprints)
Source backlog: `PHASE1_JIRA_BACKLOG.csv`

## Board Rules

- Deliver Multi-Tenant foundations first; all other streams depend on tenant scoping.
- Start API/backend and DB work before frontend stories that consume those APIs.
- Add resilience stories after core flows exist, then validate with failure injection.

## Sprint 1 (Weeks 1-2) - Foundation First

### Committed Scope (dependency order)

| Order | Key | Story | Depends On | Why Now |
|---|---|---|---|---|
| 1 | S1-01 | Add TenantContext and request interceptor | None | Required to derive tenant per request |
| 2 | S1-02 | Create tenant Flyway migration and default tenant seed | None | Needed before tenant-scoped persistence |
| 3 | S1-03 | Make mention/ticket/analytics repositories tenant-scoped | S1-01, S1-02 | Core isolation behavior |
| 4 | S1-04 | Add tenant admin configuration API | S1-01, S1-02, S1-03 | Tenant settings management |
| 5 | S1-05 | Add tenant isolation integration tests | S1-03 | Proves no cross-tenant access |
| 6 | S1-06 | Add frontend tenant switcher | S1-04 | UI depends on tenant config/API |

### Stretch (only if capacity remains)

| Order | Key | Story | Depends On |
|---|---|---|---|
| 7 | S1-X1 | Add DB indexes for search performance | S1-02 |

### Sprint 1 Exit Criteria

- All mention/ticket/analytics APIs are tenant-scoped.
- Tenant admin config API works for default + non-default tenant.
- Isolation integration tests pass.
- UI can switch active tenant and reflect tenant config.

---

## Sprint 2 (Weeks 3-4) - Filtering + Recovery + Release Gate

### Committed Scope (dependency order)

| Order | Key | Story | Depends On | Why Now |
|---|---|---|---|---|
| 1 | S2-01 | Implement `GET /api/mentions/search` endpoint | S1-03 | Search must be tenant-safe |
| 2 | S2-02 | Add saved search APIs | S2-01 | Built on search contract |
| 3 | S2-03 | Build frontend query builder v1 | S2-01, S2-02 | UI depends on search + saved APIs |
| 4 | S2-04 | Add retry + exponential backoff for external calls | S1-03 | Stabilize core processing path |
| 5 | S2-05 | Add circuit breaker for external dependencies | S2-04 | Prevent retry storms/outage cascades |
| 6 | S2-06 | Implement DLQ persistence for failed mentions | S2-04 | Capture non-recoverable failures |
| 7 | S2-07 | Implement DLQ replay endpoint/job | S2-06 | Operability on failed mentions |
| 8 | S2-08 | Add reliability metrics and dashboards | S2-04, S2-05, S2-06 | Visibility into resilience controls |
| 9 | S2-09 | Staging hardening and release gate | S2-01..S2-08 | Final verification and go/no-go |

### Sprint 2 Exit Criteria

- Search + saved-search APIs are tenant/user scoped and tested.
- Query builder v1 ships with save/reuse flow.
- Retry, circuit breaker, and DLQ are active for critical integrations.
- Replay path works for single and batch DLQ items.
- Staging release gate checklist is complete.

---

## Cross-Story Dependency Graph (compact)

```text
TenantContext + Migration
        |
        v
Tenant-scoped repositories
        |
        +--> Tenant admin API --> Tenant switcher UI
        |
        +--> Search API --> Saved search API --> Query builder UI
        |
        +--> Retry --> Circuit breaker
        |        \
        |         +--> DLQ persistence --> DLQ replay
        |
        +--> Isolation tests

All tracks --> Reliability dashboards --> Staging hardening/release gate
```

## WIP Limits and Parallelism

- Backend WIP: max 3 stories in progress.
- Frontend WIP: max 2 stories in progress.
- DB/migrations: max 1 high-risk migration story at once.
- Tests/reliability stories can run in parallel after core backend APIs land.

## Suggested Owner Lanes

- Backend lane: S1-01, S1-03, S1-04, S2-01, S2-02, S2-04, S2-05, S2-06, S2-07
- Frontend lane: S1-06, S2-03
- Data lane: S1-02, S1-X1
- QA/Platform lane: S1-05, S2-08, S2-09

