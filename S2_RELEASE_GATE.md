# S2 Release Gate (Hardening)

This checklist is the go/no-go gate for Sprint 2 hardening (`S2-09`).

## Mandatory checks

- [ ] Backend compile passes
- [ ] Tenant isolation integration tests pass
- [ ] Frontend production build passes
- [ ] DLQ replay endpoints are reachable for admin users
- [ ] Reliability metrics endpoint returns tenant-scoped data
- [ ] No critical migration errors in Flyway startup logs

## One-command gate run

```bash
bash scripts/phase2_release_gate.sh
```

## Manual spot checks (optional)

```bash
# reliability metrics (admin token required)
curl -s -H "Authorization: Bearer <TOKEN>" \
  http://localhost:8090/api/admin/reliability/metrics | cat

# dlq list (admin token required)
curl -s -H "Authorization: Bearer <TOKEN>" \
  "http://localhost:8090/api/admin/dlq?status=NEW&limit=20" | cat
```

## Rollback notes

- If backend fails after deploy, roll back app image and keep DB at current migration.
- New migrations in Sprint 2 are additive (`V700`, `V800`) and safe to keep.
- Disable auto replay if needed:
  - `sentinel.dlq.auto-replay.enabled=false`

## Gate owner sign-off

- Engineering: ________
- QA: ________
- Product: ________
- Date: ________

