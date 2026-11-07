#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
EVIDENCE_DIR="$SCRIPT_DIR/evidence"

mkdir -p "$EVIDENCE_DIR"

pass_gate() {
    local gate="$1"
    echo "PASS" > "$EVIDENCE_DIR/${gate}.result"
    echo "=== GATE $gate: PASS ==="
}

fail_gate() {
    local gate="$1"
    echo "FAIL" > "$EVIDENCE_DIR/${gate}.result"
    echo "=== GATE $gate: FAIL ==="
    exit 1
}

cd "$PROJECT_DIR"

# Gate 1: Lint (Checkstyle)
echo "=== Running Gate 1: Lint (Checkstyle) ==="
if mvn checkstyle:check -q 2>&1 | tee "$EVIDENCE_DIR/lint.log"; then
    pass_gate "lint"
else
    fail_gate "lint"
fi

# Gate 2: Security (SpotBugs + Find Security Bugs)
echo "=== Running Gate 2: Security (SpotBugs) ==="
if mvn compile spotbugs:check -q 2>&1 | tee "$EVIDENCE_DIR/security.log"; then
    pass_gate "security"
else
    fail_gate "security"
fi

# Gate 3: Secret Scan
echo "=== Running Gate 3: Secret Scan ==="
SECRET_SCAN_FAILED=0

PATTERNS='(STRAVA_ACCESS_TOKEN|POSTGRES_PASSWORD|SPRING_DATASOURCE_PASSWORD)\s*=\s*[^${\s]'
SCAN_FILES=$(find "$PROJECT_DIR" \
    -name "*.yml" -o -name "*.yaml" -o -name "*.properties" \
    -o -name "*.java" -o -name "*.xml" -o -name "*.json" \
    -o -name "*.sh" -o -name "*.env" -o -name "Dockerfile" \
    -o -name "docker-compose*" \
    | grep -v target/ | grep -v .harness/evidence/ | grep -v node_modules/ || true)

if [ -n "$SCAN_FILES" ]; then
    if echo "$SCAN_FILES" | xargs grep -nEi "$PATTERNS" > "$EVIDENCE_DIR/secret-scan.log" 2>&1; then
        echo "Hard-coded secrets detected!" | tee -a "$EVIDENCE_DIR/secret-scan.log"
        SECRET_SCAN_FAILED=1
    fi
fi

TOKEN_PATTERNS='(Bearer\s+[A-Za-z0-9]{20,}|password\s*=\s*["\x27][^$][^"\x27]+["\x27])'
if [ -n "$SCAN_FILES" ]; then
    if echo "$SCAN_FILES" | xargs grep -nEi "$TOKEN_PATTERNS" >> "$EVIDENCE_DIR/secret-scan.log" 2>&1; then
        echo "Potential secret patterns detected!" | tee -a "$EVIDENCE_DIR/secret-scan.log"
        SECRET_SCAN_FAILED=1
    fi
fi

if [ "$SECRET_SCAN_FAILED" -eq 1 ]; then
    fail_gate "secret-scan"
else
    echo "No secrets found." > "$EVIDENCE_DIR/secret-scan.log"
    pass_gate "secret-scan"
fi

# Gate 4: Tests + Coverage (JaCoCo ≥80% line coverage)
echo "=== Running Gate 4: Tests + Coverage ==="
if mvn verify 2>&1 | tee "$EVIDENCE_DIR/tests.log"; then
    pass_gate "tests"
else
    fail_gate "tests"
fi

echo ""
echo "=== ALL GATES PASSED ==="
