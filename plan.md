# Plan B: Pokemon list (US-01) and detail (US-02)

**Research:** research.md
**Date:** 2026-10-05
**Scope:** Task (2/6 indicators: new external dependency, new domain models)
**Estimated Phases:** 5
**Previous plan:** `docs/plan-a-foundation.md` (complete)

## Summary

Add two public read endpoints backed by a `PokeApiGateway` port. The domain owns `PokemonSummary`, `PokemonDetail` and `EvolutionNode`; an infrastructure adapter calls PokeAPI with `RestClient`, maps JSON to those models in one place, and caches responses with Caffeine. Same patterns as Plan A: ports in `application`, plain-Java use cases wired in `UseCaseConfig`, errors through `GlobalExceptionHandler`.

## Defaults assumed (change now if you disagree)

- Pagination: 0-based `page`, `size` default 12, max 50. Response: `items, page, size, totalItems, totalPages`.
- "Category" = types (list of names). Mass exposed as `weightKg`, height as `heightM`.
- Description: first English flavor text, control chars cleaned; `genus` also returned. Missing English -> `null`.
- Evolution chain: tree of nodes `{id, name, spriteUrl, evolvesTo[], requirements[]}`; requirement is a readable string such as `"Level up (happiness 160, day)"` or `"Use water-stone"`.
- Cache: Caffeine, TTL 1 h, max 2000 entries, per upstream resource (pokemon, species, evolution-chain, list page). Failures and 404s are not cached.
- Upstream: connect timeout 2 s, read timeout 5 s, descriptive `User-Agent`.

## Design principles (what to say in the interview)

- **Dependency Inversion / Clean Architecture:** use cases depend on `PokeApiGateway` (port); `RestClient`, Jackson DTOs and Caffeine exist only in `infrastructure/pokeapi`. Swapping PokeAPI for another source, or for the local DB in Plan C, changes no use case.
- **Single Responsibility:** client (HTTP + cache) / mapper (JSON -> domain) / adapter (orchestration) / use case (rules) / controller (HTTP translation) are separate classes.
- **Open/Closed + DRY:** one mapper is the only place that knows PokeAPI shapes; Plan C sync reuses `PokemonDetail` from the same gateway instead of re-mapping.
- **Interface Segregation:** a small `PokeApiGateway` (`findPage`, `findDetail`); use cases do not see HTTP.
- **Liskov:** an in-memory fake gateway in tests substitutes for the real adapter.

## Phase 1: Domain models, ports and use cases

**Goal:** list and detail rules (pagination bounds, id/name validation) work against a mocked gateway.

**Status:** ✅ Complete 2026-10-05
**Deviation:** none; added tests for unsafe identifiers (`../admin`, `a/b`, `pika%20`).

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Unit | `.../application/usecase/ListPokemonTest.java` | `usesDefaultsWhenNull`, `rejectsNegativePage`, `rejectsSizeAboveMax`, `rejectsSizeBelowOne`, `delegatesToGateway` | `InvalidPaginationException`; gateway called with validated page/size |
| Unit | `.../application/usecase/GetPokemonDetailTest.java` | `returnsDetail`, `normalizesNameToLowercaseAndTrims`, `rejectsBlankIdentifier`, `throwsNotFoundWhenGatewayReturnsEmpty` | `PokemonNotFoundException`, `InvalidPokemonIdentifierException` |
| Unit | `.../domain/PageResultTest.java` | `computesTotalPages`, `emptyPageHasZeroPages` | derived values |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `domain/PokemonSummary.java` | domain | Create | record: id, name, spriteUrl, types, weightKg, abilities |
| `domain/PokemonDetail.java` | domain | Create | record: id, name, imageUrl, spriteUrl, types, weightKg, heightM, abilities, stats, genus, description, evolutionChain |
| `domain/PokemonStat.java`, `domain/EvolutionNode.java` | domain | Create | records (node is recursive) |
| `domain/PageResult.java` | domain | Create | generic record + `totalPages()`; framework-neutral |
| `domain/exception/{PokemonNotFoundException,InvalidPaginationException,InvalidPokemonIdentifierException,CatalogUnavailableException}.java` | domain | Create | |
| `application/port/out/PokeApiGateway.java` | application | Create | `PageResult<PokemonSummary> findPage(int page,int size)`, `Optional<PokemonDetail> findDetail(String idOrName)` |
| `application/port/in/{ListPokemonUseCase,GetPokemonDetailUseCase}.java` | application | Create | inbound ports |
| `application/usecase/{ListPokemon,GetPokemonDetail}.java` | application | Create | defaults 12, max 50 as constants; identifier normalization |

### Implementation Notes
Pagination bounds are a business rule, so they live in the use case and hold for any entry point. Identifier rule: trimmed, lowercase, non-empty, `[a-z0-9-]+` (blocks path tricks before they reach the HTTP adapter; PokeAPI answers 400 on trailing spaces). `CatalogUnavailableException` carries a `timeout` flag so the web layer can choose 502 vs 504.

### Verification
```bash
./gradlew test --tests '*ListPokemonTest' --tests '*GetPokemonDetailTest' --tests '*PageResultTest' --tests '*ArchitectureTest'
```
**Expected result:** pass; ArchUnit still green (no Spring in domain/application).

---

## Phase 2: PokeAPI HTTP client with cache

**Goal:** typed access to the 4 PokeAPI resources with timeouts, error mapping and caching, tested against a stub server.

**Status:** ✅ Complete 2026-10-05
**Deviation:** `PokeApiClient` is built by `PokeApiConfig` (`@Bean`) instead of `@Component` so tests can bind a stub server to the exact production builder; extra tests for 4xx and malformed JSON; mutation check confirmed the cache test fails without `@Cacheable`.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Unit | `.../infrastructure/pokeapi/PokeApiClientTest.java` (`MockRestServiceServer`) | `fetchesPokemonByIdOrName`, `returnsEmptyOn404`, `throwsUnavailableOn5xx`, `throwsUnavailableOnTimeout`, `sendsUserAgent`, `buildsPageUrlWithLimitAndOffset` | DTO parsed from fixture; errors mapped; header present |
| Integration | `.../infrastructure/pokeapi/PokeApiCachingTest.java` (`@SpringJUnitConfig` with `CacheConfig` + client) | `secondCallHitsCache`, `notFoundIsNotCached`, `failuresAreNotCached` | upstream called once; evict behavior |
| Fixtures | `src/test/resources/pokeapi/{list,pokemon-25,pokemon-10001,species-25,evolution-10,evolution-67}.json` | captured from the real API (trimmed) | realistic shapes |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `build.gradle` | build | Modify | `spring-boot-starter-cache`, `com.github.ben-manes.caffeine:caffeine` |
| `infrastructure/pokeapi/dto/{PokeApiPage,PokeApiPokemon,PokeApiSpecies,PokeApiEvolutionChain}.java` | infra | Create | Jackson records, snake_case via `@JsonProperty`, unknown properties ignored |
| `infrastructure/pokeapi/PokeApiProperties.java` | infra | Create | `app.pokeapi.{base-url,connect-timeout,read-timeout,cache-ttl,cache-max-size}` |
| `infrastructure/pokeapi/PokeApiClient.java` | infra | Create | `getPage`, `getPokemon`, `getSpecies`, `getEvolutionChain`; `@Cacheable` per resource; maps 404 -> `Optional.empty`, 5xx/timeout/IO -> `CatalogUnavailableException` |
| `infrastructure/pokeapi/PokeApiConfig.java` | infra | Create | `RestClient` bean with timeouts + `User-Agent`; Caffeine `CacheManager` (`@EnableCaching`) |
| `application.yml` | config | Modify | `app.pokeapi.*` defaults |

### Implementation Notes
Build request paths only from validated ids/names (`UriBuilder` path variables) and never follow URLs from payloads; extract the id from `species.url`/`evolution_chain.url` with a strict `/(\d+)/?$` parse. Use `unless = "#result == null"`-style conditions (or cache `Optional` wrapped carefully) so 404s are not cached; exceptions are never cached by Spring. Confirm `@JsonProperty` package under Jackson 3 before writing DTOs (research risk). Cache names constants in one place.

### Verification
```bash
./gradlew test --tests '*PokeApiClientTest' --tests '*PokeApiCachingTest'
```
**Expected result:** pass; no network access during tests.

---

## Phase 3: Mapper and gateway adapter

**Goal:** PokeAPI payloads become domain models, including unit conversion, text cleanup and the evolution tree; list pages fetch details in parallel.

**Status:** ✅ Complete 2026-10-05
**Deviation:** mapper takes the evolution chain DTO and builds the tree itself (`toDetail(pokemon, species, chain)`); gateway bean + virtual-thread executor are defined in `PokeApiConfig`; concurrency cap (10 in-flight calls) is a shared semaphore; extra tests for offset overflow clamping, order preservation under slow calls and unknown triggers.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Unit | `.../infrastructure/pokeapi/PokeApiMapperTest.java` | `convertsWeightAndHeight` (60 -> 6.0 kg), `prefersOfficialArtworkAndFallsBackToSprite`, `nullSpritesBecomeNull`, `cleansFlavorTextControlChars`, `picksEnglishGenusAndDescription`, `descriptionNullWhenNoEnglish`, `mapsLinearChain` (pichu->pikachu->raichu), `mapsBranchingChain` (eevee 8 branches), `dedupesEvolutionRequirements`, `describesRequirements` | exact values from fixtures |
| Unit | `.../infrastructure/pokeapi/PokeApiGatewayAdapterTest.java` (stub client) | `listBuildsSummariesInPageOrder`, `listPropagatesTotalCount`, `listPageBeyondEndIsEmpty`, `detailCombinesPokemonSpeciesAndChain`, `detailUsesSpeciesUrlNotPokemonId` (deoxys-attack -> species 386), `detailEmptyWhenPokemonMissing`, `listFailsWhenAnyDetailFails` | ordering preserved; form handled; failure maps to `CatalogUnavailableException` |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `infrastructure/pokeapi/PokeApiMapper.java` | infra | Create | pure mapping functions, no I/O |
| `infrastructure/pokeapi/PokeApiGatewayAdapter.java` | infra | Create | implements `PokeApiGateway`; orchestrates client + mapper; parallel detail fetch |

### Implementation Notes
Parallel fetch with a virtual-thread executor and a small concurrency cap (e.g. 10) so a page of 50 does not open 50 sockets; preserve order by joining futures in list order. **`SHORTCUT:`** evolution nodes build their sprite URL from the id with the known sprites URL pattern instead of one extra call per node — ceiling: breaks if PokeAPI changes the sprite host; upgrade when it does or when node images must be exact. **`SHORTCUT:`** detail does 3 sequential calls (dependent URLs); fine thanks to caching, revisit only if cold detail latency matters. Never use `Pokemon` weight units outside the mapper.

### Verification
```bash
./gradlew test --tests '*PokeApiMapperTest' --tests '*PokeApiGatewayAdapterTest' --tests '*ArchitectureTest'
```
**Expected result:** pass; ArchUnit confirms no PokeAPI types leak into `domain`/`application`.

---

## Phase 4: Web layer and error mapping

**Goal:** `GET /api/v1/pokemon` and `GET /api/v1/pokemon/{idOrName}` return stable DTOs; all failure modes use the shared error body.

**Status:** ✅ Complete 2026-10-05
**Deviation:** error assertion adjusted: Spring Security already sends `Cache-Control: no-store` on errors, so the test asserts errors never carry the 60 s public cache instead of asserting the header is absent. Added `INVALID_PARAMETER` (type mismatch) handler and `DataIntegrity`-style generic client message for the catalog (upstream text only logged).

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Integration | `.../infrastructure/web/PokemonControllerTest.java` (`@WebMvcTest`) | `listReturnsPagedSummaries`, `listUsesDefaultPagination`, `list400OnInvalidSize`, `list400OnNonNumericSize`, `detailReturnsFullDetailWithEvolution`, `detail404WhenMissing`, `detail400OnInvalidIdentifier`, `detail502WhenCatalogDown`, `detail504OnTimeout`, `isPublicWithoutToken` | statuses, codes (`POKEMON_NOT_FOUND`, `INVALID_PAGINATION`, `CATALOG_UNAVAILABLE`, `CATALOG_TIMEOUT`), JSON shape |
| Unit | `.../infrastructure/web/GlobalExceptionHandlerTest.java` (extend) | new domain exceptions + `MethodArgumentTypeMismatchException` -> 400 | stable codes, no stack trace |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `infrastructure/web/PokemonController.java` | infra | Create | two GET endpoints calling use case ports |
| `infrastructure/web/dto/{PageResponse,PokemonSummaryResponse,PokemonDetailResponse,EvolutionNodeResponse,StatResponse}.java` | infra | Create | records + `from(domain)` factories |
| `infrastructure/web/GlobalExceptionHandler.java` | infra | Modify | handlers for the 4 new exceptions and type mismatch (400) |
| `infrastructure/config/UseCaseConfig.java` | infra | Modify | beans for list and detail use cases |

### Implementation Notes
Controller parameters are `Integer page, Integer size` (nullable) so the use case applies defaults and bounds, avoiding duplicated rules in annotations. Add `Cache-Control: public, max-age=60` on GET responses (cheap client-side cache). Do not expose PokeAPI URLs other than image URLs.

### Verification
```bash
./gradlew test --tests '*PokemonControllerTest' --tests '*GlobalExceptionHandlerTest'
```
**Expected result:** pass, including the existing auth tests.

---

## Phase 5: End-to-end through a stub upstream and Docker smoke

**Goal:** the real wiring (controller -> use case -> adapter -> client -> cache) works against a local stub of PokeAPI; cache hit verified end to end.

**Status:** ✅ Complete 2026-10-05
**Deviation:** the stand-in upstream is a `com.sun.net.httpserver.HttpServer` inside `PokemonFlowIT` (real HTTP, real timeouts) instead of `MockRestServiceServer`; no compose changes were needed (`POKEAPI_BASE_URL` already works through `application.yml`).

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| E2E | `.../PokemonFlowIT.java` (`@SpringBootTest` + `MockRestServiceServer` customizing the `RestClient` builder, or an in-test `HttpServer` serving fixtures via `@DynamicPropertySource`) | `listThenDetailEndToEnd`, `secondListRequestDoesNotCallUpstreamAgain`, `upstreamDownReturns502` | full chain, cache behavior, error body |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `src/test/.../PokemonFlowIT.java` | test | Create | e2e |
| `docker-compose.yml`, `.env.example` | build | Modify (if needed) | `POKEAPI_BASE_URL` override (default public API) |

### Implementation Notes
Keep the e2e off the real network. If the in-test `HttpServer` approach is chosen, serve the same fixtures from Phase 2. Manual smoke at the end with the real API through Docker.

### Verification
```bash
./gradlew clean build
docker compose up --build -d
curl -s "localhost:8080/api/v1/pokemon?page=0&size=3"
curl -s localhost:8080/api/v1/pokemon/eevee
docker compose down
```
**Expected result:** build green; list returns 3 summaries with sprite/types/weightKg/abilities; eevee detail shows 8 evolution branches; second identical request is noticeably faster (cache).

---

## Final Verification
```bash
./gradlew clean build
docker compose build
```

## Rollback Plan
Additive change: `git revert` the phase commits. No schema changes. Disable caching with `spring.cache.type=none` if the cache misbehaves in a demo.

## Out of Scope
Persistence/sync/update (Plan C); frontend (Plan D); README, OpenAPI docs, search/filter by name or type, resilience4j circuit breaker (candidate extra; the timeout + mapped 502/504 + cache cover the exercise), metrics.

## Implementation Status

**Completed:** 2026-10-05
**Branch:** main (local repo, still no commits)

| Phase | Status | Deviations |
| ----- | ------ | ---------- |
| 1: Domain, ports, use cases | ✅ | + unsafe identifier tests |
| 2: PokeAPI client + cache | ✅ | Client built in `PokeApiConfig`; mutation check on cache test |
| 3: Mapper + gateway adapter | ✅ | Mapper builds the tree; shared in-flight semaphore (10) |
| 4: Web layer + errors | ✅ | Error `Cache-Control` assertion reworded; `INVALID_PARAMETER` handler |
| 5: E2E + Docker smoke | ✅ | Local `HttpServer` stand-in instead of `MockRestServiceServer` |

Verified: `./gradlew clean build` green x3 (132 tests, 0 failures). Docker + real PokeAPI: list cold 0.52 s -> warm 4 ms, eevee detail cold 0.17 s -> warm 4 ms, 8 evolution branches, form `10001` works, 404/400 bodies share one shape, 0 errors in app logs.

### Notes for Plan C
- Sync should reuse `PokeApiGateway.findDetail` (single mapping point) and store the `PokemonDetail` fields; local-only fields (nickname, region, tags) must survive re-sync.
- `PokemonSummary`/`PokemonDetail` are domain records without ids from our DB: Plan C needs its own persisted aggregate that references the PokeAPI id.
