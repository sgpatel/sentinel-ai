# Phase 1 Execution Plan (Weeks 1-4)

Scope: Multi-Tenant Foundation + Advanced Filtering/Saved Searches + Error Recovery/Resilience

## Success Criteria (End of Week 4)

- Tenant isolation is enforced for all mention/ticket/analytics APIs.
- Cross-tenant access tests pass (negative and positive paths).
- `GET /api/mentions/search` supports agreed filters and pagination.
- Saved searches are tenant/user-scoped and reusable from UI.
- Retry + DLQ + circuit breaker are active for key external calls.
- Staging release is complete with rollback steps documented.

## Week-by-Week Plan

### Week 1 - Multi-Tenant Core

Goals:
- Establish tenant context from auth token.
- Add tenant-aware persistence behavior.
- Add database foundations and migration.

Deliverables:
- `TenantContext` + HTTP interceptor/filter to extract tenant claim.
- Flyway migration for tenant tables and default tenant.
- `tenant_id` backfill and index strategy for hot tables.
- Repository/service updates: all reads/writes scoped by tenant.
- Integration test skeleton for isolation.

Exit checks:
- Any read/write request without tenant context is rejected or safely defaulted (as designed).
- Existing flows still work for default tenant.

---

### Week 2 - Multi-Tenant Admin + Hardening

Goals:
- Enable tenant configuration and admin workflows.
- Close auth/authorization gaps.

Deliverables:
- Admin API for tenant config (`handle`, `brandName`, `brandTone`, feature flags).
- Frontend tenant selector + active tenant persistence.
- RBAC checks for admin endpoints.
- Audit logs for tenant config changes.
- Full integration tests for tenant isolation.

Exit checks:
- Tenant switching works from UI and reflects in backend responses.
- No cross-tenant reads in tests.

---

### Week 3 - Filtering + Saved Searches

Goals:
- Deliver analyst-facing search/filtering UX.
- Keep all search paths tenant-safe.

Deliverables:
- `GET /api/mentions/search` with filter model:
  - sentiment, priority, urgency, topic, follower range, keyword, date range
- Pagination/sort controls.
- Saved search APIs (create/list/update/delete).
- Frontend query builder (basic form-based v1) + save/reuse presets.
- Performance checks and DB indexes for filter paths.

Exit checks:
- Search correctness validated by API tests.
- Saved searches are user+tenant scoped.

---

### Week 4 - Recovery + Release Readiness

Goals:
- Improve reliability under external/API failures.
- Make failures observable and recoverable.

Deliverables:
- Retry with exponential backoff for LLM + connector calls.
- Circuit breaker for external dependencies.
- DLQ persistence for failed mention processing.
- Replay endpoint/job to reprocess DLQ items.
- Metrics/alerts: retry count, breaker state, DLQ depth.
- Staging rollout + rollback runbook.

Exit checks:
- Failure injection tests pass (timeout, connector down, malformed response).
- No silently dropped mentions in tested failure scenarios.

## Risk Register (Phase 1)

1. Tenant migration complexity
- Mitigation: ship default tenant first, backfill with guarded migration scripts.

2. Query performance regressions after tenant filters
- Mitigation: add composite indexes (`tenant_id`, `posted_at`, `priority`, `sentiment`).

3. Frontend complexity for query builder
- Mitigation: release v1 form-based builder now; advanced drag/drop in later phase.

4. Retry storms during outages
- Mitigation: cap retries + jitter + circuit breaker + DLQ fallback.

## Testing Strategy

- Unit tests:
  - Tenant context extraction and fallback behavior.
  - Search filter parsing and repository criteria.
  - Retry/circuit breaker policy behavior.
- Integration tests:
  - Cross-tenant access denial.
  - Search/saved-search behavior by tenant/user.
  - DLQ write + replay flow.
- Non-functional:
  - Basic load test for search endpoints.
  - Failure injection for external connectors.

## Release Gate Checklist

- [ ] Migrations applied cleanly in staging.
- [ ] Tenant isolation test suite green.
- [ ] Search APIs validated against production-like sample data.
- [ ] Reliability tests green (retry/breaker/DLQ).
- [ ] Dashboards/alerts configured.
- [ ] Rollback steps reviewed by team.

