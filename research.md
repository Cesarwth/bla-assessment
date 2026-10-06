# Research: Plan E — demo data, one-command start, README, GenAI document, publication

**Story/Problem:** the brief requires (a) a comprehensive README with environment setup and technical documentation, (b) the solution pre-populated with demonstration data and mock credentials, (c) a Dockerfile, (d) the GenAI exercise (prompt, output, validation/corrections/edge cases), (e) a public Git repository. Today the stack starts EMPTY (no Pokémon in the collection), needs manual `.env` steps, and the README is a stub. The owner also wants an admin to log in, basic data first, and to publish under a personal GitHub account.
**Date:** 2026-10-06
**Builds on:** Plans A-D2 (HEAD `beb77d4`).

## Problem Statement

Make the delivered repository usable by a stranger in two minutes: one command starts everything with accounts and a small, realistic collection already in place; the README explains how it works and why; the GenAI section answers the brief; and the history is published from the right identity without touching the company Git credentials.

## What the document requires (checked against the PDF)

- "Provide a README file containing environment setup procedures and technical documentation."
- "The solution must be pre-populated with relevant demonstration data." / "seeded data or mock credentials".
- "Supply a Dockerfile for containerized execution." (backend and frontend images exist; compose ties them together).
- GenAI: write the prompt for a task-management API (CRUD, task has title/description/status/due_date, tasks belong to a user), show the output code or a representative sample, describe how it was validated, corrected/improved, and how edge cases/auth/validation were handled.
- Public Git repository.

## Affected Code

| Piece | Today | Change |
| ----- | ----- | ------ |
| `infrastructure/config/DemoUserSeeder` | creates `demo` (USER) and `demo-admin` (ADMIN) from env passwords, only if `SEED_DEMO_USER=true` | keep; add a second seeder after it (ordering) |
| `docker-compose.yml`, `.env.example` | demo users seeded only when `.env` says so; no demo Pokémon | add `SEED_DEMO_DATA`, defaults that give a populated system |
| `scripts/smoke.sh` | checks the running stack | reused as the acceptance check |
| `README.md` | stub | full rewrite |
| `docs/` | plans and research | add `genai.md`; README links the plans as process evidence |
| git | 13 commits authored with the company e-mail, 4 stacked branches, no remote | local identity, re-author, linear `main`, remote, push |

## Constraints

- Demo data must work **offline and deterministically** (no PokeAPI call at startup), be **idempotent** (restarts must not duplicate or overwrite edits) and be **opt-in** by a flag: production-like runs start empty.
- Owners are users that exist only after the user seeder runs, so the Pokémon seeder runs after it and resolves owners by username; a missing owner must skip the entry with a log line, never crash the app.
- Clean Architecture: the seeder lives in `infrastructure/config`, uses ports (`PokemonRepository`, `UserRepository`) and the domain factory; no new dependencies from `domain`/`application` (ArchUnit guards it).
- Passwords are never defaults in code: demo credentials come from `.env` (copied from `.env.example`, which documents them as demo-only).
- Git: the company identity is the **global** config and must stay untouched; the personal identity goes in this repository's local config only. Pushing needs credentials for the personal account; which ones the machine has is unknown until checked.
- Public repository: no secrets, no company-internal references (checked: the only one was a line in `research.md`, removed).

## Existing Patterns

Seeders are plain `ApplicationRunner` classes wired in `UseCaseConfig` behind `@ConditionalOnProperty` and unit-tested with Mockito; properties are records under `app.seed.*`; IT tests use `@SpringBootTest` with a local PokeAPI stand-in.

## Risks

- **Idempotency:** a seeder that overwrites existing rows would erase users' edits on every restart; it must skip anything already stored.
- **Data realism:** a hand-written dataset can drift from the real API shape; it is a snapshot of real responses, loaded through the same JSON shape the API returns, and a test parses it.
- **History rewrite:** re-authoring commits changes every hash; safe only because nothing was pushed; a backup ref is kept until the result is verified.
- **Wrong identity on push:** SSH/keychain may authenticate as the company account; verify which account the credentials belong to before pushing, and never change global config.
- **README drift:** commands in the README must be run, not trusted; the smoke script is the executable proof.
- **GenAI document honesty:** it must describe what was really done (and the real errors found in AI output), not an idealized story; any code sample must compile and have tests.

## Test Coverage

- **Covered:** seeders for users; collection flows end to end.
- **Gaps:** demo data (parsing, ownership, idempotency, flag off by default).
- **Patterns:** Mockito unit tests for the seeder; a resource test that parses the real JSON; one `@SpringBootTest` showing the seeded system behaves (public list, owner/admin edit rules).

## Decisions (resolved with the user, 2026-10-06)

1. Vulnerabilities: fix those that matter — done: Vitest 5.0.3, `npm audit` 0.
2. The system must not start empty: an admin and a demo user exist, plus a basic collection to try; creating records comes after.
3. Complete README.
4. Publish to `https://github.com/Cesarwth/bla-assessment` (SSH `git@github.com:Cesarwth/bla-assessment.git`) as `cesarwth@gmail.com`, keeping the company global credentials untouched.
5. Run `/code-review` last.

## Open Questions

None — ready for planning.
