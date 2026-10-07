# Presentation: how I approached the Ballast Lane Java exercise

*Cesar Tenemaza: thought process, decisions and trade-offs. The code is in this repository; start with the [README](README.md) to run it.*

## 1. In one minute

I built a **Spring Boot (Java 21) REST API** that wraps PokeAPI and a **React + TypeScript web app** on top of it, in one repository, started with **one command** (`./scripts/start.sh`). Trainers register, browse a paginated and filterable catalog, add Pokémon to **their own collection** (persisted in PostgreSQL), and keep personal notes (nickname, region, tags) that only they can see and edit.

| What the brief asked | Where it lives |
| --- | --- |
| US-01 paginated list with sprite, category, mass, abilities; cache (nice to have) | `GET /api/v1/pokemon`, Caffeine cache (a warm page answers in ~16 ms instead of ~900 ms) |
| US-02 detail with image, stats, description, evolution chain | `GET /api/v1/pokemon/{idOrName}` |
| US-03 persist into a relational store, leaving room for own fields | `trainer_pokemon` (PostgreSQL + Flyway); own fields = nickname, region, tags |
| US-04 update with 404 / 400 and defensive logic | `PATCH /api/v1/collection/{id}`: 404, 400, 401, 409 (optimistic locking) |
| Auth, public vs protected routes | register / login (JWT, BCrypt); the catalog is public, every collection call needs a token |
| Frontend, responsive, no console warnings | React 19, Tailwind, checked in real Chrome at 320 to 1440 px, light and dark |
| README, seeded data, Dockerfile, tests, public repo | all present; demo accounts and 18 sample entries load automatically |
| **GenAI exercise** | [`docs/genai.md`](docs/genai.md): prompt, sample output, validation, corrections, edge cases |

To see it: `./scripts/start.sh`, open the printed address, log in as `demo` / `Demo12345`. The README has a two-minute tour.

## 2. How I read the brief

1. **The brief grades architecture and tests first, frontend second.** So I made the backend the centre: Clean Architecture with enforced rules (ArchUnit), and tests at every level.
2. **"A primary entity and a secondary collection for users"** and **"room for proprietary fields"** suggested a replica of PokeAPI data plus data that is *ours*. I kept the PokeAPI data as a shared catalog table and the proprietary fields on a per-trainer row (see section 4).
3. **"The reviewer should not have to do anything."** I treated the run experience as part of the product: one command, random secrets generated for them, free ports chosen automatically, demo accounts and data already loaded, a local test mailbox, and a smoke test that checks the running stack.

## 3. Architecture and why

![Architecture](docs/architecture.png)

- **Dependencies point inward only.** `domain` and `application` are plain Java: no Spring, JPA or Jackson. Use cases depend on **ports** they define; adapters (REST, JPA, PokeAPI client, SMTP) implement them. **ArchUnit fails the build** if the rule is broken, so it is verified, not just documented.
- **SOLID in practice, not as a slogan:** one class per behaviour (16 use cases), small ports (16 in, 8 out), wiring in one config class, new behaviour added by composition (`SyncSelectedPokemon` reuses `SyncPokemon`). The README has a table with where to look and how each point is checked.
- **One error shape and one place that produces it**; correct status codes (400, 401, 404, 409, 502/504).
- **Security by default:** BCrypt (with the 72-byte limit handled), JWT secret required and checked at startup, no secrets in git, containers run as non-root, a strict CSP.

## 4. Decisions I want to explain (including one I reversed)

- **Optimistic locking** (a `version` the client must send) instead of "last write wins": two tabs editing the same entry get a clear `409`, and the web app keeps what you typed and sends **only the fields you changed**, so one person can never overwrite another's change.
- **Idempotent sync** keyed by PokeAPI id: `"25"` and `"pikachu"` are the same Pokémon; a partial answer from PokeAPI never erases a complete earlier one.
- **I changed the data model after trying the app by hand.** My first version had one shared row per Pokémon owned by whoever synced it first. With two accounts it made no sense for a trainer game (the second trainer could not "have" a Pokémon the first one had). I moved to a **shared catalog + one row per trainer and Pokémon** (`trainer_pokemon`), with a data-preserving Flyway migration tested on real data. The PDF does not mention owners or roles, so this stayed within the brief. The lesson: reading the requirements was not enough; using the product exposed the design flaw.
- **Things I deliberately did not build** (and say so): login rate limiting, e-mail verification, JWT revocation after a password change, an admin view. They are not in the brief; each is listed in the README with the trade-off.
- **Extras I added because they make the product coherent:** catalog filter, multi-select add, password recovery by e-mail (a local Mailpit mailbox in the demo), a profile page, dark mode, CI.

## 5. Tests and verification

- **540 backend tests** (unit, web slice, persistence, adapters, full-context integration flows, 7 architecture rules), coverage gate 90 % lines / 80 % branches (actual ≈ 97 % / 88 %).
- **350 frontend tests** (React Testing Library + MSW at network level, accessibility checks with axe, palette contrast checks), gate 90 % / 85 % (actual ≈ 99.6 % / 95 %). Any `console.error` or `warn` fails a test.
- **A 40-check smoke test** of the whole running stack, run in CI too; image scans (Trivy) with no fixable high or critical findings.
- **Honest note on TDD:** the core (domain, use cases, adapters, first web and frontend phases) was built test-first. Later iterations were done with tests and code in the same change and verified by the full suite, the smoke test and a real-browser check, so the commit history does not always show a separate failing test.
- **What tests could not see, I checked in a real browser** and found real bugs that way: nginx dropped the port from `Host`, so the API refused every browser `POST`; and a card whose click only worked on part of its area (the image sat above the link overlay).

## 6. How I used generative AI (the mandatory part)

I used **Claude Code** throughout, with a repeatable loop kept in the repo (`.claude/`, `CLAUDE.md`): **research → plan → implement → code review**, with specialist reviewers (architecture, correctness, security, tests) running in parallel. The plans in `docs/plan-*.md` list the tests to write first and record where reality differed.

**My role was to decide and to verify; the AI executed.** Product decisions (private collections per trainer, versioned updates, what to leave out, which vulnerabilities matter) were mine. The AI was never trusted on its word: a reviewer claim that turned out to be wrong was checked against the tests and discarded, and a "bug" a reviewer reported (BCrypt's 72-byte limit) was **reproduced with a short program** before fixing.

Real mistakes AI made, found and fixed (full table in [`docs/genai.md` §4](docs/genai.md)):

- a password check that counted characters, not bytes, so a long password turned registration into a `500`;
- a re-sync that could wipe stored data when PokeAPI answered partially;
- a notes-only edit that rewrote a shared catalog row and could cause a false `409` for another trainer;
- a mechanical rename that silently changed two log messages (caught by diffing what the script touched);
- tests that could not fail, and a `Thread.sleep` used for timing.

**The task-management exercise** (prompt, output sample, how I validated and corrected it, edge cases, authentication and validation) is in [`docs/genai.md`](docs/genai.md), with a runnable sample whose 15 tests pass and whose bugs were injected on purpose to show that the tests catch them.

## 7. What I would do next

Rate limiting at a gateway; revoke tokens on password change (a token deny-list); e-mail verification; an admin view of all trainers; a real SMTP provider for recovery e-mails; more catalog search options (type, region).

## 8. A short demo plan for the interview (about 6 minutes)

1. `./scripts/start.sh`, then log in as `demo`. (Run experience.)
2. Catalog: filter `char`, open a Pokémon (detail with stats and evolution), use the **Back** arrow. (US-01, US-02.)
3. **Select Pokémon**, tick a few, add them. (US-03.)
4. My collection → edit notes → save; open it in a second tab to show the `409` handling. (US-04.)
5. `demo-admin` has its own copy of the same Pokémon with different notes. (Per-trainer model.)
6. Forgot password → open the mailbox → reset. (Auth.)
7. Code: `ArchitectureTest`, a use case, a port and its adapter; then `docs/genai.md`. (Architecture, tests, GenAI.)

## 9. Where to find things

| | |
| --- | --- |
| Run it, API reference, configuration | [README.md](README.md) |
| GenAI exercise and AI critical thinking | [docs/genai.md](docs/genai.md) |
| Plans and research per phase | [docs/](docs/) |
| Conventions the code follows | [CLAUDE.md](CLAUDE.md) |
| Architecture drawing | [docs/architecture.svg](docs/architecture.svg) |
