# BLA Assessment — Pokemon API + frontend

One repository, two domains:

| Folder | What | Tech |
| ------ | ---- | ---- |
| `backend/` | REST API: PokeAPI catalog, local collection, auth | Java 21, Spring Boot, PostgreSQL, Flyway, JWT |
| `frontend/` | Web app (coming in Plan D2) | React, Vite, TypeScript |

## Run everything

```bash
cp .env.example .env     # then change the placeholder secrets
docker compose up --build
```

The API listens on http://localhost:8080 (health: `/actuator/health`).

> This README is a stub; the full environment guide, demo credentials and technical documentation are written in Plan E.
