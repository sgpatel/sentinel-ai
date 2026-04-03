# Sprint 2 Completion Report

Date: 2026-04-04
Scope: `S2-01` through `S2-09` from `PHASE1_SPRINT_BOARD.md`

## Release summary

Sprint 2 hardening and delivery work is complete across backend + frontend, including:
- Tenant-safe mention search with pagination/sorting.
- Saved search CRUD (tenant+user scoped).
- Retry + exponential backoff for external pipeline operations.
- Circuit breaker controls for repeated external failures.
- DLQ persistence for non-recoverable processing failures.
- DLQ replay endpoints + scheduled replay job.
- Reliability metrics endpoint and admin dashboard visibility.
- Release gate checklist + one-command gate script.

## Ticket-by-ticket completion

### `S2-01` Implement `GET /api/mentions/search`
Status: ✅ Complete

Implemented:
- Tenant-scoped dynamic search endpoint in `sentinel-backend/src/main/java/io/sentinel/backend/api/MentionController.java`.
- Added JPA specification support in `sentinel-backend/src/main/java/io/sentinel/backend/repository/MentionRepository.java`.
- Filters: `q`, `sentiment`, `priority`, `urgency`, `topic`, `minFollowers`, `maxFollowers`, `fromEpochMs`, `toEpochMs`.
- Pagination/sort: `page`, `size`, `sortBy`, `direction` with sort allowlist hardening.

Validation evidence:
- Search coverage added in `sentinel-backend/src/test/java/io/sentinel/backend/api/TenantIsolationIntegrationTest.java`:
  - `searchMentionsIsTenantScoped()`
  - `searchMentionsSupportsSentimentAndFollowerFilters()`

---

### `S2-02` Saved search APIs
Status: ✅ Complete

Implemented:
- Migration: `sentinel-backend/src/main/resources/db/sentinel/V700__saved_searches.sql`.
- Entity/repository:
  - `sentinel-backend/src/main/java/io/sentinel/backend/repository/SavedSearchEntity.java`
  - `sentinel-backend/src/main/java/io/sentinel/backend/repository/SavedSearchRepository.java`
- API endpoints in `sentinel-backend/src/main/java/io/sentinel/backend/api/MentionController.java`:
  - `GET /api/saved-searches`
  - `POST /api/saved-searches`
  - `PUT /api/saved-searches/{id}`
  - `DELETE /api/saved-searches/{id}`
- Scope enforcement: current tenant + authenticated user.

Validation evidence:
- Integration test coverage in `sentinel-backend/src/test/java/io/sentinel/backend/api/TenantIsolationIntegrationTest.java`:
  - `savedSearchesAreScopedByTenantAndUser()`
  - `savedSearchUpdateDeleteBlockedAcrossTenants()`

---

### `S2-03` Frontend query builder v1
Status: ✅ Complete

Implemented:
- Frontend API helpers/hooks in `sentinel-frontend/src/hooks/useSentinel.ts`:
  - `searchMentions(...)`
  - `useSavedSearches()`
  - `createSavedSearch(...)`
  - `updateSavedSearch(...)`
  - `deleteSavedSearch(...)`
- Feed UI query builder + save/apply/delete flow in `sentinel-frontend/src/App.tsx`.
- Feed list now supports server-backed advanced-search result source.

Validation evidence:
- Frontend production build completed successfully.

---

### `S2-04` Retry + exponential backoff
Status: ✅ Complete

Implemented:
- Retry wrapper in `sentinel-backend/src/main/java/io/sentinel/backend/service/MentionProcessingService.java`:
  - configurable attempts and base delay
  - exponential backoff (`delay *= 2`)
- Applied to external calls:
  - `SentimentAgent`, `EscalationAgent`, `ReplyAgent`, `ComplianceAgent`, `TicketAgent`, `TicketConnector`.

Config knobs:
- `sentinel.retry.max-attempts` (default `3`)
- `sentinel.retry.base-delay-ms` (default `200`)

---

### `S2-05` Circuit breaker
Status: ✅ Complete

Implemented:
- Per-operation circuit state in `sentinel-backend/src/main/java/io/sentinel/backend/service/MentionProcessingService.java`.
- Circuit opens after configurable consecutive failures; requests fail-fast while open.
- Circuit resets on successful call.

Config knobs:
- `sentinel.circuit-breaker.failure-threshold` (default `5`)
- `sentinel.circuit-breaker.open-ms` (default `30000`)

---

### `S2-06` DLQ persistence
Status: ✅ Complete

Implemented:
- Migration: `sentinel-backend/src/main/resources/db/sentinel/V800__mention_dlq.sql`.
- Entity/repository:
  - `sentinel-backend/src/main/java/io/sentinel/backend/repository/MentionDlqEntity.java`
  - `sentinel-backend/src/main/java/io/sentinel/backend/repository/MentionDlqRepository.java`
- Fatal pipeline exceptions now persist to DLQ from `sentinel-backend/src/main/java/io/sentinel/backend/service/MentionProcessingService.java`.

---

### `S2-07` DLQ replay endpoint/job
Status: ✅ Complete

Implemented:
- Replay service: `sentinel-backend/src/main/java/io/sentinel/backend/service/MentionDlqService.java`.
  - Single replay, batch replay, optional scheduled auto-replay.
- Admin endpoints in `sentinel-backend/src/main/java/io/sentinel/backend/security/AdminController.java`:
  - `GET /api/admin/dlq`
  - `POST /api/admin/dlq/{id}/replay`
  - `POST /api/admin/dlq/replay`
- Tenant scoping enforced on list/replay operations.

Validation evidence:
- Integration test coverage in `sentinel-backend/src/test/java/io/sentinel/backend/api/TenantIsolationIntegrationTest.java`:
  - `dlqReplayIsTenantScoped()`
  - `dlqBatchReplayRespectsTenantContext()`

---

### `S2-08` Reliability metrics and dashboard
Status: ✅ Complete

Implemented:
- Pipeline reliability metrics snapshot from `sentinel-backend/src/main/java/io/sentinel/backend/service/MentionProcessingService.java`.
- Aggregated reliability endpoint in `sentinel-backend/src/main/java/io/sentinel/backend/security/AdminController.java`:
  - `GET /api/admin/reliability/metrics`
- DLQ count helpers in `sentinel-backend/src/main/java/io/sentinel/backend/repository/MentionDlqRepository.java`.
- Frontend integration:
  - `useReliabilityMetrics(...)` in `sentinel-frontend/src/hooks/useSentinel.ts`
  - Admin reliability card in `sentinel-frontend/src/App.tsx`

Validation evidence:
- Added test `reliabilityMetricsAreTenantScopedAndSwitchableForAdmin()` in `sentinel-backend/src/test/java/io/sentinel/backend/api/TenantIsolationIntegrationTest.java`.

---

### `S2-09` Staging hardening + release gate
Status: ✅ Complete

Implemented:
- Gate checklist doc: `S2_RELEASE_GATE.md`.
- Gate script: `scripts/phase2_release_gate.sh`.
- Script runs backend compile, tenant isolation tests, frontend build, doc presence checks, and status snapshot.

Validation evidence:
- Gate script executed and reported PASS on 2026-04-04.

## Final validation summary

Verified during execution:
- Backend compile: pass.
- Frontend build: pass.
- Tenant isolation integration suite: pass.
- Flyway migrations applied through:
  - `V600__tenants_table.sql`
  - `V700__saved_searches.sql`
  - `V800__mention_dlq.sql`

## Runbook commands

```bash
cd /Users/deekshasingh/workspace/sentinel-ai
mvn -q -DskipTests compile
mvn -q -pl sentinel-backend -Dtest=TenantIsolationIntegrationTest test
cd sentinel-frontend
npm run -s build
```

```bash
cd /Users/deekshasingh/workspace/sentinel-ai
bash scripts/phase2_release_gate.sh
```

## Outstanding follow-ups (post Sprint 2)

- Optional: add dedicated tests for retry/circuit transitions (unit-level simulation).
- Optional: add a frontend DLQ operations panel (list/replay buttons) in admin UI.
- Optional: add Prometheus export for reliability counters.

