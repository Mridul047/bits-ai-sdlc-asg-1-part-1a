#!/usr/bin/env bash
set -uo pipefail

# Gate 1: Lint (Checkstyle)
# Runs Checkstyle with the project's .harness/checkstyle.xml configuration.
# Zero violations required to pass.

GATE_NAME="LINT"
GATE_NUM="1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EVIDENCE_DIR="$PROJECT_DIR/evidence"
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")

LOG_DIR="$EVIDENCE_DIR/independent-harness-logs"
RESULT_DIR="$EVIDENCE_DIR/harness-results"
mkdir -p "$LOG_DIR" "$RESULT_DIR"

LOG_FILE="$LOG_DIR/gate-${GATE_NUM}-lint-${TIMESTAMP}.log"
RESULT_FILE="$RESULT_DIR/gate-${GATE_NUM}-lint-${TIMESTAMP}.result"

log() {
    local level="$1"
    shift
    local ts
    ts=$(date +"%Y-%m-%dT%H:%M:%S")
    echo "[$ts] [$GATE_NAME] [$level] $*" | tee -a "$LOG_FILE"
}

cd "$PROJECT_DIR"

log "INFO" "Starting Gate ${GATE_NUM}: Lint (Checkstyle)"
log "INFO" "Project directory: $PROJECT_DIR"
log "INFO" "Checkstyle config: .harness/checkstyle.xml"
log "INFO" "---"

log "INFO" "Running: mvn checkstyle:check"
MVN_OUTPUT=$(mvn checkstyle:check 2>&1)
MVN_EXIT=$?

echo "$MVN_OUTPUT" >> "$LOG_FILE"

VIOLATION_COUNT=$(echo "$MVN_OUTPUT" | grep -c "\[WARN\].*\[UnusedImports\]\|\[WARN\].*\[AvoidStarImport\]\|\[WARN\].*\[RedundantImport\]\|\[WARNING\]" | head -1 || echo "0")
VIOLATIONS=$(echo "$MVN_OUTPUT" | grep "\[WARN\]" || true)

log "INFO" "---"

if [ "$MVN_EXIT" -eq 0 ]; then
    log "INFO" "Result: PASS — zero Checkstyle violations"
    echo "PASS" > "$RESULT_FILE"
    log "INFO" "Evidence saved: $LOG_FILE"
    log "INFO" "Result saved: $RESULT_FILE"
    exit 0
else
    log "ERROR" "Result: FAIL — Checkstyle violations detected"
    if [ -n "$VIOLATIONS" ]; then
        log "ERROR" "Violations found:"
        echo "$VIOLATIONS" | while IFS= read -r line; do
            log "ERROR" "  $line"
        done
    fi
    echo "FAIL" > "$RESULT_FILE"
    log "INFO" "Evidence saved: $LOG_FILE"
    log "INFO" "Result saved: $RESULT_FILE"
    exit 1
fi
