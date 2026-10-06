# Plan D2: React frontend (catalog, detail, collection, login)

**Research:** research.md
**Date:** 2026-10-06
**Scope:** part of an epic; runs after Plan D1 (needs `backend/` layout, `ownerId` and versioned PATCH). Copy this file to `plan.md` when D1 is done.
**Branch:** `feature/plan-d2-frontend`
**Estimated Phases:** 5

## Summary

A single-page React + TypeScript app in `frontend/`, organised by the same domains as the API (`auth`, `catalog`, `collection`). Server state lives in TanStack Query, auth in a small context, paging and filters in the URL. Tests use Vitest + React Testing Library + MSW so the real API client runs against mocked HTTP; console errors fail the tests. Docker serves the build with nginx and proxies `/api/` to the backend, so `docker compose up` gives the whole product on http://localhost:3000.

## Defaults assumed (change now if you disagree)

- **Stack:** React 19, Vite, TypeScript (strict), React Router, TanStack Query, CSS Modules with CSS variables (design tokens), no UI kit. Latest versions resolved in phase 1; pin whatever installs and runs together.
- **Look:** clean card layout, soft type-coloured chips, generous whitespace, light/dark through `prefers-color-scheme`, mobile first (1 column -> 2 -> 3-4), skeleton loaders, accessible focus states.
- **Pages:** `/` catalog (paginated cards, "go to name or id" box), `/pokemon/:idOrName` detail (artwork, stat bars, description, genus, branching evolution tree, "Add to my collection"), `/collection` (cards with nickname/region/tags, "mine" badge, import range 1-20 for logged users), `/collection/:id` (view + edit form for owner/admin, delete for admin), `/login`, `/register`.
- **Auth:** token in `sessionStorage` (survives reload, dies with the tab; documented trade-off), `uid`/`sub`/`role` read from the JWT payload; expired token -> logged out and redirected with a message. No refresh tokens.
- **Search:** no list search exists in the API; the box navigates to the detail by name/id (404 handled). Not building client-side filtering of 1300 items.
- **Ports:** UI on 3000 (nginx), API stays on 8080.

## Design principles (what to say in the interview)

- **Feature folders mirror backend domains:** each feature owns its pages, components, hooks and API calls; `shared/` has only the HTTP client and UI atoms. Dependencies point from features to `shared/`, never between features except through route-level composition.
- **State:** server state in TanStack Query (cache, dedupe, retries, `keepPreviousData` for paging), URL for page/size, a tiny auth context, local state only inside forms. No global store.
- **Single API client:** one place maps HTTP errors to `ApiError{status, code, message, fieldErrors}` using the backend's error shape; components never parse responses.
- **Untrusted text:** nicknames, tags and PokeAPI descriptions are rendered as text only.
- **Accessibility and no console noise are tested**, not hoped for.

## Phase 1: Scaffold, tooling, API client, auth, shell

**Goal:** `frontend/` builds, tests run, and a logged-in/out shell with routing works against MSW.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Setup | `src/test/setup.ts` | console `error`/`warn` throw | no warnings pass silently |
| Unit | `src/shared/api/client.test.ts` | `sendsBearerTokenWhenLoggedIn`, `mapsErrorBodyToApiError`, `mapsNetworkFailure`, `returnsNullOn204`, `redirectsToLoginOn401WithToken` | error contract of the backend |
| Unit | `src/features/auth/token.test.ts` | `readsUidSubAndRole`, `rejectsMalformedToken`, `detectsExpiry` | JWT payload parsing |
| Component | `src/features/auth/AuthContext.test.tsx` | `loginStoresSessionAndExposesUser`, `logoutClears`, `restoresFromSessionStorage`, `expiredTokenStartsLoggedOut` | auth state |
| Component | `src/app/App.test.tsx` | `rendersHeaderWithLoginLinkWhenAnonymous`, `showsUserMenuWhenLoggedIn`, `unknownRouteShowsNotFound`, `protectedRouteRedirectsToLogin` | shell and guards |

### File Changes
| File | Action | What Changes |
| ---- | ------ | ------------ |
| `frontend/package.json`, `vite.config.ts` (proxy `/api` -> :8080, vitest config), `tsconfig*.json`, `eslint.config.js`, `.prettierrc` | Create | strict TS, lint, scripts `dev`, `build`, `test`, `lint`, `typecheck` |
| `frontend/src/shared/api/{client,errors,types}.ts` | Create | fetch wrapper, `ApiError`, API types |
| `frontend/src/features/auth/{AuthContext,token,useAuth}.tsx` | Create | session handling |
| `frontend/src/app/{App,routes,Layout,Header}.tsx`, `src/shared/ui/*`, `src/shared/styles/{tokens,global}.css` | Create | shell, tokens, responsive layout |
| `frontend/src/test/{setup.ts,server.ts,handlers.ts}` | Create | MSW server, shared handlers, console guard |

### Implementation Notes
First command of the phase: install and run an empty test + build to prove the toolchain works with the very recent versions (see research risk); downgrade individual packages if needed and note it. TypeScript `strict`, no `any`. The client is the only module that touches `fetch`.

### Verification
```bash
cd frontend && npm ci && npm run typecheck && npm run lint && npm test && npm run build
```
**Expected result:** all green; no console output during tests.

---

## Phase 2: Catalog list and detail (US-01, US-02)

**Goal:** browse the PokeAPI catalog and open a detailed view.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Component | `features/catalog/CatalogPage.test.tsx` | `showsSkeletonThenCards`, `cardShowsSpriteTypesWeightAbilities`, `pagingUpdatesTheUrlAndKeepsPreviousPageVisible`, `pageSizeSelector`, `emptyAndErrorStates` (502/504 message), `goToBoxNavigatesByNameOrId` | US-01 |
| Component | `features/catalog/PokemonDetailPage.test.tsx` | `showsImageStatsDescriptionGenus`, `statBarsReflectValues`, `rendersBranchingEvolutionTree` (Eevee), `handlesMissingImageAndDescription`, `notFoundShowsFriendlyPage`, `descriptionIsRenderedAsTextOnly` | US-02 |
| Unit | `features/catalog/format.test.ts` | `typeColorFallback`, `weightAndHeightFormatting`, `capitalizeSlugs` | helpers |
| A11y | both page tests | `hasNoAxeViolations` | `vitest-axe` |

### File Changes
| File | Action | What Changes |
| ---- | ------ | ------------ |
| `features/catalog/{api,queries}.ts` | Create | `useCatalogPage`, `usePokemonDetail` |
| `features/catalog/{CatalogPage,PokemonDetailPage}.tsx` + `components/{PokemonCard,TypeChip,StatBars,EvolutionTree,Pagination,GoToBox}.tsx` + CSS Modules | Create | UI |
| `src/test/handlers.ts` | Modify | catalog fixtures (captured from the real API like the backend ones) |

### Implementation Notes
Pagination state in `useSearchParams` (`page`, `size`), 0-based like the API. Cards have fixed image boxes and `loading="lazy"` to avoid layout shift. The evolution tree is a recursive component that wraps on narrow screens.

### Verification
```bash
cd frontend && npm test -- catalog && npm run typecheck && npm run lint
```
**Expected result:** pass, axe clean, no console output.

---

## Phase 3: Login/register and the collection (US-03)

**Goal:** authenticate, add Pokemon to the collection, and browse it.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Component | `features/auth/{LoginPage,RegisterPage}.test.tsx` | `submitsAndRedirectsBack`, `showsInvalidCredentials`, `showsFieldErrorsFromTheApi`, `usernameTakenMessage`, `passwordRulesHint`, `disablesSubmitWhileLoading` | auth flows |
| Component | `features/collection/CollectionPage.test.tsx` | `listsEntriesWithNicknameRegionTags`, `marksMyEntries` (ownerId = uid), `emptyStateInvitesToAddFromCatalog`, `pagination`, `importRangeShowsReportWithFailures`, `importHiddenWhenAnonymous`, `abortedReportIsExplained` | list + range sync |
| Component | `features/catalog/PokemonDetailPage.test.tsx` (extend) | `addToCollectionNeedsLogin`, `addToCollectionSyncsAndLinksToTheEntry` (201 vs 200 wording), `shows502Gracefully` | single sync |

### File Changes
| File | Action | What Changes |
| ---- | ------ | ------------ |
| `features/auth/{LoginPage,RegisterPage}.tsx`, `features/auth/api.ts` | Create | forms with accessible errors |
| `features/collection/{api,queries}.ts`, `{CollectionPage}.tsx`, `components/{CollectionCard,ImportRangeForm,SyncReport}.tsx` | Create | list + sync |
| `features/catalog/PokemonDetailPage.tsx` | Modify | "Add to my collection" |
| `src/app/routes.tsx` | Modify | routes + `RequireAuth` |

### Implementation Notes
After a sync, invalidate the collection queries and seed the cache with the response. Mutations expose loading/error to the UI via TanStack Query; no ad-hoc `useEffect` fetching.

### Verification
```bash
cd frontend && npm test -- auth collection catalog && npm run typecheck && npm run lint
```
**Expected result:** pass.

---

## Phase 4: Collection entry, edit and delete (US-04)

**Goal:** owner/admin edit local fields with proper validation and conflict handling; admin deletes.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Component | `features/collection/CollectionEntryPage.test.tsx` | `showsFullRecordWithLocalFields`, `ownerSeesEditForm`, `strangerSeesReadOnlyWithExplanation`, `adminSeesEditAndDelete`, `anonymousSeesReadOnly`, `savesOnlyChangedFieldsAndSendsVersion`, `updatesVersionAfterSave`, `tagEditorNormalizesAndLimitsTen`, `showsFieldErrorsFrom400`, `conflict409OffersReload`, `notFound404`, `forbidden403Message`, `deleteAsksConfirmationThenRedirects` | US-04 behavior |
| Unit | `features/collection/tags.test.ts` | `trimLowercaseDedupe`, `rejectsTooLongTag` | mirrors the backend rules for instant feedback |
| A11y | page test | `formControlsHaveLabelsAndErrorsAreAnnounced` | axe + roles |

### File Changes
| File | Action | What Changes |
| ---- | ------ | ------------ |
| `features/collection/{CollectionEntryPage,EditForm}.tsx`, `components/{TagInput,ConfirmDialog}.tsx` | Create | edit and delete UX |
| `features/collection/{api,queries}.ts` | Modify | `useUpdateEntry`, `useDeleteEntry` |

### Implementation Notes
Client-side checks only give early feedback; the API stays the authority (limits come from the backend error responses). Send only changed fields plus `version`. On 409 show "changed by someone else" with a reload button that refetches and keeps the user's draft visible. Confirmation uses a native `<dialog>` for accessibility.

### Verification
```bash
cd frontend && npm test -- collection && npm run typecheck && npm run lint
```
**Expected result:** pass.

---

## Phase 5: Production build, Docker, quality gates

**Goal:** the whole product starts with one command and meets the "no console warnings, responsive, accessible" criteria.

### Tests to Write First (RED)
| Level | Test File | Test Case | Asserts |
| ----- | --------- | --------- | ------- |
| Component | `app/App.test.tsx` (extend) | `sessionExpiryShowsMessageAndLogsOut`, `errorBoundaryShowsFallback`, `titleChangesPerRoute` | resilience |
| Config | `vite.config.ts` | coverage thresholds (lines 85 %, branches 75 %) | gate like the backend |
| Smoke | compose | `curl localhost:3000/` serves the SPA, `/api/v1/pokemon?size=2` returns JSON through nginx, deep link `/collection/25` falls back to `index.html` | proxy + SPA fallback |

### File Changes
| File | Action | What Changes |
| ---- | ------ | ------------ |
| `frontend/Dockerfile` | Create | multi-stage: `node:22-alpine` build -> `nginxinc/nginx-unprivileged` runtime |
| `frontend/nginx.conf` | Create | SPA fallback, `/api/` proxy to `app:8080`, security headers (CSP, nosniff, frame-deny), cache for hashed assets |
| `docker-compose.yml` | Modify | `frontend` service on `3000`, depends on `app` |
| `frontend/.dockerignore`, `.env.example` | Create/Modify | exclude `node_modules`, `dist` |
| `frontend/src/app/ErrorBoundary.tsx` | Create | fallback UI |

### Implementation Notes
Check the real browser console once by hand (Chrome devtools) on each page at phone and desktop widths; record the result in the plan status. CSP must allow images from `raw.githubusercontent.com` (PokeAPI sprites) and nothing else external.

### Verification
```bash
cd frontend && npm run typecheck && npm run lint && npm run test:coverage && npm run build && cd ..
docker compose up --build -d
curl -s -o /dev/null -w "%{http_code}\n" localhost:3000/ && curl -s "localhost:3000/api/v1/pokemon?size=2" | head -c 200 && curl -s -o /dev/null -w "%{http_code}\n" localhost:3000/collection/25
docker compose down -v
```
**Expected result:** all gates green; 200 / JSON / 200 (SPA fallback); no console warnings in the manual browser check.

---

## Final Verification
```bash
(cd backend && ./gradlew clean build) && (cd frontend && npm ci && npm run test:coverage && npm run build) && docker compose build
```

## Rollback Plan
Feature branch; the frontend is additive (`frontend/` + one compose service). Drop the service and folder to revert.

## Out of Scope
README and GenAI document, curated demo data (Plan E); i18n; refresh tokens; client-side catalog search; end-to-end browser automation (Playwright); PWA/offline; analytics.
