#!/usr/bin/env bash
set -uo pipefail

# Gate 2: Security (SpotBugs + Find Security Bugs)
# Runs SpotBugs at Medium threshold with Find Security Bugs plugin.
# Zero medium/high severity findings required to pass.

GATE_NAME="SECURITY"
GATE_NUM="2"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EVIDENCE_DIR="$PROJECT_DIR/evidence"
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")

LOG_DIR="$EVIDENCE_DIR/independent-harness-logs"
RESULT_DIR="$EVIDENCE_DIR/harness-results"
mkdir -p "$LOG_DIR" "$RESULT_DIR"

LOG_FILE="$LOG_DIR/gate-${GATE_NUM}-security-${TIMESTAMP}.log"
RESULT_FILE="$RESULT_DIR/gate-${GATE_NUM}-security-${TIMESTAMP}.result"

log() {
    local level="$1"
    shift
    local ts
    ts=$(date +"%Y-%m-%dT%H:%M:%S")
    echo "[$ts] [$GATE_NAME] [$level] $*" | tee -a "$LOG_FILE"
}

cd "$PROJECT_DIR"

log "INFO" "Starting Gate ${GATE_NUM}: Security (SpotBugs + Find Security Bugs)"
log "INFO" "Project directory: $PROJECT_DIR"
log "INFO" "SpotBugs threshold: Medium"
log "INFO" "Exclude filter: .harness/spotbugs-exclude.xml"
log "INFO" "---"

log "INFO" "Running: mvn compile spotbugs:check"
MVN_OUTPUT=$(mvn compile spotbugs:check 2>&1)
MVN_EXIT=$?

echo "$MVN_OUTPUT" >> "$LOG_FILE"

BUGS=$(echo "$MVN_OUTPUT" | grep "\[ERROR\].*EI_EXPOSE_REP\|Medium:\|High:" || true)

log "INFO" "---"

if [ "$MVN_EXIT" -eq 0 ]; then
    log "INFO" "Result: PASS — zero SpotBugs findings"
    echo "PASS" > "$RESULT_FILE"
    log "INFO" "Evidence saved: $LOG_FILE"
    log "INFO" "Result saved: $RESULT_FILE"
    exit 0
else
    BUG_COUNT=$(echo "$MVN_OUTPUT" | grep -c "\[ERROR\].*Medium:\|\[ERROR\].*High:" || echo "0")
    log "ERROR" "Result: FAIL — $BUG_COUNT SpotBugs finding(s) detected"
    if [ -n "$BUGS" ]; then
        log "ERROR" "Findings:"
        echo "$BUGS" | while IFS= read -r line; do
            log "ERROR" "  $line"
        done
    fi
    echo "FAIL" > "$RESULT_FILE"
    log "INFO" "Evidence saved: $LOG_FILE"
    log "INFO" "Result saved: $RESULT_FILE"
    exit 1
fi
