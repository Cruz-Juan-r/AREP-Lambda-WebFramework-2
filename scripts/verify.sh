#!/usr/bin/env bash
# Smoke test against a running instance (local or cloud).
#   ./scripts/verify.sh                          -> http://localhost:8080, expects development
#   ./scripts/verify.sh https://my-app.example production
set -u
BASE="${1:-http://localhost:8080}"
MODE="${2:-development}"
PASS=0; FAIL=0

check() { # name, url, expected status, optional expected body fragment
  local name="$1" url="$2" expected="$3" fragment="${4:-}"
  local body status
  if [[ -z "$fragment" ]]; then
    body=""; status=$(curl -s -o /dev/null -w '%{http_code}' "$BASE$url")
  else
    body=$(curl -s -w $'\n%{http_code}' "$BASE$url")
    status="${body##*$'\n'}"; body="${body%$'\n'*}"
  fi
  if [[ "$status" == "$expected" && ( -z "$fragment" || "$body" == *"$fragment"* ) ]]; then
    echo "PASS  $name ($status)"; PASS=$((PASS+1))
  else
    echo "FAIL  $name: expected $expected${fragment:+ containing '$fragment'}, got $status"; FAIL=$((FAIL+1))
  fi
}

echo "Verifying $BASE (mode: $MODE)"
check "index page"            "/"                          200 "Lambda Web Framework"
check "index.html"            "/index.html"                200 "<html"
check "styles.css"            "/styles.css"                200 ":root"
check "app.js"                "/app.js"                    200 "fetch("
check "logo.png"              "/images/logo.png"           200
check "hello with name"       "/hello?name=Pedro"          200 "Pedro"
check "hello without name"    "/hello"                     200 "world"
check "pi"                    "/pi"                        200 "3.14159"
check "sum (2 params)"        "/api/sum?a=2&b=3"           200 "\"sum\":5.0"
check "sum missing param"     "/api/sum?a=2"               400
check "info"                  "/api/info"                  200 "\"appEnv\":\"$MODE\""
check "unknown route"         "/unknown"                   404 "404 Not Found"
if [[ "$MODE" == "production" ]]; then
  check "shutdown disabled"   "/shutdown"                  404
fi

echo "----"
echo "$PASS passed, $FAIL failed"
[[ $FAIL -eq 0 ]]
