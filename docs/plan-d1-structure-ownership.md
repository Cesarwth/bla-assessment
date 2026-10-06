# Plan D1: one repo (`backend/` + `frontend/`), owner-based edits, versioned PATCH

> **Superseded in part:** this plan describes the first collection model (one shared row per Pokémon, edit by owner or admin, admin-only delete). Collections are now per trainer (see `plan-e-delivery.md`, "Design change after manual testing: per-trainer collections"); the rest of the plan still applies.


**Research:** research.md
**Date:** 2026-10-06
**Scope:** part of an epic (frontend + backend, 6+ phases, endpoints changed) -> split: **D1 = this file** (repo layout + API contract, backend only), **D2 = `docs/plan-d2-frontend.md`** (React app). Run D1 first: the frontend consumes the final contract and the final folder layout.
**Branch:** `feature/plan-d1-structure-ownership` from `feature/plan-c-collection` (HEAD `5d1098d`).
**Estimated Phases:** 5

## Summary

Move the Spring Boot project into `backend/` (history preserved), keep orchestration/docs at the root, then add the acting user to the write use cases: a record's owner is whoever first synced it; PATCH is allowed to the owner or an ADMIN and must carry the `version` it was read at. Same patterns as before: rules in the domain/use cases, JWT claims read only in the web layer, test first.

## Defaults assumed (change now if you disagree)

- **Owner** = user who first creates the record by sync; re-sync by anyone refreshes PokeAPI data but never changes the owner. Records with no owner (legacy/seed) are editable by ADMIN only.
- **Who can do what:** sync = any authenticated user; PATCH = owner or ADMIN; DELETE = ADMIN only (unchanged); reads public.
- **Non-owner PATCH** -> 403 `FORBIDDEN_NOT_OWNER`; checked **before** the version, so a stranger never learns whether a version is current.
- **PATCH body** requires `version` (number): missing -> 400 `VALIDATION_FAILED`; stale -> 409 `CONCURRENT_MODIFICATION` (existing code).
- **Responses** add `ownerId` (number or null). The UI compares it with the `uid` claim of its own token; no usernames are exposed.
- Order of checks in `UpdateStoredPokemon`: id positive -> body has fields -> record exists (404) -> ownership (403) -> version (409) -> validation of attributes (400) -> save.

## Design principles (what to say in the interview)

- **Clean Architecture:** the use cases receive an `Actor` value (userId, username, role) built by the web layer from the JWT; the domain never sees tokens or Spring Security. Authorization that depends on data (ownership) lives in the domain/use case; coarse route rules stay in `SecurityConfig`.
- **Single Responsibility / Open-Closed:** `StoredPokemon.canBeEditedBy(actor)` is the one place that decides; adding a rule later (e.g. teams) changes one method.
- **DRY:** one `Actor` type for every use case that needs identity; the controller uses a single helper to build it.
- **Defense in depth:** route rules (401/403 by role) + ownership rule in the use case + optimistic lock in the database.

## Phase 1: Repository layout (no behavior change)

**Goal:** everything backend lives in `backend/`; `docker compose up --build` and the whole suite work from the new place; nothing else changed.

**Status:** ✅ Complete 2026-10-06
**Deviation:** `HELP.md` was untracked (ignored) so it was moved with plain `mv`; stale root `build/` and `.gradle/` artifacts were deleted. Root `README.md` stub created.

### Tests to Write First (RED)
Verification-first (a pure move has no new behavior): before moving, record the baseline — `./gradlew clean build` (307 tests, coverage gate green) and the compose smoke (login + sync + patch). After moving, the same commands must give the same result from `backend/`.

| Level | Check | Asserts |
| ----- | ----- | ------- |
| Suite | `cd backend && ./gradlew clean build` | 307 tests, JaCoCo gate passes |
| Smoke | `docker compose up --build -d` + curl login/sync | same responses as before the move |

### File Changes
| File | Action | What Changes |
| ---- | ------ | ------------ |
| `src/`, `build.gradle`, `settings.gradle`, `gradle/`, `gradlew`, `gradlew.bat`, `Dockerfile`, `.dockerignore`, `HELP.md`, `.gitattributes` | Move (`git mv`) | into `backend/` |
| `docker-compose.yml` | Modify | `app.build: ./backend` |
| `.gitignore` | Modify | patterns that must also cover `backend/build`, `backend/.gradle`, `frontend/node_modules`, `frontend/dist` |
| `CLAUDE.md`, `.claude/commands/code-review.md`, `.claude/skills/{plan,implement}/SKILL.md` | Modify | commands become `cd backend && ./gradlew ...`; architecture section notes the two domains |
| `README.md` | Create (stub) | root README with the layout and the one-command start; filled in Plan E |

### Implementation Notes
Use `git mv` so `git log --follow` keeps history. Do not touch package names. Keep `.env`, `.env.example`, `docker-compose.yml`, `docs/`, `.claude/`, `plan.md`, `research.md` at the root. Frontend folder is created in D2.

### Verification
```bash
cd backend && ./gradlew clean build && cd ..
docker compose up --build -d && curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/actuator/health && docker compose down -v
git log --follow --oneline backend/build.gradle | tail -2
```
**Expected result:** build green with the same test count; health 200; history still reachable.

---

## Phase 2: Actor, ownership and versioned update (domain + use cases)

**Goal:** the rules work against mocked ports.
**Status:** ✅ Complete 2026-10-06
**Deviation:** phases 2, 3 and 4 were implemented test-first but verified together, because changing the use-case and `StoredPokemon` signatures makes domain, persistence and web tests compile (or fail to) as one unit; `ActorResolver` throws `BadCredentialsException` and `GlobalExceptionHandler` maps `AuthenticationException` to 401 so a token without `uid` cannot become a 500; `Actor` validation reuses `InvalidUserException`.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Unit | `.../domain/ActorTest.java` | `isAdminOnlyForAdminRole`, `rejectsMissingIdentity` | value object invariants |
| Unit | `.../domain/StoredPokemonTest.java` | `canBeEditedByOwner`, `canBeEditedByAdmin`, `cannotBeEditedByAnotherUser`, `ownerlessRecordsAreAdminOnly`, `refreshKeepsTheOwner`, `fromDetailSetsTheOwner` | ownership logic |
| Unit | `.../application/usecase/SyncPokemonTest.java` | `newRecordIsOwnedByTheActor`, `refreshByAnotherUserKeepsTheOriginalOwner`, `racedRetryStillKeepsOwnership` | owner on create/refresh |
| Unit | `.../application/usecase/SyncPokemonRangeTest.java` | `passesTheActorToEverySync` | actor propagation |
| Unit | `.../application/usecase/UpdateStoredPokemonTest.java` | `ownerCanUpdate`, `adminCanUpdateAnyRecord`, `anotherUserGetsNotOwner`, `nonOwnerWithStaleVersionStillGetsNotOwner`, `staleVersionIsRejectedBeforeSaving`, `missingVersionIsRejected`, `ownerlessRecordNeedsAdmin` | order of checks |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `domain/Actor.java` | domain | Create | record(userId, username, role) + `isAdmin()` |
| `domain/exception/NotOwnerException.java` | domain | Create | |
| `domain/StoredPokemon.java` | domain | Modify | `Long ownerId`; `fromDetail(detail, now, owner)`; `canBeEditedBy(actor)`; refresh keeps owner |
| `application/port/in/{SyncPokemonUseCase,SyncPokemonRangeUseCase,UpdateStoredPokemonCommand}.java` | application | Modify | take the `Actor`; command gets `Long version` |
| `application/usecase/{SyncPokemon,SyncPokemonRange,UpdateStoredPokemon}.java` | application | Modify | rules above |

### Implementation Notes
`Actor` lives in `domain` (it is data, no framework). `SyncPokemonRange` passes the same actor to each id. Keep the version comparison in the use case (clear 409 before hitting the database); the entity `@Version` remains the last line of defense.

### Verification
```bash
cd backend && ./gradlew test --tests '*ActorTest' --tests '*StoredPokemonTest' --tests '*SyncPokemon*' --tests '*UpdateStoredPokemonTest' --tests '*ArchitectureTest'
```
**Expected result:** pass (other test classes will not compile until phases 3-4 update their call sites; run only these).

---

## Phase 3: Persistence — owner column

**Goal:** the owner is stored and loaded; deleting a user cannot break records.
**Status:** ✅ Complete 2026-10-06
**Deviation:** phases 2, 3 and 4 were implemented test-first but verified together, because changing the use-case and `StoredPokemon` signatures makes domain, persistence and web tests compile (or fail to) as one unit; `ActorResolver` throws `BadCredentialsException` and `GlobalExceptionHandler` maps `AuthenticationException` to 401 so a token without `uid` cannot become a 500; `Actor` validation reuses `InvalidUserException`.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Integration | `.../persistence/JpaPokemonRepositoryAdapterTest.java` | `ownerRoundTrips`, `ownerlessRecordLoads`, `deletingTheOwnerUserKeepsTheRecordWithoutOwner` | FK `ON DELETE SET NULL` |
| Unit | `.../persistence/PokemonPersistenceMapperTest.java` | `roundTripKeepsTheOwner` | mapper symmetry |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `db/migration/V3__add_pokemon_owner.sql` | infra | Create | `owner_id BIGINT`, FK to `users(id)` `ON DELETE SET NULL`, index |
| `infrastructure/persistence/{PokemonEntity,PokemonPersistenceMapper}.java` | infra | Modify | `ownerId` |

### Implementation Notes
Never edit V1/V2. Column is nullable on purpose (legacy and demo-seed rows). H2 and PostgreSQL both run the script (verified again in Phase 5).

### Verification
```bash
cd backend && ./gradlew test --tests '*JpaPokemonRepositoryAdapterTest' --tests '*PokemonPersistenceMapperTest'
```
**Expected result:** pass on H2 with the same migration.

---

## Phase 4: Web — actor from the JWT, version in PATCH, 403

**Goal:** the API contract the frontend will use.
**Status:** ✅ Complete 2026-10-06
**Deviation:** phases 2, 3 and 4 were implemented test-first but verified together, because changing the use-case and `StoredPokemon` signatures makes domain, persistence and web tests compile (or fail to) as one unit; `ActorResolver` throws `BadCredentialsException` and `GlobalExceptionHandler` maps `AuthenticationException` to 401 so a token without `uid` cannot become a 500; `Actor` validation reuses `InvalidUserException`.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Integration | `.../web/CollectionControllerTest.java` | `patchRequiresVersion` (400 `VALIDATION_FAILED`), `patchPassesVersionAndActorToTheUseCase`, `patch403WhenNotOwner`, `sync passes the actor from the token`, `responsesIncludeOwnerId` | contract |
| Unit | `.../web/GlobalExceptionHandlerTest.java` | `notOwnerIs403WithStableCode` | `FORBIDDEN_NOT_OWNER` |
| Unit | `.../web/ActorResolverTest.java` | `buildsTheActorFromJwtClaims`, `rejectsATokenWithoutUid` | claims `uid`/`sub`/`role` |
| Integration | `.../security/SecurityRulesMatrixTest.java` | unchanged rows still pass | route rules |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `infrastructure/web/ActorResolver.java` | infra | Create | `Actor from(Jwt)`; the only place that reads claims |
| `infrastructure/web/CollectionController.java` | infra | Modify | `@AuthenticationPrincipal Jwt` on sync/patch; passes actor |
| `infrastructure/web/dto/{UpdatePokemonRequest,StoredPokemonResponse,StoredPokemonSummaryResponse}.java` | infra | Modify | `@NotNull Long version`; `ownerId` |
| `infrastructure/web/GlobalExceptionHandler.java` | infra | Modify | `NotOwnerException` -> 403 `FORBIDDEN_NOT_OWNER` |

### Implementation Notes
The `Jwt` type stays in `infrastructure.web`; ArchUnit still forbids it in domain/application. A token without `uid` is a bad token: 401 through the existing entry point.

### Verification
```bash
cd backend && ./gradlew test
```
**Expected result:** whole suite green (call sites of phases 2-3 are updated here).

---

## Phase 5: End-to-end ownership flows and Docker

**Goal:** the rules hold through the real stack, including PostgreSQL.

**Status:** ✅ Complete 2026-10-06
**Deviation:** the concurrency e2e now asserts that exactly ONE of 8 edits sent from the same version wins (stricter than before); a second regular user is registered on demand inside the IT.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| E2E | `.../CollectionFlowIT.java` | `anotherUserCannotEditButAdminCan`, `resyncByAnotherUserKeepsTheOwner`, `patchWithAStaleVersionIsRejected`, `patchBumpsTheVersionClientsMustSendNext`, `ownerIdMatchesTheTokenUid` | ownership + versioning end to end |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `backend/src/test/.../CollectionFlowIT.java` | test | Modify | new flows; existing flows send `version` |
| `docker-compose.yml`, `.env.example` | build | Modify (if needed) | none expected |

### Implementation Notes
Create a second regular user in the e2e (register + login) to act as "another user". Re-measure coverage; keep the gate (90/80).

### Verification
```bash
cd backend && ./gradlew clean build && cd ..
docker compose up --build -d
# as demo: sync + PATCH ok; as another registered user: PATCH -> 403 FORBIDDEN_NOT_OWNER; as demo-admin: PATCH ok; stale version -> 409
docker compose down -v
```
**Expected result:** build green with the coverage gate; the manual flow gives 200 / 403 / 200 / 409 as listed; migrations V1-V3 apply on PostgreSQL.

---

## Final Verification
```bash
cd backend && ./gradlew clean build && cd .. && docker compose build
```

## Rollback Plan
Feature branch: drop or `git revert`. The layout move is one commit (revert restores root layout). Schema: V3 only adds a nullable column; roll back with `V4__drop_owner.sql` if already deployed, never by editing V3.

## Out of Scope
Frontend (D2); README content and demo data (Plan E); usernames in API responses; transferring ownership; per-user private collections; rate limiting (declined); the skipped review minors.

## Implementation Status

**Completed:** 2026-10-06
**Branch:** feature/plan-d1-structure-ownership

| Phase | Status | Deviations |
| ----- | ------ | ---------- |
| 1: Repository layout | ✅ | `HELP.md` moved with plain `mv`; root README stub |
| 2: Actor + ownership + versioned update | ✅ | Verified together with 3-4 (shared compile unit) |
| 3: Owner column (V3) | ✅ | none |
| 4: Web: actor from JWT, version in PATCH, 403 | ✅ | `AuthenticationException` -> 401 handler |
| 5: E2E + Docker | ✅ | stricter concurrency assertion |

Verified: `cd backend && ./gradlew clean build` green (337 tests, 0 failures; coverage 98.2 % lines / 87.5 % branches, gate 90/80). Docker/PostgreSQL manual flow: sync by demo (owner = demo), PATCH by a stranger 403 `FORBIDDEN_NOT_OWNER`, by admin 200, with a stale version 409 `CONCURRENT_MODIFICATION`, without version 400 `VALIDATION_FAILED`, by the owner with the current version 200; Flyway V1-V3 applied; no errors in the logs.

### Contract notes for Plan D2 (frontend)
- `ownerId` is on collection list items, entries and sync/patch responses; compare it with the `uid` claim of the logged-in token.
- PATCH body: `{version, nickname?, region?, tags?}`; send the `version` of the last read; 409 -> reload.
- 403 `FORBIDDEN_NOT_OWNER` for a non-owner (an ADMIN may edit anything); DELETE stays ADMIN-only.
