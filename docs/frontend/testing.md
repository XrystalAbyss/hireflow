# HireFlow Frontend & Full-Stack Testing Strategy (CAP-8.5 Baseline)

This document specifies the verification architecture, automated testing layers, test categorization, and execution commands for the complete HireFlow system across React 19, Spring Boot 4.1.1, and PostgreSQL 16.

---

## 1. Testing Pyramid & Toolchain Architecture

HireFlow employs an unyielding multi-layer verification pyramid designed to guarantee mechanical contract adherence, transactional persistence, accessibility compliance, and visual excellence:

```
                          ┌──────────────────────────┐
                          │   Playwright E2E Suite   │
                          │ 35 Tests / 5 Viewports   │
                          │ WCAG 2.2 AA (Axe Core)   │
                          ├──────────────────────────┤
                          │ Live HTTP Backend Test   │
                          │ Real Spring Boot + PgSQL │
                          │ 7 End-to-End Workflows   │
                          ├──────────────────────────┤
                          │ Vitest Contract & Units  │
                          │ 134 Mocked & UI Tests    │
                          │ 40 DTO Machine Schemas   │
                          ├──────────────────────────┤
                          │ Java Backend Regressions │
                          │ 678 Spring Boot & Root   │
                          │ Exclusion GiST & Greedy  │
                          ├──────────────────────────┤
                          │ Type Integrity & Linters │
                          │ TS Strict + Oxlint       │
                          └──────────────────────────┘
```

| Layer | Tools / Frameworks | Test Count | Scope & Verification Invariants | Execution Command |
| :--- | :--- | :--- | :--- | :--- |
| **Java Domain & CLI** | JUnit 5, Mockito | **495 passed** | GreedyScheduler, business rules, company/drive/student entities, CLI commands. | `mvn test` (root) |
| **Java REST API** | Spring Boot Test, MockMvc, JPA | **183 passed** | Controllers, security filters, CSRF exchange, preview cache, Flyway migrations. | `mvn test -pl hireflow-api` |
| **TypeScript Strict** | TypeScript 5.8+ | 0 errors | Zero `any` leaks, strict null checks, 40 machine-verified DTO interfaces. | `npm run build` |
| **Code Linting** | Oxlint | 0 errors | React 19 compiler rules, dead imports, no console/token leaks. | `npm run lint` |
| **Vitest Unit & Contract**| Vitest 5.0, Testing Library | **136 passed** | DTO contract schemas, UI components, scheduling grid, modals, API client. | `npm test` |
| **Live Backend Integration** | Vitest + Node fetch (Port 8081) | **5 passed** | Real HTTP against running Spring Boot + PostgreSQL (`hireflow_test`): Auth, Student, Officer, Preview, Publication. | `npx vitest run src/test/live-backend-integration.test.ts` |
| **Playwright E2E & Visual QA**| Playwright 1.50+ (Chromium) | **35 passed** | Multi-viewport visual QA (390px, 768px, 1024px, 1440px, 1920px), Axe-core a11y, Officer Command Center, Student Hub, Scheduling Studio. | `npm run test:e2e` |

**Total Combined Automated Suite: 854 Passing Tests (0 Failures, 0 Skipped).**

---

## 2. Test Layer Categorization & Exact Breakdown

### 2.1 Backend Java Regressions (678 Tests)
- **`hireflow-core` & Domain Regressions (495 tests)**:
  - Validates the authoritative CAP-7 greedy scheduling allocation engine (`GreedyScheduler.java`).
  - Verifies slot creation, interviewer time window allocation, room capacity validation, and candidate ranking.
  - Verifies domain model immutability and state transition rules (`ApplicationStatus`: `APPLIED` -> `ELIGIBLE` -> `SHORTLISTED` -> `SCHEDULED` -> `SELECTED`).
- **`hireflow-api` Integration Regressions (183 tests)**:
  - Spring Boot controller unit and MockMvc integration tests.
  - Spring Security session fixation protection and CSRF token endpoints.
  - `SchedulingPreviewCacheService`: 15-minute TTL eviction, atomic in-memory consumption, and replay rejection.
  - Exception mappers: `PreviewNotFoundException` (404), `PreviewExpiredException` (410), `PreviewConsumedException` (409).

### 2.2 Vitest Frontend Unit & Contract Suite (141 Tests across 9 files)
1. `src/test/live-backend-integration.test.ts` (5 tests): Live HTTP execution against running Spring Boot on port 8081 + PostgreSQL `hireflow_test`:
   - 1. Officer authentication, session cookie issuance, CSRF exchange, and resource setup (company, interviewer, room, recruitment drive).
   - 2. Student authentication, profile retrieval, academic eligibility evaluation, application submission, and duplicate prevention.
   - 3. Candidate shortlisting, GreedyScheduler preview generation (verifying 0 DB records inserted), transactional publication, single-use token replay defense (HTTP 409), and unknown token rejection (HTTP 404).
   - 4. Student timetable visibility verifying confirmed interview (`SCHEDULED`, `SES-PROP-001`).
   - 5. Drive closure and immutable closed-drive mutation rejection.
2. `src/test/scheduling-contract.test.ts` (23 tests): Independent runtime validation testing exact Java DTO contracts for scheduling endpoints:
   - Preview request/response schema parsing and wall-clock time parsing (`HH:mm`, `YYYY-MM-DDTHH:mm:ss`).
   - Publication outcome envelopes: `ALL_CONFIRMED` (200), `PARTIAL_SUCCESS` (207), `NONE_CONFIRMED` (409), `EXPIRED` (410), and server error recovery.
3. `src/test/officer-contract.test.ts` (24 tests): Independent DTO contract tests for 18 officer management endpoints.
4. `src/test/contract-validation.test.ts` (49 tests): Independent DTO contract tests for student workflows and Zod schemas.
5. `src/test/scheduling-components.test.tsx` (11 tests): Scheduling Studio UI components:
   - `SchedulingConfigForm` input bounds and validation.
   - `AdvisoryTimeGrid` and `AdvisoryPreviewTable` appointment slot rendering.
   - `UnscheduledCandidatesSection` and reason badges.
   - `PublicationConfirmDialog` consequential warning prompt and focus trapping.
   - `PublicationOutcomeView` handling 200, 207, 404, 409, 410, and 500 error envelopes.
   - `ConfirmedTimetable` presentation and `SessionDetailDialog` modal inspection.
6. `src/test/officer-components.test.tsx` (7 tests): Officer UI dialogs, cards, and stat widgets.
7. `src/test/student.test.tsx` (9 tests): Student academic dossier, drive explorer, and scorecard.
8. `src/test/components.test.tsx` (9 tests): UI primitives (Button, Card, Badge, Dialog, Table).
9. `src/test/api-client.test.ts` (4 tests): CSRF lifecycle, credentials transport, and 401 handling.

### 2.3 Playwright End-to-End Suite (35 Tests across 5 spec files)
1. `tests/e2e/accessibility.spec.ts` (2 tests):
   - Automated Axe-core audit verifying WCAG 2.2 AA zero-violation baseline.
   - Comprehensive keyboard navigation, focus trapping, Escape key handling, and ARIA dialog semantics.
2. `tests/e2e/scheduling-studio.spec.ts` (7 tests):
   - Multi-viewport visual audit of the Scheduling Studio (390px, 768px, 1024px, 1440px, 1920px).
   - Configuration panel interaction, resource selection, and preview generation.
   - Advisory time grid and accessible tabular view switching.
   - Publication confirmation dialog and outcome reporting (200, 207, 404, 409, 410, 500).
   - Confirmed timetable filtering and session inspection dialog.
3. `tests/e2e/officer-command-center.spec.ts` (7 tests):
   - Officer dashboard analytics, corporate partner directory, drive creation, candidate shortlisting, and resource administration.
4. `tests/e2e/student-hub.spec.ts` (7 tests):
   - Student dashboard, recruitment drive explorer, eligibility breakdown, modal application submission, application history, and interview briefing.
5. `tests/e2e/showcase.spec.ts` (12 tests):
   - Responsive layout audits across all 5 standard viewports.

---

## 3. Multi-Viewport Visual Audit Matrix

Playwright systematically captures and validates the application across 5 mandatory screen sizes:

1. **390px (Mobile Portrait - iPhone 14/15)**:
   - Verifies mobile hamburger navigation drawer and accessible touch targets (min 44x44px).
   - Ensures zero horizontal scrolling on body canvas (`overflow-x: hidden`).
2. **768px (Tablet Portrait - iPad Mini)**:
   - Validates responsive flex wrapping, card grid collapse, and toolbar responsiveness.
3. **1024px (Laptop Display)**:
   - Validates dense administrative data tables, split-pane scheduling grids, and dialog centering.
4. **1440px (Desktop Baseline)**:
   - Primary reference viewport for Placement Command Center, Student Hub, and Scheduling Studio.
5. **1920px (Wide Desktop Display)**:
   - Verifies container centering (`max-w-7xl mx-auto`) and background surface fill.

All 18 visual screenshots are preserved as formal artifacts in `<brain-dir>/screenshots/`.

---

## 4. Production Fixture & Security Isolation Policy

- **Strict Isolation**: Development preview fixtures reside strictly in `fixtures.ts` and are used exclusively when running the dev server in offline preview mode.
- **Production Workshop Exclusion**: The Component Workshop (`/workshop`) is excluded from production builds; visiting `/workshop` in production automatically redirects to `/auth/login`.
- **Zero Token Leakage**: CSRF tokens and scheduling preview IDs never leak into URLs, query parameters, console logging, error reporting, screenshots, or persistent browser storage.
- **Bundle Optimization**: Route-level code splitting ensures the main production bundle is under 430 kB with zero vendor warnings.
