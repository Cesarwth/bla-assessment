---
name: plan
description: MUST BE USED when turning research.md into an implementation plan for this Java 21 / Spring Boot / Gradle project. Use PROACTIVELY when someone says "plan this", "break this down", "what are the steps", "design the solution", "how should we build US-0x". Produces plan.md with max 5 TDD phases ordered along Clean Architecture layers (domain -> application -> infrastructure), each with file changes, test-first test list, and a runnable Gradle verification command.
---

# Plan Skill (BLA Pokemon API)

Turn `research.md` into a phased, test-first `plan.md`. A plan is a specification; no code.

## Workflow

### 1. Load Context

1. Read `CLAUDE.md`.
2. Read `research.md`. If missing, STOP: "No research document found. Run `/research` first."

### 2. Check for Blockers

If **Open Questions** is not "None — ready for planning", print them and STOP:

```
⛔ Cannot plan — the following questions need answers first:
1. <question>
Resolve these in research.md, then run /plan again.
```

### 3. Evaluate Scope

Count how many are true:

1. Spans multiple capabilities (e.g. auth AND sync AND listing)
2. Needs 6+ phases
3. External dependency needing separate setup (new infra, third-party beyond PokeAPI)
4. 2+ new aggregates/tables
5. 3+ new or changed endpoints
6. Backend AND frontend both with real complexity

- **3+ true -> too big for one plan.** Print `📐 Scope Assessment: EPIC (<N>/6)` with the true items and split the work into per-story plans (e.g. Plan A: auth + base setup, Plan B: US-01/02, Plan C: US-03/04, Plan D: frontend, Plan E: Docker/README/GenAI doc). Ask which to plan first, then plan only that one.
- **2 or fewer -> TASK.** Print `📐 Scope Assessment: TASK (<N>/6)` and continue.

### 3.5 Should We Build This At All?

Is the smallest correct diff zero? Does an existing port/use case/adapter already cover most of it? Can it be a deletion? If the work is unnecessary, STOP and say so with evidence. This never licenses skipping validation, auth checks, or tests.

### 4. Design the Approach

1. Follow the architecture in `CLAUDE.md`. No new libraries or patterns unless research identifies a gap.
2. **Order phases inside-out:** domain model + rules -> use case + ports (mocked) -> adapters (persistence / PokeAPI client) -> web layer + error handling + security -> wiring/e2e. Each phase builds on verified work.
3. **Test-first per phase:** list the tests to write BEFORE the production files. Choose the level:
   - Domain / use case: unit (JUnit 5 + Mockito on ports only)
   - Controller: `@WebMvcTest` (status codes, validation 400, 404, 401/403)
   - Persistence adapter: `@DataJpaTest`
   - PokeAPI adapter: WireMock
   - Cross-cutting flow: `@SpringBootTest`
4. Plan the edge cases explicitly: PokeAPI 404/timeout, null sprites, duplicate sync (idempotent upsert), malformed payload, unknown id, expired/invalid JWT, pagination bounds.

### 5. Write plan.md

````markdown
# Plan: <title>

**Research:** research.md
**Date:** <today>
**Scope:** Task (<N>/6)
**Estimated Phases:** <count>

## Summary
<2-3 sentences: what and the approach>

## Phase 1: <name>

**Goal:** <what is true when done>

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |

### File Changes
| File | Layer | Action | What Changes |
| ---- | ----- | ------ | ------------ |

### Implementation Notes
<exact classes/methods, patterns to follow, edge cases, ports touched. Precise enough for an agent to execute.>

### Verification
```bash
./gradlew test --tests '<pattern>'
```
**Expected result:** <specific pass criteria>

---
(Repeat; max 5 phases.)

## Final Verification
```bash
./gradlew clean build
docker compose build   # if Docker files changed
```

## Rollback Plan
<git revert / Flyway migration undo / config toggles>

## Out of Scope
<noted, not planned>
````

## Key Rules

- NO code in the plan.
- Max 5 phases; each completable in one session; each has runnable verification and expected result.
- Every phase lists tests before production files (TDD).
- Be precise: `PokemonSyncService.sync(int id)` in `application/usecase/...`, not "update the service".
- The domain and application layers must stay free of Spring/JPA imports; a phase that would break this is wrong.
- Mark intentional simplifications with a `SHORTCUT:` comment note (ceiling + upgrade trigger); never for validation, auth, or tests.
- Flag anything touching security or secrets: env vars only, BCrypt, no hardcoded credentials except documented seed demo users.

## Definition of Done

- Scope assessment printed; plan.md has <= 5 phases with tests, file changes, notes, verification
- Rollback and Out of Scope filled
- Tell the user: "Plan ready. Review `plan.md`, then run `/implement`."
