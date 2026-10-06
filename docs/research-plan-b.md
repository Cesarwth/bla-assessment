# Research: Plan B — Pokemon list (US-01) and detail (US-02) from PokeAPI

**Story/Problem:** US-01 paginated list (sprite, category, mass, abilities) with caching; US-02 detail (image, stats, description, evolution chain).
**Date:** 2026-10-05
**Builds on:** Plan A (auth, error model, security, Docker) — see `docs/plan-a-foundation.md`. Overall research kept in `docs/research-overall.md`.

## Problem Statement

Expose two public read endpoints that aggregate several PokeAPI calls into clean domain models, behind a port so the domain never sees PokeAPI JSON. Responses must be cached because a single list page costs many upstream calls.

## Affected Code

| File | Layer | Role | Key Symbols |
| ---- | ----- | ---- | ----------- |
| `infrastructure/security/SecurityConfig.java:35` | infra | already permits `GET /api/v1/pokemon` and `/**` | route rules (no change needed) |
| `infrastructure/web/GlobalExceptionHandler.java` | infra | single error mapper; needs new domain exceptions | `unexpected()` falls back to 500 |
| `infrastructure/config/UseCaseConfig.java` | infra | where use cases are wired | `@Bean` methods |
| `ArchitectureTest.java` | test | layering guard that new code must satisfy | 3 rules |
| `application.yml` | config | needs `app.pokeapi.*` (base URL, timeouts, cache TTL) | — |
| `build.gradle` | build | needs `starter-cache`, `caffeine` | — |

New (does not exist): `domain` models, `PokeApiGateway` port, list/detail use cases, PokeAPI client + DTOs + mapper, cache config, `PokemonController`.

## Data Flow (target)

1. GET `/api/v1/pokemon?page&size` -> `PokemonController` -> `ListPokemonUseCase` (validates page/size) -> `PokeApiGateway.findPage` -> adapter
2. Adapter: 1 call `/pokemon?limit&offset` (names+urls) -> **N detail calls** `/pokemon/{id}` -> mapper -> `PokemonSummary` — *why: the list endpoint has no sprite/type/weight/abilities*
3. GET `/api/v1/pokemon/{idOrName}` -> `GetPokemonDetailUseCase` -> gateway -> `/pokemon/{x}` then `/pokemon-species/{id}` then `/evolution-chain/{id}` — *why sequential: species URL comes from the pokemon payload, chain URL from species*
4. Mapper converts units and cleans text; web layer maps domain -> response DTO.

## External Contract (verified live 2026-10-05)

- **List:** `{count:1351,next,previous,results:[{name,url}]}`; id = last path segment of `url`. Includes alternate forms (ids > 10000, e.g. `deoxys-attack`, `*-mega`), so ids are NOT contiguous. `offset` beyond count returns 200 with empty `results`.
- **Detail input:** by id or lowercase name; `Pikachu` (uppercase) works, `pokemon/0` -> 404, trailing space -> **400**, unknown name -> 404.
- **Units:** `weight` in hectograms (60 -> 6.0 kg), `height` in decimeters.
- **Sprites:** `sprites.front_default` and `sprites.other["official-artwork"].front_default` present on 5/5 sampled (incl. forms); still treat as nullable.
- **Forms:** `pokemon/10001` (deoxys-attack) -> `species.url` = `/pokemon-species/386/` (species id != pokemon id), so species must be resolved from the pokemon payload, never by reusing the id.
- **Species:** `genera[]` (filter en), `flavor_text_entries[]` (28-33 English entries, text has `\n` and `\f`), `evolution_chain.url`.
- **Evolution chain:** recursive `chain{species{name,url},evolution_details[],evolves_to[]}`. Eevee (chain 67) has **8 branches**; details mix triggers (`use-item` + item, `level-up` + `min_happiness` + `time_of_day`), some with `min_level` null and empty details; duplicates inside `evolution_details` occur.
- **Stats:** `stats[].stat.name` + `base_stat` (hp, attack, defense, special-attack, special-defense, speed).
- **Latency:** 1 list + 12 sequential detail calls = **1.09 s** (~85 ms each) -> a cold page is ~1 s sequentially; parallel fetch + cache is worthwhile.
- **Etiquette:** public, no key, asks for caching. A default Python UA is blocked (403); Java UAs are fine; send a descriptive `User-Agent` anyway.

## Current Behavior

Only auth exists. `GET /api/v1/pokemon` is public in `SecurityConfig` but no controller handles it (returns 404 via the shared handler).

## Constraints

- **Architecture:** `domain`/`application` free of Spring/Jackson/HTTP; ArchUnit enforces it. PokeAPI DTOs and cache annotations live in `infrastructure`.
- **Spring Boot 4:** `RestClient` is the HTTP client; Jackson 3 (`tools.jackson.databind`) but Jackson annotations remain `com.fasterxml.jackson.annotation` (to verify); `@WebMvcTest` is in `org.springframework.boot.webmvc.test.autoconfigure`; `@MockitoBean` for mocks.
- **Errors:** every failure must use `ErrorResponse`. Unmapped exceptions become 500, so type-mismatch on `?size=abc` (`MethodArgumentTypeMismatchException`, not a Spring `ErrorResponse`) would wrongly be 500 today.
- **Security:** read endpoints stay public; no PII involved; outbound calls only to the configured PokeAPI base URL (SSRF: never follow arbitrary URLs from payloads, build paths from validated ids).
- **Upstream abuse:** unbounded `size` multiplies upstream calls -> cap page size.
- **Testing:** tests never call the real PokeAPI.

## Existing Patterns

Ports in `application/port/{in,out}`, plain-Java use cases wired in `UseCaseConfig`, records for DTOs, one `@RestControllerAdvice`, `@WebMvcTest` + `@Import(SecurityConfig, JwtConfig, GlobalExceptionHandler)` for controller tests, H2 per-context for integration tests (`application-test.yml`).

## Risks

- **Regression:** a new `GlobalExceptionHandler` branch could change existing auth error codes; existing tests cover them.
- **Data integrity:** none (read-only). The domain `Pokemon` shape chosen here is reused by sync/persistence in Plan C — a bad shape propagates.
- **Performance:** N+1 upstream calls per page; cold cache latency; thundering herd if many users request the same cold page.
- **Coupling:** PokeAPI JSON leaking into domain; `weight` unit; evolution detail variety (triggers) leaking into UI.
- **Availability:** PokeAPI down/slow must not hang threads: need timeouts and a mapped 502/504.
- **Test blindspot:** mapper edge cases (null sprite, missing English flavor text, branching chain, duplicated details) are invisible without explicit fixtures; cache correctness (hit/miss, no caching of failures/404s) needs its own test.
- **Challenge (should this exist?):** list/detail are pass-through while Plan C stores the same data. Chosen stance: keep the gateway as the single mapping point and have sync reuse it (DRY).

## Test Coverage

- **Covered:** nothing for this area; auth tests unaffected.
- **Gaps:** everything in Plan B.
- **Test patterns to follow:** unit tests with Mockito on ports; `MockRestServiceServer` bound to `RestClient.Builder` for the client (replaces the WireMock idea in `CLAUDE.md`; fewer deps, Boot 4 safe); fixtures captured from the real API under `src/test/resources/pokeapi/`.

## Open Questions

None — ready for planning. (Defaults assumed and listed in plan.md: page size default 12, max 50, 0-based pages; description language English; evolution nodes carry id/name/sprite by URL pattern.)
