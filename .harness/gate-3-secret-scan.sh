#!/usr/bin/env bash
set -uo pipefail

# Gate 3: Secret Scan
# Grep-based scan over repository files for hardcoded secrets,
# covering docker-compose, properties, Java, YAML, XML, JSON, shell, env files.
# Zero matches required to pass.

GATE_NAME="SECRET-SCAN"
GATE_NUM="3"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EVIDENCE_DIR="$PROJECT_DIR/evidence"
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")

LOG_DIR="$EVIDENCE_DIR/independent-harness-logs"
RESULT_DIR="$EVIDENCE_DIR/harness-results"
mkdir -p "$LOG_DIR" "$RESULT_DIR"

LOG_FILE="$LOG_DIR/gate-${GATE_NUM}-secret-scan-${TIMESTAMP}.log"
RESULT_FILE="$RESULT_DIR/gate-${GATE_NUM}-secret-scan-${TIMESTAMP}.result"

log() {
    local level="$1"
    shift
    local ts
    ts=$(date +"%Y-%m-%dT%H:%M:%S")
    echo "[$ts] [$GATE_NAME] [$level] $*" | tee -a "$LOG_FILE"
}

cd "$PROJECT_DIR"

log "INFO" "Starting Gate ${GATE_NUM}: Secret Scan"
log "INFO" "Project directory: $PROJECT_DIR"
log "INFO" "Scan scope: *.yml, *.yaml, *.properties, *.java, *.xml, *.json, *.sh, *.env, Dockerfile, docker-compose*"
log "INFO" "Exclusions: target/, .harness/evidence/, node_modules/, evidence/"
log "INFO" "---"

SECRET_SCAN_FAILED=0

SCAN_FILES=$(find "$PROJECT_DIR" \
    \( -name "*.yml" -o -name "*.yaml" -o -name "*.properties" \
    -o -name "*.java" -o -name "*.xml" -o -name "*.json" \
    -o -name "*.sh" -o -name "*.env" -o -name "Dockerfile" \
    -o -name "docker-compose*" \) \
    | grep -v "target/" | grep -v ".harness/evidence/" | grep -v "node_modules/" \
    | grep -v "evidence/independent-harness-logs/" | grep -v "evidence/harness-results/" || true)

# Pattern 1: Hardcoded credential assignments
PATTERNS='(STRAVA_ACCESS_TOKEN|POSTGRES_PASSWORD|SPRING_DATASOURCE_PASSWORD)\s*=\s*[^${\s]'
log "INFO" "Scanning for hardcoded credential assignments..."
MATCH1=""
if [ -n "$SCAN_FILES" ]; then
    MATCH1=$(echo "$SCAN_FILES" | xargs grep -nEi "$PATTERNS" 2>/dev/null || true)
    if [ -n "$MATCH1" ]; then
        log "ERROR" "Hardcoded credentials detected:"
        echo "$MATCH1" | while IFS= read -r line; do
            log "ERROR" "  $line"
        done
        SECRET_SCAN_FAILED=1
    fi
fi

# Pattern 2: Bearer tokens and quoted passwords
TOKEN_PATTERNS='(Bearer\s+[A-Za-z0-9]{20,}|password\s*=\s*["\x27][^$][^"\x27]+["\x27])'
log "INFO" "Scanning for bearer tokens and quoted passwords..."
MATCH2=""
if [ -n "$SCAN_FILES" ]; then
    MATCH2=$(echo "$SCAN_FILES" | xargs grep -nEi "$TOKEN_PATTERNS" 2>/dev/null || true)
    if [ -n "$MATCH2" ]; then
        log "ERROR" "Potential secret patterns detected:"
        echo "$MATCH2" | while IFS= read -r line; do
            log "ERROR" "  $line"
        done
        SECRET_SCAN_FAILED=1
    fi
fi

log "INFO" "---"

if [ "$SECRET_SCAN_FAILED" -eq 1 ]; then
    log "ERROR" "Result: FAIL — secrets or secret-like patterns found"
    echo "FAIL" > "$RESULT_FILE"
    log "INFO" "Evidence saved: $LOG_FILE"
    log "INFO" "Result saved: $RESULT_FILE"
    exit 1
else
    log "INFO" "Result: PASS — no hardcoded secrets found"
    echo "PASS" > "$RESULT_FILE"
    log "INFO" "Evidence saved: $LOG_FILE"
    log "INFO" "Result saved: $RESULT_FILE"
    exit 0
fi
