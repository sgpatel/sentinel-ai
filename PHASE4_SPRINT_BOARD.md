# Phase 4 Dependency-Ordered Sprint Board

Planning window: Weeks 13-14 (one 2-week sprint)
Source backlog: `PHASE4_JIRA_BACKLOG.csv`

## Sprint 5 (Weeks 13-14)

### Committed scope (dependency order)

| Order | Key | Story | Depends On | Why now |
|---|---|---|---|---|
| 1 | S5-01 | Create workflow rule schema and migrations | None | Foundation for all automation |
| 2 | S5-02 | Add workflow rule management API | S5-01 | Tenant-safe CRUD for rules |
| 3 | S5-03 | Implement `RuleEvaluatorService` | S5-01 | Deterministic condition/action pipeline |
| 4 | S5-04 | Add dry-run execution mode | S5-03 | Safe rollout and operator confidence |
| 5 | S5-05 | Build action executors (`escalate`, `assign`, `notify`) | S5-03 | First production automation actions |
| 6 | S5-06 | Persist workflow execution audit trail | S5-03, S5-05 | Traceability and compliance |
| 7 | S5-07 | Create KB schema + admin CRUD APIs | None | Data foundation for retrieval |
| 8 | S5-08 | Implement `KnowledgeRetrievalService` | S5-07 | Fetch relevant KB snippets |
| 9 | S5-09 | Integrate KB retrieval into reply pipeline | S5-08 | User-visible quality improvement |
| 10 | S5-10 | Add `attach_kb_article` workflow action | S5-05, S5-08 | Bridge rules and knowledge enrichment |
| 11 | S5-11 | Integration tests for workflow + KB tenant safety | S5-02, S5-06, S5-07, S5-09 | Release confidence |
| 12 | S5-12 | Phase 4 increment release gate | S5-01..S5-11 | Go/no-go readiness |

### Sprint 5 exit criteria

- Workflow rules are tenant-scoped, deterministic, and auditable.
- Dry-run mode logs intended actions without side effects.
- Reply generation can include compliant KB citations.
- Increment release gate passes in staging.

---

## Dependency graph (compact)

```text
Workflow Schema
    ├── Rule Management API
    ├── Rule Evaluator ──> Dry Run
    ├── Action Executors
    └── Execution Audit Trail

KB Schema + Admin CRUD
    └── Knowledge Retrieval ──> Reply Pipeline Integration
                              └── attach_kb_article Action

All streams ──> Integration Tests ──> Release Gate
```

## WIP limits

- Backend WIP: 3 stories max.
- Data/migrations WIP: 1 change set at a time.
- Frontend/API-consumer WIP: 2 stories max.
- QA/release WIP: 1 end-to-end lane at a time.

## Suggested owner lanes

- Backend/API lane: S5-02, S5-03, S5-04, S5-05, S5-08, S5-09, S5-10
- Data lane: S5-01, S5-06, S5-07
- QA/release lane: S5-11, S5-12

## Lane split (parallel execution)

| Lane | Candidate owner | Stories | Exit target |
|---|---|---|---|
| Workflow engine lane | _Owner TBD_ | S5-01, S5-02, S5-03, S5-04, S5-05, S5-06 | Deterministic rule execution + dry-run + audit |
| Knowledge base lane | _Owner TBD_ | S5-07, S5-08, S5-09, S5-10 | KB retrieval and citation enrichment live |
| QA/release lane | _Owner TBD_ | S5-11, S5-12 | Integration confidence and release gate PASS |

