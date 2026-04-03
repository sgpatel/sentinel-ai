# Phase 3 Execution Plan (Weeks 9-12)

Scope: Multi-Channel Orchestration + Viral Prediction + Competitive Intelligence

## Success Criteria (End of Week 12)

- Multi-channel posting abstraction is live with at least one production-ready connector and one safe fallback connector.
- Predictive virality pipeline runs for high-risk mentions and emits actionable alerts.
- Competitive analytics endpoints deliver side-by-side sentiment/volume trends for configured competitor handles.
- Tenant isolation remains intact across all new endpoints.
- Release gate checks pass in staging (compile, tests, build, smoke flow).

## Week-by-Week Plan

### Week 9 - Multi-Channel Foundation

Goals:
- Introduce channel posting abstraction and execution model.
- Keep current approval workflow intact while adding post-to-channel capability.

Deliverables:
- `ChannelConnector` interface + factory/registry.
- `MockChannelConnector` (always available).
- First real connector scaffold (Twitter/X) with config-driven enablement.
- Mention reply-post endpoint (`POST /api/replies/{mentionId}/post`).
- Persist channel posting results (status, channel, externalPostId, errors).

Exit checks:
- One-click post sends approved replies to selected channel(s).
- Disabled channel connectors fail gracefully with clear response.

---

### Week 10 - Viral Prediction + Alerting

Goals:
- Add prediction signal generation for potentially viral incidents.
- Convert prediction output into operational alerts.

Deliverables:
- `PredictionService` with configurable trigger conditions.
- Virality score calculation (short-term and 24h horizon).
- Alert event publishing (`alert.predicted_crisis`) via WebSocket.
- Priority escalation policy for high virality scores.
- Prediction history persistence table + endpoints.

Exit checks:
- High-risk mention triggers prediction and alert in near real-time.
- Prediction data is queryable for audit and model tuning.

---

### Week 11 - Competitive Intelligence

Goals:
- Ingest and analyze competitor activity using tenant-configured handles.
- Expose benchmark views in API and UI.

Deliverables:
- Competitor handle config (tenant-scoped).
- Competitive analytics endpoint family:
  - sentiment comparison
  - volume trend comparison
  - share-of-voice snapshot
- Baseline UI card/tab for side-by-side benchmark.
- Caching/indexing for competitive queries.

Exit checks:
- Tenant can view own-vs-competitor sentiment and volume comparison for selected window.

---

### Week 12 - Hardening + Release Gate

Goals:
- Stabilize Phase 3 behavior under failures and load.
- Produce release-ready artifacts and handoff docs.

Deliverables:
- Integration tests for:
  - channel posting access + tenant safety
  - prediction endpoint + alert behavior
  - competitive analytics tenant scoping
- Release gate script and checklist (`phase3_release_gate.sh`, `S3_RELEASE_GATE.md`).
- Final completion report (`SPRINT3_COMPLETION_REPORT.md`).

Exit checks:
- Build/tests pass and release gate reports PASS.
- Rollback notes and runbook updated.

## Key Risks and Mitigation

1. External channel API instability
- Mitigation: connector-level retries, feature flags, fallback to mock channel.

2. False-positive prediction noise
- Mitigation: tune thresholds by tenant and add suppression windows.

3. Query cost for competitive analytics
- Mitigation: indexes + bounded lookback + response caching.

4. Cross-tenant leaks on new endpoints
- Mitigation: enforce `TenantContext` in all new repositories/controllers + integration tests.

## Validation Strategy

- Unit: connector contract tests, prediction score logic, benchmark calculators.
- Integration: tenant safety checks for all new APIs.
- UI: smoke test post-to-channel and benchmark widgets.
- Non-functional: endpoint latency and error-path tests.

