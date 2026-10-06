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
# First value of a JSON field (no jq needed). Prints nothing, instead of aborting the script, when it is absent.
field() { grep -o "\"$1\":[^,}]*" | head -1 | sed 's/.*://; s/"//g' || true; }

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

COLLECTION="$BASE/api/v1/collection"
echo "Collections are private"
check "anonymous visitors cannot read a collection" 401 "$(status "$COLLECTION")"

if [ "${SEED_DEMO_DATA:-false}" = "true" ]; then
  echo "The demo data"
  ADMIN_PASSWORD="${DEMO_ADMIN_PASSWORD:?set DEMO_ADMIN_PASSWORD (copy .env.example to .env)}"
  admin_body="{\"username\":\"demo-admin\",\"password\":\"$ADMIN_PASSWORD\"}"
  check "the admin account can log in" 200 "$(status -X POST "$BASE/api/v1/auth/login" -H "$JSON" -d "$admin_body")"
  ADMIN_TOKEN="$(curl -s -X POST "$BASE/api/v1/auth/login" -H "$JSON" -d "$admin_body" | field token)"
  demo_total="$(curl -s "$COLLECTION?size=48" -H "$AUTH" | field totalItems)"
  admin_total="$(curl -s "$COLLECTION?size=48" -H "Authorization: Bearer $ADMIN_TOKEN" | field totalItems)"
  check "the demo trainer already has a collection" yes "$([ "${demo_total:-0}" -ge 1 ] && echo yes || echo no)"
  check "the admin trainer already has a collection" yes "$([ "${admin_total:-0}" -ge 1 ] && echo yes || echo no)"
fi

echo "A trainer's collection"
# A second trainer, new on every run, proves the collections are independent of each other.
OTHER="smoke$$"
register_body="{\"username\":\"$OTHER\",\"email\":\"$OTHER@example.com\",\"password\":\"Smoke${$}pass1\"}"
other_login="{\"username\":\"$OTHER\",\"password\":\"Smoke${$}pass1\"}"
check "a new trainer can create a profile" 201 "$(status -X POST "$BASE/api/v1/auth/register" -H "$JSON" -d "$register_body")"
OTHER_AUTH="Authorization: Bearer $(curl -s -X POST "$BASE/api/v1/auth/login" -H "$JSON" -d "$other_login" | field token)"
check "a new trainer starts with an empty collection" 0 "$(curl -s "$COLLECTION" -H "$OTHER_AUTH" | field totalItems)"

# Mew (151) is not part of the demo data, so adding and removing it never touches it.
sync_status="$(status -X POST "$COLLECTION/mew/sync" -H "$AUTH")"
case "$sync_status" in 200|201) check "sync adds or refreshes" ok ok ;; *) check "sync adds or refreshes" "200 or 201" "$sync_status" ;; esac
version="$(curl -s "$COLLECTION/151" -H "$AUTH" | field version)"
edit_kanto="{\"version\":$version,\"region\":\"Smoke $$-a\"}"
edit_johto="{\"version\":$version,\"region\":\"Smoke $$-b\"}"
check "anonymous edits are refused" 401 "$(status -X PATCH "$COLLECTION/151" -H "$JSON" -d "$edit_kanto")"
check "edit with the current version" 200 "$(status -X PATCH "$COLLECTION/151" -H "$AUTH" -H "$JSON" -H "Origin: $BASE" -d "$edit_kanto")"
check "edit with a stale version is a conflict" 409 "$(status -X PATCH "$COLLECTION/151" -H "$AUTH" -H "$JSON" -d "$edit_johto")"
check "another trainer does not see it" 404 "$(status "$COLLECTION/151" -H "$OTHER_AUTH")"
check "another trainer cannot edit it" 404 "$(status -X PATCH "$COLLECTION/151" -H "$OTHER_AUTH" -H "$JSON" -d "$edit_johto")"
check "another trainer can add the same Pokémon" 201 "$(status -X POST "$COLLECTION/mew/sync" -H "$OTHER_AUTH")"
check "its notes are independent of the first trainer's" "" "$(curl -s "$COLLECTION/151" -H "$OTHER_AUTH" | field region | grep -o 'Smoke' || true)"
check "a trainer removes their own copy" 204 "$(status -X DELETE "$COLLECTION/151" -H "$AUTH")"
check "it is gone for them" 404 "$(status "$COLLECTION/151" -H "$AUTH")"
check "but the other trainer still has theirs" 200 "$(status "$COLLECTION/151" -H "$OTHER_AUTH")"
check "the other trainer cleans up" 204 "$(status -X DELETE "$COLLECTION/151" -H "$OTHER_AUTH")"

if [ "$failed" -eq 0 ]; then echo "All checks passed."; else echo "Some checks FAILED."; fi
exit "$failed"
