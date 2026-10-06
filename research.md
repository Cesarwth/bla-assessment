# Research: Plan D — one repo, backend + frontend (ownership, versioned edits, React app)

**Story/Problem:** the exercise requires a modern frontend (React or Vue) that is responsive, user-centric, executes the CRUD use cases, keeps clean component organization and efficient state management, and ships in the same delivery as the API. Review decisions also change the API (owner-based edits, versioned PATCH) and the delivery must be ONE link.
**Date:** 2026-10-06
**Builds on:** Plans A-C and review batches 1-2 (HEAD `5d1098d`). Earlier research in `docs/`.

## Problem Statement

Make the repository a single deliverable with two clearly separated domains (`backend/`, `frontend/`) started by one `docker compose up`, change the collection API so edits belong to their owner and carry a version, and build a React app that consumes the catalog (US-01/02), the sync (US-03) and the local edits (US-04) with login.

## What the document says (S1 question)

- US-04: "Enable update operations for **any** Pokemon currently stored within the local database" + 404 / 400 / "further defensive logic".
- Technical: "auxiliary API for user registration, authentication, and the management of **protected versus public routes**"; database needs a primary entity and "a secondary collection for user management".
- Nothing mentions ownership or per-user data. So per-owner is **not required**; it is a defensible, small addition of "defensive logic" that gives the users table a real purpose. Risk of the document reading: "any Pokemon" is satisfied because owner **or ADMIN** can edit any record.

## Affected Code

| File | Role | Change needed |
| ---- | ---- | ------------- |
| root `build.gradle`, `settings.gradle`, `gradle/`, `gradlew*`, `src/`, `Dockerfile`, `.dockerignore`, `HELP.md` | backend project at repo root | move under `backend/` (history preserved with `git mv`) |
| `docker-compose.yml`, `.env.example`, `.gitignore` | orchestration | `build: ./backend`, add `frontend` service |
| `CLAUDE.md`, `.claude/commands/code-review.md`, `.claude/skills/{plan,implement}/SKILL.md` | workflow docs mention `./gradlew`/paths | update to `cd backend && ./gradlew ...` |
| `domain/StoredPokemon.java` | aggregate | add `ownerId`; `canBeEditedBy(actor)` |
| `application/port/in/*Sync*`, `UpdateStoredPokemonCommand`, use cases `SyncPokemon`, `SyncPokemonRange`, `UpdateStoredPokemon` | use cases | receive the acting user; ownership + version rules |
| `infrastructure/persistence/PokemonEntity|Mapper`, new `V3__add_pokemon_owner.sql` | persistence | `owner_id` FK to `users` |
| `infrastructure/web/CollectionController`, DTOs, `GlobalExceptionHandler` | web | build the actor from the JWT, PATCH requires `version`, 403 for non-owner |
| `infrastructure/security/JwtTokenIssuer` | already puts `uid`, `sub`, `role` in the token | no change; the frontend can read the same claims |

## API surface the frontend will consume (verified against code)

- Public: `GET /api/v1/pokemon?page&size` -> `{items[{id,name,spriteUrl,types,weightKg,abilities}], page,size,totalItems,totalPages}`; `GET /api/v1/pokemon/{idOrName}` -> detail incl. `stats[{name,baseValue}]`, `genus`, `description`, `evolutionChain` tree `{id,name,spriteUrl,requirements[],evolvesTo[]}` (nullable fields: `imageUrl`, `description`, `genus`, `evolutionChain`).
- Public: `GET /api/v1/collection[?page&size]`, `GET /api/v1/collection/{id}` (adds `version`, `nickname`, `region`, `tags`, `syncedAt`; `id` is the PokeAPI id).
- Auth: `POST /api/v1/auth/{register,login}` -> login returns `{token, tokenType:"Bearer", expiresAt}`; JWT claims `sub` (username), `uid`, `role`.
- Protected: `POST /collection/{idOrName}/sync` (201 created / 200 refreshed), `POST /collection/sync {from,to}` (`{created,updated,failures[],abortedReason}`), `PATCH /collection/{id}`, `DELETE /collection/{id}` (ADMIN).
- Errors: one shape `{timestamp,status,code,message,fieldErrors[{field,message}]}`; codes the UI must map: `VALIDATION_FAILED`, `INVALID_*`, `POKEMON_NOT_FOUND`, `STORED_POKEMON_NOT_FOUND`, `USERNAME_TAKEN`, `EMAIL_TAKEN`, `INVALID_CREDENTIALS`, `UNAUTHORIZED`, `FORBIDDEN`, `CONCURRENT_MODIFICATION`, `CATALOG_UNAVAILABLE`, `CATALOG_TIMEOUT`.
- Public GET responses carry `Cache-Control: public, max-age=60`.

## Constraints

- **One link:** single repo, one `docker compose up` brings DB + API + UI; README explains both. Node 22.21 / npm 10.9 are installed locally; Docker builds the UI so a reviewer needs only Docker.
- **Same origin in Docker:** the UI container (nginx) serves the SPA and proxies `/api/` to the API, so no CORS in the demo; the dev server (Vite) proxies the same way. CORS config in the API stays for direct use.
- **Frontend versions are very new** (npm latest today): react/react-dom 19.3, vite 8.3, @vitejs/plugin-react 6.1, typescript 7.0, vitest 5.0, @testing-library/react 16.3, jsdom 30, msw 3.0, react-router 7.18, @tanstack/react-query 5.104, eslint 10 + typescript-eslint 8.71. Compatibility between them (TS 7 vs typescript-eslint, vitest 5 vs vite 8, msw 3 API) is unverified.
- **Token storage:** no refresh tokens and no cookies on the API. `sessionStorage` (cleared when the tab closes) vs memory-only (lost on reload) vs `localStorage` (XSS-exposed, persists).
- **Evaluation:** "no warnings in the browser console", responsiveness, clean component organization, efficient state management, CRUD for the use cases.
- **Org rule:** a UI for external/company use should first be offered to the Foundation DS UI Builder; this is a personal exercise for another company (individual use), so building it here is the working assumption; the user can switch.

## Existing Patterns

Backend: ports/adapters, one error shape, test-first, ArchUnit guard, JaCoCo gate (90/80). The frontend will mirror the **domains** (`auth`, `catalog`, `collection`) as feature folders, with a thin `shared/` for the API client and UI atoms; server state in TanStack Query, auth in a small context, pagination in the URL.

## Risks

- **Regression (restructure):** moving files can break Docker paths, CI-less habits (`./gradlew` at root), `.claude` docs; mitigated by running the full suite and the compose smoke from the new location before any other change.
- **Regression (contract):** making `version` required and adding the actor changes every PATCH/sync test and the e2e flows (~30 tests); the JaCoCo gate and ArchUnit stay as guards.
- **Authorization:** owner check must live in the use case (defense in depth), not only in the controller; a non-owner with a stale version must get 403, never 409/200 (no information leak).
- **Frontend toolchain:** a bleeding-edge combination may not install or run together; fail early in phase 1 and pin working versions.
- **Security (UI):** JWT in storage is exposed to XSS; render API text as text only (no `dangerouslySetInnerHTML`; PokeAPI descriptions and user nicknames are untrusted), strict CSP in nginx.
- **UX/perf:** a cold catalog page takes ~0.5 s (parallel upstream calls) and a cold detail ~0.2 s; needs skeletons and `keepPreviousData` so paging does not flash; images need fixed dimensions and lazy loading to avoid layout shift.
- **Test blindspot:** console warnings (React `act`, router future flags, missing keys) only surface if tests fail on `console.error/warn`; accessibility regressions need an automated check.
- **Challenge (should this exist?):** a full SPA is mandatory by the brief; the owner rule is optional but cheap and meaningful. What should NOT be built: client-side search over the catalog (the API has no search; a "go to name/id" box covers the need), multi-user collections per se, refresh tokens, i18n.

## Test Coverage

- **Covered (backend):** 307 tests, 98 % line / 87 % branch, role matrix, concurrency, e2e.
- **Gaps:** nothing for ownership/version (new), nothing for the UI.
- **Frontend test patterns:** Vitest + React Testing Library + MSW (network-level mocks so the real API client runs), `vitest-axe` for accessibility, console errors fail the test run, coverage gate like the backend.

## Decisions (resolved with the user, 2026-10-06)

1. Edits by **owner** (first user to sync the record) **or ADMIN**; reads public; delete stays ADMIN-only.
2. **No** login rate limiting / lockout and no 12-character rule (it is a game, not a bank): documented as a conscious decision in the README.
3. PATCH **must carry `version`** (optimistic locking visible to clients).
4. Minor review notes (repeated handlers, `RegisterAdmin`, cache stampede, Docker digests/read-only): **not necessary, skipped**; mention hardening ideas in the README only.
5. **One repository**, separated by domain: `backend/` and `frontend/` at the top level, one compose file, one README.

## Open Questions

None — ready for planning. (Assumptions in the plans: React + Vite + TypeScript, CSS Modules with design tokens and no UI kit, `sessionStorage` for the token, UI served on port 3000.)
