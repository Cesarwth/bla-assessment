# Research: Pokemon API — full exercise (greenfield)

**Story/Problem:** Ballast Lane Java exercise: US-01 list, US-02 detail, US-03 sync, US-04 local update, auth API, frontend, Docker, README, GenAI section.
**Date:** 2026-10-05

## Problem Statement

Build a Spring Boot REST API (Java 21, Gradle, Docker) that reads from PokeAPI, replicates Pokemon into a relational DB, and lets authenticated users edit local copies. Evaluation weights Clean Architecture, TDD, code quality, working demo, and a clean frontend.

## Affected Code

| File | Layer | Role | Key Symbols |
| ---- | ----- | ---- | ----------- |
| `build.gradle` | build | Boot 4.1.1, Java 21 toolchain; only `spring-boot-starter` + test | no web/JPA/security deps yet |
| `settings.gradle` | build | `rootProject.name` only | no toolchain resolver |
| `src/main/java/com/ballastlane/assessment/BlaAssessmentApplication.java` | boot | main class | — |
| `src/main/resources/application.properties` | config | only `spring.application.name` | — |
| `src/test/.../BlaAssessmentApplicationTests.java` | test | context-loads smoke test | — |
| `CLAUDE.md` | docs | architecture rules, stack | — |

Everything else (domain, ports, adapters, Docker, frontend) does not exist yet.

## Data Flow (target seams, from PokeAPI contract)

1. GET `/api/v1/pokemon?page&size` -> list use case -> `PokeApiGateway` port -> PokeAPI adapter
2. List needs 1 call `/pokemon?limit&offset` (names+urls only) + **N detail calls** `/pokemon/{id}` — *why: list endpoint carries no sprite/type/weight/abilities*
3. Detail needs `/pokemon/{id}` + `/pokemon-species/{id}` (description, genus, chain URL) + `/evolution-chain/{id}`: 3 sequential-ish calls
4. Sync: gateway -> domain `Pokemon` -> `PokemonRepository` port -> JPA adapter (upsert by PokeAPI id)
5. Update: controller -> use case (validate) -> repository port; 404 when missing

## External Contract (PokeAPI, verified live 2026-10-05)

- List: `{count:1351,next,previous,results:[{name,url}]}`; id is the last path segment of `url`.
- `/pokemon/25`: `id, name, weight(=60, hectograms -> 6.0 kg), height(=4, decimeters), types[].type.name, abilities[].ability.name, stats[].stat.name + base_stat (hp, attack, defense, special-attack, special-defense, speed), sprites.front_default, sprites.other["official-artwork"].front_default, species.url`.
- `/pokemon-species/25`: `genera[]` (`genus` "Mouse Pokémon", filter `language.name=="en"`), `flavor_text_entries[]` (33 English entries; text contains `\n` and `\f` control chars that need cleaning), `evolution_chain.url`.
- `/evolution-chain/10`: recursive `chain{species.name, evolution_details[], evolves_to[]}`. Pichu -> Pikachu (level-up, `min_level` null: happiness) -> Raichu (use-item thunder-stone, detail duplicated). Branching chains exist (e.g. Eevee).
- Unknown name -> HTTP 404. Sprites/artwork can be null for newer Pokemon (not verified per id; treat as nullable).
- PokeAPI is public, no key; asks consumers to cache aggressively (fair-use, rate limits not formally published).

## Current Behavior

Greenfield: app starts with an empty Spring context. No endpoints, no DB, no security.

## Constraints

- **Environment:** only JDK 17 installed locally; build requires 21 (toolchain). Without a resolver `./gradlew test` fails. Docker 28 present, Node 22 present.
- **Database:** PostgreSQL (compose) + H2 (tests); Flyway migrations; need primary entity (Pokemon) and secondary collection (users) with unique PK and 2+ attributes.
- **APIs:** consistent response/error structure; standard verbs; versioned `/api/v1`.
- **Security:** public: `/api/v1/auth/**`, `GET /api/v1/pokemon/**`, health/docs; protected: sync + update (+ delete). Secrets via env vars; BCrypt; demo credentials only as documented seed.
- **Architecture:** `infrastructure -> application -> domain`; domain/application free of Spring/JPA/Jackson; enforced with ArchUnit.
- **Spring Boot 4.x:** modular starters (webmvc, data-jpa, security, validation, cache, flyway), Jackson 3 (`tools.jackson`), Spring Security 7, relocated test slice packages (`@WebMvcTest`), `@MockitoBean` instead of `@MockBean`.

## Existing Patterns

None in code. Patterns come from `CLAUDE.md` (ports/adapters, use cases as plain Java wired in `infrastructure/config`, one `@RestControllerAdvice`, records for DTOs).

## Risks

- **Regression:** none yet; the ArchUnit test becomes the regression guard for layering.
- **Data integrity:** repeated sync must upsert on PokeAPI id (unique constraint) — duplicate rows otherwise. Local edits must not be overwritten by a re-sync (needs a rule: sync refreshes PokeAPI-owned fields, keeps local custom fields).
- **Performance:** list page = 1 + N upstream calls; unbounded `size` would hammer PokeAPI. Needs max page size, cache, bounded concurrency.
- **Coupling:** PokeAPI JSON shape leaking into domain; `weight` unit semantics (hg) leaking into UI.
- **Test blindspot:** tests must never hit real PokeAPI; Boot 4 test tooling compatibility (WireMock vs `MockRestServiceServer`) is unverified.
- **Challenge (should this exist?):** list and detail are pure pass-through to PokeAPI while sync stores the same data locally — two sources for the same Pokemon. Risk of duplicated mapping code (DRY) unless one domain model + one mapper serves both.

## Test Coverage

- **Covered:** context-loads smoke test only.
- **Gaps:** everything.
- **Test patterns:** JUnit 5 via `spring-boot-starter-test`; need to add web/JPA/security/ArchUnit test deps.

## Decisions (resolved with the user)

1. Java 21 via Foojay toolchain resolver in `settings.gradle` (auto-download JDK).
2. "Category" in US-01 = Pokemon **type(s)**; genus shown only in detail.
3. PostgreSQL in docker-compose; H2 for tests; Flyway.
4. Sync by id or name plus range sync, idempotent upsert; update edits local/custom fields (nickname, region, tags) and basic data; protected by JWT.

## Open Questions

None — ready for planning.
