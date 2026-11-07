#!/usr/bin/env bash
set -uo pipefail

# Gate 4: Tests + Coverage (JaCoCo >= 80% line coverage)
# Runs mvn clean verify which executes all tests, JaCoCo instrumentation,
# and the jacoco:check enforcement rule (80% minimum line coverage).

GATE_NAME="TESTS"
GATE_NUM="4"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EVIDENCE_DIR="$PROJECT_DIR/evidence"
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")

LOG_DIR="$EVIDENCE_DIR/independent-harness-logs"
RESULT_DIR="$EVIDENCE_DIR/harness-results"
mkdir -p "$LOG_DIR" "$RESULT_DIR"

LOG_FILE="$LOG_DIR/gate-${GATE_NUM}-tests-${TIMESTAMP}.log"
RESULT_FILE="$RESULT_DIR/gate-${GATE_NUM}-tests-${TIMESTAMP}.result"

log() {
    local level="$1"
    shift
    local ts
    ts=$(date +"%Y-%m-%dT%H:%M:%S")
    echo "[$ts] [$GATE_NAME] [$level] $*" | tee -a "$LOG_FILE"
}

cd "$PROJECT_DIR"

log "INFO" "Starting Gate ${GATE_NUM}: Tests + Coverage (JaCoCo >= 80%)"
log "INFO" "Project directory: $PROJECT_DIR"
log "INFO" "JaCoCo minimum line coverage: 80%"
log "INFO" "---"

log "INFO" "Running: mvn clean verify"
MVN_OUTPUT=$(mvn clean verify 2>&1)
MVN_EXIT=$?

echo "$MVN_OUTPUT" >> "$LOG_FILE"

# Extract test summary
TEST_SUMMARY=$(echo "$MVN_OUTPUT" | grep "Tests run:" | tail -1 || true)
# Extract coverage from JaCoCo CSV if available
JACOCO_CSV="$PROJECT_DIR/target/site/jacoco/jacoco.csv"
COVERAGE_PCT="N/A"
if [ -f "$JACOCO_CSV" ]; then
    COVERAGE_PCT=$(awk -F, 'NR>1 {missed+=$8; covered+=$9} END {total=missed+covered; pct=(total>0)?(covered*100/total):0; printf "%.1f%%", pct}' "$JACOCO_CSV")
fi

log "INFO" "---"
log "INFO" "Test summary: $TEST_SUMMARY"
log "INFO" "JaCoCo line coverage: $COVERAGE_PCT"

if [ "$MVN_EXIT" -eq 0 ]; then
    log "INFO" "Result: PASS — all tests pass, coverage meets threshold"
    echo "PASS" > "$RESULT_FILE"
    log "INFO" "Evidence saved: $LOG_FILE"
    log "INFO" "Result saved: $RESULT_FILE"
    exit 0
else
    FAILURES=$(echo "$MVN_OUTPUT" | grep -E "Tests run:.*Failures: [1-9]|Tests run:.*Errors: [1-9]" || true)
    COVERAGE_FAIL=$(echo "$MVN_OUTPUT" | grep "Rule violated" || true)
    if [ -n "$FAILURES" ]; then
        log "ERROR" "Test failures detected:"
        echo "$FAILURES" | while IFS= read -r line; do
            log "ERROR" "  $line"
        done
    fi
    if [ -n "$COVERAGE_FAIL" ]; then
        log "ERROR" "Coverage threshold violation:"
        echo "$COVERAGE_FAIL" | while IFS= read -r line; do
            log "ERROR" "  $line"
        done
    fi
    log "ERROR" "Result: FAIL"
    echo "FAIL" > "$RESULT_FILE"
    log "INFO" "Evidence saved: $LOG_FILE"
    log "INFO" "Result saved: $RESULT_FILE"
    exit 1
fi
