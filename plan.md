# Plan C: Local collection — sync (US-03) and update/delete (US-04)

**Research:** research.md
**Date:** 2026-10-05
**Scope:** Task (2/6 indicators: new tables/aggregate, 3+ endpoints). 5 phases.
**Branch:** create `feature/plan-c-collection` from `main` (base commit `b9dbbde`); merge when green. Later updates follow the same pattern: one branch per change, new Flyway `V<n>` files, `/api/v1` kept stable.

## Summary

Add a persisted replica of Pokemon. `SyncPokemon` pulls `PokemonDetail` from the existing `PokeApiGateway` and upserts by PokeAPI id, refreshing PokeAPI-owned data and keeping local attributes. Users edit local attributes with `PATCH`; admins delete. Same architecture as before: domain rules in plain Java, a `PokemonRepository` port, JPA adapter in infrastructure, a controller that only translates HTTP.

## Defaults assumed (change now if you disagree)

- **Editable (local-only) fields:** `nickname` (localized name, <= 50), `region` (<= 50), `tags` (<= 10 items, each <= 30, lower-cased, trimmed, de-duplicated). PokeAPI-owned fields (name, stats, types, ...) are not editable, so a re-sync can never conflict with an edit.
- **PATCH semantics:** absent or `null` = unchanged; empty string / empty list = clear. Empty body or body with no recognised field = 400.
- **Sync single:** `POST /api/v1/collection/{idOrName}/sync` -> 201 created / 200 refreshed.
- **Sync range:** `POST /api/v1/collection/sync` with `{"from":1,"to":20}`; max 20 ids per call; ids missing upstream (gap 1026-10000) are reported as failures, not aborts; a catalog outage aborts with 502/504 after saving what already succeeded. Response `{created, updated, failures:[{identifier, reason}]}`.
- **Read:** `GET /api/v1/collection?page&size` (same defaults and 50 cap) and `GET /api/v1/collection/{pokeApiId}` — public, DB only, never calls PokeAPI.
- **Delete:** `DELETE /api/v1/collection/{pokeApiId}` -> 204; ADMIN only (403 for USER, 401 anonymous).
- **Evolution chain** stored as a JSON text column (`SHORTCUT:` ceiling: cannot query by evolution; upgrade to a table when needed).
- **Concurrency:** `@Version` optimistic locking, version carried in the domain; stale write -> 409 `CONCURRENT_MODIFICATION`.
- **Demo admin:** `demo-admin` seeded from env (`DEMO_ADMIN_PASSWORD`), same enable flag as the demo user.

## Design principles (what to say in the interview)

- **Single Responsibility:** each use case does one thing (`SyncPokemon`, `SyncPokemonRange`, `UpdateStoredPokemon`, ...); the controller never decides business rules.
- **DRY:** `PokemonIdentifier` value object is the single place that normalizes/validates ids; both `GetPokemonDetail` and the sync use cases use it. One mapper per boundary.
- **Open/Closed:** new editable attributes later = new fields in `LocalAttributes` + a migration; use cases and adapters stay untouched.
- **Liskov / Dependency Inversion:** use cases depend on `PokemonRepository` and `PokeApiGateway` interfaces; tests use Mockito fakes, production uses JPA and HTTP adapters.
- **Interface Segregation:** repository port exposes only what use cases need (`findByPokeApiId`, `findPage`, `save`, `deleteByPokeApiId`).
- **Clean Architecture proof:** ArchUnit rule that `web` never depends on `persistence`, plus the existing inward-only rules.

## Phase 1: Domain model and sync use cases (US-03)

**Goal:** syncing one Pokemon or a range works against mocked ports: upsert keeps local data; ranges tolerate missing ids.

**Status:** ✅ Complete 2026-10-05
**Deviation:** `SyncResult` record added for the single sync response; `SyncReport.Failure` is nested; extra tests for blank/long region and tags and for the maximum range size.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Unit | `.../domain/PokemonIdentifierTest.java` | `normalizesCaseAndWhitespace`, `rejectsBlankAndUnsafe` | same rules as today's `GetPokemonDetail` |
| Unit | `.../domain/LocalAttributesTest.java` | `trimsAndLowercasesTags`, `dedupesTags`, `rejectsTooLongNickname`, `rejectsTooManyTags`, `rejectsBlankTag` | `InvalidLocalAttributesException` |
| Unit | `.../domain/StoredPokemonTest.java` | `createdFromDetailHasEmptyLocalAttributes`, `refreshKeepsLocalAttributesIdAndVersion`, `refreshReplacesPokeApiOwnedFields` | merge rule |
| Unit | `.../application/usecase/SyncPokemonTest.java` | `createsWhenNotStored`, `refreshesWhenStoredKeepingLocalEdits`, `throwsNotFoundWhenUpstreamHasNothing`, `rejectsInvalidIdentifier`, `stampsSyncedAtFromTheClock` | `SyncOutcome.CREATED/UPDATED`, repository `save` args |
| Unit | `.../application/usecase/SyncPokemonRangeTest.java` | `syncsEveryIdInRange`, `reportsMissingIdsAsFailuresAndContinues`, `abortsOnCatalogUnavailable`, `rejectsRangeAboveMax`, `rejectsFromGreaterThanTo`, `rejectsNonPositiveFrom` | `SyncReport` counts, `InvalidSyncRangeException`, exception propagation |
| Unit | `.../application/usecase/GetPokemonDetailTest.java` (existing) | unchanged cases | still green after refactor |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `domain/PokemonIdentifier.java` | domain | Create | value object: `of(String)` normalizes + validates |
| `domain/LocalAttributes.java` | domain | Create | record nickname, region, tags + invariants/normalization |
| `domain/StoredPokemon.java` | domain | Create | record: id, version, pokeApiId, name, sprite/image urls, types, abilities, stats, weightKg, heightM, genus, description, evolutionChain, `LocalAttributes`, syncedAt; `fromDetail(...)`, `refreshedFrom(detail, now)`, `withLocalAttributes(...)` |
| `domain/SyncOutcome.java`, `domain/SyncReport.java` | domain | Create | enum + record (created, updated, failures) |
| `domain/exception/{InvalidLocalAttributesException,InvalidSyncRangeException}.java` | domain | Create | |
| `application/port/out/PokemonRepository.java` | application | Create | `findByPokeApiId`, `findPage`, `save`, `deleteByPokeApiId` |
| `application/port/in/{SyncPokemonUseCase,SyncPokemonRangeUseCase}.java` | application | Create | inbound ports |
| `application/usecase/{SyncPokemon,SyncPokemonRange}.java` | application | Create | use `PokemonIdentifier`, `Clock` injected |
| `application/usecase/GetPokemonDetail.java` | application | Modify | delegate to `PokemonIdentifier` (behavior unchanged) |

### Implementation Notes
`Clock` is a constructor argument so `syncedAt` is testable. Range loop converts ids to strings and reuses `SyncPokemon.sync`; it catches only `PokemonNotFoundException` per id (-> failure "not found upstream") and lets `CatalogUnavailableException` propagate. `StoredPokemon.refreshedFrom` must copy `id`, `version` and `localAttributes` from `this`.

### Verification
```bash
./gradlew test --tests '*PokemonIdentifierTest' --tests '*LocalAttributesTest' --tests '*StoredPokemonTest' --tests '*SyncPokemon*' --tests '*GetPokemonDetailTest' --tests '*ArchitectureTest'
```
**Expected result:** pass; ArchUnit still green (no Spring/JPA in domain/application).

---

## Phase 2: Update, delete and read use cases (US-04)

**Goal:** edit/delete/list/get stored Pokemon with the right errors, against a mocked repository.

**Status:** ✅ Complete 2026-10-05
**Deviation:** non-positive ids raise `InvalidPokemonIdentifierException` (400) via `PokemonIdentifier.requirePositive` (new, tested); `Pagination` is package-private and `ListPokemon` keeps its public constants delegating to it, so Plan B tests are untouched.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Unit | `.../application/usecase/UpdateStoredPokemonTest.java` | `updatesOnlyProvidedFields`, `nullFieldsStayUnchanged`, `emptyStringAndEmptyListClear`, `rejectsEmptyUpdate`, `throwsNotFoundWhenMissing`, `rejectsInvalidAttributes`, `keepsPokeApiOwnedFieldsUntouched` | saved aggregate; `InvalidStoredPokemonUpdateException`, `StoredPokemonNotFoundException` |
| Unit | `.../application/usecase/DeleteStoredPokemonTest.java` | `deletesWhenPresent`, `throwsNotFoundWhenMissing` | repository calls |
| Unit | `.../application/usecase/ListStoredPokemonTest.java` | `usesDefaultsAndCap`, `rejectsInvalidPagination`, `returnsRepositoryPage` | same pagination rules as the catalog |
| Unit | `.../application/usecase/GetStoredPokemonTest.java` | `returnsStored`, `throwsNotFoundWhenMissing`, `rejectsNonPositiveId` | |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `domain/exception/{StoredPokemonNotFoundException,InvalidStoredPokemonUpdateException}.java` | domain | Create | |
| `application/port/in/{UpdateStoredPokemonUseCase,DeleteStoredPokemonUseCase,ListStoredPokemonUseCase,GetStoredPokemonUseCase}.java` + commands | application | Create | `UpdateStoredPokemonCommand(pokeApiId, nickname, region, tags)` |
| `application/usecase/{UpdateStoredPokemon,DeleteStoredPokemon,ListStoredPokemon,GetStoredPokemon}.java` | application | Create | |
| `application/usecase/Pagination.java` | application | Create | extract defaults/cap shared with `ListPokemon` (DRY); `ListPokemon` delegates, tests stay green |

### Implementation Notes
Update flow: load -> build merged `LocalAttributes` (invariants validated there) -> `save`. "No recognised field" check happens before touching the repository.

### Verification
```bash
./gradlew test --tests '*UpdateStoredPokemonTest' --tests '*DeleteStoredPokemonTest' --tests '*ListStoredPokemonTest' --tests '*GetStoredPokemonTest' --tests '*ListPokemonTest' --tests '*ArchitectureTest'
```
**Expected result:** pass.

---

## Phase 3: Persistence — migration V2 and JPA adapter

**Goal:** stored Pokemon persist and load with all collections, upsert by PokeAPI id, version checks.

**Status:** ✅ Complete 2026-10-05
**Deviation:** `VARCHAR(n)` instead of `TEXT` for description/evolution JSON (H2 maps TEXT to CLOB and Hibernate `validate` would reject it); order column named `sort_order` (avoids the SQL `POSITION` keyword); no separate `MigrationTest` because every `@DataJpaTest` already validates the schema against the entities; added tests for huge page numbers (int overflow guard) and version bump.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Integration | `.../infrastructure/persistence/JpaPokemonRepositoryAdapterTest.java` (`@DataJpaTest`) | `savesAndFindsWithAllCollections`, `uniquePokeApiId`, `updateKeepsOrderOfTypesAbilitiesStats`, `tagsPersistAndClear`, `evolutionChainRoundTrips`, `staleVersionIsRejected`, `findPageOrdersByPokeApiId`, `findPageBeyondEndIsEmpty`, `deleteRemovesChildRows` | optimistic lock exception, ordering, cascade |
| Unit | `.../infrastructure/persistence/PokemonPersistenceMapperTest.java` | `roundTrip`, `evolutionJsonHandlesNullAndNestedBranches` | mapper symmetry |
| Integration | `.../infrastructure/persistence/MigrationTest.java` | `schemaValidatesAgainstEntities` | `ddl-auto: validate` passes (context loads) |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `db/migration/V2__create_pokemon.sql` | infra | Create | tables `pokemon` (PK, `pokeapi_id` UNIQUE, name, urls, weight/height `DOUBLE PRECISION`, genus, description `TEXT`, `evolution_chain_json TEXT`, nickname, region, `synced_at` `TIMESTAMP WITH TIME ZONE`, `version`), `pokemon_types`, `pokemon_abilities`, `pokemon_stats`, `pokemon_tags` (FK `ON DELETE CASCADE`, `position`) |
| `infrastructure/persistence/{PokemonEntity,StatEmbeddable,SpringDataPokemonRepository,JpaPokemonRepositoryAdapter,PokemonPersistenceMapper}.java` | infra | Create | entity with `@Version`, `@ElementCollection` + `@OrderColumn`; adapter `@Transactional`; mapper serializes the chain with Jackson 3 `JsonMapper` |
| `application.yml` | config | Modify | `spring.jpa.properties.hibernate.default_batch_fetch_size: 50` |

### Implementation Notes
Follow the users adapter pattern (package-private entity/repo, public adapter, `saveAndFlush`). Adapter methods read inside a transaction so lazy collections are initialized before mapping to domain. Never edit V1. Types: only the portable ones from the research constraints. Verify the exact column types against Hibernate 7 validation on H2 first, then on Postgres in Phase 5.

### Verification
```bash
./gradlew test --tests '*JpaPokemonRepositoryAdapterTest' --tests '*PokemonPersistenceMapperTest' --tests '*MigrationTest'
```
**Expected result:** pass on H2 with the same migration.

---

## Phase 4: Web, security and error mapping

**Goal:** the six endpoints behave per the table; roles enforced; all failures use the shared error body.

**Status:** ✅ Complete 2026-10-05
**Deviation:** optimistic-lock failures are translated by the adapter into a domain `StaleStoredPokemonException` (handler maps only domain types, nothing from Spring Data/Hibernate crosses upward); two ArchUnit rules (web ⟂ persistence, both directions); seeder now takes `PasswordHasher` and the admin is seeded only when `DEMO_ADMIN_PASSWORD` is set (no default password in code); list items use a lighter `StoredPokemonSummaryResponse`.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Integration | `.../infrastructure/web/CollectionControllerTest.java` (`@WebMvcTest`) | `listAndGetArePublic`, `get404WhenMissing`, `syncSingle201ThenOk200`, `syncRangeReturnsReport`, `syncRequires401WithoutToken`, `syncRange400OnBadRange`, `patch200UpdatesLocalFields`, `patch400OnEmptyBody`, `patch400OnTooLongNickname`, `patch400OnMalformedJson`, `patch404WhenMissing`, `patch409OnConcurrentModification`, `patch401WithoutToken`, `delete204AsAdmin`, `delete403AsUser`, `delete401Anonymous`, `delete404WhenMissing` | statuses, codes, JSON shape, role rule |
| Unit | `.../infrastructure/web/GlobalExceptionHandlerTest.java` (extend) | new exceptions -> status/code; `ObjectOptimisticLockingFailureException` -> 409 | stable codes |
| Unit | `.../infrastructure/config/DemoUserSeederTest.java` (extend) | `seedsAdminWithAdminRole`, `adminSeedIsIdempotent` | role ADMIN stored via ports |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `infrastructure/web/CollectionController.java` | infra | Create | the six endpoints under `/api/v1/collection` |
| `infrastructure/web/dto/{StoredPokemonResponse,SyncRangeRequest,SyncReportResponse,UpdatePokemonRequest}.java` | infra | Create | request validation shape (`@Size`), response factories |
| `infrastructure/web/GlobalExceptionHandler.java` | infra | Modify | codes: `STORED_POKEMON_NOT_FOUND` 404, `INVALID_LOCAL_ATTRIBUTES`/`INVALID_UPDATE`/`INVALID_SYNC_RANGE` 400, `CONCURRENT_MODIFICATION` 409 |
| `infrastructure/security/SecurityConfig.java` | infra | Modify | `GET /api/v1/collection/**` permitAll, `DELETE /api/v1/collection/**` hasRole ADMIN, rest authenticated |
| `infrastructure/config/{UseCaseConfig,DemoUserSeeder,DemoUserProperties}.java` | infra | Modify | wire six use cases + `Clock`; admin seed through `PasswordHasher` + `UserRepository` |
| `application.yml`, `docker-compose.yml`, `.env.example` | config | Modify | `DEMO_ADMIN_PASSWORD` (required var, placeholder only in `.env.example`) |
| `ArchitectureTest.java` | test | Modify | rule: `..infrastructure.web..` must not depend on `..infrastructure.persistence..` |

### Implementation Notes
Controller parameters for pagination stay nullable (rules live in the use case). `PATCH` request DTO uses nullable fields; `@Size` on strings/lists is shape only, real limits are enforced in `LocalAttributes`. Admin seeding must not go through `RegisterUser` (it always assigns USER); it builds the `User` with role ADMIN using the existing ports. Add a role test per verb so a forgotten rule fails a test.

### Verification
```bash
./gradlew test --tests '*CollectionControllerTest' --tests '*GlobalExceptionHandlerTest' --tests '*DemoUserSeederTest' --tests '*ArchitectureTest'
```
**Expected result:** pass, including every existing auth/catalog test.

---

## Phase 5: End-to-end and Docker on PostgreSQL

**Goal:** the full flow works through the real stack and on PostgreSQL: login -> sync -> read -> patch -> re-sync keeps edits -> delete as admin.

**Status:** ✅ Complete 2026-10-05
**Deviation:** the PokeAPI stand-in moved to `support/FakePokeApi` and is shared by `PokemonFlowIT` and `CollectionFlowIT`; the concurrency test asserts invariants (only 200/409, version grows by the number of wins) instead of forcing a specific interleaving, which would be flaky.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| E2E | `.../CollectionFlowIT.java` (`@SpringBootTest`, local PokeAPI stand-in as in `PokemonFlowIT`) | `loginSyncPatchResyncDelete`, `rangeSyncReportsGapIdsAsFailures`, `concurrentPatchesOneWins409`, `userCannotDeleteAdminCan`, `publicReadsNeedNoToken` | whole chain: roles, merge rule, version conflict |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `src/test/.../CollectionFlowIT.java` | test | Create | e2e; extract the upstream stand-in from `PokemonFlowIT` into a shared test helper (DRY) |
| `src/test/.../support/FakePokeApi.java` | test | Create | reusable HttpServer stand-in |

### Implementation Notes
Extract the stand-in first and re-run `PokemonFlowIT` to prove nothing changed. Run the manual smoke against real PokeAPI on Postgres to catch H2/Postgres differences (element collections, timestamp type).

### Verification
```bash
./gradlew clean build
docker compose up --build -d
# login as demo (USER) and demo-admin (ADMIN), then:
curl -s -X POST localhost:8080/api/v1/collection/pikachu/sync -H "Authorization: Bearer $TOKEN"
curl -s -X PATCH localhost:8080/api/v1/collection/25 -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"nickname":"Pika","region":"Kanto","tags":["starter"]}'
curl -s -X POST localhost:8080/api/v1/collection/pikachu/sync -H "Authorization: Bearer $TOKEN"   # edits survive
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE localhost:8080/api/v1/collection/25 -H "Authorization: Bearer $TOKEN"        # 403 as USER
docker compose down
```
**Expected result:** build green; sync 201 then 200; PATCH 200; nickname/region/tags still present after re-sync; DELETE 403 as USER and 204 as ADMIN; no errors in app logs.

---

## Final Verification
```bash
./gradlew clean build
docker compose build
```

## Rollback Plan
All on a feature branch: `git revert`/drop the branch. Schema: V2 only adds tables; roll back with `docker compose down -v` locally, or a new `V3__drop_pokemon.sql` if already deployed (never edit applied migrations).

## Out of Scope
Frontend (Plan D); README, extra seed data (curated demo Pokemon via a demo migration/profile), OpenAPI docs (Plan E); search/filter of the collection by tag/type; editing PokeAPI-owned fields; bulk delete; audit trail (who edited what); idempotent retry of racing first-time syncs (currently 409).

## Implementation Status

**Completed:** 2026-10-05
**Branch:** feature/plan-c-collection (not committed yet)

| Phase | Status | Deviations |
| ----- | ------ | ---------- |
| 1: Domain + sync use cases | ✅ | `SyncResult`; extra attribute tests |
| 2: Update/delete/read use cases | ✅ | Non-positive id -> 400; shared `Pagination` |
| 3: Persistence V2 + adapter | ✅ | `VARCHAR(n)` not `TEXT` (H2 CLOB); `sort_order`; no separate MigrationTest |
| 4: Web, roles, errors | ✅ | Stale write translated to a domain exception; 2 ArchUnit rules; admin only if env var set |
| 5: E2E + Docker/PostgreSQL | ✅ | Shared `FakePokeApi`; invariant-based concurrency test |

Verified: `./gradlew clean build` green x3 (224 tests, 0 failures). Docker on PostgreSQL with the real PokeAPI: V1+V2 migrations applied and Hibernate `validate` passed; sync 201 -> PATCH -> re-sync 200 keeps nickname/region/tags (version 0 -> 1 -> 2); range 1025..1027 created 1 and reported 1026/1027 as failures; public list works; DELETE 401 anonymous / 403 USER / 204 ADMIN / 404 repeated; 400 bodies share one shape; no errors in app logs.

### Notes for Plan D (frontend)
- Collection API: `GET /api/v1/collection[?page&size]`, `GET /{id}`, `POST /{idOrName}/sync`, `POST /sync {from,to}`, `PATCH /{id}`, `DELETE /{id}` (ADMIN). `id` is the PokeAPI id.
- Catalog API from Plan B stays the browsing source; the frontend can offer "save to my collection" via sync.
- CORS allows only `CORS_ALLOWED_ORIGINS` (default `http://localhost:5173`).
- Demo accounts: `demo` / `DEMO_USER_PASSWORD`, `demo-admin` / `DEMO_ADMIN_PASSWORD` (see `.env.example`).

## Code review follow-up — batch 1 (2026-10-05)

Fixed test-first (each finding had a failing test before the fix). 242 tests, 0 failures; verified again on Docker/PostgreSQL.

| Finding | Fix |
| ------- | --- |
| C1 username/email not normalized | `User` stores them stripped + lower-case; register, `exists*` and login use the same normalization |
| C2 BCrypt 72-byte limit -> 500 | Password length measured in bytes (`PasswordLimits`); register -> 400 `WEAK_PASSWORD`, login -> 401 without hashing |
| C3 re-sync could wipe genus/description/chain | `StoredPokemon.refreshedFrom` keeps stored values when upstream returns none |
| C4 range sync lost its report | Stale edits become per-id failures; if upstream dies midway the partial report is returned with `abortedReason` (nothing processed yet -> still 502/504) |
| C5 malformed upstream data -> 500 | Missing stat/species names and oversized ids raise `CatalogUnavailableException` (502) |
| S4 default credentials | No default demo password; demo user only seeded when `DEMO_USER_PASSWORD` is set; compose seeds only if `SEED_DEMO_USER=true` |
| S5 PII in logs | Integrity violations log only the exception type, no message or stack trace |
| A1 weak ArchUnit | No `allowEmptyShould`; new rules: web only uses inbound ports, adapters do not depend on web/persistence, use-case implementations are wired only by config |
| A2 `AuthToken` in `port.out` | Moved to `domain`; controller no longer depends on an outbound port |

### Still open (batch 2 / decisions)
- A4 translate unique violations in the user adapter (`USERNAME_TAKEN` under races); first-sync race on `pokeapi_id`.
- T1 JaCoCo + coverage gate; T2 role/verb matrix test; T3 tests for `ApiSecurityErrorHandler`/wiring; T4 remove `Thread.sleep` from tests.
- S2 user enumeration (register 409 echoes the username; login skips BCrypt for unknown users).
- Decisions for the owner: S1 (any registered user can edit the shared collection), S3 (rate limiting / password length 12), A3 (`version` exposed but PATCH does not take it).
