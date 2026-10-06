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
- Frontend: React 19 + Vite 7 + TypeScript 5.9 (strict) in `frontend/`; TanStack Query for server state, React Router, **Tailwind CSS v4** (theme tokens in `@theme`, mobile-first); Vitest + React Testing Library + MSW + axe
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
- Versioned routes under `/api/v1`. Public: `/api/v1/auth/**`, `GET /api/v1/pokemon/**`, Swagger/health. Protected: the whole `/api/v1/collection/**` (each trainer only sees and edits their own entries: another trainer's entry is a 404).

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
docker compose up --build      # postgres + backend + frontend; the app is on http://localhost:8081, the API on :8080
./scripts/smoke.sh             # checks the running stack through nginx, the way a browser reaches it
cd frontend && npm ci && npm run typecheck && npm run lint && npm run test:coverage && npm run build
cd frontend && npm run dev     # Vite on :5173, proxies /api to localhost:8080
```

## Frontend rules

- Feature folders mirror the API domains (`features/auth`, `catalog`, `collection`); `shared/` holds the API client and UI primitives (`shared/ui`). Features never import each other: `app/App.tsx` is the only place that composes them (e.g. the detail page receives `AddToCollection` through a prop).
- One API client (`shared/api/client.ts`) is the only code that calls `fetch`; it turns every failure into `ApiError` using the backend's error shape. Server state lives in TanStack Query, paging in the URL, auth in `AuthProvider`; no global store.
- Text from the API (descriptions, nicknames, tags) is rendered as text, never as HTML.
- **Styling = Tailwind v4, one stylesheet.** `src/index.css` is the only CSS file: `@import "tailwindcss"`, the `@theme` tokens (semantic colors `canvas`, `surface`, `fg`, `muted`, `border`, `primary`, …, per Pokémon type a palette `--color-type-<t>-from/-to/-pill/-ink` exposed through `data-type` and the `type-card`, `type-glass`, `type-pill` utilities, stat bar colors, radius, shadow, `max-w-content`), the dark palette (the same variables overridden under `prefers-color-scheme: dark`, so components never need `dark:`), a few `@utility` blocks and the reduced-motion rule. Components use semantic utilities (`bg-surface`, `text-muted`), never raw hex. No CSS Modules, no `@apply` sprawl: repeated patterns are components in `shared/ui` (`Button`, `Card`, `Badge`, `Alert`, `RemovableChip`, `BackLink`, field classes…). `className` on those primitives is for layout only. `PokemonCardFrame` (type gradient, glass number pill, glowing artwork, inset white panel) and `PokemonHero` (detail banner) are the shared card and banner. **Contrast is enforced:** `index.css.test.ts` reads the real stylesheet and requires WCAG AA (≥ 4.5:1) for the ink on every type gradient, so a new palette value that fails breaks the build. **Whole-card links:** the card's title link covers the card with `after:absolute after:inset-0 after:z-1`; the z-index matters because the positioned art area would otherwise paint above it and swallow clicks (jsdom cannot see this: it was found with real mouse events in Chrome). Controls that must stay clickable on a card (the selection checkbox) sit at the same z-index later in the DOM.
- **Responsive, mobile-first:** design for 375 px and scale up with `sm:`/`md:`; tap targets ≥ 44 px on phones; no horizontal scroll from 320 px. Every list page keeps its state in the URL, and every detail page has a `BackLink` (history back when there is in-app history, a fallback link otherwise).
- Tests are written first and use MSW at the network level. Any `console.error`/`console.warn` fails a test; pages are checked with axe. Coverage gate: lines/statements/functions 90 %, branches 85 %.
- Things jsdom cannot see (real CORS, CSP, nginx, layout, responsive behaviour) are covered by `scripts/smoke.sh` and a check in real Chrome through the DevTools protocol (3 widths × light/dark, no horizontal overflow, no console problems).

## Code standards

- Constructor injection only, `final` fields, no field `@Autowired`.
- Java 21 features welcome: records for DTOs/value objects, sealed types, pattern matching, `Optional` return from lookups (never return null).
- Bean Validation on request DTOs (`@Valid`); domain invariants re-checked in the domain/use case.
- No secrets in code or in git: JWT secret, DB password come from env vars; demo credentials live only in seed data documented in README (SOC2/PCI: least privilege, no hardcoded secrets, passwords hashed with BCrypt).
- Comments explain *why*, not *what*. No dead code, no TODO without reason.
- PokeAPI calls: timeouts, graceful mapping of 404/5xx to domain errors, cache responses.

## Workflow skills

`/research` -> `/plan` -> `/implement` -> `/code-review`. Artifacts: `research.md`, `plan.md` at the project root (git-ignored or kept for the interview presentation).
