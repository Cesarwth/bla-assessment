#!/usr/bin/env bash
# One command to run the whole project: database + API + web app, with demo accounts and demo data.
# Needs Docker (with the compose plugin) and curl. Safe to run again: it only creates .env if it is missing.
set -euo pipefail
cd "$(dirname "$0")/.."

command -v docker >/dev/null 2>&1 || { echo "Docker is required: https://docs.docker.com/get-docker/"; exit 1; }

random_hex() {
  if command -v openssl >/dev/null 2>&1; then openssl rand -hex 32; else head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n'; fi
}

# Replaces the value of KEY in .env (portable: no sed -i, which differs between macOS and Linux).
set_env() {
  tmp="$(mktemp)"
  awk -v key="$1" -v value="$2" 'BEGIN { FS = OFS = "=" } $1 == key { print key "=" value; next } { print }' .env > "$tmp"
  mv "$tmp" .env
}

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

docker compose up --build -d

echo "Waiting for the API and the web app to be ready (the first build can take a few minutes)..."
ready=0
for _ in $(seq 1 90); do
  if curl -fs http://localhost:8080/actuator/health >/dev/null 2>&1 && curl -fs http://localhost:8081/ >/dev/null 2>&1; then
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

Ready.

  Web app : http://localhost:8081
  API     : http://localhost:8080   (health: /actuator/health)

  Both ports are bound to this machine only (127.0.0.1); put a TLS-terminating proxy in front to expose them.

  Demo accounts (from .env):
    admin : demo-admin / ${DEMO_ADMIN_PASSWORD:-<not set>}   a trainer with their own collection
    user  : demo       / ${DEMO_USER_PASSWORD:-<not set>}   a trainer with their own collection

  Check the running stack : ./scripts/smoke.sh
  Stop it                 : docker compose down        (add -v to also erase the database)
MESSAGE
