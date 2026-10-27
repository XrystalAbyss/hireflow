# HireFlow Frontend Architecture Specification (CAP-8.5 Baseline)

## 1. Architectural Overview

HireFlow's frontend is architected as an institutional-grade React 19 + TypeScript Single Page Application (SPA) located in `hireflow-app/`. It interfaces directly with the authoritative Spring Boot REST backend (`hireflow-api`, commit `bdbf0e1dce7407ab9034f893712ccea8d7fced56`, published backend baseline v4.0.0-cap7, Spring Boot 4.1.1 parent, Java 21) backed by PostgreSQL 16.

The frontend provides two distinct role-specific operational shells (`StudentShell` and `OfficerShell`) underpinned by an Institutional × Contemporary design system, WCAG 2.2 AA compliant UI primitives, a type-safe API client, server-managed session authentication, route-level code splitting, and strict token hygiene.

```
hireflow/
├── hireflow-api/                 # Authoritative Spring Boot 4.1.1 REST Backend (Port 8081)
│   ├── src/main/java/com/hireflow/
│   │   ├── auth/                 # Spring Security, UserProfile, Session Fixation
│   │   ├── company/              # Company entities, services & controllers
│   │   ├── drive/                # Recruitment drive lifecycle & criteria
│   │   ├── student/              # Student profiles, applications & dashboard
│   │   ├── officer/              # Officer command center, interviewers, rooms
│   │   └── scheduling/           # GreedyScheduler, PreviewCache, Publication
│   └── src/main/resources/
│       ├── db/migration/         # Flyway V1, V2, V3 database migrations
│       └── application.properties # Server port, JDBC datasource, GiST exclusions
├── hireflow-app/                 # React 19 + Vite + TypeScript Frontend (Port 5173)
│   ├── src/
│   │   ├── components/
│   │   │   ├── ui/               # 17 Accessible Radix-backed UI Primitives
│   │   │   ├── domain/           # 11 HireFlow domain-specific components
│   │   │   └── shells/           # StudentShell, OfficerShell, AuthenticationLayout
│   │   ├── core/
│   │   │   ├── api/              # Secure ApiClient, in-memory CSRF & error envelope classification
│   │   │   └── auth/             # AuthContext, session lifecycle & cache isolation
│   │   ├── features/
│   │   │   ├── auth/             # LoginPage
│   │   │   ├── student/          # Complete Student Opportunity Hub (CAP-8.2)
│   │   │   ├── officer/          # Placement Officer Command Center (CAP-8.3)
│   │   │   ├── scheduling/       # Interview Scheduling Studio (CAP-8.4)
│   │   │   └── workshop/         # Component Workshop (excluded from production builds)
│   │   ├── styles/               # Semantic design tokens (tokens.css, index.css)
│   │   ├── types/                # Machine-grounded TypeScript interfaces for 40 DTOs
│   │   └── test/                 # Vitest component, contract & live-backend integration tests
│   └── tests/e2e/                # Playwright visual QA, multi-viewport & axe a11y tests
└── docs/frontend/                # Technical documentation (architecture, contracts, setup, QA)
```

---

## 2. Authentication, Session & CSRF Lifecycle

### 2.1 Server-Managed Session Cookies
- Authentication is strictly backed by server-managed `JSESSIONID` HTTP-only cookies.
- No JWT tokens, refresh tokens, passwords, or user credentials are ever stored in `localStorage`, `sessionStorage`, or IndexedDB.
- All network requests set `credentials: 'include'` (or `withCredentials: true`) to ensure seamless cookie transport across same-origin endpoints.

### 2.2 CSRF Protection & In-Memory Token Management
- All mutating HTTP methods (`POST`, `PUT`, `DELETE`, `PATCH`) require the `X-CSRF-TOKEN` header.
- Safe HTTP methods (`GET`, `HEAD`, `OPTIONS`) never transmit CSRF headers.
- The CSRF token is fetched via `GET /api/v1/auth/csrf` and retained **strictly in memory** in the singleton `ApiClient`.
- **Privacy Enforcement**: CSRF tokens and scheduling preview IDs (`previewId`) are strictly excluded from browser URLs, query parameters, console logging, analytics payloads, screenshots, and persistent browser storage. Tokens travel exclusively in HTTP request/response JSON payloads.

### 2.3 Session Rotation Invariant
- Spring Security performs session fixation protection during `POST /api/v1/auth/login`, issuing a fresh `JSESSIONID` and invalidating the pre-login CSRF token.
- `ApiClient.login()` immediately triggers `fetchCsrfToken()` upon receiving a 200 OK login response, synchronizing the in-memory token state before subsequent mutations.
- When `POST /api/v1/auth/logout` is called, the in-memory CSRF token is cleared, the session cookie is invalidated on the server, and the client-side state is reset.

### 2.4 Error Handling & Non-Blind Retries
- Failed mutations returning HTTP 403 are classified rather than blindly retried:
  - If unauthenticated, the user is redirected to `/auth/login`.
  - If forbidden due to role mismatch, an access boundary is displayed.
  - Blind automated retries on HTTP 403 are strictly forbidden.
- Unauthorized responses (HTTP 401) trigger session clearance only if an authenticated user session was previously active, preventing infinite query discard loops during unauthenticated routing.

---

## 3. Query Cache Isolation & Memory Governance

- TanStack Query v5 manages asynchronous server state.
- Whenever an active user logs out, or switches accounts, `queryClient.clear()` is immediately executed.
- This prevents cross-tenant data leakage between students, placement officers, or administrators sharing a physical terminal.
- Mutation hooks implement targeted invalidation (e.g. invalidating `['officer', 'drives']` upon drive creation or closure; invalidating `['student', 'applications']` upon submission).

---

## 4. Scheduling Engine Transactional Semantics & Recovery

- **Ephemeral Advisory Preview**: `POST /api/v1/officer/scheduling/preview` executes the backend `GreedyScheduler` algorithm in server memory. It has an active 15-minute Time-To-Live (TTL). It performs **zero PostgreSQL mutations** and creates zero `InterviewSession` records.
- **Per-Session Publication**: `POST /api/v1/officer/scheduling/publish` is **NOT** atomic across the entire batch. Each proposed appointment is persisted within its own isolated transaction against PostgreSQL GiST exclusion constraints (`room_id` and `interviewer_id` temporal overlaps).
- **Three Core Publication Outcomes**:
  - `HTTP 200 OK` (`ALL_CONFIRMED`): All proposed appointments committed. Candidate applications transition from `SHORTLISTED` to `SCHEDULED`.
  - `HTTP 207 Multi-Status` (`PARTIAL_SUCCESS`): Confirmed appointments persisted; conflicted candidates remain shortlisted for future allocation.
  - `HTTP 409 Conflict` (`NONE_CONFIRMED`): PostgreSQL temporal GiST conflict; zero appointments committed.
- **Preview Token Lifecycle & Invariants**:
  - **404 vs 410 Distinction (B1)**:
    - An unknown or evicted preview token (due to backend restart or cache TTL eviction) yields `PreviewNotFoundException` mapped to `HTTP 404 Not Found`.
    - An expired preview token that is still present in cache yields `PreviewExpiredException` mapped to `HTTP 410 Gone`.
    - Both scenarios indicate preview invalidation; the frontend safely prompts the placement officer to generate a fresh preview.
  - **Single-Use Atomic Consumption (B2)**:
    - When publication begins, the backend atomically marks the cached preview as consumed without deleting the entry.
    - Any replay or duplicate submission yields `PreviewConsumedException` mapped to `HTTP 409 Conflict`.
  - **Ambiguous Network/Transport Failure Recovery (G6)**:
    - If a publication request suffers a network timeout or HTTP 5xx error, the client does NOT automatically retry the POST mutation.
    - The UI transitions to an `UNCERTAIN` recovery state, providing one-click read-only reconciliation via `GET /api/v1/officer/scheduling/published` to inspect any partial commits before generating a new preview.
- **No Drag-to-Reschedule**: Rescheduling is strictly algorithmic and deterministic; client-side manual drag-and-drop timetable rearrangement is unsupported.

---

## 5. Route-Level Code Splitting & Production Bundle Integrity

In accordance with institutional performance standards, the application implements route-level code splitting using `React.lazy` and `React.Suspense` with an accessible `InstitutionalFallback` loading indicator:

- **Main Entry Chunk (`index.js`)**: **426.09 kB** (gzip: 128 kB), well below the 500 kB performance threshold.
- **Officer Scheduling Studio Chunk**: **106.20 kB** (lazy-loaded only when accessing `/officer/scheduling`).
- **Component Workshop Chunk**: **62.62 kB** (isolated, lazy-loaded, and strictly gated in production).
- **Production Fixture & Workshop Exclusion**:
  - The Component Workshop route (`/workshop`) is strictly gated: in production (`import.meta.env.PROD`), requests to `/workshop` automatically redirect to `/auth/login`.
  - Production builds contain zero hardcoded credentials or test tokens.
  - Development fixture fallbacks (`isOfflineNetworkError`) are strictly limited to standalone preview environments when the backend is offline, and never mask authoritative 400, 404, 409, 410, 422, or 500 responses from the real Spring Boot API.

---

## 6. Network Proxy & Deployment Architecture

- **Development Proxy**: Vite dev server proxies `/api` requests to `http://localhost:8081` via `vite.config.ts`, ensuring same-origin behavior for cookies and CSRF headers.
- **Production Deployment**: The production deployment standard utilizes a same-origin reverse proxy (Nginx, Caddy, or Spring Cloud Gateway) routing:
  - `/api/*` -> Upstream Spring Boot cluster (`hireflow-api:8081`)
  - `/*` -> Static React SPA assets served with `Cache-Control: public, max-age=31536000, immutable` for hashed assets and `no-cache` for `index.html`.
- **CORS Policy**: Cross-origin deployments requiring wildcard or credentials-enabled CORS are avoided in institutional production environments to preserve strict cookie security and prevent cross-site leakage.
