#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TEST_ROOT="$ROOT_DIR/sentinel-backend/src/test/java"

run_matching_tests() {
  local label="$1"
  shift
  local list

  list=$(find "$TEST_ROOT" -type f -name "*IntegrationTest.java" -print 2>/dev/null \
    | while IFS= read -r file; do
        class_name="$(basename "$file" .java)"
        for token in "$@"; do
          if [[ "$class_name" == *"$token"* ]]; then
            echo "$class_name"
            break
          fi
        done
      done \
    | sort -u \
    | paste -sd, -)

  if [[ -z "${list:-}" ]]; then
	echo "[gate] FAIL: no $label integration tests found under $TEST_ROOT"
	exit 1
  fi

  echo "[gate] running $label tests: $list"
  mvn -q -pl sentinel-backend -Dtest="$list" test
}

echo "[gate] root: $ROOT_DIR"

cd "$ROOT_DIR"
echo "[gate] 1/6 backend compile"
mvn -q -DskipTests compile

echo "[gate] 2/6 baseline tenant isolation integration tests"
mvn -q -pl sentinel-backend -Dtest=TenantIsolationIntegrationTest test

echo "[gate] 3/6 workflow + knowledge base integration tests"
run_matching_tests "workflow" "Workflow"
run_matching_tests "knowledge-base" "KnowledgeBase" "KB"

echo "[gate] 4/6 frontend build"
cd "$ROOT_DIR/sentinel-frontend"
npm run -s build

cd "$ROOT_DIR"
echo "[gate] 5/6 verify phase 4 docs exist"
test -f "$ROOT_DIR/PHASE4_EXECUTION_PLAN.md"
test -f "$ROOT_DIR/PHASE4_SPRINT_BOARD.md"
test -f "$ROOT_DIR/S4_RELEASE_GATE.md"

echo "[gate] 6/6 git status snapshot"
git --no-pager status --short | cat

echo "[gate] PASS: Phase 4 release gate checks completed"

