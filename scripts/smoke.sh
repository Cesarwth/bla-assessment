#!/usr/bin/env bash
# Smoke test of the running stack (`docker compose up --build`), through the same door a browser uses: nginx on WEB_PORT (8081 by default).
# Needs only curl. Reads the demo password from .env. Exit code 0 = everything passed.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -f .env ]; then set -a; . ./.env; set +a; fi

BASE="${BASE:-http://localhost:${WEB_PORT:-8081}}"
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
bulk_body='{"ids":[1,4,7,4]}'
check "several selected Pokémon are added at once (duplicates collapsed)" 3 "$(curl -s -X POST "$COLLECTION/sync" -H "$OTHER_AUTH" -H "$JSON" -d "$bulk_body" | field created)"
check "they show up as collected" "[1,4,7]" "$(curl -s "$COLLECTION/ids" -H "$OTHER_AUTH")"
check "an empty selection is refused" 400 "$(status -X POST "$COLLECTION/sync" -H "$OTHER_AUTH" -H "$JSON" -d '{"ids":[]}')"

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

echo "Password recovery and profile"
MAILBOX="${MAILBOX:-http://localhost:${MAIL_UI_PORT:-8025}}"
# Bodies are built in variables first (bash 3.2 mangles escaped quotes nested in "$(...)").
forgot_known="{\"email\":\"$OTHER@example.com\"}"
forgot_unknown='{"email":"nobody-here@example.com"}'
check "the reset request is answered the same for a known e-mail" 202 "$(status -X POST "$BASE/api/v1/auth/forgot-password" -H "$JSON" -d "$forgot_known")"
check "and for an unknown one (nothing is revealed)" 202 "$(status -X POST "$BASE/api/v1/auth/forgot-password" -H "$JSON" -d "$forgot_unknown")"
sleep 2
mail_id="$(curl -s "$MAILBOX/api/v1/search?query=to:$OTHER@example.com" | grep -o '"ID":"[^"]*"' | head -1 | sed 's/.*://; s/"//g' || true)"
reset_token="$(curl -s "$MAILBOX/api/v1/message/$mail_id" | grep -o 'token=[A-Za-z0-9_-]*' | head -1 | sed 's/token=//' || true)"
check "the reset e-mail arrives in the mailbox with a link" yes "$([ -n "$reset_token" ] && echo yes || echo no)"
new_pw="Fresh${$}pass2"
weak_body="{\"token\":\"$reset_token\",\"newPassword\":\"short\"}"
reset_body="{\"token\":\"$reset_token\",\"newPassword\":\"$new_pw\"}"
reuse_body="{\"token\":\"$reset_token\",\"newPassword\":\"${new_pw}x\"}"
new_login="{\"username\":\"$OTHER\",\"password\":\"$new_pw\"}"
check "a weak new password is refused" WEAK_PASSWORD "$(curl -s -X POST "$BASE/api/v1/auth/reset-password" -H "$JSON" -d "$weak_body" | field code)"
check "the link sets a new password" 204 "$(status -X POST "$BASE/api/v1/auth/reset-password" -H "$JSON" -d "$reset_body")"
check "the link works only once" INVALID_RESET_TOKEN "$(curl -s -X POST "$BASE/api/v1/auth/reset-password" -H "$JSON" -d "$reuse_body" | field code)"
check "the old password no longer works" 401 "$(status -X POST "$BASE/api/v1/auth/login" -H "$JSON" -d "$other_login")"
check "the new password does" 200 "$(status -X POST "$BASE/api/v1/auth/login" -H "$JSON" -d "$new_login")"
PROFILE_AUTH="Authorization: Bearer $(curl -s -X POST "$BASE/api/v1/auth/login" -H "$JSON" -d "$new_login" | field token)"
email_body="{\"email\":\"other$OTHER@example.com\"}"
check "a trainer reads their profile" "$OTHER" "$(curl -s "$BASE/api/v1/users/me" -H "$PROFILE_AUTH" | field username)"
check "changing the e-mail needs the current password" INVALID_CURRENT_PASSWORD "$(curl -s -X PATCH "$BASE/api/v1/users/me" -H "$PROFILE_AUTH" -H "$JSON" -d "$email_body" | field code)"
check "the profile endpoints are private" 401 "$(status "$BASE/api/v1/users/me")"

if [ "$failed" -eq 0 ]; then echo "All checks passed."; else echo "Some checks FAILED."; fi
exit "$failed"
