# Plan E: demo data, one-command start, README, GenAI document, publication

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
