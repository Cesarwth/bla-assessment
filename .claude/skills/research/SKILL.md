---
name: research
description: MUST BE USED before planning or coding any user story or bug in this Java 21 / Spring Boot / Gradle project. Use PROACTIVELY when someone says "look into", "investigate", "understand how", "scope", "what does PokeAPI return for", or starts a user story (US-01..US-04, auth, sync, frontend). Produces research.md with affected code, PokeAPI contract findings, data flow across Clean Architecture layers, constraints, existing patterns, test coverage, risks, and open questions.
---

# Research Skill (BLA Pokemon API)

Understand the problem space BEFORE designing or coding. Output is `research.md` in the project root, which feeds `/plan`.

## When to Use

- Starting a user story (US-01 Enumeration, US-02 Detail, US-03 Sync, US-04 Update, Auth, Frontend, Docker, GenAI doc)
- Needing to know what PokeAPI returns for a field (sprite, types, weight, abilities, stats, flavor text, evolution chain)
- Investigating a bug or unexpected behavior in existing code

## Workflow

### 1. Read Context

Read `CLAUDE.md` (architecture rules, stack, TDD policy) and the assessment PDF requirements it summarizes. The codebase may be nearly empty early on; that is expected — then research focuses on external contracts and decisions.

### 2. Explore with Subagents

Spawn Explore subagents (parallel, single message). Each returns ONLY a concise summary — never full file dumps.

**Subagent 1 — Affected files:** Glob/Grep for classes, ports, controllers, entities, migrations, config related to the story. Return paths with a one-line role each. If nothing exists yet, say so and list which layer packages are empty.

**Subagent 2 — Data flow:** Trace (or, for greenfield, list the existing seams) controller -> use case -> port -> adapter -> DB / PokeAPI. Note layer boundaries crossed and where mapping happens (DTO <-> domain <-> JPA entity). Add a "why" only where intent is non-obvious.

**Subagent 3 — Test coverage:** Find tests under `backend/src/test/java` (or `frontend/src`) for the area (unit, `@WebMvcTest`, `@DataJpaTest`, WireMock, ArchUnit). For each path, name the test that drives it from an entry point. A test that mocks the class under test is not coverage.

**Subagent 4 — Constraints:** Flyway migrations and schema, `application*.yml` / env vars, Gradle dependencies and versions (Spring Boot 4.x compatibility!), security rules (public vs protected routes), cache config, Docker files, TODOs.

**Subagent 5 — External contract (when PokeAPI is involved):** Use WebFetch against https://pokeapi.co/docs/v2 and sample endpoints (`/pokemon?limit=&offset=`, `/pokemon/{id|name}`, `/pokemon-species/{id}`, `/evolution-chain/{id}`). Return exact JSON paths needed (e.g. `sprites.other.official-artwork.front_default`, `types[].type.name`, `weight`, `abilities[].ability.name`, `stats[]`, `flavor_text_entries[]` filtered by language, `evolution_chain.url`), pagination shape, N+1 call cost for listing, rate-limit/caching guidance, and nullable fields.

### 3. Synthesize

Merge results, resolve contradictions, drop irrelevant detail.

### 4. Challenge Your Findings

- **Should this exist?** Already satisfied by current code or a simpler existing seam? Cheapest change wins.
- **Is this the real problem?** Upstream/downstream cause?
- **Is the flow complete?** Unhappy paths: PokeAPI down/slow/404, duplicate sync, concurrent update, invalid payload, expired JWT.
- **What is load-bearing but invisible?** Cache keys, DB unique constraints, security filter order.
- **What would break silently?** Missing null in PokeAPI sprites, language fallback for flavor text, pagination off-by-one.
- **Dependency rule:** does anything found already violate `infrastructure -> application -> domain`?

Findings become Risks or Open Questions.

### 5. Open Questions

Flag ambiguity and decisions needing the human (e.g. "sync one Pokemon or bulk range?", "what is 'category' — type or genus?"). These MUST be resolved before `/plan`.

### 6. Write research.md

```markdown
# Research: <title>

**Story/Problem:** <US-xx or request>
**Date:** <today>

## Problem Statement
<2-3 sentences>

## Affected Code
| File | Layer | Role | Key Symbols |
| ---- | ----- | ---- | ----------- |

## Data Flow
1. <entry> (`path:line`)
2. -> <use case> (`path:line`) — *why: ...*
3. -> <port/adapter> ...

## External Contract (PokeAPI)
<endpoints, JSON paths, nullable fields, call counts>

## Current Behavior
<what exists today; "greenfield" if empty>

## Constraints
- **Database:** <schema, migrations, unique keys>
- **APIs:** <REST contract, status codes, PokeAPI limits>
- **Config:** <env vars, profiles>
- **Security:** <public vs protected routes, JWT>
- **Architecture:** <dependency rule, ArchUnit>

## Existing Patterns
<conventions the implementation MUST match>

## Risks
- **Regression:** / **Data integrity:** / **Performance (N+1 to PokeAPI):** / **Coupling:** / **Test blindspot:**

## Test Coverage
- **Covered:** / **Gaps:** / **Test patterns:**

## Open Questions
1. ...
(or "None — ready for planning.")
```

## Key Rules

- Do NOT propose solutions or write source code. Research only.
- Every claim cites a file path and symbol/line, or a PokeAPI URL/JSON path.
- Unsure -> Open Questions. Never guess.
- Under 350 lines.
- Never call external services other than public PokeAPI docs/endpoints; never print secrets from env files.

## Definition of Done

- Subagent tasks returned; step 4 explicitly run
- Affected Code and Data Flow specific; PokeAPI contract captured when relevant
- Risks and Test Coverage filled; Open Questions listed or "None"
- Tell the user: "Research complete. Review `research.md`, resolve Open Questions, then run `/plan`."
