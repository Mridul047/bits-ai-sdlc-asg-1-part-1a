#!/usr/bin/env bash
set -uo pipefail

# Harness Orchestrator: Runs all 4 gates sequentially.
# Each gate is independent — a failure in one does NOT skip subsequent gates.
# The orchestrator exits non-zero if ANY gate fails.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OVERALL_RESULT=0

echo "============================================"
echo "  HARNESS: Running all gates"
echo "  Timestamp: $(date +"%Y-%m-%dT%H:%M:%S")"
echo "============================================"
echo ""

run_gate() {
    local gate_script="$1"
    local gate_name="$2"

    echo "--------------------------------------------"
    echo "  Running: $gate_name"
    echo "--------------------------------------------"

    bash "$SCRIPT_DIR/$gate_script"
    local exit_code=$?

    if [ "$exit_code" -ne 0 ]; then
        echo ">>> $gate_name: FAIL"
        OVERALL_RESULT=1
    else
        echo ">>> $gate_name: PASS"
    fi
    echo ""
    return 0
}

run_gate "gate-1-lint.sh"        "Gate 1: Lint (Checkstyle)"
run_gate "gate-2-security.sh"    "Gate 2: Security (SpotBugs)"
run_gate "gate-3-secret-scan.sh" "Gate 3: Secret Scan"
run_gate "gate-4-tests.sh"       "Gate 4: Tests + Coverage"

echo "============================================"
if [ "$OVERALL_RESULT" -eq 0 ]; then
    echo "  HARNESS RESULT: ALL GATES PASSED"
else
    echo "  HARNESS RESULT: ONE OR MORE GATES FAILED"
fi
echo "============================================"

exit "$OVERALL_RESULT"
