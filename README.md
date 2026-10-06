# BLA Assessment — Pokemon API + frontend

One repository, two domains:

| Folder | What | Tech |
| ------ | ---- | ---- |
| `backend/` | REST API: PokeAPI catalog, local collection, auth | Java 21, Spring Boot, PostgreSQL, Flyway, JWT |
| `frontend/` | Web app: catalog, detail, collection, login | React 19, Vite, TypeScript, TanStack Query |

## Run everything

```bash
cp .env.example .env     # then change the placeholder secrets
docker compose up --build
```

- App: http://localhost:8081 (nginx serves the UI and proxies `/api` to the backend)
- API: http://localhost:8080 (health: `/actuator/health`)

Check the running stack with `./scripts/smoke.sh`.

> This README is a stub; the full environment guide, demo credentials and technical documentation are written in Plan E.
