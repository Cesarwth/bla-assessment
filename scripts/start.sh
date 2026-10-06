#!/usr/bin/env bash
# One command to run the whole project: database + API + web app, with demo accounts and demo data.
# Needs Docker (with the compose plugin) and curl. Safe to run again: it only creates .env if it is missing.
set -euo pipefail
cd "$(dirname "$0")/.."

command -v docker >/dev/null 2>&1 || { echo "Docker is required: https://docs.docker.com/get-docker/"; exit 1; }

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from .env.example (demo-only values; change them for anything real)."
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

  Demo accounts (from .env):
    admin : demo-admin / ${DEMO_ADMIN_PASSWORD:-<not set>}   can edit everything and delete
    user  : demo       / ${DEMO_USER_PASSWORD:-<not set>}   can edit what it added

  Check the running stack : ./scripts/smoke.sh
  Stop it                 : docker compose down        (add -v to also erase the database)
MESSAGE
