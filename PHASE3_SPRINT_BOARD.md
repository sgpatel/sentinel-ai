# Phase 3 Dependency-Ordered Sprint Board

Planning window: Weeks 9-12 (two 2-week sprints)
Source backlog: `PHASE3_JIRA_BACKLOG.csv`

## Sprint 3 (Weeks 9-10)

### Committed scope (dependency order)

| Order | Key | Story | Depends On | Why now |
|---|---|---|---|---|
| 1 | S3-01 | Create `ChannelConnector` interface and factory | None | Foundation for all channel posting |
| 2 | S3-02 | Implement `MockChannelConnector` | S3-01 | Safe default path and tests |
| 3 | S3-03 | Implement Twitter connector scaffold | S3-01 | First real external channel |
| 4 | S3-04 | Add reply posting endpoint | S3-01, S3-02 | API entry point for orchestration |
| 5 | S3-05 | Persist channel posting results | S3-04 | Auditability + post status visibility |
| 6 | S3-06 | Create PredictionService | None | Core predictive capability |
| 7 | S3-07 | Add prediction history persistence | S3-06 | Store outputs for tuning/audit |
| 8 | S3-08 | Emit `alert.predicted_crisis` event | S3-06 | Operational alerting |
| 9 | S3-09 | Virality escalation rule | S3-06 | Response automation |

### Sprint 3 exit criteria

- Approved replies can be posted through connector abstraction.
- Prediction outputs are generated and stored for triggered mentions.
- Crisis alert event is emitted when threshold conditions are met.

---

## Sprint 4 (Weeks 11-12)

### Committed scope (dependency order)

| Order | Key | Story | Depends On | Why now |
|---|---|---|---|---|
| 1 | S4-01 | Add competitor handle config | None | Input for competitive analytics |
| 2 | S4-02 | Competitive sentiment comparison API | S4-01 | Primary benchmark metric |
| 3 | S4-03 | Competitive volume trend API | S4-01 | Time-series benchmarking |
| 4 | S4-04 | Share-of-voice API | S4-01 | Market position indicator |
| 5 | S4-05 | Frontend benchmark cards | S4-02, S4-03, S4-04 | User-visible competitive insights |
| 6 | S4-06 | Frontend channel-post action | S3-04, S3-05 | End-to-end multi-channel UX |
| 7 | S4-07 | Prediction analytics endpoint | S3-07 | Observe model quality over time |
| 8 | S4-08 | Phase 3 hardening + release gate | S3-01..S4-07 | Go/no-go readiness |

### Sprint 4 exit criteria

- Competitive analytics endpoints and UI are tenant-safe and production-ready.
- Channel posting UX is integrated with reply workflow.
- Phase 3 release gate passes on staging.

---

## Dependency graph (compact)

```text
ChannelConnector + Factory
    ├── MockConnector
    ├── TwitterConnector
    └── Reply Post Endpoint ──> Posting Result Persistence ──> Frontend Post Action

PredictionService
    ├── Prediction History
    ├── Crisis Event
    └── Virality Escalation ──> Prediction Analytics Endpoint

Competitor Config
    ├── Sentiment Comparison API
    ├── Volume Trend API
    └── Share-of-Voice API ──> Frontend Benchmark Cards

All streams ──> Hardening + Release Gate
```

## WIP limits

- Backend WIP: 3 stories max.
- Frontend WIP: 2 stories max.
- DB migrations: 1 in-flight change at a time.
- Integration tests can begin once first API in each stream lands.

## Suggested owner lanes

- Backend/API lane: S3-01..S3-09, S4-01..S4-04, S4-07
- Frontend lane: S4-05, S4-06
- Data lane: S3-05, S3-07, S4-01
- QA/release lane: S4-08

