#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "[gate] root: $ROOT_DIR"

cd "$ROOT_DIR"
echo "[gate] 1/5 backend compile"
mvn -q -DskipTests compile

echo "[gate] 2/5 backend tenant isolation integration tests"
mvn -q -pl sentinel-backend -Dtest=TenantIsolationIntegrationTest test

echo "[gate] 3/5 frontend build"
cd "$ROOT_DIR/sentinel-frontend"
npm run -s build

cd "$ROOT_DIR"
echo "[gate] 4/5 verify key docs exist"
test -f "$ROOT_DIR/PHASE1_SPRINT_BOARD.md"
test -f "$ROOT_DIR/PHASE1_EXECUTION_PLAN.md"

echo "[gate] 5/5 git status snapshot"
git --no-pager status --short | cat

echo "[gate] PASS: Phase 2 release gate checks completed"

