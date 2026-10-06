# Plan A: Foundation + Auth + Docker

**Research:** research.md
**Date:** 2026-10-05
**Scope:** Epic overall (5/6 indicators) -> split into plans A-E. This file details **Plan A** only.
**Estimated Phases:** 5

## Roadmap (one plan each, run research-lite + /implement per plan)

| Plan | Content | Stories |
| ---- | ------- | ------- |
| **A** | Build guardrails, auth (register/login/JWT), error model, Postgres + Docker | Auth, Docker |
| B | PokeAPI gateway + cache; list and detail endpoints | US-01, US-02 |
| C | Pokemon persistence, sync (id/name/range, upsert), protected update/delete | US-03, US-04 |
| D | React frontend (responsive list, detail with evolution, login, edit form) | Frontend |
| E | Seed data, README, GenAI document, coverage, final polish | Delivery |

## Design principles applied (what to say in the interview)

**Clean Architecture** — dependencies point inward. `domain` (User, rules) knows nothing; `application` owns use cases and the ports it needs (`UserRepository`, `PasswordHasher`, `TokenIssuer`); `infrastructure` implements them (JPA, BCrypt, JWT, MVC). Frameworks are details at the edge. An ArchUnit test proves it.

**SOLID**
- **S**: one use case per class (`RegisterUser`, `AuthenticateUser`); controllers only translate HTTP.
- **O**: new behavior = new adapter/use case (e.g. swap JWT for another `TokenIssuer`) without editing use cases.
- **L**: any `UserRepository` implementation (JPA, in-memory fake in tests) is substitutable.
- **I**: small ports (`PasswordHasher` has `hash`/`matches`, nothing more) instead of one fat service.
- **D**: use cases depend on port interfaces; Spring injects implementations via config.

**DRY** — one error model and one `GlobalExceptionHandler`; one mapper per boundary; one domain `Pokemon` later shared by list, detail, and sync (no duplicate mapping). Do not over-abstract: duplicate once, extract on the third use.

## Phase 1: Build guardrails and architecture test

**Goal:** `./gradlew test` runs on JDK 21 (auto-provisioned) with all needed deps and a failing-if-violated layering rule.

**Status:** ✅ Complete 2026-10-05
**Deviation:** JWT via `spring-boot-starter-oauth2-resource-server` (Nimbus) instead of jjwt; this removes the need for a custom `JwtAuthenticationFilter` in Phase 4. Test config is `application-test.yml` + `@ActiveProfiles("test")`.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Unit | `src/test/java/com/ballastlane/assessment/ArchitectureTest.java` | `domainDoesNotDependOnOtherLayers`, `applicationDoesNotDependOnInfrastructure`, `domainAndApplicationAreFrameworkFree` | ArchUnit rules from CLAUDE.md hold |
| Integration | existing `BlaAssessmentApplicationTests` | `contextLoads` (H2) | app boots with new deps |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `settings.gradle` | build | Modify | add `org.gradle.toolchains.foojay-resolver-convention` plugin |
| `build.gradle` | build | Modify | add starters: webmvc, validation, data-jpa, security, flyway (+ flyway-database-postgresql); runtime: postgresql, h2 (test); jjwt (api/impl/jackson) or `spring-security-oauth2-jose`; test: security-test, webmvc-test, ArchUnit |
| `src/main/resources/application.yml` | config | Create (replace .properties) | datasource from env (`DB_URL`, `DB_USER`, `DB_PASSWORD`), `JWT_SECRET`, `JWT_TTL`; profile `test` with H2 |
| `src/main/java/com/ballastlane/assessment/{domain,application,infrastructure}/package-info.java` | all | Create | document layer responsibilities |

### Implementation Notes
Pin versions only where the Boot BOM lacks them (jjwt, ArchUnit). Verify Boot 4 artifact names against the Boot 4.1 docs before adding (modular starters). `ArchitectureTest` uses `ClassFileImporter` on `com.ballastlane.assessment`; `domain` and `application` must not depend on `org.springframework..`, `jakarta.persistence..`, `tools.jackson..`, or `..infrastructure..`.

### Verification
```bash
./gradlew test --tests '*ArchitectureTest' --tests '*BlaAssessmentApplicationTests'
```
**Expected result:** BUILD SUCCESSFUL on JDK 21; ArchUnit rules pass (vacuously until classes exist).

---

## Phase 2: Auth domain and use cases (pure Java)

**Goal:** register and authenticate rules are implemented and tested with no framework.

**Status:** ✅ Complete 2026-10-05
**Deviation:** added `EmailAlreadyExistsException` (+ test) so a duplicate email returns 409 instead of a DB constraint 500; `User.toString()` redacts the hash; `AuthToken` record lives in `application.port.out`.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Unit | `.../domain/UserTest.java` | `rejectsBlankUsername`, `rejectsInvalidEmail` | domain invariants throw `InvalidUserException` |
| Unit | `.../application/usecase/RegisterUserTest.java` | `registersNewUserWithHashedPassword`, `failsWhenUsernameTaken`, `failsOnWeakPassword` | hash stored (never raw), `UsernameAlreadyExistsException`, `WeakPasswordException` |
| Unit | `.../application/usecase/AuthenticateUserTest.java` | `returnsTokenOnValidCredentials`, `failsOnUnknownUserSameAsWrongPassword` | `InvalidCredentialsException` for both cases (no user enumeration) |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `domain/User.java` | domain | Create | record/class: id, username, email, passwordHash, role; invariants |
| `domain/Role.java` | domain | Create | enum USER, ADMIN |
| `domain/exception/*.java` | domain | Create | `InvalidUserException`, `UsernameAlreadyExistsException`, `InvalidCredentialsException`, `WeakPasswordException` |
| `application/port/in/RegisterUserUseCase.java`, `AuthenticateUserUseCase.java` | application | Create | inbound ports + command/result records |
| `application/port/out/UserRepository.java`, `PasswordHasher.java`, `TokenIssuer.java` | application | Create | outbound ports |
| `application/usecase/RegisterUser.java`, `AuthenticateUser.java` | application | Create | implementations |

### Implementation Notes
Plain constructors, no annotations. Password policy (min 8, letter+digit) lives in the use case/domain, not in Bean Validation, so rules hold regardless of entry point. Both failure modes of authentication throw the same exception.

### Verification
```bash
./gradlew test --tests 'com.ballastlane.assessment.domain.*' --tests 'com.ballastlane.assessment.application.*'
```
**Expected result:** all unit tests pass; ArchitectureTest still green.

---

## Phase 3: User persistence adapter and migration

**Goal:** users persist in a relational table behind the `UserRepository` port.

**Status:** ✅ Complete 2026-10-05
**Deviation:** `DemoUserSeeder` is a plain class (unit-tested); it is registered as a bean in `UseCaseConfig` in Phase 4 behind `app.seed.demo-user.enabled`, because it needs the `PasswordHasher` adapter. Added `UserPersistenceMapperTest` and a duplicate-email constraint test.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Integration | `.../infrastructure/persistence/JpaUserRepositoryAdapterTest.java` (`@DataJpaTest`) | `savesAndFindsByUsername`, `uniqueUsernameConstraint`, `mapsRoundTripWithoutLeakingEntity` | adapter maps domain <-> entity; unique index enforced |
| Integration | `.../infrastructure/config/DemoUserSeederTest.java` | `seedsDemoUserOnlyWhenEnabled`, `isIdempotent` | demo user created once, hashed |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `src/main/resources/db/migration/V1__create_users.sql` | infra | Create | `users(id PK, username UNIQUE, email UNIQUE, password_hash, role, created_at)` |
| `infrastructure/persistence/UserEntity.java`, `SpringDataUserRepository.java`, `JpaUserRepositoryAdapter.java`, `UserPersistenceMapper.java` | infra | Create | JPA entity, Spring Data repo, adapter implementing `UserRepository`, mapper |
| `infrastructure/config/DemoUserSeeder.java` | infra | Create | `ApplicationRunner` creating demo user via `RegisterUserUseCase` when `app.seed.demo-user.enabled=true` |
| `application.yml` | config | Modify | `app.seed.demo-user.{username,password}` for local/demo only |

### Implementation Notes
Seeding through the use case guarantees the password is BCrypt-hashed at runtime, so no hash or secret is committed. Demo credentials are documented in the README (Plan E) as intentional seed data. Entities stay in `infrastructure/persistence`; `ddl-auto=validate` so Flyway owns the schema.

### Verification
```bash
./gradlew test --tests '*persistence*' --tests '*DemoUserSeederTest'
```
**Expected result:** tests pass against H2 with the same Flyway migration.

---

## Phase 4: Web API, JWT security, error model

**Goal:** `/api/v1/auth/register` and `/login` work; protected vs public routes enforced; all errors share one shape.

**Status:** ✅ Complete 2026-10-05
**Deviation:** no custom `JwtAuthenticationFilter` (resource-server handles bearer tokens); added `JwtConfig`, `JwtProperties` (fail-fast on weak/missing secret), `ApiSecurityErrorHandler` (401/403 JSON from the filter chain), `DemoUserProperties` wiring in `UseCaseConfig`, CORS config + test, and `JwtPropertiesTest`/`BCryptPasswordHasherTest`. `GlobalExceptionHandler` also keeps Spring MVC statuses (404/405/415) instead of turning them into 500.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Integration | `.../infrastructure/web/AuthControllerTest.java` (`@WebMvcTest`) | `register201`, `register400OnInvalidBody`, `register409OnDuplicate`, `login200ReturnsToken`, `login401OnBadCredentials` | statuses and JSON bodies |
| Integration | `.../infrastructure/security/SecurityConfigTest.java` | `publicRouteOpen`, `protectedRoute401WithoutToken`, `protectedRoute401WithExpiredToken`, `protectedRoute200WithValidToken` | route rules and JWT validation |
| Unit | `.../infrastructure/security/JwtTokenIssuerTest.java` | `issuesAndParsesToken`, `rejectsTamperedToken`, `rejectsExpired` | signing, expiry |
| Unit | `.../infrastructure/web/GlobalExceptionHandlerTest.java` | `mapsDomainExceptionsToStatuses`, `neverLeaksStackTrace` | single `ErrorResponse(timestamp,status,code,message,fieldErrors)` |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `infrastructure/web/AuthController.java` | infra | Create | `POST /api/v1/auth/register`, `/login`; calls use case ports |
| `infrastructure/web/dto/{RegisterRequest,LoginRequest,AuthResponse,ErrorResponse}.java` | infra | Create | records with Bean Validation (shape only) |
| `infrastructure/web/GlobalExceptionHandler.java` | infra | Create | `@RestControllerAdvice` for domain exceptions, validation (400), generic (500) |
| `infrastructure/security/{SecurityConfig,JwtTokenIssuer,JwtAuthenticationFilter,BCryptPasswordHasher}.java` | infra | Create | stateless chain, public/protected rules, adapters for `TokenIssuer` and `PasswordHasher` |
| `infrastructure/config/UseCaseConfig.java` | infra | Create | `@Bean` wiring of use cases to ports |

### Implementation Notes
Stateless sessions, CSRF off for token API, CORS limited to the frontend origin (env var). JWT secret from `JWT_SECRET` (fail fast if missing/short outside tests). Return the same 401 body for missing/invalid token. Do not log passwords or tokens. Route table lives only in `SecurityConfig` (single source).

### Verification
```bash
./gradlew test --tests '*AuthControllerTest' --tests '*SecurityConfigTest' --tests '*JwtTokenIssuerTest' --tests '*GlobalExceptionHandlerTest'
```
**Expected result:** all pass; ArchitectureTest still green.

---

## Phase 5: Docker and end-to-end smoke

**Goal:** `docker compose up --build` starts app + Postgres and a login flow works end to end.

**Status:** ✅ Complete 2026-10-05
**Deviation:** protected-route assertion uses an unmapped path (authenticated -> 404 from MVC, anonymous -> 401) because no protected controller exists until Plan C. Actuator starter was added in Phase 1.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| E2E | `.../AuthFlowIT.java` (`@SpringBootTest` + MockMvc, H2) | `registerThenLoginThenCallProtectedRoute` | full chain across layers, no mocks |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |
| `Dockerfile` | build | Create | multi-stage: `gradle`/JDK 21 build -> `eclipse-temurin:21-jre` runtime, non-root user, healthcheck |
| `docker-compose.yml` | build | Create | `app` + `postgres:16`, env from `.env`, healthcheck, `depends_on: service_healthy` |
| `.env.example` | build | Create | `DB_*`, `JWT_SECRET` placeholders (no real secrets) |
| `.dockerignore`, `.gitignore` | build | Create/Modify | exclude `build/`, `.gradle/`, `.env` |

### Implementation Notes
`.env` is git-ignored; compose reads `.env.example` values only as documented defaults for local demo. Spring actuator `health` (add starter) used by healthchecks. Image runs as non-root; no secrets baked in.

### Verification
```bash
./gradlew clean build
docker compose up --build -d && curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/actuator/health && docker compose down
```
**Expected result:** build green; health returns `200`; login with the demo user returns a JWT.

---

## Final Verification
```bash
./gradlew clean build
docker compose build
```

## Rollback Plan
All work is additive on a feature branch: `git revert` the phase commits. Schema rollback: drop the compose volume (`docker compose down -v`); V1 is the only migration.

## Out of Scope (covered by later plans)
PokeAPI client, cache, list/detail (B); Pokemon table, sync, update (C); frontend (D); README, GenAI doc, coverage gate, extra seed data (E). Refresh tokens, roles beyond USER/ADMIN, rate limiting on `/auth` (candidate extras).

## Implementation Status

**Completed:** 2026-10-05
**Branch:** main (new local repo, no commits yet)

| Phase | Status | Deviations |
| ----- | ------ | ---------- |
| 1: Build guardrails + ArchUnit | ✅ | JWT via oauth2-resource-server instead of jjwt |
| 2: Auth domain + use cases | ✅ | + EmailAlreadyExistsException, hash redacted in toString |
| 3: User persistence + migration | ✅ | Seeder wired in Phase 4 |
| 4: Web, security, error model | ✅ | No custom JWT filter; + CORS, JwtProperties fail-fast, security error handler |
| 5: Docker + e2e | ✅ | Protected-route check via unmapped path |

Verified: `./gradlew clean build` green x3 (63 tests, 0 failures); `docker compose up --build` on Postgres: health UP, demo login OK, 401/400/404 bodies share one shape.

### Deviations Detail

- **Test isolation bug found at final verification:** the shared in-memory H2 (`testdb`) let `AuthFlowIT` leak a committed user into `JpaUserRepositoryAdapterTest` depending on execution order. Fixed with one H2 database per Spring context (`jdbc:h2:mem:${random.uuid}`) in `application-test.yml`.
- **Demo credentials:** `DEMO_USER_PASSWORD` defaults to a documented demo value in `application.yml`; seeding is off unless `SEED_DEMO_USER=true` (compose enables it for the demo). Revisit in Plan E (README).
