# Plan E: demo data, one-command start, README, GenAI document, publication

> **Read this with the later sections:** the first part of this document (Plan E and its code-review follow-up) describes the original ownership model (owner or admin edit, admin-only delete, a public shared collection). It was superseded on 2026-10-06; see "Design change after manual testing: per-trainer collections" and the sections after it for the current behaviour.


**Research:** research.md
**Date:** 2026-10-06
**Scope:** Task (2/6 indicators). 5 phases.
**Branch:** `feature/plan-e-delivery` from `feature/plan-d2-frontend` (HEAD `beb77d4`); merged into `main` when the history is published.

## Summary

Seed a small, realistic collection (a snapshot of real PokeAPI data bundled with the backend) owned by the demo user and the admin, behind a flag, after the user seeder; a `scripts/start.sh` that prepares `.env` and starts everything; a complete README; a GenAI document that tells what was really done and includes a verified sample for the task-management exercise; and publish the history under the personal identity.

## Defaults assumed (change now if you disagree)

- **Accounts (demo only, from `.env.example`):** `demo` / `Demo12345` (USER) and `demo-admin` / `Admin12345` (ADMIN). Registering new users stays open.
- **Dataset: 16 Pokémon** (snapshot of `GET /api/v1/pokemon/{id}`): demo owns Bulbasaur, Charmander, Squirtle, Pikachu, Eevee, Snorlax; the admin owns Ivysaur, Venusaur, Charmeleon, Charizard, Wartortle, Blastoise, Raichu, Ditto, Mewtwo, Mew. Each has a nickname/region/tags so the "local fields" are visible; the demo user can edit its own and sees 403 on the admin's (ownership demo).
- **Flags:** `SEED_DEMO_USER` (accounts, exists) and new `SEED_DEMO_DATA` (collection). Both `true` in `.env.example`; both default `false` in the code, so a real deployment starts empty.
- **Idempotent:** an entry already stored is skipped, never overwritten.
- **Missing owner** (password not set): that entry is skipped with a log line.

## Design principles (what to say in the interview)

- **Clean Architecture:** the seeder is an infrastructure adapter that talks to ports and uses the domain factory; the demo JSON is data, parsed at the edge.
- **Single Responsibility:** reader (JSON -> entries), seeder (entries -> repository), config (flags, ordering) are separate; each is unit-tested.
- **Open/Closed:** adding demo Pokémon = editing the JSON, no code.
- **Executable documentation:** every command in the README was run; `scripts/smoke.sh` is the acceptance test of the delivered stack.

## Phase 1: Demo dataset and seeder (TDD)

**Goal:** with the flag on, a fresh database starts with the 16 Pokémon and their owners; restarting changes nothing.

**Status:** ✅ Complete 2026-10-06
**Deviation:** no `DemoDataProperties` record: `@ConditionalOnProperty` is enough for a single boolean flag; the snapshot is 16 real PokeAPI responses saved through this API (`GET /api/v1/pokemon/{id}`) with owner/nickname/region/tags added; `DemoDataIT` also asserts that a user's edit survives a second seeder run.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Unit | `.../config/DemoDataReaderTest.java` | `readsTheBundledSnapshot` (16 entries), `idsAreUniqueAndKnownOwners`, `everyEntryIsAValidAggregate` (domain invariants hold), `evolvingPokemonCarryTheirChain`, `localFieldsRespectTheLimits` | the real JSON is sound |
| Unit | `.../config/DemoDataSeederTest.java` | `storesEachEntryOwnedByItsUser`, `appliesNicknameRegionAndTags`, `stampsSyncedAtFromTheClock`, `skipsWhatIsAlreadyStored` (idempotent, edits kept), `skipsEntriesWhoseOwnerDoesNotExist`, `doesNothingWhenTheListIsEmpty` | seeding rules |
| Unit | `.../config/UseCaseConfigTest.java` (extend) | `demoDataSeederExistsOnlyWhenEnabled` | flag off by default |
| Integration | `.../DemoDataIT.java` (`@SpringBootTest`, both flags) | `startsWithTheCollectionAndAccounts`, `publicListShowsTheDemoPokemon`, `ownerEditsOwnAndIsRefusedOnTheAdminsOne`, `adminEditsAnything`, `runningTheSeederAgainDoesNotDuplicate` | the seeded system behaves |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `backend/src/main/resources/demo/pokemon.json` | infra | Create | snapshot: `[ {owner, nickname, region, tags, detail{...}} ]` |
| `infrastructure/config/{DemoEntry,DemoDataReader,DemoDataSeeder,DemoDataProperties}.java` | infra | Create | record, JSON reader, `ApplicationRunner`, `app.seed.demo-data.enabled` |
| `infrastructure/config/UseCaseConfig.java` | infra | Modify | bean behind the flag; `@Order(1)` users, `@Order(2)` data |
| `application.yml` | config | Modify | `demo-data.enabled: ${SEED_DEMO_DATA:false}` |

### Implementation Notes
The seeder resolves owners with `UserRepository.findByUsername`, builds `StoredPokemon.fromDetail(detail, now, actor).withLocalAttributes(...)` and saves through `PokemonRepository`. The reader uses its own `JsonMapper` (like the persistence mapper) so the file format does not depend on web serialization settings.

### Verification
```bash
cd backend && ./gradlew test --tests '*DemoData*' --tests '*UseCaseConfigTest' --tests '*ArchitectureTest'
```
**Expected result:** pass.

---

## Phase 2: One-command start

**Goal:** from a fresh clone, one command gives a running, populated system.

**Status:** ✅ Complete 2026-10-06
**Deviation:** acceptance run done from a truly clean state (containers, volume and `.env` removed): `./scripts/start.sh` created `.env`, built, started and printed the accounts; `./scripts/smoke.sh` passed 18/18 (5 new checks on the seeded system, only run when `SEED_DEMO_DATA=true`).

### Tests to Write First (RED)
Acceptance-first: from a clean state (`docker compose down -v`, no `.env`), `./scripts/start.sh` must end with the app answering and `./scripts/smoke.sh` extended with checks for the seeded data must pass.

| Level | Check | Asserts |
| ----- | ----- | ------- |
| Script | `scripts/smoke.sh` (extend) | `demo` and `demo-admin` can log in; the public collection lists 16 Pokémon; the demo user gets 403 editing the admin's Pikachu line and 200 on its own |

### File Changes
| File | Action | What Changes |
| ---- | ------ | ------------ |
| `scripts/start.sh` | Create | creates `.env` from `.env.example` if missing, `docker compose up --build -d`, waits for health, prints URLs and demo accounts |
| `.env.example`, `docker-compose.yml` | Modify | `SEED_DEMO_DATA`, comments explaining the demo-only values |

### Implementation Notes
bash 3.2 compatible (macOS): no nested escaped quotes inside `$(...)`. No destructive commands in the script.

### Verification
```bash
docker compose down -v; ./scripts/start.sh && ./scripts/smoke.sh
```
**Expected result:** health up, smoke passes including the new checks.

---

## Phase 3: Complete README

**Goal:** a reader can run, understand, test and evaluate the project from the README alone.

**Status:** ✅ Complete 2026-10-06
**Deviation:** every command in the README was executed (start, smoke, `bootRun` against the Dockerized DB, `npm run dev` with the proxy); that exposed a second collision on this machine (a local Postgres on 5432), so the development override publishes the database on **5433**; screenshots were captured from the seeded app in Chrome and stored in `docs/screenshots/`.

### Checks (documentation is verified by running it)
| Check | Asserts |
| ----- | ------- |
| every command in the README was executed | no stale instructions |
| links resolve (files and anchors) | no dead links |
| the story-to-code table points at real classes/files | traceability |

### File Changes
| File | Action | What Changes |
| ---- | ------ | ------------ |
| `README.md` | Rewrite | overview, quick start, demo accounts and data, features per user story, architecture (layers, ports, packages), API reference, auth and roles, error model, testing strategy and how to run each suite, configuration table, design decisions and trade-offs (what was left out and why), security notes, repository layout, troubleshooting, process evidence (plans/reviews) |

### Verification
```bash
./scripts/start.sh && ./scripts/smoke.sh   # the README quick start, verbatim
```
**Expected result:** works exactly as written.

---

## Phase 4: GenAI document

**Goal:** answer the GenAI section of the brief truthfully and verifiably.

**Status:** ✅ Complete 2026-10-06
**Deviation:** the sample was kept as documentation under `docs/genai/task-api-sample/` (outside the build) instead of being deleted; three deliberate mutations (owner filter, due-date check, status workflow) were run and each was caught by 2–3 tests; the first controller test had a wrong expectation (status on POST) and was corrected — used as a real example in the document.

### Tests to Write First (RED)
The sample code is real: written test-first in a scratch location inside `backend/` test sources, run, then copied into the document.

| Level | Test | Asserts |
| ----- | ---- | ------- |
| Unit | `TaskServiceTest` (scratch) | create/update/delete rules, ownership (another user's task -> not found), due date not in the past, status transitions, validation |
| Slice | `TaskControllerTest` (scratch) | 201/200/204, 400 on invalid body, 401 without token, 404/403 on someone else's task |

### File Changes
| File | Action | What Changes |
| ---- | ------ | ------------ |
| `docs/genai.md` | Create | (1) the prompt, (2) representative output (domain, use case, controller, tests), (3) how it was validated, corrected and hardened — including real corrections from this project — (4) the workflow used for the exercise itself |

### Implementation Notes
Scratch code is removed from the repository after the verified sample is copied (it is documentation, not part of the product).

### Verification
```bash
cd backend && ./gradlew test --tests '*genai*'   # while the scratch code exists
```
**Expected result:** sample compiles and its tests pass before it is quoted.

---

## Phase 5: Publish under the personal identity

**Goal:** the project is on `github.com/Cesarwth/bla-assessment`, authored as the personal account, with the company credentials untouched.

### Checks (verification-first)
| Check | Asserts |
| ----- | ------- |
| `git config --global --list` is identical before and after | company identity untouched |
| `git log --format='%ae %ce' --all` shows only the personal e-mail | history re-authored |
| tree content of every commit is identical before/after (compare `git rev-parse <old>^{tree}` and `<new>^{tree}`) | rewrite changed metadata only |
| `ssh -T git@github.com` (or HTTPS credential check) names the personal account | pushing as the right user |
| trivy / secret scan clean on the tree to publish | nothing sensitive goes public |

### File Changes
No product files. Git only: repository-local `user.name`/`user.email`, history re-authored (local, unpushed commits; backup refs kept until verified), branches merged into a linear `main`, `origin` added, push of `main`.

### Implementation Notes
Never modify global git config. If the machine's credentials belong to the company account, stop and ask the owner how to authenticate (token or SSH key for the personal account) instead of pushing.

### Verification
```bash
git log --oneline --all --format='%h %ae %s' | head
git ls-remote origin
```
**Expected result:** remote shows `main` at the local tip; every commit by the personal e-mail.

---

## Final Verification
```bash
cd backend && ./gradlew clean build && cd ../frontend && npm ci && npm run typecheck && npm run lint && npm run test:coverage && npm run build && cd .. && docker compose down -v && ./scripts/start.sh && ./scripts/smoke.sh
```

## Rollback Plan
Branch-based; the history rewrite keeps `refs/original/*` until the owner confirms. Demo seeding is opt-in: remove the flags to start empty.

## Out of Scope
CI pipeline, deployment, OpenAPI/Swagger UI, extra seed data volume, i18n, rate limiting (declined), refresh tokens.

## Code review follow-up (2026-10-06)

Final review (architecture, correctness, security, tests): 0 critical, 7 major. All fixed, test-first where code is involved:

| Finding | Fix |
| ------- | --- |
| Public placeholder JWT secret shipped by default | `JwtProperties` refuses secrets starting with `change-me`; `start.sh` generates random `JWT_SECRET`/`POSTGRES_PASSWORD` for a new `.env` and replaces a placeholder secret in an existing one |
| API published on `0.0.0.0:8080` | API and app bound to `127.0.0.1`; DB not published |
| No TLS/HSTS story | documented; nginx passes `X-Forwarded-Proto` through and sends HSTS only over HTTPS |
| Edit conflict could overwrite untouched fields (data loss) | `EditForm` tracks only the fields the user edited, sends only those, and rebases the rest on the latest version; typing during a save is kept |
| 15 s proxy timeout cut bulk adds | exact-match route `/api/v1/collection/sync` with 120 s; shared `proxy-api.conf` |
| Delete's admin-only rule lived only in the URL config | `DeleteStoredPokemon.delete(id, actor)` throws `AdminRequiredException` (403 `FORBIDDEN`); route rule kept as defense in depth |
| Gates not enforced anywhere | `.github/workflows/ci.yml` (backend build, frontend checks, stack smoke); linted with actionlint |

Minors fixed: compose no longer requires demo passwords when seeding is off; the collection page explains an out-of-range page; `smoke.sh` survives a missing field and uses a different region per run; the frontend console guard also covers `log/info/debug`; `start.sh` refuses (with instructions) to generate a new password when an old database volume exists.

Reviewer claims checked and discarded: JaCoCo report "missing" (a concurrent `clean` of mine), `smoke.sh` failing on a second run (it passed repeatedly), missing test for the demo-data flag (exists in `UseCaseConfigTest`), session-expiry cache reset untested (covered in `App.test`).

Verified after the fixes: backend 356 tests (98.1 % / 88.1 %), frontend 172 tests (99.3 % / 93.8 %), `npm audit` 0, stack smoke 18/18 from a clean state in both `.env` paths, Chrome pass on all pages with 0 console problems.

## Design change after manual testing: per-trainer collections (2026-10-06)

Trying the app with two accounts showed that "the first user to sync a Pokémon owns it" made no sense for a trainer game: `demo-admin` could not have the Bulbasaur that `demo` already had, and the shared list showed other trainers' entries. The brief (US-03/US-04) asks for a local replica with room for proprietary fields and for update operations; it says nothing about owners or roles, so the model could be fixed without leaving the brief.

| Before | After |
| ------ | ----- |
| one `pokemon` row per Pokémon, `owner_id`, nickname, region, tags on it | `pokemon` = shared catalog (PokeAPI data only); `trainer_pokemon` = one row per (trainer, Pokémon) with that trainer's nickname, region, tags and an optimistic-lock `version`; `trainer_pokemon_tags` |
| `/collection` public, shared by everybody | every `/collection` call needs a token and is scoped to the caller; another trainer's entry is a `404` |
| edit: owner or admin (`403` otherwise); delete: admin only | edit and remove: the trainer, on their own entries; no `403` left in the collection |
| "Yours" badge, "added by another trainer", owner line | removed: everything in "My collection" is yours; the detail page offers "Add" only when you do not have it (`GET /collection/ids`) |
| — | login page links to "Create your profile"; the Collection link and pages require a session |

Migration `V4__trainer_collections.sql` keeps existing data: every owned row becomes an entry of its owner (notes and tags copied), ownerless rows stay catalog-only. It was verified on H2 (`TrainerCollectionsMigrationTest`) and on a real PostgreSQL volume that held the previous schema with 16 owned rows.

Not done on purpose: an admin view of every trainer's collection (not in the brief; the `ADMIN` role already travels in the token, so it would be a small extension).

### Selecting Pokémon instead of importing a number range (2026-10-06)

The "Import Pokémon" form asked for a Pokédex number range (1–20), which nobody can use without knowing the numbers. It was replaced by selection in the catalog: logged-in trainers tick cards (or "select all on this page") and add them with one button. The API changed accordingly: `POST /collection/sync` takes `{ids:[…]}` (1–50 distinct ids, 50 = the largest catalog page) instead of `{from,to}`; the report shape is unchanged. Cards already collected carry the "In my collection" mark and no checkbox.

### Tailwind CSS, responsive design and back navigation (2026-10-06)

Feedback while trying the app: detail pages had no way back to the list, the design had to be responsive, and the team standard for styling is Tailwind.

- **Back navigation:** `shared/ui/BackLink` on every detail page. With in-app history it calls `navigate(-1)` (the list returns with its page, filter and selection); on a deep link it is a normal link to the catalog or to my collection. Visible text "Back"; the destination is added for screen readers only when it is certain (WCAG label-in-name).
- **Tailwind v4 instead of CSS Modules:** 29 `*.module.css` files and the old token files were replaced by one `src/index.css` (`@import "tailwindcss"`, `@theme` tokens, dark palette via variables under `prefers-color-scheme`, a few `@utility` blocks). Components use semantic utilities; repeated patterns became primitives in `shared/ui` (`Button`, `Card`, `Badge`, `Alert`, `RemovableChip`, `TagList`, field classes). No `@apply` sprawl, no runtime style injection, so the nginx CSP is unchanged (`style-src 'self'`).
- **Responsive:** mobile-first from 375 px with `sm`/`md` breakpoints, tap targets ≥ 44 px on phones, two-row header on phones, sticky selection tray on phones, vertical evolution tree on phones.
- **Verification in real Chrome:** 15 page variants × 375/768/1280 px × light/dark, plus 320 px: no horizontal overflow in any of the 105 combinations and no console or network problems apart from the two deliberate 404s (a missing Pokémon and a missing entry).
- **Documentation updated:** `CLAUDE.md` (stack and frontend rules), README (stack, structure, styling and UX notes, test counts).

### Card redesign with type gradients (2026-10-06)

Reference from the user: a Tailwind card with a type-coloured gradient, a glass number pill, a glowing large image, an inset white panel with solid type pills and coloured stat bars. Implemented with our theme system rather than pasted markup:

- Backend: the list items of `GET /pokemon` and `GET /collection` carry `imageUrl` (official artwork; the sprite when there is none).
- Frontend: `PokemonCardFrame` and `PokemonHero` in `shared/ui`; per-type palettes (`from/to/pill/ink`) in `@theme`; stat bars use a native `<meter>` so the browser picks low/medium/high colours; a stylised pokéball drawn as inline SVG in the header (own drawing, no official logo).
- Accessibility: gradients are dark enough, or the text ink is dark on light types (fire uses dark text on orange), so every type reaches ≥ 4.5:1; `index.css.test.ts` enforces it for all 18 types plus the unknown fallback.
- Checked in Chrome: 161 page × width × scheme combinations, no overflow and no console problems except the deliberate 404s.

### Whole-card click fix (2026-10-06)

Manual testing: only the info panel of a card opened the detail. Cause: the title link's `::after` (which makes the whole card a link) sat below the positioned art area, so clicks on the image never reached it (0 of 5 real clicks navigated). Fix: `after:z-1` on the link; the selection checkbox keeps working above it. Verified with real mouse events in Chrome: 49 of 49 probe points over a card hit the link, 5 of 5 clicks navigate, collection cards navigate, and clicking the selection checkbox ticks it without leaving the list.

### Forest background and lighter buttons (2026-10-06)

Feedback: the page background was basic (reference mood: a dark night forest in blue/teal/green) and the buttons felt heavy. The reference image is third-party artwork, so it was not used: the background is an original procedurally generated SVG scene (four files, 15–27 kB: night/day × wide/tall) rendered in a fixed, click-through layer. Text always sits on glass panels; contrast against the lightest and darkest tones of the art is tested. Buttons: 1px borders, no shadows, softer tinted primary. Checked in Chrome (105 page × width × scheme combinations, no overflow, no console problems, whole-card clicks still work 5/5).

### Password recovery and profile management (2026-10-06)

Requested last: trainers can recover a lost password and edit their own details. Outside the brief, kept small:

- **Flow:** `POST /auth/forgot-password` (always 202, no enumeration) e-mails a one-time link; `POST /auth/reset-password` sets the new password. Tokens: 32 random bytes, only the SHA-256 hash stored (Flyway `V5`), 30-minute TTL, single use, newer requests invalidate older ones.
- **Profile:** `GET/PATCH /users/me`, `POST /users/me/password`. Changing the e-mail needs the current password; a wrong current password is a `400` (never a `401`, which the web client treats as an expired session). A username change returns a new JWT because the token carries the username.
- **E-mail in the demo:** a Mailpit container (`axllent/mailpit`) receives the messages at http://localhost:8025; production would point `MAIL_HOST` at a real SMTP server. The mail health indicator is disabled so a missing mail server never makes the API unhealthy.
- **Known limits:** stateless JWTs stay valid after a password change until they expire; no rate limiting (a conscious scope decision, as for login).
- **Verified live:** register → forgot → read the e-mail in Mailpit → weak password refused → reset → the link works once → old password rejected, new one accepted → profile read and rename; the reset token appears in no log. The smoke test now covers this (40 checks).

### Final review round (2026-10-06)

Three read-only reviewers (security of the new auth code, architecture and correctness, frontend and documentation accuracy) found no critical issues and these real ones, all fixed:

- A notes-only edit rewrote the shared catalog row, so two trainers editing their own notes of the same Pokémon could collide (`updateNotes` now touches only the trainer's entry).
- Bulk add had no time budget (now `SYNC_MAX_DURATION_SECONDS`, default 90 s, with a partial report); a duplicate no longer fails the whole batch.
- A profile edit could revert a concurrent password reset (targeted updates); `ResetPassword` invalidates the other links before writing the password.
- The reset e-mail was sent synchronously, which made response time reveal which e-mails exist (now sent in the background).
- The reset token appeared in the nginx access log (the request that opens the link is no longer logged).
- Settings documented in the README were not passed to the API container (compose now forwards them).
- Features imported `features/auth` (the session context moved to `shared/session` and ESLint now enforces the isolation rule); a bulk add was lost when navigating away during the request; form errors were not announced and did not take focus; the client password policy differed from the server's; fireflies stayed visible under reduced motion; typing in the filter created a history entry per pause.
- Images scanned with Trivy: no fixable HIGH/CRITICAL findings and no secrets in the API, web and Mailpit (pinned to v1.31) images.

Accepted and documented, not changed: no rate limiting, JWTs stay valid after a password change until they expire, no e-mail verification, no Unicode confusable check on usernames.
