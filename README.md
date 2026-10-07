# Pokédex — Spring Boot API + React app

A full-stack take on the Ballast Lane Java exercise. A **REST API** (Java 21, Spring Boot, PostgreSQL) wraps the public [PokeAPI](https://pokeapi.co/docs/v2), lets every trainer **build their own collection** by replicating Pokémon into a local database and **keep personal notes** on them (nickname, region, tags), and a **React app** (TypeScript) lets you browse the catalog, collect and edit. It is built with **Clean Architecture** and **TDD**, and everything starts with one command.

**Reviewing this?** [`PRESENTATION.md`](PRESENTATION.md) explains the thought process and decisions in about five minutes; the generative-AI exercise is in [`docs/genai.md`](docs/genai.md); to run everything, jump to [Quick start](#quick-start).

![Catalog](docs/screenshots/catalog.png)

| My collection (logged in as `demo`) | Editing your notes |
| --- | --- |
| ![Collection](docs/screenshots/collection.png) | ![Entry](docs/screenshots/entry-edit.png) |

## Contents

1. [Quick start](#quick-start)
2. [Demo accounts and data](#demo-accounts-and-data)
3. [What it does (user stories)](#what-it-does-user-stories)
4. [Architecture](#architecture) (with the SOLID / DRY notes)
5. [API reference](#api-reference)
6. [Trainers, privacy and concurrency](#trainers-privacy-and-concurrency)
7. [The web app](#the-web-app)
8. [Configuration](#configuration)
9. [Development without Docker for everything](#development-without-docker-for-everything)
10. [Testing and quality](#testing-and-quality)
11. [Design decisions and trade-offs](#design-decisions-and-trade-offs)
12. [Security notes](#security-notes)
13. [Repository layout](#repository-layout)
14. [Troubleshooting](#troubleshooting)
15. [How it was built (process and AI)](#how-it-was-built-process-and-ai)

## Quick start

**You need:** Docker (Docker Desktop, or Docker Engine with the Compose plugin), `curl`, and a terminal with `bash` (macOS, Linux, WSL or Git Bash). No Java, Node or database installed; no accounts, no keys, nothing to configure.

> **On a Mac without Docker Desktop:** [Rancher Desktop](https://rancherdesktop.io/) works as a free replacement. In its *Preferences → Container Engine* choose **dockerd (moby)** (not containerd), so that the `docker` and `docker compose` commands the script uses exist, and make sure it is running before you start. (Colima and Podman Desktop with Docker compatibility are alternatives.) The project itself is plain Docker Compose and uses nothing specific to Docker Desktop; the script has been run here with Docker Desktop only, so if Rancher gives you trouble, the troubleshooting table below is the first stop.

```bash
git clone https://github.com/Cesarwth/bla-assessment.git
cd bla-assessment
./scripts/start.sh
```

That single command does everything (the first build takes a few minutes):

1. checks that Docker is installed and **running** (and says what to do if not);
2. creates `.env` with **random secrets** for the JWT and the database (the example file is public, so its secrets are never used as they are);
3. **picks free ports**: it prefers `8081` (web), `8080` (API) and `8025` (mailbox) and, if something else on your machine already uses one, moves to the next free port and tells you;
4. builds and starts PostgreSQL, the API, the web app and a local test mailbox, waits until they answer, and **prints the real addresses and the demo accounts**.

```text
Ready. Open the web app and log in with a demo account.

  Web app : http://localhost:8081
  API     : http://localhost:8080   (health: /actuator/health)
  Mailbox : http://localhost:8025   (the password-reset e-mails of the demo arrive here)

  Demo accounts (for testing only):
    user  : demo       / Demo12345   a trainer with their own collection
    admin : demo-admin / Admin12345   a trainer with their own collection
```

Open the **Web app** address that was printed (it can differ from the one above if a port was busy) and log in. The system is **not empty**: both demo trainers already have Pokémon in their collection.

| What | Default address | Notes |
| --- | --- | --- |
| Web app | http://localhost:8081 | the only door for the browser (nginx serves the app and proxies `/api`) |
| API | http://localhost:8080 | health: `/actuator/health` |
| Mailbox (demo) | http://localhost:8025 | the password-reset e-mails arrive here ([Mailpit](https://mailpit.axllent.org/), a local test inbox; nothing is sent to the internet) |

All ports are bound to **this machine only** (`127.0.0.1`); see [Security notes](#security-notes) before exposing them. The accounts and sample data are **for testing only**: a real deployment turns them off (see [Configuration](#configuration)).

### A two-minute tour (what to try)

1. **Log in** as `demo` → *Catalog*: type `char` in the filter (it narrows the list as you type), or a number such as `25`.
2. Press **Select Pokémon**, tick a few cards (the chosen names appear in a tray) and **Add** them to your collection; cards you already have are marked *In my collection*.
3. Open **My collection**, click a card, **Edit notes** (nickname, region, tags) and save. Use the **Back** arrow to return to the list exactly as you left it.
4. Log out and log in as `demo-admin`: its collection is separate, and the same Pokémon can carry different notes.
5. **Create profile** (register) with any name and e-mail, then try **Forgot your password?**: open the *Mailbox* address, click the link in the e-mail and choose a new password. Your **profile** page (click your username) changes your details and password.
6. Resize the window or open it on a phone: the layout is responsive; dark mode follows the system setting.

### Check it, stop it, other cases

```bash
./scripts/smoke.sh        # 40 checks against the running stack, exit code 0 when all pass (the same script runs in CI)
docker compose down       # stop (add -v to also erase the database)
```

- **Re-running** `./scripts/start.sh` is safe: it keeps your `.env` and the ports the stack already uses.
- **Windows without bash:** `copy .env.example .env`, put a random value in `JWT_SECRET` (the API refuses the placeholder on purpose), then `docker compose up --build`. To use other ports set `WEB_PORT`, `API_PORT` and `MAIL_UI_PORT` in `.env`.
- **By hand:** `cp .env.example .env`, set `JWT_SECRET` (`openssl rand -hex 32`), `docker compose up --build`.

## Demo accounts and data

The project does not start empty. With the defaults in `.env.example` (`SEED_DEMO_USER=true`, `SEED_DEMO_DATA=true`):

| Account | Password | Role | Can |
| --- | --- | --- | --- |
| `demo-admin` | `Admin12345` | `ADMIN` | a trainer with their own collection (the role travels in the token, reserved for future admin features) |
| `demo` | `Demo12345` | `USER` | a trainer with their own collection |

You can also **create your own trainer profile**: the "Create profile" link in the app (or `POST /api/v1/auth/register`). A new trainer starts with an empty collection.

The demo trainers start with **18 collection entries over 16 Pokémon** (a snapshot of real PokeAPI responses bundled in `backend/src/main/resources/demo/pokemon.json`, so the demo needs no network at startup): `demo` has 6 and `demo-admin` has 12, each with a nickname, a region and tags. Both have Bulbasaur and Pikachu **with different notes**, which shows that the Pokémon is shared but the notes are personal.

> These credentials are **demo-only placeholders** documented on purpose. A real deployment sets `SEED_DEMO_USER=false` and `SEED_DEMO_DATA=false` (the code defaults) and starts empty. Seeding only ever **adds**: restarting never duplicates rows or undoes edits.

## What it does (user stories)

| Story | Behavior | API | UI | Main code (backend) |
| --- | --- | --- | --- | --- |
| **US-01 Browse** | Paginated list showing sprite, category (types), mass and abilities; responses cached | `GET /api/v1/pokemon` | Catalog page: cards with the official artwork on a gradient of the Pokémon's type, paging in the URL, page-size selector, a **filter** that narrows the list as you type (name or number, kept in the URL), an "In my collection" mark on what you already have | `ListPokemon`, `PokeApiGatewayAdapter`, `PokeApiClient` (`@Cacheable`) |
| **US-02 Detail** | Image, base stats, narrative description, evolution line (branching supported, e.g. Eevee) | `GET /api/v1/pokemon/{idOrName}` | Detail page: artwork, stat bars, evolution tree | `GetPokemonDetail`, `PokeApiMapper` |
| **US-03 Sync** | Persist PokeAPI data in a relational store; each trainer's own fields (nickname, region, tags) live next to it | `POST /api/v1/collection/{idOrName}/sync`, `POST /api/v1/collection/sync` | "Add to my collection" on the detail page, and **press "Select Pokémon", tick cards (the chosen names are listed in a tray) and add them all at once** | `SyncPokemon`, `SyncSelectedPokemon`, `JpaPokemonRepositoryAdapter`, Flyway `V2`/`V4` |
| **US-04 Update** | Edit stored Pokémon; `404` missing, `400` malformed, plus `401/409` | `PATCH /api/v1/collection/{id}`, `DELETE …/{id}` | Entry page: notes view, edit form, tags editor, conflict handling, remove from my collection | `UpdateStoredPokemon`, `LocalAttributes`, `GlobalExceptionHandler` |
| **Auth** | Register, login, public vs protected routes | `POST /api/v1/auth/register`, `/login` | Login and register pages | `RegisterUser`, `AuthenticateUser`, `SecurityConfig`, `JwtTokenIssuer` |

Technical requirements from the brief: public repository ✔ · tests ✔ · proper error handling (one error shape, see below) ✔ · caching layer for PokeAPI responses ✔ · front-end that consumes the API ✔ · relational database with a primary entity and a secondary collection for users, with unique keys and descriptive attributes (`pokemon` catalog, `trainer_pokemon` per-trainer entries, `users`) ✔ · CRUD API with standard verbs and consistent responses ✔ · auxiliary API for registration/authentication/route protection ✔ · dedicated data-access layer (persistence adapters) ✔ · business-logic layer independent from API and data access (`domain` + `application`) ✔ · unit tests for every core component ✔ · README + seeded data + Dockerfiles ✔ · GenAI exercise → [`docs/genai.md`](docs/genai.md) ✔.

## Architecture

Clean Architecture: dependencies point **inward only**. The business rules know nothing about Spring, JPA, HTTP or JSON.

[![Architecture: infrastructure adapters depend on the application layer (use cases and ports), which depends on the pure Java domain](docs/architecture.png)](docs/architecture.svg)

*(Click the picture for the vector version, `docs/architecture.svg`.)*

More diagrams (what the backend includes, security layers, error handling, logging, tests, frontend, CI/CD) are in [`docs/diagrams/`](docs/diagrams/) and are used in [`PRESENTATION.md`](PRESENTATION.md).

- **`domain`** — entities, value objects and rules (`LocalAttributes` limits, `PokemonSearchQuery`, `PokemonIdentifier`). No framework imports.
- **`application`** — one class per use case, written as plain Java against **ports** it defines (`port.in` for what the outside asks, `port.out` for what it needs).
- **`infrastructure`** — everything that touches a framework: REST controllers and DTOs, Spring Security + JWT, the PokeAPI HTTP client with Caffeine caching, JPA adapters and Flyway migrations, and the configuration that wires use cases into Spring (the only place that does).
- The rule is **enforced, not just documented**: `ArchitectureTest` (ArchUnit) fails the build if `domain`/`application` import Spring/JPA/Jackson, if a controller touches an outbound port, an adapter or a use-case implementation, or if layers depend the wrong way.

### SOLID, DRY and clean code: where to look

| Principle | How it shows in the code | How it is checked |
| --- | --- | --- |
| **S**ingle responsibility | one class per behaviour: 16 use cases (`SyncPokemon`, `ResetPassword`, `UpdateStoredPokemon`…); controllers only translate HTTP to a use-case call; `GlobalExceptionHandler` is the only place that turns exceptions into the error body; the largest backend class has about 200 lines | `ArchitectureTest` (a controller may only use input ports), code review |
| **O**pen/closed | new behaviour is added by new classes, not by editing old ones: `SyncSelectedPokemon` composes the existing `SyncPokemon`; the reset e-mail is a port (`PasswordResetNotifier`) with an SMTP adapter, so another channel is another adapter | `SyncSelectedPokemonTest` exercises it by mocking only the single-sync use case |
| **L**iskov substitution | any adapter that honours a port can replace another: the tests swap the real PokeAPI client for a stand-in HTTP server, and the mail adapter for a capturing fake, without touching the use cases | integration tests run the same use cases against fakes and against the real adapters |
| **I**nterface segregation | 16 small input ports (one per use case) and 8 narrow output ports (`PokeApiGateway`, `PokemonRepository`, `UserRepository`, `PasswordHasher`, `TokenIssuer`…): a class depends only on what it uses | `ArchitectureTest` |
| **D**ependency inversion | use cases depend on ports they define themselves; Spring, JPA and Jackson appear only in `infrastructure`; the wiring lives in `UseCaseConfig` | **ArchUnit fails the build** if `domain`/`application` import a framework or an adapter |
| **DRY** | one password policy for register, reset and change (`PasswordPolicy`); one place for the limits and normalization of nickname/region/tags (`LocalAttributes`); one error shape and one handler; one API client in the web app; shared UI primitives and card frame; design tokens defined once | tests on each shared piece |
| **Clean code** | small intention-revealing names, comments that say *why* (and `SHORTCUT:` notes for conscious simplifications), no dead code, TypeScript `strict`, ESLint with zero warnings (and a lint rule that forbids features importing each other), **no one-letter names** (Checkstyle in the backend build and ESLint `id-length` in the frontend fail on them), no unused code (`knip` reports nothing) | `./gradlew build` (includes Checkstyle), `npm run lint`, CI |

Honest limits: some rules are repeated on purpose (the password policy exists in the API *and* in the form, and DTO validation repeats what the use case enforces: defence in depth, kept in line by tests on both sides); `UseCaseConfig` is a long but boring wiring class; the use cases are plain Java without transactions (the adapters own them), which the code documents where it matters.

In the drawing, the browser only talks to nginx (which serves the app and proxies `/api` to the API), so the app needs **no CORS** configuration; the API additionally refuses foreign origins (nginx forwards the original `Host`, port included, so same-origin requests are recognised as such).

## API reference

Base path `/api/v1`. JSON in, JSON out. Dates are ISO-8601 UTC. Pages are **0-based** (`?page=0&size=12`, `size` ≤ 50, default 12).

### Auth

| Method | Path | Body | Success | Notes |
| --- | --- | --- | --- | --- |
| `POST` | `/auth/register` | `{username, email, password}` | `201` user | username and email are stored lower-case and trimmed; password 8–72 bytes with letters and digits |
| `POST` | `/auth/login` | `{username, password}` | `200 {token, tokenType: "Bearer", expiresAt}` | `401 INVALID_CREDENTIALS` for wrong user *or* password (same answer, same effort) |
| `POST` | `/auth/forgot-password` | `{email}` | `202`, always | the same answer whether or not the account exists (nothing leaks); if it does, an e-mail with a one-time link is sent |
| `POST` | `/auth/reset-password` | `{token, newPassword}` | `204` | `400 INVALID_RESET_TOKEN` for an unknown, expired or used link (same message for all three); the link is valid 30 minutes and works once |
| `GET` | `/users/me` | — | `200 {id, username, email, role}` | needs a token |
| `PATCH` | `/users/me` | `{username?, email?, currentPassword?}` | `200 {token, tokenType, expiresAt, user}` | needs a token; **changing the e-mail needs `currentPassword`** (`400 INVALID_CURRENT_PASSWORD`, never a 401); the returned token carries the new username |
| `POST` | `/users/me/password` | `{currentPassword, newPassword}` | `204` | needs a token |

Send the token as `Authorization: Bearer <token>` (JWT HS256, 60 minutes by default).

### Catalog (public, backed by PokeAPI)

| Method | Path | Success |
| --- | --- | --- |
| `GET` | `/pokemon?page&size&q` | `q` (optional) filters: digits = that Pokédex number, text = names containing it (`mr mime` finds `mr-mime`), max 50 chars. `{items[{id,name,spriteUrl,types,weightKg,abilities}], page,size,totalItems,totalPages}` |
| `GET` | `/pokemon/{idOrName}` | detail: `imageUrl, types, weightKg, heightM, abilities, stats[{name,baseValue}], genus, description, evolutionChain{…evolvesTo[…]}` |

### Collection (each trainer's own copy; every call needs a token)

| Method | Path | Success |
| --- | --- | --- |
| `GET` | `/collection?page&size` | page of **your** entries (+ `nickname, region, tags`) |
| `GET` | `/collection/ids` | the PokeAPI ids you already have, e.g. `[1,4,25]` (the app uses it to offer "Add" only when needed) |
| `GET` | `/collection/{pokeApiId}` | your full entry (+ `version`, `syncedAt`); `404` if it is not in **your** collection |
| `POST` | `/collection/{idOrName}/sync` | `201` added to your collection · `200` already there, PokeAPI data refreshed, your notes untouched |
| `POST` | `/collection/sync` `{ids:[…]}` | adds several at once (1–50 distinct ids): `200 {created, updated, failures[], abortedReason}` |
| `PATCH` | `/collection/{pokeApiId}` `{version, nickname?, region?, tags?}` | `200` updated entry. Missing field = unchanged; `""` / `[]` = clear |
| `DELETE` | `/collection/{pokeApiId}` | `204`, removes it from **your** collection only |

Try it:

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"Demo12345"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

curl -s "localhost:8080/api/v1/pokemon?size=2"
curl -s localhost:8080/api/v1/collection/25 -H "Authorization: Bearer $TOKEN"
curl -s -X PATCH localhost:8080/api/v1/collection/25 -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"version":0,"nickname":"Sparky II"}'   # use the entry's current version
```

### One error shape for everything

```json
{ "timestamp": "2026-10-06T16:00:00Z", "status": 409, "code": "CONCURRENT_MODIFICATION",
  "message": "The record was modified by someone else; reload it and try again", "fieldErrors": [] }
```

| Status | `code` values |
| --- | --- |
| `400` | `VALIDATION_FAILED` (with `fieldErrors`), `MALFORMED_REQUEST`, `INVALID_PARAMETER`, `INVALID_PAGINATION`, `INVALID_POKEMON_IDENTIFIER`, `INVALID_LOCAL_ATTRIBUTES`, `INVALID_UPDATE`, `INVALID_SEARCH_QUERY`, `INVALID_SYNC_SELECTION`, `INVALID_RESET_TOKEN`, `INVALID_CURRENT_PASSWORD`, `WEAK_PASSWORD`, `INVALID_REQUEST` |
| `401` | `UNAUTHORIZED` (no/invalid/expired token), `INVALID_CREDENTIALS` |
| `403` | `FORBIDDEN` (access denied by the route rules) |
| `404` | `POKEMON_NOT_FOUND`, `STORED_POKEMON_NOT_FOUND` |
| `409` | `USERNAME_TAKEN`, `EMAIL_TAKEN`, `CONCURRENT_MODIFICATION`, `CONFLICT` |
| `502` / `504` | `CATALOG_UNAVAILABLE` / `CATALOG_TIMEOUT` (PokeAPI down or slow) |
| `500` | `INTERNAL_ERROR` (a fixed message; details only in the server log) |

## Trainers, privacy and concurrency

- **Public**: auth endpoints and the catalog (`/pokemon`, backed by PokeAPI). **Everything else needs a token**, including every `/collection` call. Route rules live in one place, `SecurityConfig`, and are checked by a table-driven test (`SecurityRulesMatrixTest`: every verb × path × caller).
- **Personal collections**: the Pokémon data is a shared catalog table; what a trainer collects is a row in `trainer_pokemon` (trainer + Pokémon, unique together) that holds **that trainer's nickname, region and tags**. Two trainers can have the same Pokémon with different notes; one trainer cannot have it twice. Every collection operation is scoped to the authenticated trainer, so another trainer's entry simply does not exist for you: `404`, never `403` (nothing leaks, not even that it exists). Removing an entry removes your row only.
- **Admin**: `demo-admin` is an account with the `ADMIN` role in its token and database row, but no collection operation depends on it: the brief does not ask for admin features. A "see every trainer's collection" view would be a small extension.
- **Versioned updates**: every entry has a `version`; `PATCH` must send the version it read. A stale one is refused with `409 CONCURRENT_MODIFICATION` (checked in the use case and again by the database through optimistic locking). The web app keeps what you typed, loads the latest version, and sends **only the fields you changed** — so after a conflict it can never overwrite what somebody else saved in a field you did not touch.
- **Idempotent sync**: sync is an upsert keyed by the **PokeAPI id** (`"25"` and `"pikachu"` are the same Pokémon). It refreshes the shared PokeAPI data (never erasing a complete earlier answer with a partial one) and keeps the trainer's notes; if two syncs race, the loser re-reads and retries (up to 3 attempts).

## The web app

React 19 + TypeScript (strict) + Vite + **Tailwind CSS v4**, in `frontend/`. Feature folders mirror the API domains:

```
frontend/src/
  index.css       the only stylesheet: Tailwind import, @theme tokens, dark palette
  app/            App (routes), Layout, Header, RequireLogin, ErrorBoundary
  features/
    auth/         login, register, forgot/reset password, profile, session (JWT in sessionStorage), AuthProvider
    catalog/      catalog list with filter, detail page, queries
    collection/   collection list, entry page, edit form, tags, delete; the catalog's multi-select
  shared/         API client (the only code that calls fetch), ui/ primitives (Button, Card, Badge, Alert, BackLink…), formatting, paging
  test/           MSW server, fixtures, helpers, console guard
```

- **State**: server state in TanStack Query (cache, retries only for network/5xx, previous page stays visible while the next loads); paging lives in the URL (shareable); auth in a small context; no global store. Features never import each other: `App.tsx` is the only place that composes them, and ESLint fails the build if one feature imports another.
- **One API client** turns every failure into an `ApiError` (status, code, field errors) using the backend's error shape, so components never parse responses. A `401` on a request that carried a token ends the session, shows a notice and refetches the data without the dead token.
- **Styling**: Tailwind v4 with a single stylesheet. The design tokens (semantic colors, the 18 Pokémon type colors, radius, shadow) live in an `@theme` block; the dark palette overrides the same variables under `prefers-color-scheme`, so a class like `bg-surface` is right in both schemes. Repeated patterns are small components in `shared/ui` instead of long class lists or `@apply`. The build emits one static CSS file (≈ 44 kB, 9 kB gzip), so the strict CSP (`style-src 'self'`) still holds.
- **Background**: an original forest scene drawn as SVG (a misty morning in the light scheme, a night forest with fireflies in the dark one; 15–27 kB each, no third-party artwork). Text always sits on glass panels over it, and a test checks the contrast against the art's lightest and darkest tones. Buttons are deliberately light (thin borders, soft tints).
- **Accounts**: "Create profile" (register), "Forgot your password?" on the login page (the e-mail link opens `/reset-password`; the token is removed from the address bar as soon as the page reads it) and a **profile page** (`/profile`, reached by clicking your username) to change username, e-mail (asks for the current password) and password. After a username change the app adopts the new token the API returns, so the header updates at once. A wrong current password is a form error and never logs you out.
- **Cards**: each Pokémon card has a gradient taken from its primary type, a glass number pill (`#0025`), the official artwork with a soft glow, and an inset panel with solid type pills; stat bars change colour with the value. The list endpoints carry `imageUrl` (official artwork, falling back to the sprite). Every type palette is checked for WCAG AA contrast by a unit test.
- **UX**: mobile-first and responsive (1 column on a phone up to 4 on a wide screen, tap targets ≥ 44 px, no horizontal scroll from 320 px), a **Back** arrow on every detail page (returns to the list with its page, filter and selection), light/dark through `prefers-color-scheme`, skeleton loaders, friendly empty/error states with retry, route titles, focus moved to the content on navigation, skip link, keyboard-friendly forms with per-field errors.
- **Safety**: text from the API (descriptions, nicknames, tags) is rendered as text, never as HTML; nginx sends a strict CSP (`script-src 'self'`, images only from this origin and the PokeAPI sprites host).

## Configuration

Set in `.env` (copied from `.env.example`); Docker Compose passes them to the containers.

| Variable | Used by | Default | Meaning |
| --- | --- | --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | db, API | `.env.example` | database name and credentials (required) |
| `JWT_SECRET` | API | — | **required**, at least 32 bytes and not the `change-me…` placeholder of `.env.example`; the API refuses to start otherwise (`./scripts/start.sh` generates one) |
| `JWT_TTL_MINUTES` | API | `60` | token lifetime |
| `CORS_ALLOWED_ORIGINS` | API | `http://localhost:5173` | only needed when a browser calls the API directly (e.g. `npm run dev`) |
| `WEB_PORT`, `API_PORT`, `MAIL_UI_PORT` | Compose | `8081`, `8080`, `8025` | the ports on this machine (`127.0.0.1`) for the web app, the API and the test mailbox; `./scripts/start.sh` rewrites them in `.env` when one is busy |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM` | API | `mailpit`, `1025`, `no-reply@pokedex.local` | where the password-reset e-mails go. Real SMTP: also `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_AUTH=true`, `MAIL_STARTTLS=true`, and drop the `mailpit` service |
| `APP_PUBLIC_URL` | API | `http://localhost:8081` | the address the reset link in the e-mail points to |
| `PASSWORD_RESET_TTL_MINUTES` | API | `30` | how long a reset link is valid |
| `SYNC_MAX_DURATION_SECONDS` | API | `90` | time budget of one "add selected Pokémon" request; when it runs out the answer lists what was added and why it stopped (nginx cuts the connection at 120 s) |
| `LOG_LEVEL`, `APP_LOG_LEVEL` | API | `INFO`, `INFO` | log level of the libraries and of this application's own code (see [Logging](#logging)) |
| `POKEAPI_BASE_URL` | API | `https://pokeapi.co/api/v2` | upstream catalog |
| `SEED_DEMO_USER`, `DEMO_USER_PASSWORD`, `DEMO_ADMIN_PASSWORD` | API | `false` / empty | create the `demo` and `demo-admin` accounts (each exists only if its password is set; the passwords may be left empty when seeding is off) |
| `SEED_DEMO_DATA` | API | `false` | add the 16 demo Pokémon (needs the demo accounts) |

Cache and upstream settings (`app.pokeapi.*` in `backend/src/main/resources/application.yml`): connect timeout 2 s, read timeout 5 s, cache TTL 1 h, up to 2000 entries per cache.

### Logging

Logs go to the console (standard output), which is where Docker and any log collector read them: `docker compose logs -f app`.

- **Control:** `LOG_LEVEL` (libraries) and `APP_LOG_LEVEL` (this application) in `.env`, `INFO` by default; use `APP_LOG_LEVEL=DEBUG` to investigate. Never run `LOG_LEVEL=DEBUG` in production: Spring Security's own debug output prints bearer tokens.
- **One line per request**, with a request id: `INFO [3f6479aa-…] … RequestLogFilter : GET /api/v1/pokemon 200 14ms` (WARN from status 500). Send your own `X-Request-Id` (letters, digits, `.`, `_`, `-`, up to 64 characters) or one is generated; it is always returned in the response header, so a user's report can be matched to the log lines of that request. Query strings, headers and bodies are never logged, and the Docker health check only appears at `DEBUG`.
- **Business events at INFO, with ids only:** `User 7 registered`, `Trainer 7 added Pokemon 25`, `Trainer 7 bulk add: requested=3 created=1 updated=1 failed=1 aborted=no`, `Password reset requested`, `Login failed` (WARN, without the username). Upstream problems are one WARN each (`Pokemon catalog unavailable (timeout=false, cause=…): PokeAPI answered 503`); only an unexpected exception logs an ERROR with a stack trace.
- **No secrets, no personal data:** passwords, tokens, reset links, e-mails and usernames never reach the log. A test runs register, failed login, password recovery and profile update and asserts that none of those values appears in the captured output.
- **Structured logs for production:** Spring Boot's built-in JSON format is available with `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (add it to the `app` service environment); every line is then a JSON object with the request id as its own field. Local development keeps the readable format.

## Development without Docker for everything

Backend tests need no database (H2 in memory, same Flyway migrations):

```bash
cd backend && ./gradlew test        # unit, slice and end-to-end tests
cd backend && ./gradlew build       # + coverage gate
```

Run the API from your IDE or Gradle against the Dockerized database (published on `localhost:5433` by the dev override):

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d db
set -a; . ./.env; set +a
cd backend && DB_URL=jdbc:postgresql://localhost:5433/$POSTGRES_DB DB_USER=$POSTGRES_USER \
  DB_PASSWORD=$POSTGRES_PASSWORD ./gradlew bootRun
```

Run the web app with hot reload (Node 22+); Vite proxies `/api` to `localhost:8080`:

```bash
cd frontend && npm ci && npm run dev      # http://localhost:5173
```

## Testing and quality

The core (domain, use cases, persistence and PokeAPI adapters, the first web and frontend phases) was built **test-first** (red → green → refactor), and each phase's planned tests are listed in `docs/plan-*.md`. Later iterations (collections per trainer, filter, selection, password recovery, restyling) were done with tests and code written in the same change and verified by running the whole suite, the stack smoke test and a real-browser check; the commit history does not always show a separate failing-test commit for those.

| Suite | Command | What it covers |
| --- | --- | --- |
| Backend | `cd backend && ./gradlew test` | **566 tests**, by kind (approximate grouping): about **167 unit** tests of the domain and the use cases (pure Java, Mockito only on ports), **190 web-slice** tests (`@WebMvcTest` controllers, the table-driven security rules, the error handler, the request log filter), **49 persistence** tests (`@DataJpaTest` on H2 with the production Flyway migrations, including a migration that upgrades real data), **109 adapter and configuration** tests (PokeAPI client against a stand-in HTTP server, JWT, cache, mail, wiring), **44 integration** tests that boot the whole Spring context and exercise complete flows over HTTP (register → sync → edit → delete; password recovery; two trainers with the same Pokémon; the catalog filter; the demo data; that logs contain no secrets), and **7 ArchUnit** architecture rules |
| Backend gate | `./gradlew build` | JaCoCo: fails below **90 % lines / 80 % branches** (actual ≈ 97 % / 89 %) |
| Frontend | `cd frontend && npm test` | **350 tests**: Vitest + React Testing Library with the API mocked at the network level (MSW), so each page is tested together with its real data layer (this is the integration level of the web app); accessibility checks with axe; contrast checks of the colour palettes |
| Frontend gate | `npm run test:coverage` | fails below 90 % lines / 85 % branches (actual ≈ 99.6 % / 95 %); **any `console.error`/`warn` fails the test** (the brief asks for no browser-console warnings) |
| Static checks | `npm run typecheck && npm run lint` | strict TypeScript, ESLint with zero warnings |
| Whole stack | `./scripts/smoke.sh` | 40 checks through nginx: SPA, proxy, security headers, browser-style requests with `Origin`, login, private collections, two trainers with the same Pokémon, versions |
| CI | `.github/workflows/ci.yml` | on every push to `main` and every pull request: backend `build` (tests + coverage gate), frontend typecheck/lint/coverage/build/audit, and the whole stack with `start.sh` + `smoke.sh` |

Beyond what jsdom can see (real CORS, CSP, nginx, layout) the delivered stack was also checked in Chrome through the DevTools protocol on desktop, mobile and dark mode, logged in and out: **no console warnings or errors, no CSP violations, no failed requests**. That pass found and fixed a real bug (nginx dropped the port from `Host`, so the API treated the browser's `Origin` as foreign and refused every `POST`/`PATCH`/`DELETE`); the smoke script now guards it.

## Design decisions and trade-offs

| Decision | Why | Trade-off / limit |
| --- | --- | --- |
| Ports and adapters (hexagonal) on top of Clean Architecture | separates the layers: business rules in the middle, frameworks at the edges, so each layer is tested alone and an adapter (database, PokeAPI, mail) can be replaced without touching the rules | more interfaces than a layered CRUD, accepted for the clarity |
| Java 21 (LTS) with records, pattern matching and virtual threads, **no Lombok** | the version I know best with the latest language features; records and sealed types replace most of what Lombok is used for, so the build has fewer dependencies and no annotation processor magic | none significant |
| Docker + Compose, multi-stage images, non-root | the same artifact runs on a laptop, on the reviewer's machine and in a pipeline; the CI workflow builds, runs the tests and the coverage gates, then starts the real stack and runs the smoke test | needs Docker installed |
| One repository with `backend/` and `frontend/` | practical: one clone, one command, API and UI evolve together and stay in step; each project still has its own structure, toolchain, tests, quality gates and Dockerfile, so they could be split into separate pipelines later | a monorepo needs path-aware CI at larger scale |
| Research → plan → implement → code review | decide before coding: understand the business and the external contract first, write the tests to create per phase, then implement, then review independently; problems show up in the plan, not in production | slower start, paid back in fewer reversals |
| Use cases are plain Java wired in one config class | the business layer is independent of Spring, as the brief asks | a little wiring code |
| Shared Pokémon table + one row per trainer and Pokémon (`trainer_pokemon`) | the data is the same for everybody, the personalisation is individual; no duplicated PokeAPI data | one join when reading a collection |
| Local copy = separate store, not a cache of the catalog | US-03/04 need data we own and edit; the catalog stays a pure read-through of PokeAPI | two read models for the same Pokémon |
| Sync merges instead of overwriting | re-syncing must refresh PokeAPI data without erasing local edits | the local fields are the only editable ones |
| Optimistic locking with a version the client must send | prevents silent lost updates, visible to users as a conflict they can resolve | with 4+ simultaneous writers on one entry a retryable `409` can still happen |
| Evolution chain stored as a JSON column (`SHORTCUT:` in the code) | the tree is read as a whole and never queried | cannot query by evolution; move to a table if that is ever needed |
| Cache per upstream resource (Caffeine, 1 h), failures and 404s not cached | a catalog page costs 1 + N upstream calls; a blip must not be remembered | in-process cache (one instance); fine for this scope |
| Parallel detail fetch with a cap of 10 in-flight calls | cold page ≈ 0.5 s instead of ≈ 1 s without hammering PokeAPI | — |
| JWT in `sessionStorage` | survives a reload, ends with the tab, simple | readable by injected scripts; mitigated by a strict CSP and by never rendering API text as HTML |
| Demo data bundled as a snapshot, opt-in | works offline, deterministic, restart-safe | the snapshot does not follow PokeAPI changes (sync refreshes any entry) |
| **No** login rate limiting / lockout, no refresh tokens, no i18n | a small game, not a bank; the brief does not ask | a production system would add rate limiting (e.g. at a gateway) |

## Security notes

- Passwords: BCrypt, with a 72-byte limit checked in bytes; unknown users cost the same hash work as wrong passwords; usernames/emails are normalized so look-alike accounts cannot exist.
- **Password recovery**: the reset token is 32 random bytes; only its SHA-256 hash is stored, it is valid 30 minutes and works once, a newer request invalidates the older links, and a successful reset invalidates the rest. The request endpoint answers the same for known and unknown e-mails, and the reset link appears in no log (the API never logs it, and nginx does not log the request that opens it). Changing the e-mail address requires the current password, so a stolen session cannot redirect future resets. Known limit: tokens are stateless JWTs, so after a password change or reset an already issued token stays valid until it expires (60 minutes by default); a token deny-list would close that. There is no rate limiting on these endpoints (a conscious choice for this scope).
- No secrets in code or git: the JWT secret and database password come from the environment (the API refuses a short, missing or placeholder secret, and `start.sh` generates random ones); `.env` is git-ignored.
- **Transport**: everything here is plain HTTP on `localhost` — the API (`8080`) and the app (`8081`) are published on `127.0.0.1` only and the database is not published at all. For anything beyond a local demo, terminate **TLS in front of nginx** (a reverse proxy or load balancer): nginx honours `X-Forwarded-Proto` and then sends `Strict-Transport-Security`. Without TLS, passwords and tokens would cross the network in clear text.
- Errors never leak internals (fixed `500` message, upstream text only in logs; database messages that could quote personal data are not logged).
- Containers run as non-root (API: dedicated user; web: unprivileged nginx); the database port is not published; nginx sends CSP, `nosniff`, frame-deny and a referrer policy.
- Dependencies were scanned (npm audit, Trivy on both images): **0 known fixable HIGH/CRITICAL findings and no secrets**. Tomcat and Jackson are pinned to patched versions in `backend/build.gradle` (with a note to remove the override once Spring Boot manages them).

## Repository layout

```
backend/            Spring Boot API: src/main (domain, application, infrastructure), src/test, Dockerfile, Gradle build
frontend/           React app: src, Dockerfile (Node build → unprivileged nginx), nginx.conf
scripts/            start.sh (one-command run), smoke.sh (stack check)
docs/               genai.md, plans and research notes, screenshots
docker-compose.yml  db + api + web app;  docker-compose.dev.yml  publishes the DB on localhost:5433
.env.example        every setting, with demo-only values
CLAUDE.md           the conventions the code follows (architecture rules, TDD policy, standards)
.claude/            the workflow used to build it (research → plan → implement → code-review)
```

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| A port is taken (`port is already allocated`) | `./scripts/start.sh` already avoids this: it moves to the next free port and prints the real addresses. With a plain `docker compose up`, set `WEB_PORT`, `API_PORT` or `MAIL_UI_PORT` in `.env` (the dev override also uses `5433`) |
| `Docker is installed but not running` | start Docker Desktop (or the docker service) and run `./scripts/start.sh` again |
| The web page shows an old version | hard-reload the browser (Cmd/Ctrl + Shift + R) after rebuilding |
| Compose says `copy .env.example to .env` | run `./scripts/start.sh` (it creates `.env`) or `cp .env.example .env` |
| The API exits at startup mentioning `app.jwt.secret` | `JWT_SECRET` is missing or shorter than 32 bytes |
| Catalog shows "not answering right now" | PokeAPI is unreachable from your network; the collection and demo data still work. Check connectivity and retry |
| Want a clean slate | `docker compose down -v && ./scripts/start.sh` |
| The API exits with `password authentication failed for user "pokemon"` | the database volume was created with another password (you deleted `.env` and kept the volume). `docker compose down -v` erases it and starts clean |
| The API exits saying `app.jwt.secret is still the placeholder` | you copied `.env.example` by hand: put a random value in `JWT_SECRET` (`openssl rand -hex 32`) or use `./scripts/start.sh` |
| Adding many Pokémon at once ends in a gateway timeout | a selection of 50 can need up to ~150 PokeAPI calls; nginx allows 120 s for that route (15 s for the rest). Check your connection to pokeapi.co and retry: the ones already added are just refreshed |
| Something fails | `docker compose logs app` (API), `docker compose logs frontend` (nginx), `docker compose ps` |

## How it was built (process and AI)

- The work followed a repeatable loop with Claude Code: **research → plan → implement (TDD, phase by phase) → code review**; the skills live in `.claude/`, the conventions in `CLAUDE.md`, and each plan and research note is kept in [`docs/`](docs/) (plans A–E and their research notes). Every plan lists the tests to write first and records where reality differed from the plan.
- Multi-reviewer **code reviews** (architecture, correctness, security, tests) found real issues — for example BCrypt's 72-byte limit turning into a `500`, re-sync wiping data when PokeAPI answered partially, user enumeration — all fixed test-first; a reviewer claim that turned out to be wrong was checked against the test suite and discarded.
- The GenAI exercise (prompt, sample output, validation, corrections, edge cases) is in **[`docs/genai.md`](docs/genai.md)**.
