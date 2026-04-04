# S4 Release Gate (Phase 4 Increment 1)

This checklist is the go/no-go gate for Phase 4 Weeks 13-14 hardening (`S5-12`).

## Mandatory checks

- [ ] Backend compile passes
- [ ] Baseline tenant isolation integration test passes
- [ ] Workflow integration tests pass (dry-run behavior + audit trail)
- [ ] KB integration tests pass (compliance filtering + citations)
- [ ] Frontend production build passes
- [ ] Workflow audit endpoints return tenant-scoped records
- [ ] No critical migration errors in Flyway startup logs

## One-command gate run

```bash
bash scripts/phase4_release_gate.sh
```

## Manual spot checks (optional)

```bash
# list active workflow rules (admin token required)
curl -s -H "Authorization: Bearer <TOKEN>" \
  "http://localhost:8090/api/workflows/rules?status=ACTIVE" | cat

# sample workflow executions (admin token required)
curl -s -H "Authorization: Bearer <TOKEN>" \
  "http://localhost:8090/api/workflows/executions?limit=20" | cat

# sample KB retrieval preview (admin token required)
curl -s -H "Authorization: Bearer <TOKEN>" \
  "http://localhost:8090/api/admin/kb/search?q=refund%20delay" | cat
```

## Rollback notes

- If backend deploy fails, roll back app image and keep DB at current migration.
- Phase 4 increment-1 migrations are additive; keep schema and disable workflow rules until fixed.
- To reduce blast radius during rollback:
  - disable rule execution via feature flag (if present)
  - disable KB context injection in reply generation (if present)

## Gate owner sign-off

- Engineering: ________
- QA: ________
- Product: ________
- Date: ________

