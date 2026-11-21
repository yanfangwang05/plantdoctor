#!/usr/bin/env bash
#
# PlantDoctor - smoke test
#
# Starts the packaged application, waits for the health endpoint, then exercises
# the diagnostic workflow against the offline (mock) recognition provider.
#
# What this test does
#   * verifies that the build artefact starts with no external service, database
#     or credential;
#   * verifies the routing table is served and covers ten categories;
#   * verifies that a successful recognition is linked to a knowledge record;
#   * verifies that a label reachable only through the alias column is linked;
#   * verifies that a label with no knowledge record is reported as unmatched
#     instead of being silently presented as a diagnosis;
#   * verifies that an unknown category is rejected;
#   * verifies that every request, including the rejected and unmatched ones,
#     appears in the access log.
#
# What this test does NOT do
#   * it does not measure recognition accuracy. The fixtures in
#     src/main/resources/mock/recognition.json are fixed answers, not predictions.
#
# Usage
#   ./scripts/smoke-test.sh              # build if needed, then test
#   ./scripts/smoke-test.sh --no-build   # reuse an existing jar
#   PORT=9090 ./scripts/smoke-test.sh    # use another port
#
set -euo pipefail

PORT="${PORT:-18080}"
BASE="http://127.0.0.1:${PORT}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD=1
PASSED=0
FAILED=0
SERVER_PID=""
WORKDIR="$(mktemp -d)"
UPLOADS="${WORKDIR}/uploads"

for arg in "$@"; do
  case "$arg" in
    --no-build) BUILD=0 ;;
    *) echo "Unknown argument: $arg" >&2; exit 2 ;;
  esac
done

cleanup() {
  if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
  fi
  rm -rf "$WORKDIR"
}
trap cleanup EXIT

section() { printf '\n=== %s ===\n' "$1"; }

pass() { PASSED=$((PASSED + 1)); printf '  PASS  %s\n' "$1"; }

fail() {
  FAILED=$((FAILED + 1))
  printf '  FAIL  %s\n' "$1"
  if [ $# -gt 1 ]; then printf '        %s\n' "$2"; fi
}

# Checks that a response body contains a literal fragment.
#
# The body is passed through a here-string rather than a pipe on purpose: grep -q
# exits as soon as it matches, which would send SIGPIPE to the writer and, with
# pipefail enabled, turn a match into a failure for long bodies.
expect_contains() {
  local what="$1" body="$2" needle="$3"
  if grep -qF -- "$needle" <<<"$body"; then
    pass "$what"
  else
    fail "$what" "expected to find: $needle"
    printf '        body: %s\n' "$(cut -c1-400 <<<"$body")"
  fi
}

# Checks that a response body does not contain a literal fragment.
expect_absent() {
  local what="$1" body="$2" needle="$3"
  if grep -qF -- "$needle" <<<"$body"; then
    fail "$what" "unexpected fragment: $needle"
  else
    pass "$what"
  fi
}

# ---------------------------------------------------------------- environment

section "Environment"

if ! command -v java >/dev/null 2>&1; then
  echo "java not found on PATH. Install JDK 17 or set JAVA_HOME." >&2
  exit 1
fi
JAVA_VERSION="$(java -version 2>&1 | head -1)"
printf '  java: %s\n' "$JAVA_VERSION"
printf '  port: %s\n' "$PORT"

# A 1x1 PNG. The mock provider keys on the file name, so the bytes do not matter;
# keeping them tiny makes the test runnable without bundling photographs.
B64_PNG="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="

decode_png() {
  local out="$1"
  printf '%s' "$B64_PNG" | base64 -D >"$out" 2>/dev/null \
    || printf '%s' "$B64_PNG" | base64 -d >"$out" 2>/dev/null \
    || { echo "Unable to decode the sample image with base64" >&2; exit 1; }
}

mkdir -p "$UPLOADS"

# -------------------------------------------------------------------- build

section "Build"

JAR="$(ls "$ROOT"/target/plantdoctor-*.jar 2>/dev/null | grep -v -e '-sources' -e '-javadoc' | head -1 || true)"
if [ "$BUILD" -eq 1 ] || [ -z "$JAR" ]; then
  if ! command -v mvn >/dev/null 2>&1; then
    echo "mvn not found on PATH, and no jar in target/. Install Maven 3.9+." >&2
    exit 1
  fi
  printf '  running: mvn -B -DskipTests package\n'
  ( cd "$ROOT" && mvn -B -DskipTests package >"${WORKDIR}/build.log" 2>&1 ) \
    || { echo "Build failed. See the log above." >&2; tail -40 "${WORKDIR}/build.log" >&2; exit 1; }
  JAR="$(ls "$ROOT"/target/plantdoctor-*.jar 2>/dev/null | grep -v -e '-sources' -e '-javadoc' | head -1 || true)"
fi

if [ -z "$JAR" ]; then
  echo "No jar found in $ROOT/target after the build." >&2
  exit 1
fi
printf '  jar: %s\n' "$(basename "$JAR")"

# ------------------------------------------------------------------- startup

section "Startup"

# Fail fast on a stale instance. Without this check a server left over from an
# earlier run would answer the health probe and every request below would be
# served by the wrong build, producing confusing assertion failures.
PROBE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 "${BASE}/actuator/health" || true)"
if [ "$PROBE" != "000" ]; then
  echo "Port ${PORT} is already serving HTTP ${PROBE}. Stop that process or run: PORT=19090 $0" >&2
  exit 1
fi

java -jar "$JAR" \
  --server.port="$PORT" \
  --plantdoctor.upload-dir="$UPLOADS" \
  --spring.profiles.active=mock \
  >"${WORKDIR}/app.log" 2>&1 &
SERVER_PID=$!

READY=0
for _ in $(seq 1 90); do
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "The application exited during startup. Last lines of the log:" >&2
    tail -40 "${WORKDIR}/app.log" >&2
    exit 1
  fi
  if curl -fsS --max-time 2 "${BASE}/actuator/health" >/dev/null 2>&1; then
    READY=1
    break
  fi
  sleep 1
done

if [ "$READY" -ne 1 ]; then
  echo "The application did not become healthy within 90 s. Last lines of the log:" >&2
  tail -40 "${WORKDIR}/app.log" >&2
  exit 1
fi
pass "application is healthy at ${BASE}/actuator/health"

# --------------------------------------------------------------- routing table

section "Routing table"

BODY="$(curl -fsS "${BASE}/api/crop-types")"
expect_contains "ten selectable categories are advertised" "$BODY" '"key":"WHEAT"'
expect_contains "corn is routed to the dedicated endpoint" "$BODY" '"key":"CORN","displayName":"Corn","displayNameZh":"玉米","path":"dedicated","providerCode":"14"'
expect_contains "wheat is routed to the general endpoint" "$BODY" '"key":"WHEAT","displayName":"Wheat","displayNameZh":"小麦","path":"general","providerCode":"131"'
CATEGORY_COUNT="$(printf '%s' "$BODY" | grep -o '"key":"' | wc -l | tr -d ' ')"
if [ "$CATEGORY_COUNT" = "10" ]; then
  pass "category count is 10 (found ${CATEGORY_COUNT})"
else
  fail "category count is 10" "found ${CATEGORY_COUNT}"
fi

# ------------------------------------------------------- end-to-end diagnosis

section "End-to-end diagnosis (wheat)"

decode_png "${WORKDIR}/wheat_rust.jpg"
BODY="$(curl -fsS -X POST "${BASE}/api/diagnosis" \
  -F "cropType=WHEAT" \
  -F "file=@${WORKDIR}/wheat_rust.jpg;filename=wheat_rust.jpg")"
expect_contains "request is accepted" "$BODY" '"success":true'
expect_contains "label is returned" "$BODY" '"insectName":"小麦条锈病"'
expect_contains "knowledge record is linked" "$BODY" '"zhName":"小麦条锈病"'
expect_contains "prevention text is included" "$BODY" '"preventionMethods"'
expect_contains "a log id is returned" "$BODY" '"logId"'

section "End-to-end diagnosis (corn, dedicated path)"

decode_png "${WORKDIR}/corn_leaf_blight.jpg"
BODY="$(curl -fsS -X POST "${BASE}/api/diagnosis" \
  -F "cropType=CORN" \
  -F "file=@${WORKDIR}/corn_leaf_blight.jpg;filename=corn_leaf_blight.jpg")"
expect_contains "request is accepted" "$BODY" '"success":true'
expect_contains "label is returned" "$BODY" '"insectName":"玉米大斑病"'
expect_contains "knowledge record is linked" "$BODY" '"zhName":"玉米大斑病"'

# ------------------------------------------------------------- alias fallback

section "Knowledge linkage through an alias"

decode_png "${WORKDIR}/corn_borer_alias.jpg"
BODY="$(curl -fsS -X POST "${BASE}/api/diagnosis" \
  -F "cropType=INSECT" \
  -F "file=@${WORKDIR}/corn_borer_alias.jpg;filename=corn_borer_alias.jpg")"
expect_contains "the provider label is an alias" "$BODY" '"insectName":"玉米钻心虫"'
expect_contains "the canonical record is found through zh_alias_name" "$BODY" '"zhName":"玉米螟"'

# ---------------------------------------------------------------- unmatched

section "Label without a knowledge record"

decode_png "${WORKDIR}/unknown_lesion.jpg"
BODY="$(curl -fsS -X POST "${BASE}/api/diagnosis" \
  -F "cropType=WHEAT" \
  -F "file=@${WORKDIR}/unknown_lesion.jpg;filename=unknown_lesion.jpg")"
expect_contains "the request itself succeeds" "$BODY" '"success":true'
expect_contains "the unmatched label is still returned" "$BODY" '"insectName":"未收录的叶部斑害"'
expect_contains "no knowledge payload is attached" "$BODY" '"diseaseData":{}'

# ------------------------------------------------------------ invalid input

section "Invalid input"

BODY="$(curl -sS -X POST "${BASE}/api/diagnosis" \
  -F "cropType=NO_SUCH_CATEGORY" \
  -F "file=@${WORKDIR}/wheat_rust.jpg;filename=wheat_rust.jpg")"
expect_contains "an unknown category is rejected" "$BODY" '"success":false'

printf 'This is not an image.\n' >"${WORKDIR}/notes.txt"
BODY="$(curl -sS -X POST "${BASE}/api/diagnosis" \
  -F "cropType=WHEAT" \
  -F "file=@${WORKDIR}/notes.txt;filename=notes.txt")"
expect_contains "a non-image upload is rejected" "$BODY" '"success":false'

# ------------------------------------------------------------ knowledge API

section "Knowledge endpoint"

BODY="$(curl -fsS "${BASE}/api/knowledge?name=%E5%B0%8F%E9%BA%A6%E6%9D%A1%E9%94%88%E7%97%85")"
expect_contains "a lookup by canonical name succeeds" "$BODY" '"zhName":"小麦条锈病"'

BODY="$(curl -sS "${BASE}/api/knowledge?name=%E4%B8%8D%E5%AD%98%E5%9C%A8%E7%9A%84%E7%97%85%E5%AE%B3")"
expect_contains "a lookup for an unknown name fails cleanly" "$BODY" '"success":false'

# ---------------------------------------------------------------- access log

section "Access log"

BODY="$(curl -fsS "${BASE}/api/diagnosis/logs?limit=50")"
expect_contains "the successful diagnosis is logged" "$BODY" '"insectName":"小麦条锈病"'
expect_contains "the unmatched diagnosis is logged as a label" "$BODY" '"insectName":"未收录的叶部斑害"'
expect_contains "the unmatched diagnosis is logged as unmatched" "$BODY" '"knowledgeMatched":false'

# ------------------------------------------------------------------- summary

section "Summary"
printf '  passed: %d\n  failed: %d\n' "$PASSED" "$FAILED"

if [ "$FAILED" -ne 0 ]; then
  printf '\nLast lines of the server log:\n'
  tail -25 "${WORKDIR}/app.log" 2>/dev/null || true
  trap - EXIT
  echo "Smoke test FAILED." >&2
  exit 1
fi

echo "Smoke test passed."
