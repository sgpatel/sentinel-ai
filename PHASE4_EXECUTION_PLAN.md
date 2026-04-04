# Phase 4 Execution Plan (Weeks 13-14)

Scope: Automation and Global Readiness (Increment 1)

## Success Criteria (End of Week 14)

- Workflow automation foundation is production-ready for at least three core actions (`escalate`, `assign`, `notify`).
- Rule evaluation supports dry-run mode with full execution trace and no side effects.
- Tenant-safe auditability is in place for all workflow executions.
- Knowledge base retrieval can enrich reply generation for configured tenants.
- Phase 4 release gate checks pass in staging (compile, tests, build, smoke flow).

## Week-by-Week Plan

### Week 13 - Workflow Engine Foundation

Goals:
- Establish core workflow rule model and deterministic execution path.
- Deliver the first automation slice without breaking existing mention/reply flow.

Deliverables:
- Database schema and migrations:
  - `workflow_rules`
  - `workflow_rule_conditions`
  - `workflow_rule_actions`
  - `workflow_executions`
  - `workflow_execution_steps`
- Rule management API (tenant-scoped):
  - create/update/enable/disable/list rules
  - priority and conflict-resolution fields
- `RuleEvaluatorService`:
  - evaluate conditions (urgency, sentiment, platform, follower bands, topic tags)
  - deterministic action ordering
  - dry-run support with simulated outputs
- Action executor framework:
  - `EscalateActionExecutor`
  - `AssignReviewerActionExecutor`
  - `NotifyWebhookActionExecutor` (safe retries + timeout)
- Workflow execution audit trail:
  - per-condition result
  - per-action outcome
  - duration, failure reason, correlation id

Exit checks:
- Tenant can create and activate a rule, then observe evaluated conditions and actions in execution logs.
- Dry-run demonstrates exactly what would happen, with side effects suppressed.
- Failed action does not crash mention processing; failure is recorded and surfaced.

---

### Week 14 - KB Integration + Hardening + Increment Release Gate

Goals:
- Increase reply quality with retrieval from tenant knowledge base.
- Stabilize workflow and retrieval behavior for controlled Phase 4 rollout.

Deliverables:
- Knowledge base schema and APIs:
  - `knowledge_base_articles`
  - `knowledge_base_article_tags`
  - `knowledge_base_feedback`
  - admin CRUD endpoints with compliance visibility (`PUBLIC`, `INTERNAL`, `RESTRICTED`)
- `KnowledgeRetrievalService`:
  - keyword + vector-style retrieval abstraction (initial implementation can start keyword-only)
  - top-N snippets with source references
  - compliance-aware filtering before prompt injection
- Reply pipeline integration:
  - add KB context block to generated replies when confidence threshold is met
  - attach citation metadata for UI display and audit
- Workflow-KB bridge:
  - optional action: `attach_kb_article` for selected conditions
- Hardening and release artifacts:
  - integration tests for workflow tenant isolation and dry-run behavior
  - integration tests for KB compliance filtering and citation output
  - release gate script/checklist (`scripts/phase4_release_gate.sh`, `S4_RELEASE_GATE.md`)
  - completion report skeleton (`SPRINT4_INCREMENT1_COMPLETION_REPORT.md`)

Exit checks:
- Reply generation includes relevant KB snippets for supported scenarios without leaking restricted content.
- Workflow engine and KB retrieval both pass integration and smoke checks.
- Increment release gate reports PASS in staging.

## Recommended Sequencing After Week 14

1. VIP/influencer track (registry, auto-detection, dedicated queue).
2. Multi-language track (translation layer, regional tone profiles, cultural checks).
3. Reporting and scheduled exports (executive/compliance templates, email delivery, audit logs).
4. Full Phase 4 consolidation gate after parallel tracks stabilize.

## Key Risks and Mitigation

1. Rule complexity and unpredictable outcomes
- Mitigation: deterministic evaluation order, max actions per rule, dry-run mandatory before activation.

2. KB data quality and hallucination risk
- Mitigation: citation-only injection, confidence thresholds, strict compliance filtering.

3. Cross-tenant data leakage
- Mitigation: enforce `TenantContext` at repository/query boundary and integration tests for every new endpoint.

4. Webhook/action reliability under load
- Mitigation: bounded retries, idempotency keys, circuit breaker, dead-letter path for failed executions.

## Validation Strategy

- Unit: rule condition evaluators, action executors, KB ranking/filter logic.
- Integration: tenant scoping for workflow + KB CRUD + retrieval.
- End-to-end: mention arrives -> rule executes -> reply enriched with KB -> audit visible.
- Non-functional: timeout/retry behavior, execution latency, failure-path resilience.

