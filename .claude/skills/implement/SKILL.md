---
name: implement
description: MUST BE USED when executing plan.md phase by phase in this Java 21 / Spring Boot / Gradle project. Use PROACTIVELY when someone says "build this", "implement", "start coding", "execute the plan", "implement phase N". Enforces strict TDD (red -> green -> refactor), Clean Architecture dependency rules, runs ./gradlew tests after each phase, then hands off to /code-review.
---

# Implement Skill (BLA Pokemon API)

Execute `plan.md` precisely, test-first, one phase at a time.

## Workflow

### 1. Load Context

1. Read `CLAUDE.md`.
2. Read `plan.md`. If missing, STOP: "No plan document found. Run `/plan` first."
3. Note phases, which are marked `✅ Complete`, and the Final Verification.

### 2. Pre-Flight

1. **Git:** if not a git repo, tell the user and offer `git init` (the assessment needs a public repo). If on `main`/`master` with other work, suggest a branch like `feature/us-01-pokemon-list`. Never push or create the remote without being asked.
2. **Baseline:** run `cd backend && ./gradlew test` (and `cd frontend && npm test` when working on the frontend). If it fails before any change, STOP and report failures.
3. **Resume:** skip phases already ✅.
4. Print:

```
📋 Plan: <title>
📊 Phases: <total> (<done> done)
🌿 Branch: <name>
✅ Baseline: passing

Starting Phase <n>...
```

### 3. Execute Each Phase (TDD loop)

Announce: `Phase <N>/<total>: <name> — Goal: <goal>`.

1. **RED** — write the tests from "Tests to Write First". Run them; confirm they fail for the right reason (compile errors for missing classes count as the first red; then add empty skeletons and re-run to see a real assertion failure).
2. **GREEN** — write the minimum production code to pass. Follow the File Changes table and Implementation Notes.
3. **REFACTOR** — clean up with tests green. Run the phase verification command.

Code rules while implementing (see `CLAUDE.md`):

- Dependency direction `infrastructure -> application -> domain`. `domain`/`application` have no Spring, JPA, or Jackson imports. Wire use cases as beans in `infrastructure/config`.
- Constructor injection, `final` fields, records for DTOs/value objects, `Optional` not null.
- Request DTOs validated with Bean Validation; domain invariants enforced in the domain too.
- JPA entities and PokeAPI DTOs never cross into the web layer or domain; map at the adapter boundary.
- All errors via the shared `@RestControllerAdvice` with the consistent error body; correct status codes (400 / 401 / 403 / 404 / 409 / 502-504 for PokeAPI failures).
- PokeAPI client: timeouts, mapped errors, cached responses. Tests stub it with WireMock — never hit the real API.
- Secrets from env vars only; passwords BCrypt-hashed; no secrets in logs, code, or git.
- Mark deliberate simplifications `SHORTCUT: <ceiling> — upgrade when <trigger>`; never for validation, auth, or tests.
- Comments explain why, not what.

### 3a. Result

**Pass:**
```
✅ Phase <N>: PASSED
   Files changed: <list>
   Tests: <count> passing
```

**Fail:** read the output, find the root cause (common: missing `@Valid`, bean not wired, Flyway/H2 dialect mismatch, JPA lazy loading outside tx, Jackson naming of PokeAPI snake_case fields, WireMock stub mismatch, security filter blocking test requests), fix within phase scope, re-run. After 3 failed attempts STOP:

```
❌ Phase <N>: STUCK after 3 attempts
Error: <error>
Attempted fixes: <list>
Please review and advise.
```

### 3b. Record Progress

After each phase add to `plan.md`: `**Status:** ✅ Complete <date>` and `**Deviation:** ...` if any. If the plan has more than 3 phases, run `/compact` and re-read `plan.md` before the next phase.

### 4. Final Verification

1. Run the plan's Final Verification (default `cd backend && ./gradlew clean build`; include ArchUnit and coverage checks).
2. If the frontend changed: `cd frontend && npm test && npm run build` and check the browser console is free of warnings.
3. If Docker files changed: `docker compose build`.
4. `git status` for untracked files that belong in the commit.

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Implementation Complete
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Phases: <N>/<total>
Tests: <count> passing
Build: clean / <issues>
Deviations: <count or none>

Next: run /code-review, then commit.
```

Append an "Implementation Status" table to `plan.md` (phase, status, deviations).

## Key Rules

- Follow the plan. If it is wrong, note the issue and ASK before deviating.
- Never write production code without a failing test first.
- Run tests after EVERY phase; never batch phases.
- No refactors, features, or files outside the plan unless a failure requires it — document the deviation.
- Do not change the architecture mid-implementation; stop and ask.
- If stuck, say so. No speculative fixes.
- Do not commit, push, or publish without being asked.

## Definition of Done

- All phases ✅ in `plan.md`; full build and tests pass; deviations documented
- User told to run `/code-review`
