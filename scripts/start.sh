#!/usr/bin/env bash
# One command to run the whole project: database + API + web app + test mailbox, with demo accounts and demo data.
# Needs Docker (with the compose plugin) and curl. Safe to run again: it only creates .env if it is missing, and if a
# port it wants is already taken by something else it picks the next free one (and says so).
set -euo pipefail
cd "$(dirname "$0")/.."

command -v docker >/dev/null 2>&1 || { echo "Docker is required: https://docs.docker.com/get-docker/"; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "The Docker Compose plugin is required (docker compose ...): https://docs.docker.com/compose/install/"; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker is installed but not running: start Docker Desktop (or the docker service) and run this again."; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "curl is required."; exit 1; }

random_hex() {
  if command -v openssl >/dev/null 2>&1; then openssl rand -hex 32; else head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n'; fi
}

# Replaces the value of KEY in .env, or appends it when missing (portable: no sed -i, which differs between macOS and Linux).
set_env() {
  tmp="$(mktemp)"
  if grep -q "^$1=" .env; then
    awk -v key="$1" -v value="$2" 'BEGIN { FS = OFS = "=" } $1 == key { print key "=" value; next } { print }' .env > "$tmp"
  else
    cat .env > "$tmp"; echo "$1=$2" >> "$tmp"
  fi
  mv "$tmp" .env
}
get_env() { grep "^$1=" .env | head -1 | cut -d= -f2- || true; }

# True when something already listens on that local port.
port_in_use() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

# The example file is public, so its secrets must never be used as they are.
if [ ! -f .env ]; then
  # A database volume from an earlier run keeps the password it was created with: a new random one would not match.
  project="$(basename "$PWD" | tr 'A-Z' 'a-z' | tr -cd 'a-z0-9_-')"
  if docker volume inspect "${project}_pgdata" >/dev/null 2>&1; then
    echo "There is no .env, but a database from an earlier run exists (volume ${project}_pgdata) with the old password."
    echo "To start from scratch (this ERASES that database):"
    echo "  cp .env.example .env && docker compose down -v && rm .env && ./scripts/start.sh"
    exit 1
  fi
  cp .env.example .env
  set_env JWT_SECRET "$(random_hex)"
  set_env POSTGRES_PASSWORD "$(random_hex)"
  echo "Created .env from .env.example with a random JWT secret and database password (the demo accounts keep their demo passwords)."
elif grep -qi '^JWT_SECRET=change-me' .env; then
  set_env JWT_SECRET "$(random_hex)"
  echo "Replaced the placeholder JWT_SECRET in .env with a random one."
fi

# Ports. When the stack is already running its own containers hold them, so leave them alone; otherwise keep the
# preferred port if it is free and move to the next free one if not (a different app may be using 8080/8081/8025).
if [ -z "$(docker compose ps -q 2>/dev/null)" ]; then
  chosen=" "
  for entry in "WEB_PORT:8081" "API_PORT:8080" "MAIL_UI_PORT:8025"; do
    name="${entry%%:*}"; preferred="$(get_env "$name")"; preferred="${preferred:-${entry##*:}}"
    port="$preferred"
    while port_in_use "$port" || [[ "$chosen" == *" $port "* ]]; do port=$((port + 1)); done
    [ "$port" != "$preferred" ] && echo "Port $preferred is already in use on this machine: $name will be $port instead."
    set_env "$name" "$port"; chosen="$chosen$port "
  done
fi
WEB_PORT="$(get_env WEB_PORT)"; API_PORT="$(get_env API_PORT)"; MAIL_UI_PORT="$(get_env MAIL_UI_PORT)"
# The reset link in the e-mails must point at the port the app is really served on (unless a custom address was set).
if get_env APP_PUBLIC_URL | grep -Eq '^http://localhost:[0-9]+$'; then set_env APP_PUBLIC_URL "http://localhost:$WEB_PORT"; fi

docker compose up --build -d

echo "Waiting for the API and the web app to be ready (the first build can take a few minutes)..."
ready=0
for _ in $(seq 1 90); do
  if curl -fs "http://localhost:$API_PORT/actuator/health" >/dev/null 2>&1 && curl -fs "http://localhost:$WEB_PORT/" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 2
done
if [ "$ready" -ne 1 ]; then
  echo "The app did not become ready in time. See what happened with: docker compose logs"
  exit 1
fi

set -a; . ./.env; set +a
cat <<MESSAGE

Ready. Open the web app and log in with a demo account.

  Web app : http://localhost:$WEB_PORT
  API     : http://localhost:$API_PORT   (health: /actuator/health)
  Mailbox : http://localhost:$MAIL_UI_PORT   (the password-reset e-mails of the demo arrive here)

  Demo accounts (for testing only):
    user  : demo       / ${DEMO_USER_PASSWORD:-<not set>}   a trainer with their own collection
    admin : demo-admin / ${DEMO_ADMIN_PASSWORD:-<not set>}   a trainer with their own collection

  The ports are bound to this machine only (127.0.0.1); put a TLS-terminating proxy in front to expose them.

  Check the running stack : ./scripts/smoke.sh
  Stop it                 : docker compose down        (add -v to also erase the database)
MESSAGE
