#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# test.sh — Full integration tests for java-localserver
#
# Usage:
#   ./test.sh          # server must already be running
#   ./test.sh --start  # script starts & stops the server automatically
# ─────────────────────────────────────────────────────────────────────────────

# ─── Config ──────────────────────────────────────────────────────────────────

ADDR1="127.0.0.1:8081"     # default server (localhost)
ADDR2="127.0.0.1:8096"     # site2's dedicated port
HOST1="localhost"
HOST2="site2.localhost"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TMP_DIR="$(mktemp -d)"

SERVER_PID=""
AUTO_START=false
[[ "${1:-}" == "--start" ]] && AUTO_START=true

# ─── Colors & counters ───────────────────────────────────────────────────────

GREEN='\033[0;32m'
RED='\033[0;31m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

PASS=0; FAIL=0

section() { echo -e "\n${CYAN}${BOLD}━━━  $1  ━━━${NC}"; }
pass()    { echo -e "  ${GREEN}✓${NC}  $1"; PASS=$((PASS + 1)); }
fail()    { echo -e "  ${RED}✗${NC}  $1"; [ -n "${2:-}" ] && echo -e "       ${RED}↳ $2${NC}"; FAIL=$((FAIL + 1)); }

# ─── Assertion helpers ───────────────────────────────────────────────────────

assert_status() {
    local desc="$1" expected="$2" actual="$3"
    if [[ "$actual" == "$expected" ]]; then
        pass "$desc (HTTP $actual)"
    else
        fail "$desc" "expected HTTP $expected, got HTTP $actual"
    fi
}

assert_body() {
    local desc="$1" pattern="$2" body="$3"
    if echo "$body" | grep -q "$pattern"; then
        pass "$desc"
    else
        fail "$desc" "pattern '$pattern' not found in response body"
    fi
}

assert_header() {
    local desc="$1" pattern="$2" headers="$3"
    if echo "$headers" | grep -qi "$pattern"; then
        pass "$desc"
    else
        fail "$desc" "pattern '$pattern' not found in headers"
    fi
}

# curl wrappers
get()       { curl -s --max-time 5 -H "Host: $HOST1" "$@"; }
get2()      { curl -s --max-time 5 -H "Host: $HOST2" "$@"; }
status_of() { curl -s --max-time 5 -o /dev/null -w "%{http_code}" "$@"; }
headers_of(){ curl -sI --max-time 5 "$@"; }

# ─── Server lifecycle ────────────────────────────────────────────────────────

start_server() {
    echo -e "Starting server…"
    cd "$SCRIPT_DIR"
    make all -s
    java -cp target Main &>/tmp/server_test.log &
    SERVER_PID=$!
    echo "Server PID: $SERVER_PID"
}

stop_server() {
    if [[ -n "$SERVER_PID" ]]; then
        echo -e "\nStopping server (PID $SERVER_PID)…"
        kill "$SERVER_PID" 2>/dev/null || true
    fi
}

wait_for_server() {
    local retries=30
    while (( retries-- > 0 )); do
        if curl -s --max-time 1 -H "Host: $HOST1" "http://$ADDR1/" &>/dev/null; then
            echo "Server is up."; return 0
        fi
        sleep 0.3
    done
    echo "Server did not start in time."; exit 1
}

cleanup() {
    rm -rf "$TMP_DIR"
    stop_server
}
trap cleanup EXIT

# ─── Pre-flight ──────────────────────────────────────────────────────────────

if $AUTO_START; then
    start_server
    wait_for_server
else
    if ! curl -s --max-time 2 -H "Host: $HOST1" "http://$ADDR1/" &>/dev/null; then
        echo -e "${RED}Server not reachable at $ADDR1. Start it or run with --start.${NC}"
        exit 1
    fi
fi

echo -e "\n${BOLD}java-localserver integration tests${NC}"
echo    "Server 1 : http://$ADDR1  (Host: $HOST1)"
echo    "Server 2 : http://$ADDR1  (Host: $HOST2)  +  http://$ADDR2"

# ─────────────────────────────────────────────────────────────────────────────
# 1. STATIC FILES
# ─────────────────────────────────────────────────────────────────────────────
section "1 · Static files"

CODE=$(status_of -H "Host: $HOST1" "http://$ADDR1/index.html")
assert_status "GET /index.html" 200 "$CODE"

BODY=$(get "http://$ADDR1/index.html")
assert_body "index.html has HTML content" "<html\|<!DOCTYPE" "$BODY"

# Wrong method on static route
CODE=$(curl -s --max-time 5 -o /dev/null -w "%{http_code}" \
    -H "Host: $HOST1" -X PUT "http://$ADDR1/index.html")
assert_status "PUT /index.html returns 405" 405 "$CODE"

# ─────────────────────────────────────────────────────────────────────────────
# 2. DIRECTORY LISTING
# ─────────────────────────────────────────────────────────────────────────────
section "2 · Directory listing"

BODY=$(get "http://$ADDR1/")
CODE=$(status_of -H "Host: $HOST1" "http://$ADDR1/")
assert_status "GET / returns 200" 200 "$CODE"
assert_body   "GET / contains listing" "Index of\|<a href" "$BODY"

BODY=$(get "http://$ADDR1/uploads/")
assert_body "GET /uploads/ returns listing" "Index of\|<a href\|<ul>" "$BODY"

# ─────────────────────────────────────────────────────────────────────────────
# 3. VIRTUAL HOST ROUTING
# ─────────────────────────────────────────────────────────────────────────────
section "3 · Virtual host routing"

BODY1=$(get  "http://$ADDR1/index.html")
BODY2=$(get2 "http://$ADDR1/index.html")
if [[ "$BODY1" != "$BODY2" ]]; then
    pass "Host header selects different virtual hosts"
else
    fail "Host header selects different virtual hosts" "both returned identical content"
fi

CODE=$(status_of -H "Host: $HOST2" "http://$ADDR2/")
assert_status "site2: GET / on dedicated port 8096" 200 "$CODE"

CODE=$(status_of -H "Host: $HOST2" "http://$ADDR1/")
assert_status "site2: GET / on shared port 8081 via Host header" 200 "$CODE"

# ─────────────────────────────────────────────────────────────────────────────
# 4. CGI — SERVER 1
# ─────────────────────────────────────────────────────────────────────────────
section "4 · CGI — server 1 (localhost)"

CODE=$(status_of -H "Host: $HOST1" "http://$ADDR1/cgi-bin/hello.py")
assert_status "GET /cgi-bin/hello.py" 200 "$CODE"

BODY=$(get "http://$ADDR1/cgi-bin/hello.py")
assert_body "CGI response has HTML" "<html\|CGI Hello" "$BODY"
assert_body "CGI sets REQUEST_METHOD=GET" "GET" "$BODY"

BODY=$(get "http://$ADDR1/cgi-bin/hello.py?lang=en&user=alice")
assert_body "CGI QUERY_STRING forwarded" "lang=en" "$BODY"

CODE=$(curl -s --max-time 5 -o /dev/null -w "%{http_code}" \
    -H "Host: $HOST1" -X POST -d "payload=test" \
    "http://$ADDR1/cgi-bin/hello.py")
assert_status "POST /cgi-bin/hello.py" 200 "$CODE"

BODY=$(curl -s --max-time 5 -H "Host: $HOST1" \
    -X POST -d "payload=test" "http://$ADDR1/cgi-bin/hello.py")
assert_body "CGI POST body received" "payload=test\|POST" "$BODY"

# Method not allowed on CGI route
CODE=$(curl -s --max-time 5 -o /dev/null -w "%{http_code}" \
    -H "Host: $HOST1" -X DELETE "http://$ADDR1/cgi-bin/hello.py")
assert_status "DELETE /cgi-bin/hello.py returns 405" 405 "$CODE"

# ─────────────────────────────────────────────────────────────────────────────
# 5. CGI — SERVER 2
# ─────────────────────────────────────────────────────────────────────────────
section "5 · CGI — server 2 (site2.localhost)"

CODE=$(status_of -H "Host: $HOST2" "http://$ADDR1/cgi-bin/hello.py")
assert_status "site2: GET /cgi-bin/hello.py via port 8081" 200 "$CODE"

CODE=$(status_of -H "Host: $HOST2" "http://$ADDR2/cgi-bin/hello.py")
assert_status "site2: GET /cgi-bin/hello.py via port 8096" 200 "$CODE"

# session_demo — first visit should set SESSION_ID cookie
COOKIE_JAR="$TMP_DIR/session_jar.txt"
curl -s --max-time 5 -c "$COOKIE_JAR" -o "$TMP_DIR/session1.html" \
    -H "Host: $HOST2" "http://$ADDR2/cgi-bin/session_demo.py"
CODE=$?

SESSION_COOKIE=$(grep "SESSION_ID" "$COOKIE_JAR" 2>/dev/null | awk '{print $NF}')
if [[ -n "$SESSION_COOKIE" ]]; then
    pass "session_demo: SESSION_ID cookie set on first visit"
else
    fail "session_demo: SESSION_ID cookie set on first visit"
fi

# Second visit: cookie jar sent back → server resumes session
BODY2=$(curl -s --max-time 5 -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
    -H "Host: $HOST2" "http://$ADDR2/cgi-bin/session_demo.py")
assert_body "session: visit count increments on reload" "Visits\|visits\|visit" "$BODY2"
assert_body "session: session marked as resumed (not new)" "Resumed\|resumed" "$BODY2"

# Cookie params: name + theme both become Set-Cookie
RAW_HEADERS=$(curl -s --max-time 5 -D - -o /dev/null \
    -H "Host: $HOST2" \
    "http://$ADDR2/cgi-bin/session_demo.py?name=alice&theme=dark")
assert_header "session_demo: 'name' cookie set via query param"  "set-cookie.*name=alice"  "$RAW_HEADERS"
assert_header "session_demo: 'theme' cookie set via query param" "set-cookie.*theme=dark" "$RAW_HEADERS"

# ─────────────────────────────────────────────────────────────────────────────
# 6. FILE UPLOAD — RAW
# ─────────────────────────────────────────────────────────────────────────────
section "6 · File upload — raw POST"

echo "hello from test script" > "$TMP_DIR/hello.txt"

CODE=$(curl -s --max-time 5 -o /dev/null -w "%{http_code}" \
    -H "Host: $HOST1" -X POST \
    --data-binary @"$TMP_DIR/hello.txt" \
    "http://$ADDR1/uploads/hello.txt")
assert_status "raw POST /uploads/hello.txt" 204 "$CODE"

CODE=$(status_of -H "Host: $HOST1" "http://$ADDR1/uploads/hello.txt")
assert_status "uploaded file is GET-accessible" 200 "$CODE"

BODY=$(get "http://$ADDR1/uploads/hello.txt")
assert_body "uploaded file has correct content" "hello from test script" "$BODY"

# ─────────────────────────────────────────────────────────────────────────────
# 7. FILE UPLOAD — MULTIPART
# ─────────────────────────────────────────────────────────────────────────────
section "7 · File upload — multipart POST"

echo "multipart content" > "$TMP_DIR/mp_single.txt"

CODE=$(curl -s --max-time 5 -o /dev/null -w "%{http_code}" \
    -H "Host: $HOST1" -X POST \
    -F "file=@$TMP_DIR/mp_single.txt;filename=mp_single.txt" \
    "http://$ADDR1/uploads/")
assert_status "multipart POST single file" 200 "$CODE"

CODE=$(status_of -H "Host: $HOST1" "http://$ADDR1/uploads/mp_single.txt")
assert_status "multipart-uploaded file is accessible" 200 "$CODE"

BODY=$(get "http://$ADDR1/uploads/mp_single.txt")
assert_body "multipart-uploaded file has correct content" "multipart content" "$BODY"

# Multiple files in one request
echo "file A" > "$TMP_DIR/mp_a.txt"
echo "file B" > "$TMP_DIR/mp_b.txt"
CODE=$(curl -s --max-time 5 -o /dev/null -w "%{http_code}" \
    -H "Host: $HOST1" -X POST \
    -F "a=@$TMP_DIR/mp_a.txt;filename=mp_a.txt" \
    -F "b=@$TMP_DIR/mp_b.txt;filename=mp_b.txt" \
    "http://$ADDR1/uploads/")
assert_status "multipart POST two files at once" 200 "$CODE"

CODE=$(status_of -H "Host: $HOST1" "http://$ADDR1/uploads/mp_a.txt")
assert_status "first file from multi-upload accessible" 200 "$CODE"

CODE=$(status_of -H "Host: $HOST1" "http://$ADDR1/uploads/mp_b.txt")
assert_status "second file from multi-upload accessible" 200 "$CODE"

# ─────────────────────────────────────────────────────────────────────────────
# 8. DELETE
# ─────────────────────────────────────────────────────────────────────────────
section "8 · DELETE"

CODE=$(curl -s --max-time 5 -o /dev/null -w "%{http_code}" \
    -H "Host: $HOST1" -X DELETE "http://$ADDR1/uploads/hello.txt")
assert_status "DELETE /uploads/hello.txt" 204 "$CODE"

CODE=$(status_of -H "Host: $HOST1" "http://$ADDR1/uploads/hello.txt")
assert_status "deleted file returns 404 on GET" 404 "$CODE"

CODE=$(curl -s --max-time 5 -o /dev/null -w "%{http_code}" \
    -H "Host: $HOST1" -X DELETE "http://$ADDR1/uploads/hello.txt")
assert_status "DELETE non-existent file returns 404" 404 "$CODE"

CODE=$(curl -s --max-time 5 -o /dev/null -w "%{http_code}" \
    -H "Host: $HOST1" -X DELETE "http://$ADDR1/")
assert_status "DELETE / returns 405 (only GET allowed)" 405 "$CODE"

# Clean up remaining test uploads
for f in mp_single.txt mp_a.txt mp_b.txt; do
    curl -s --max-time 5 -o /dev/null \
        -H "Host: $HOST1" -X DELETE "http://$ADDR1/uploads/$f" 2>/dev/null || true
done

# ─────────────────────────────────────────────────────────────────────────────
# 9. ERROR RESPONSES
# ─────────────────────────────────────────────────────────────────────────────
section "9 · Error responses"

CODE=$(status_of -H "Host: $HOST1" "http://$ADDR1/does-not-exist.html")
assert_status "404 for missing file" 404 "$CODE"

BODY=$(get "http://$ADDR1/does-not-exist.html")
assert_body "404 serves custom error page" "<html\|404\|Not Found" "$BODY"

CODE=$(curl -s --max-time 5 -o /dev/null -w "%{http_code}" \
    -H "Host: $HOST1" -X DELETE "http://$ADDR1/")
assert_status "DELETE / returns 405" 405 "$CODE"

# Path traversal — server must NOT serve files outside root
CODE=$(status_of -H "Host: $HOST1" "http://$ADDR1/../etc/passwd")
if [[ "$CODE" == "403" || "$CODE" == "404" || "$CODE" == "400" ]]; then
    pass "path traversal blocked (HTTP $CODE)"
else
    fail "path traversal blocked" "expected 400/403/404, got $CODE"
fi

# 413 — payload too large  (site2 limit = 512 000 bytes)
dd if=/dev/urandom of="$TMP_DIR/big.bin" bs=1024 count=600 2>/dev/null
CODE=$(curl -s --max-time 10 -o /dev/null -w "%{http_code}" \
    -H "Host: $HOST2" -X POST \
    --data-binary @"$TMP_DIR/big.bin" \
    "http://$ADDR1/cgi-bin/hello.py")
assert_status "413 when body exceeds site2 client_max_body_size" 413 "$CODE"

# ─────────────────────────────────────────────────────────────────────────────
# 10. CHUNKED REQUEST BODY
# ─────────────────────────────────────────────────────────────────────────────
section "10 · Chunked transfer encoding"

CHUNK_DATA="chunked-upload-test"
CODE=$(printf '%s' "$CHUNK_DATA" | curl -s --max-time 5 \
    -o /dev/null -w "%{http_code}" \
    -H "Host: $HOST1" \
    -H "Transfer-Encoding: chunked" \
    -H "Content-Type: text/plain" \
    -X POST --data-binary @- \
    "http://$ADDR1/uploads/chunked.txt")
assert_status "chunked POST /uploads/chunked.txt" 204 "$CODE"

BODY=$(get "http://$ADDR1/uploads/chunked.txt")
assert_body "chunked-uploaded file has correct content" "chunked-upload-test" "$BODY"

curl -s --max-time 5 -o /dev/null \
    -H "Host: $HOST1" -X DELETE "http://$ADDR1/uploads/chunked.txt" || true

# ─────────────────────────────────────────────────────────────────────────────
# 11. KEEP-ALIVE
# ─────────────────────────────────────────────────────────────────────────────
section "11 · Keep-alive"

# curl --next sends two requests over the same TCP connection
R1=$(curl -s --max-time 10 \
    -o /dev/null -w "%{http_code}" \
    -H "Host: $HOST1" -H "Connection: keep-alive" \
    "http://$ADDR1/" \
    --next \
    -o /dev/null -w " %{http_code}" \
    -H "Host: $HOST1" \
    "http://$ADDR1/index.html" 2>/dev/null || true)

CODE1=$(echo "$R1" | awk '{print $1}')
CODE2=$(echo "$R1" | awk '{print $2}')
[[ "$CODE1" == "200" ]] \
    && pass "keep-alive: first request returns 200" \
    || fail "keep-alive: first request" "got $CODE1"
[[ "$CODE2" == "200" ]] \
    && pass "keep-alive: second request on same connection returns 200" \
    || fail "keep-alive: second request" "got $CODE2"

# Connection: close — server must respond 200; it will close the socket (no header check needed)
CODE=$(curl -s --max-time 5 -o /dev/null -w "%{http_code}" \
    -H "Host: $HOST1" -H "Connection: close" "http://$ADDR1/")
assert_status "Connection: close still gets a valid response" 200 "$CODE"

# ─────────────────────────────────────────────────────────────────────────────
# 12. CONTENT-TYPE / MIME
# ─────────────────────────────────────────────────────────────────────────────
section "12 · Content-Type / MIME types"

HEADERS=$(headers_of -H "Host: $HOST1" "http://$ADDR1/index.html")
assert_header "index.html → text/html" "content-type.*text/html" "$HEADERS"

# Upload a JSON file and check its MIME
echo '{"test":1}' > "$TMP_DIR/data.json"
curl -s --max-time 5 -o /dev/null \
    -H "Host: $HOST1" -X POST \
    --data-binary @"$TMP_DIR/data.json" \
    "http://$ADDR1/uploads/data.json" || true
# Use GET with -D - to capture headers (HEAD / -sI would return 405 on this route)
HEADERS=$(curl -s --max-time 5 -D - -o /dev/null \
    -H "Host: $HOST1" "http://$ADDR1/uploads/data.json")
assert_header "uploaded .json served with a content-type" "content-type" "$HEADERS"

curl -s --max-time 5 -o /dev/null \
    -H "Host: $HOST1" -X DELETE "http://$ADDR1/uploads/data.json" || true

# ─────────────────────────────────────────────────────────────────────────────
# 13. CONCURRENT CONNECTIONS
# ─────────────────────────────────────────────────────────────────────────────
section "13 · Concurrent connections"

CONCURRENT=10
RESULTS_FILE="$TMP_DIR/concurrent.txt"
: > "$RESULTS_FILE"

for i in $(seq 1 $CONCURRENT); do
    curl -s --max-time 5 -o /dev/null -w "%{http_code}\n" \
        -H "Host: $HOST1" "http://$ADDR1/" >> "$RESULTS_FILE" &
done
wait

TOTAL_REQ=$CONCURRENT
OK_REQ=$(grep -c "^200$" "$RESULTS_FILE" || true)
if [[ "$OK_REQ" -eq "$TOTAL_REQ" ]]; then
    pass "all $CONCURRENT concurrent GET / returned 200"
else
    fail "concurrent GET /" "$OK_REQ/$TOTAL_REQ returned 200"
fi

# ─────────────────────────────────────────────────────────────────────────────
# Summary
# ─────────────────────────────────────────────────────────────────────────────

TOTAL=$((PASS + FAIL))
echo ""
echo -e "${BOLD}─────────────────────────────${NC}"
echo -e "  ${GREEN}${BOLD}Passed:${NC} $PASS / $TOTAL"
[[ $FAIL -gt 0 ]] && echo -e "  ${RED}${BOLD}Failed:${NC} $FAIL / $TOTAL"
echo -e "${BOLD}─────────────────────────────${NC}"

if [[ $FAIL -eq 0 ]]; then
    echo -e "\n${GREEN}${BOLD}All tests passed!${NC}\n"
else
    echo -e "\n${RED}${BOLD}$FAIL test(s) failed.${NC}\n"
    exit 1
fi
