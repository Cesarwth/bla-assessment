# Code Review Command

Review the current changes against the assessment criteria. Keep it focused: only report critical or major issues.

## Arguments

$ARGUMENTS

Optional: `--no-tests` skips the test review.

## Steps

1. Get the changes: `git diff HEAD --stat` then `git diff HEAD` (if not a git repo or no diff, review the files named in `plan.md`; if none, say there is nothing to review).
2. Launch these reviewers IN PARALLEL (one message, multiple Agent calls, subagent_type `general-purpose`), each given the changed files and diff:
   - **Architecture** — `infrastructure -> application -> domain` dependency rule; no Spring/JPA/Jackson imports in `domain` or `application`; controllers use use-case interfaces only; no JPA entities or PokeAPI DTOs exposed over REST; ports/adapters used correctly.
   - **Correctness & API** — each user story requirement met (US-01..US-04, auth); status codes (400/401/403/404/409); consistent error body; validation; PokeAPI failures/timeouts/null fields handled; idempotent sync; caching correct.
   - **Security** — public vs protected routes, JWT handling, BCrypt, no hardcoded secrets or credentials outside documented seed data, input validation, no sensitive data in logs or error responses.
   - **Tests** (skip with `--no-tests`) — tests exist for every use case, adapter, mapper, controller, and exception handler; TDD evidence; no real PokeAPI calls; tests assert behavior, not mocks.
3. Run `./gradlew test` and include the result.
4. Merge into one report:

```markdown
# Code Review — <date>

| Area | Score (A-F) | Critical | Major |
| ---- | ----------- | -------- | ----- |

## 🔴 Critical (must fix)
| # | File:Line | Issue | Fix |

## 🟡 Major (should fix)
| # | File:Line | Issue | Fix |

## Positives
- ...

## Verdict
✅ APPROVED  |  🔴 ISSUES FOUND (<X> critical, <Y> major)
```

Scoring: A none, B minor only, C 1-2 major, D multiple major or 1 critical, F multiple critical. Overall = lowest. APPROVED only with zero critical and zero major.

Skip style nitpicks. Do not modify code; report only.
