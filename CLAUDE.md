# BLA Assessment — Pokemon API

Technical interview exercise (Ballast Lane). RESTful API on Spring Boot that wraps PokeAPI, replicates data locally, and lets users modify it, plus a React frontend. Source requirements: `Java - BLA - Technical Interview Exercise V2.pdf`.

## Repository layout (one repo, two domains)

```
backend/            Spring Boot API (Gradle project, Dockerfile, tests)
frontend/           React + Vite + TypeScript app (feature folders mirror the API domains)
docker-compose.yml  db + backend + frontend, one command: docker compose up --build
docs/               plans, research, GenAI write-up
.claude/            workflow skills (/research /plan /implement /code-review)
```

All Java paths below (`src/main/...`, `src/test/...`) are relative to `backend/`.

## Stack

- Java 21 (Gradle toolchain), Spring Boot 4.1.x, Gradle (Groovy DSL, wrapper `backend/gradlew`)
- Persistence: relational DB (PostgreSQL in Docker, H2 for tests), Flyway migrations, Spring Data JPA (infrastructure only)
- Security: Spring Security + JWT (register / login, public vs protected routes)
- Cache: Spring Cache + Caffeine for PokeAPI responses
- Tests: JUnit 5, Mockito, AssertJ, MockMvc, `MockRestServiceServer` + captured JSON fixtures (PokeAPI stub), ArchUnit
- Frontend: React + Vite + TypeScript in `frontend/` (responsive, consumes the API)
- Delivery: `Dockerfile` (multi-stage) + `docker-compose.yml`, README, seeded demo data and demo credentials

## User Stories (the backlog)

- **US-01 Enumeration** — paginated list: sprite, category (type), mass (weight), skills (abilities). Cache nice-to-have.
- **US-02 Detail** — image, core stats, description (flavor text), evolution chain.
- **US-03 Sync** — persist PokeAPI data into the local DB; leave room for custom fields (localized name, region, internal tags).
- **US-04 Local update** — update stored Pokemon; 404 missing, 400 malformed payload, other defensive checks (409 conflicts, 401/403).
- **Auth API** — register, login, protected vs public routes.
- **GenAI section** — task-management API prompt, output sample, how it was validated/corrected (document in `docs/genai.md`).

## Architecture (Clean Architecture, package `com.ballastlane.assessment`)

```
domain/            entities, value objects, domain exceptions — NO Spring, NO JPA, NO Jackson
application/
  port/in/         use case interfaces
  port/out/        PokemonRepository, PokeApiGateway, UserRepository, PasswordHasher, TokenIssuer
  usecase/         use case implementations + validation/business rules (plain Java)
infrastructure/
  web/             controllers, request/response DTOs, mappers, @RestControllerAdvice
  persistence/     JPA entities, Spring Data repos, adapters implementing port/out
  pokeapi/         HTTP client adapter + PokeAPI DTOs -> domain mapping, cache config
  security/        JWT, filters, SecurityFilterChain, password encoder adapter
  config/          bean wiring
```

Rules:

- Dependencies point inward only: `infrastructure -> application -> domain`. Enforced by an ArchUnit test — keep it green.
- `domain` and `application` never import `org.springframework.*`, `jakarta.persistence.*`, or infrastructure packages. Use cases are wired as beans from `infrastructure/config`.
- Controllers never touch repositories or JPA entities; they call use case interfaces and map DTOs.
- Never expose JPA entities or PokeAPI DTOs over the REST API.
- Consistent error body from one `@RestControllerAdvice` (status, error code, message, field errors, timestamp). No stack traces to clients.
- Versioned routes under `/api/v1`. Public: `/api/v1/auth/**`, `GET /api/v1/pokemon/**`, Swagger/health. Protected: sync and update/delete.

## TDD workflow (mandatory)

Red -> Green -> Refactor, one behavior at a time. Commit history should show the test before the implementation where practical.

- Domain / use cases: pure unit tests, Mockito for ports only.
- Web: `@WebMvcTest` slices (validation, status codes, security rules).
- Persistence: `@DataJpaTest` against H2 or Testcontainers.
- PokeAPI adapter: `MockRestServiceServer` with fixtures; never call the real PokeAPI in tests.
- A few end-to-end `@SpringBootTest` flows for happy paths (login -> sync -> update).
- Every use case, adapter, mapper, and exception handler has tests. Bug fix = failing test first.

## Commands

```bash
cd backend
./gradlew test                 # all tests
./gradlew test --tests '*PokemonServiceTest'
./gradlew build                # compile + test + jacoco verification (line >= 90%, branch >= 80%)
./gradlew bootRun              # run locally (needs DB_* and JWT_SECRET env vars)
cd ..
docker compose up --build      # postgres + backend (+ frontend once it exists)
cd frontend && npm test && npm run build
```

## Code standards

- Constructor injection only, `final` fields, no field `@Autowired`.
- Java 21 features welcome: records for DTOs/value objects, sealed types, pattern matching, `Optional` return from lookups (never return null).
- Bean Validation on request DTOs (`@Valid`); domain invariants re-checked in the domain/use case.
- No secrets in code or in git: JWT secret, DB password come from env vars; demo credentials live only in seed data documented in README (SOC2/PCI: least privilege, no hardcoded secrets, passwords hashed with BCrypt).
- Comments explain *why*, not *what*. No dead code, no TODO without reason.
- PokeAPI calls: timeouts, graceful mapping of 404/5xx to domain errors, cache responses.

## Workflow skills

`/research` -> `/plan` -> `/implement` -> `/code-review`. Artifacts: `research.md`, `plan.md` at the project root (git-ignored or kept for the interview presentation).
