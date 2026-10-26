# Interview Scheduling Studio — Architecture, Algorithmic Allocation & Publication Guide (CAP-8.4)

## 1. Executive Summary

Milestone **CAP-8.4** delivers HireFlow's flagship **Interview Scheduling Studio**, providing university placement officers and career services administrators with an authoritative, enterprise-grade scheduling environment. Grounded strictly in the authoritative CAP-7 Spring Boot backend (`v4.0.0-cap7`, commit `bdbf0e1dce7407ab9034f893712ccea8d7fced56`) and the algorithmic `GreedyScheduler`, the Studio enforces:

1. **Advisory Algorithmic Preview**: Non-destructive slot generation evaluated in instance-local server memory with strict 15-minute validity.
2. **Transactional Publication**: Per-session database persistence with multi-status reconciliation (HTTP 200, 207, 409, 410, 5xx).
3. **Confirmed Timetable Inspection**: Read-after-write verification, query filtering, and candidate briefing dossiers.
4. **Strict Token & Security Hygiene**: In-memory ephemeral token lifecycle, zero storage/URL leakage, and campus wall-clock temporal discipline.

---

## 2. Architecture & Component Inventory

The Scheduling Studio is located at `/officer/scheduling` within `OfficerLayout.tsx`, guarded by role checks (`PLACEMENT_OFFICER` and `ADMINISTRATOR`). It replaces the previous deferred placeholder (`OfficerSchedulingDeferredPage.tsx`).

### Component Hierarchy

```
OfficerSchedulingStudioPage.tsx (Primary Container)
├── Navigation Tabs: Studio ("Algorithmic Studio") | Confirmed ("Confirmed Timetable")
│
├── Tab 1: Studio Tab
│   ├── SchedulingConfigPanel.tsx (Step 1: Configuration Form)
│   │   ├── Recruitment Drive Selector & Shortlisted Candidate Counter
│   │   ├── Interview Date & Time Range Pickers
│   │   ├── Duration & Buffer Inputs (Defaults: 45m / 15m)
│   │   ├── Interviewer Resource Selector (Explicit Selection vs Company Default Pool)
│   │   ├── Room Resource Selector (Explicit Selection vs Campus Default Pool)
│   │   └── Developer Simulation Mode Drawer (Explicit Opt-In)
│   │
│   ├── AdvisoryPreviewView.tsx (Step 2: Ephemeral Preview Studio)
│   │   ├── Advisory Warning Banner & Masked Token Badge (e.g. "PREV-9F2B****")
│   │   ├── Dynamic TTL Expiration Countdown (15-min server Instant)
│   │   ├── Coordinated View Switcher:
│   │   │   ├── SchedulingTimeGrid.tsx (Visual Multi-Lane Timeline Grid with Buffer Intervals)
│   │   │   └── SchedulingDataTable.tsx (Accessible Data Table with Search Filtering)
│   │   ├── UnscheduledCandidatesList.tsx (Dedicated Card with Algorithmic Non-Allocation Reasons)
│   │   └── Publication Review Trigger Action
│   │
│   ├── PublicationConfirmDialog.tsx (Step 3: Consequence Review Modal)
│   │   ├── Structured Configuration & Allocation Summary
│   │   ├── Per-Session Persistence Consequence Warning
│   │   └── Officer Explicit Acknowledgement Checkbox
│   │
│   └── PublicationOutcomeView.tsx (Step 4: Publication Outcome State Machine)
│       ├── HTTP 200 ALL_CONFIRMED: Newly Persisted vs Skipped Duplicates Breakdown
│       ├── HTTP 207 PARTIAL_SUCCESS: Confirmed Sessions + Itemized Conflict Rejections + Recovery Steps
│       ├── HTTP 409 NONE_CONFIRMED: Business Conflict Analysis & Reconfiguration Guide
│       ├── HTTP 409 CONSUMED: Token Replay Prevention & Confirmed Timetable Navigation
│       ├── HTTP 410 EXPIRED: Cache Eviction Notice & Fresh Preview Regeneration
│       ├── HTTP 500 UNCERTAIN: Server Failure Warning with Read-After-Write Inspection
│       └── NETWORK_ERROR: Ambiguous Lost Response Guidance
│
└── Tab 2: Confirmed Timetable Tab
    ├── ConfirmedTimetableView.tsx (Step 5: Inspection & Filtering)
    │   ├── Filter Bar: Drive Selector, Company Selector, Date Filter
    │   ├── Desktop Table (Dense Institutional Layout)
    │   ├── Mobile Responsive Cards (< 768px Viewports)
    │   └── Pagination Controls (Standard PagedResponse Envelope)
    │
    └── ConfirmedSessionDetailDialog.tsx (Step 6: Interview Briefing Dossier)
        ├── Candidate & Academic Information (CGPA, Branch, Roll Number)
        ├── Interviewer & Room Assignment Details
        ├── Campus Wall-Clock Interview Window
        └── Current Application Status Badge
```

---

## 3. Verified DTO-to-TypeScript Contract Matrix

All four scheduling endpoints and their DTOs map 1-to-1 to the authoritative Java Spring Boot classes:

### Endpoint 1: POST /api/v1/officer/scheduling/preview
- **Java Controller**: `OfficerSchedulingController.previewSchedule()`
- **Service**: `SchedulingEngine.generatePreview()`, `SchedulingPreviewCacheService.putPreview()`
- **Request DTO**: `OfficerSchedulingPreviewRequestDto`
  | Java Field | TypeScript Type | Validation Constraints |
  | :--- | :--- | :--- |
  | `driveId` | `string` | Required, non-empty |
  | `date` | `string` | Required, `YYYY-MM-DD` |
  | `startTime` | `string` | Required, `HH:mm` |
  | `endTime` | `string` | Required, `HH:mm`, must be strictly after `startTime` |
  | `durationMinutes`| `number \| null` | Optional, integer 1–240 (default: 45) |
  | `bufferMinutes` | `number \| null` | Optional, integer 0–120 (default: 15) |
  | `interviewerIds` | `string[] \| null`| Optional list of UUID strings |
  | `roomIds` | `string[] \| null`| Optional list of UUID strings |
- **Response DTO**: `OfficerSchedulingPreviewResponseDto`
  | Java Field | TypeScript Type | Description |
  | :--- | :--- | :--- |
  | `previewId` | `string` | In-memory server cache key (UUID) |
  | `driveId` | `string` | Associated campaign identifier |
  | `jobTitle` | `string` | Role name |
  | `companyName` | `string` | Corporate partner name |
  | `date` | `string` | `YYYY-MM-DD` |
  | `startTime` | `string` | `HH:mm` |
  | `endTime` | `string` | `HH:mm` |
  | `durationMinutes`| `number` | Applied interview duration |
  | `bufferMinutes` | `number` | Applied buffer duration |
  | `expiresAt` | `string` | UTC ISO `Instant` (`2026-10-19T18:45:00Z`) |
  | `totalRequested`| `number` | Shortlisted candidate count |
  | `totalScheduled`| `number` | Successfully allocated count |
  | `totalUnscheduled`| `number` | Unscheduled candidate count |
  | `proposedSessions`| `ProposedSessionDto[]` | Array of proposed slot allocations |
  | `unscheduledCandidates`| `UnscheduledCandidateDto[]` | Array of unallocated candidates |

- **Sub-DTO**: `ProposedSessionDto`
  - `sessionId: string` (e.g. `PROP-xxx`)
  - `applicationId: string`
  - `studentId: string`
  - `studentName: string`
  - `studentBranch: string`
  - `interviewerId: string`
  - `interviewerName: string`
  - `roomId: string`
  - `roomName: string`
  - `startTime: string` (Campus `LocalDateTime`: `YYYY-MM-DDTHH:mm:ss`)
  - `endTime: string` (Campus `LocalDateTime`: `YYYY-MM-DDTHH:mm:ss`)

- **Sub-DTO**: `UnscheduledCandidateDto`
  - `applicationId: string`
  - `studentId: string`
  - `studentName: string`
  - `studentBranch: string`
  - `reason: string` (e.g. `"No interview room available"`, `"Exhausted daily time window"`)

---

### Endpoint 2: POST /api/v1/officer/scheduling/publish
- **Java Controller**: `OfficerSchedulingController.publishSchedule()`
- **Service**: `InterviewService.publishSchedule()`
- **Request DTO**: `OfficerSchedulingPublishRequestDto`
  | Java Field | TypeScript Type | Description |
  | :--- | :--- | :--- |
  | `previewId` | `string` | Cryptographic in-memory preview key |
- **Response DTO**: `OfficerPublicationSummaryDto`
  | Java Field | TypeScript Type | Description |
  | :--- | :--- | :--- |
  | `attemptId` | `string` | UUID audit attempt ID |
  | `previewId` | `string` | Echoed preview ID |
  | `previewReference`| `string \| null` | Masked reference for display |
  | `driveId` | `string` | Drive identifier |
  | `jobTitle` | `string` | Job role title |
  | `companyName` | `string` | Company name |
  | `outcome` | `PublicationOutcome` | `ALL_CONFIRMED \| PARTIAL_SUCCESS \| NONE_CONFIRMED` |
  | `recoveryInstructions`| `string \| null`| Actionable backend recovery guidance |
  | `publishedAt` | `string` | Instant `publishedAt` |
  | `totalProposals`| `number` | Total proposals processed |
  | `publishedCount`| `number` | Newly persisted sessions count |
  | `skippedDuplicatesCount`| `number` | Already existing duplicate sessions skipped |
  | `conflictsCount`| `number` | Concurrent conflict count |
  | `errorsCount` | `number` | Database/infrastructure errors count |
  | `unattemptedCount`| `number` | Unattempted proposals count |
  | `confirmedSessionIds`| `string[]` | Primary keys in PostgreSQL |
  | `publishedSessions`| `PublishedSessionDetailDto[]` | Details of newly persisted sessions |
  | `skippedDuplicateApplicationIds`| `string[]` | IDs of duplicate applications skipped |
  | `conflictRejections`| `PublicationConflictDetailDto[]` | Itemized conflict causes |
  | `infrastructureErrors`| `string[]` | Infrastructure error messages |

- **Sub-DTO**: `PublishedSessionDetailDto`
  - `sessionId: string`
  - `applicationId: string`
  - `studentName: string`
  - `interviewerName: string`
  - `roomName: string`
  - `startTime: string` (Campus `LocalDateTime`)
  - `endTime: string` (Campus `LocalDateTime`)

- **Sub-DTO**: `PublicationConflictDetailDto`
  - `applicationId: string`
  - `message: string`
  - `category: string`

---

### Endpoint 3: GET /api/v1/officer/scheduling/published
- **Java Controller**: `OfficerSchedulingController.getPublishedSchedule()`
- **Query Parameters**: `driveId` (optional), `companyId` (optional), `date` (optional, `YYYY-MM-DD`), `page` (default 0), `size` (default 20).
- **Response**: `PagedResponse<OfficerInterviewDetailDto>`

### Endpoint 4: GET /api/v1/officer/scheduling/published/{id}
- **Java Controller**: `OfficerSchedulingController.getPublishedSession()`
- **Path Parameter**: `id` (Session UUID)
- **Response**: `OfficerInterviewDetailDto`
  - `sessionId: string` (Authoritative identifier)
  - `applicationId: string`
  - `studentId: string`
  - `studentName: string`
  - `studentBranch: string`
  - `driveId: string`
  - `jobTitle: string`
  - `companyName: string`
  - `interviewerId: string`
  - `interviewerName: string`
  - `roomId: string`
  - `roomName: string`
  - `startTime: string` (Campus `LocalDateTime`)
  - `endTime: string` (Campus `LocalDateTime`)
  - `status: ApplicationStatus` (Authoritative mapping from associated application: `APPLIED`, `ELIGIBLE`, `INELIGIBLE`, `SHORTLISTED`, `SCHEDULED`, `REJECTED`, `SELECTED`).

---

## 4. Preview Token Lifecycle & In-Memory Hygiene

The Spring Boot backend maintains preview proposals in an in-memory `ConcurrentHashMap` (`SchedulingPreviewCacheService`):
1. **Single-Use Atomic Consumption**: Previews are consumed atomically via `cache.remove(previewId)` upon the publication request. Replaying the same preview token returns HTTP 409 Conflict with `"Preview has already been consumed or published"`.
2. **15-Minute Expiration**: Entries expire after 900 seconds. Expired preview tokens return HTTP 410 Gone with `"Preview has expired"`.
3. **Frontend Token Hygiene**:
   - The raw `previewId` is maintained **strictly in React component state**.
   - It is never placed into URL query parameters or route path segments.
   - It is never serialized into `localStorage` or `sessionStorage`.
   - It is never written to console logs, analytics, error traces, or DOM attributes.
   - For UI rendering, `maskPreviewToken(previewId)` formats the identifier as `PREV-XXXX****`.
   - On officer logout or tab switch, preview state is discarded from memory.

---

## 5. Publication State Machine & Error Handling

Publication is **not an all-or-nothing batch operation**. The backend attempts per-session persistence against PostgreSQL GiST exclusion constraints. The frontend implements a strict typed state machine:

```
[ Preview Generated ]
         │
         ▼
[ Officer Confirms in Modal ] ──(POST /publish)──► [ Pending Request: Disable Buttons ]
         │
         ├───► HTTP 200 (ALL_CONFIRMED)
         │       • Displays newly persisted sessions count.
         │       • Displays skipped duplicates count separately.
         │       • Provides direct navigation to Confirmed Timetable tab.
         │
         ├───► HTTP 207 (PARTIAL_SUCCESS)
         │       • Valid application outcome (never treated as failure toast).
         │       • Displays successfully confirmed sessions.
         │       • Displays itemized list of conflicted candidates.
         │       • Unconfirmed candidates remain in SHORTLISTED state.
         │       • Actionable backend recovery guidance rendered.
         │
         ├───► HTTP 409 (NONE_CONFIRMED)
         │       • Zero new sessions confirmed due to concurrent slot booking.
         │       • Explains room/interviewer conflicts.
         │       • Prompts officer to adjust date/time window and re-preview.
         │
         ├───► HTTP 409 (CONSUMED REPLAY)
         │       • Detected when preview token was already used.
         │       • Prevents duplicate submission.
         │       • Offers read-only inspection of confirmed timetable.
         │
         ├───► HTTP 410 (EXPIRED)
         │       • 15-minute validity window elapsed or backend restarted.
         │       • Preserves configuration parameters so officer can regenerate with 1 click.
         │
         ├───► HTTP 5xx (DATABASE / INFRASTRUCTURE FAILURE)
         │       • Token was consumed by server before execution.
         │       • Some sessions may have committed before failure.
         │       • NEVER claims zero sessions committed.
         │       • Renders explicit UNCERTAIN state and reconciliation guidance.
         │
         └───► NETWORK FAILURE / LOST RESPONSE
                 • Ambiguous state. Automatic retry is strictly disabled.
                 • Directs officer to inspect confirmed timetable before re-attempting.
```

---

## 6. Temporal Handling & Clock Invariants

The backend returns two distinct temporal formats that must not be conflated:
1. **Server Expiration (`expiresAt`)**:
   - Format: UTC ISO-8601 `Instant` (`2026-10-19T18:45:00Z`).
   - Parsed with `new Date(expiresAt).getTime()`.
   - Evaluated against `Date.now()` to render the live countdown timer.
2. **Interview Appointment Slots (`startTime`, `endTime`)**:
   - Format: Campus `LocalDateTime` without timezone offset (`2026-10-22T09:00:00`).
   - Parsed with `parseCampusLocalDateTime()` by extracting year, month, day, hour, and minute directly from string chunks.
   - **Critical Rule**: Never parse appointment strings with `new Date("2026-10-22T09:00:00")` because browser timezone offsets would shift campus wall-clock interview times (e.g., shifting 09:00 AM IST to 03:30 AM UTC).

---

## 7. Accessibility (WCAG 2.2 AA Compliance)

1. **Dual Representation**: Advisory appointments are presented in both a visual time-grid (`SchedulingTimeGrid.tsx`) and an accessible tabular format (`SchedulingDataTable.tsx`). Officers with visual or motor impairments can switch with a single keystroke.
2. **Keyboard Operability**: Full `Tab`, `Shift+Tab`, `Space`, and `Enter` navigation through configuration controls, resource selection tags, timeline cells, and modal dialogs.
3. **Focus Trapping**: Modals (`PublicationConfirmDialog.tsx`, `ConfirmedSessionDetailDialog.tsx`) trap focus while active and restore focus to trigger elements upon dismissal.
4. **Live Regions & ARIA**:
   - Dynamic TTL countdown announces when 2 minutes remain.
   - Status banners and publication outcome cards use semantic `role="status"` and `role="alert"`.
   - Form inputs have associated `<label>` elements and `aria-invalid` bindings.
5. **Color Independence**: Every status and appointment utilizes icon indicators, text badges, and borders alongside color cues.
6. **Automated Axe-Core Audits**: Clean 0 violations on both desktop and mobile viewports.

---

## 8. Development Simulation Mode

When the Spring Boot backend is offline, the Studio includes an explicit, non-silent simulation engine (`src/features/officer/api/schedulingSimulation.ts`):
- **Explicit Developer Opt-In**: Simulation requires manual activation via the "Dev Simulation" drawer in the configuration panel.
- **Visual Disclaimers**: Simulated previews and publication outcomes feature prominent warning ribbons: `SIMULATED ADVISORY — LOCAL DEMO ENGINE ACTIVE` and `SIMULATION MODE — NO POSTGRESQL MUTATIONS PERFORMED`.
- **Deterministic Scenarios**:
  - `ALL_CONFIRMED`: 100% allocation and publication.
  - `PARTIAL_SUCCESS`: HTTP 207 with 2 confirmed, 1 conflict, 1 skipped duplicate.
  - `NONE_CONFIRMED`: HTTP 409 with GiST constraint conflict causes.
  - `CONSUMED_REPLAY`: HTTP 409 consumed token replay rejection.
  - `EXPIRED`: HTTP 410 preview cache eviction.
  - `DATABASE_ERROR`: HTTP 500 infrastructure failure with ambiguous persistence.
- **Production Isolation**: In production builds, simulation logic is completely bypassed. Unreachable real endpoints surface standard network errors.

---

## 9. Visual QA & Multi-Viewport Verification

Captured across 18 comprehensive screenshots in `<artifacts_dir>/screenshots/`:

| Index | Artifact Name | Viewport | Verified Elements |
| :---: | :--- | :---: | :--- |
| 01 | `01_scheduling_config_desktop_1440.png` | 1440px | 3-column configuration layout, resource pool disclaimers, shortlisted count |
| 02 | `02_scheduling_config_wide_1920.png` | 1920px | Wide display responsiveness, centered container max-width |
| 03 | `03_scheduling_config_laptop_1024.png` | 1024px | 2-column reflow, touch-friendly dropdown targets |
| 04 | `04_scheduling_config_tablet_768.png` | 768px | Single-column reflow, stacked resource selectors |
| 05 | `05_scheduling_config_mobile_390.png` | 390px | Mobile form stacking, accessible input tap targets |
| 06 | `06_advisory_preview_timegrid_desktop.png`| 1440px | Multi-lane room timeline, buffer blocks, advisory warning ribbon |
| 07 | `07_advisory_preview_tabular_desktop.png` | 1440px | Accessible table alternative with candidate search and column sorting |
| 08 | `08_unscheduled_candidates_section.png` | 1440px | Dedicated card showing non-allocated candidates with backend reason strings |
| 09 | `09_publication_confirmation_dialog.png` | 1440px | Modal review panel, consequence warnings, acknowledgement checkbox |
| 10 | `10_publication_outcome_all_confirmed_200.png`| 1440px | HTTP 200 outcome card, confirmed vs duplicate counts, timetable link |
| 11 | `11_publication_outcome_partial_success_207.png`| 1440px| HTTP 207 multi-status card, conflict items, recovery guidance |
| 12 | `12_publication_outcome_none_confirmed_409.png`| 1440px | HTTP 409 conflict card, GiST exclusion causes, reconfigure action |
| 13 | `13_publication_outcome_expired_410.png` | 1440px | HTTP 410 cache eviction card, preserved configuration re-preview |
| 14 | `14_publication_outcome_consumed_replay_409.png`| 1440px| Replay prevention card, token invalidation explanation |
| 15 | `15_publication_outcome_server_error_uncertain_500.png`| 1440px| HTTP 500 uncertain outcome card, read-after-write reconciliation link |
| 16 | `16_confirmed_timetable_desktop_1440.png`| 1440px | Confirmed timetable grid with drive/company filters and pagination |
| 17 | `17_confirmed_timetable_mobile_390.png` | 390px | Mobile stacked appointment cards with interview briefing triggers |
| 18 | `18_confirmed_session_detail_dialog.png` | 1440px | Session briefing modal with candidate, interviewer, room, and status |

---

## 10. Automated Test Matrix

- **TypeScript Compilation**: `cmd /c npm run build` -> 0 errors.
- **ESLint**: `cmd /c npm run lint` -> 0 errors.
- **Vitest Unit & Contract Suite**: `cmd /c npm run test` -> **8 test files, 134 passed out of 134 (100% green)**:
  - `src/test/scheduling-contract.test.ts`: 22 tests (Zod validation, default durations, invalid time windows, empty allocations, unscheduled candidates, instant parsing, LocalDateTime parsing, malformed payload rejection, publication outcome state machine).
  - `src/test/scheduling-components.test.tsx`: 10 tests (rendering configuration panel, resource pool indicators, simulation mode switching, timeline grid rendering, accessible table rendering, publication confirmation modal, publication outcome displays).
  - `src/test/officer-contract.test.ts`: 24 tests (prior regressions preserved).
  - `src/test/contract-validation.test.ts`: 49 tests (prior regressions preserved).
  - `src/test/officer-components.test.tsx`: 7 tests.
  - `src/test/student.test.tsx`: 9 tests.
  - `src/test/components.test.tsx`: 9 tests.
  - `src/test/api-client.test.ts`: 4 tests.
- **Playwright End-to-End Suite**: `cmd /c npx playwright test tests/e2e/scheduling-studio.spec.ts` -> **9 passed out of 9 (100% green)**:
  - Multi-viewport configuration testing (1440px, 1920px, 1024px, 768px, 390px).
  - Advisory preview with timeline grid and tabular view switching.
  - Unscheduled candidate section display.
  - Publication confirmation modal workflow.
  - Publication outcomes (200, 207, 409, 410, replay, 500).
  - Confirmed timetable filtering and pagination.
  - Confirmed session detail inspection modal.
  - Token hygiene audit (zero `previewId` in URL or storage).
  - Automated Axe accessibility audits.

---

## 11. Integration Status

**Current Status: `SCHEDULING STUDIO COMPLETE — LIVE INTEGRATION PENDING`**

The frontend implementation strictly adheres to the Java REST contract and GreedScheduler architecture. In accordance with safety policies, destructive actions against unknown databases are prohibited. Real HTTP execution against live Spring Boot with PostgreSQL remains pending until an explicitly provisioned, disposable integration database is authorized.
