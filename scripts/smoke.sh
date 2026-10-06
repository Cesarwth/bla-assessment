#!/usr/bin/env bash
# Smoke test of the running stack (`docker compose up --build`), through the same door a browser uses: nginx on :8081.
# Needs only curl. Reads the demo password from .env. Exit code 0 = everything passed.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -f .env ]; then set -a; . ./.env; set +a; fi

BASE="${BASE:-http://localhost:8081}"
USERNAME="${DEMO_USERNAME:-demo}"
PASSWORD="${DEMO_USER_PASSWORD:?set DEMO_USER_PASSWORD (copy .env.example to .env)}"
JSON='Content-Type: application/json'
failed=0

# Bodies are built in variables first: macOS ships bash 3.2, which mangles escaped quotes nested in "$(...)".
login_body="{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}"

check() { # description expected actual
  if [ "$2" = "$3" ]; then printf '  ok    %s\n' "$1"; else printf '  FAIL  %s (expected %s, got %s)\n' "$1" "$2" "$3"; failed=1; fi
}
status() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
field() { grep -o "\"$1\":[^,}]*" | head -1 | sed 's/.*://; s/"//g'; } # first value of a JSON field (no jq needed)

echo "Smoke test against $BASE"

echo "The app"
check "serves the single-page app" 200 "$(status "$BASE/")"
check "deep links fall back to index.html" 200 "$(status "$BASE/collection/25")"
check "unknown assets are a real 404" 404 "$(status "$BASE/assets/does-not-exist.js")"
check "sends the security headers" 4 "$(curl -sI "$BASE/" | grep -ciE '^(x-content-type-options|x-frame-options|content-security-policy|referrer-policy)')"

echo "The API through the proxy"
check "catalog answers" 200 "$(status "$BASE/api/v1/pokemon?size=1")"
check "unknown Pokémon is a 404" 404 "$(status "$BASE/api/v1/pokemon/missingno")"
# A browser always sends Origin on POST. Through the proxy it must count as same-origin (this once failed with 403).
check "login works with the Origin header a browser sends" 200 "$(status -X POST "$BASE/api/v1/auth/login" -H "$JSON" -H "Origin: $BASE" -d "$login_body")"
check "a foreign Origin is still refused" 403 "$(status -X POST "$BASE/api/v1/auth/login" -H "$JSON" -H 'Origin: http://evil.example' -d "$login_body")"

TOKEN="$(curl -s -X POST "$BASE/api/v1/auth/login" -H "$JSON" -d "$login_body" | field token)"
AUTH="Authorization: Bearer $TOKEN"

if [ "${SEED_DEMO_DATA:-false}" = "true" ]; then
  echo "The demo data"
  ADMIN_PASSWORD="${DEMO_ADMIN_PASSWORD:?set DEMO_ADMIN_PASSWORD (copy .env.example to .env)}"
  admin_body="{\"username\":\"demo-admin\",\"password\":\"$ADMIN_PASSWORD\"}"
  check "the demo collection is already there (16 Pokémon)" 16 "$(curl -s "$BASE/api/v1/collection?size=48" | field totalItems)"
  check "the admin account can log in" 200 "$(status -X POST "$BASE/api/v1/auth/login" -H "$JSON" -d "$admin_body")"
  ADMIN_TOKEN="$(curl -s -X POST "$BASE/api/v1/auth/login" -H "$JSON" -d "$admin_body" | field token)"
  raichu_edit="{\"version\":$(curl -s "$BASE/api/v1/collection/26" | field version),\"nickname\":\"Raichu\"}"
  charmander_edit="{\"version\":$(curl -s "$BASE/api/v1/collection/4" | field version),\"nickname\":\"Char\"}"
  check "the demo user is refused on a Pokémon the admin added" 403 "$(status -X PATCH "$BASE/api/v1/collection/26" -H "$AUTH" -H "$JSON" -d "$raichu_edit")"
  check "the admin can edit it" 200 "$(status -X PATCH "$BASE/api/v1/collection/26" -H "Authorization: Bearer $ADMIN_TOKEN" -H "$JSON" -d "$raichu_edit")"
  check "the demo user can edit its own Pokémon" 200 "$(status -X PATCH "$BASE/api/v1/collection/4" -H "$AUTH" -H "$JSON" -d "$charmander_edit")"
fi

echo "The collection"
sync_status="$(status -X POST "$BASE/api/v1/collection/pikachu/sync" -H "$AUTH")"
case "$sync_status" in 200|201) check "sync adds or refreshes" ok ok ;; *) check "sync adds or refreshes" "200 or 201" "$sync_status" ;; esac
version="$(curl -s "$BASE/api/v1/collection/25" | field version)"
edit_kanto="{\"version\":$version,\"region\":\"Kanto\"}"
edit_johto="{\"version\":$version,\"region\":\"Johto\"}"
check "anonymous edits are refused" 401 "$(status -X PATCH "$BASE/api/v1/collection/25" -H "$JSON" -d "$edit_kanto")"
check "edit with the current version" 200 "$(status -X PATCH "$BASE/api/v1/collection/25" -H "$AUTH" -H "$JSON" -H "Origin: $BASE" -d "$edit_kanto")"
check "edit with a stale version is a conflict" 409 "$(status -X PATCH "$BASE/api/v1/collection/25" -H "$AUTH" -H "$JSON" -d "$edit_johto")"
check "a user cannot delete (admin only)" 403 "$(status -X DELETE "$BASE/api/v1/collection/25" -H "$AUTH")"

if [ "$failed" -eq 0 ]; then echo "All checks passed."; else echo "Some checks FAILED."; fi
exit "$failed"
