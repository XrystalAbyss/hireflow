# Placement Officer Command Center — Architecture & Operations Guide (CAP-8.3)

## 1. Executive Summary

Milestone **CAP-8.3** delivers HireFlow's complete **Placement Officer Command Center** and recruitment administration suite. Grounded strictly in the authoritative CAP-7 Spring Boot backend (`v4.0.0-cap7`, commit `bdbf0e1dce7407ab9034f893712ccea8d7fced56`), this implementation covers all **18 officer management endpoints** across corporate partner management, recruitment drive lifecycles, candidate review & shortlisting, and campus interview resource pools.

In accordance with milestone specifications, the four algorithmic scheduling endpoints are explicitly deferred to **CAP-8.4**.

---

## 2. Institutional Architecture & Route Inventory

The Officer Portal is nested under `/officer/*` and wrapped by `OfficerLayout.tsx`, enforcing authenticated institutional access for users with role `PLACEMENT_OFFICER` or `ADMINISTRATOR`. If a student attempts to navigate directly to `/officer/*`, the system denies access and renders a clear administrative notice with a redirect link back to the Student Portal.

### Implemented Route Directory

| Path | Component | Description |
| :--- | :--- | :--- |
| `/officer` | `Navigate to /officer/dashboard` | Root redirect to dashboard |
| `/officer/dashboard` | `OfficerDashboardPage` | Operational command center overview, active campaigns table, and shortlisting workload box |
| `/officer/companies` | `OfficerCompaniesPage` | Searchable, paginated corporate partner directory with registration modal |
| `/officer/companies/:id` | `OfficerCompanyDetailPage` | Corporate dossier with active campaigns and registered corporate assessment leads |
| `/officer/drives` | `OfficerDrivesPage` | Campus recruitment campaign directory with status/company filters and launch modal |
| `/officer/drives/:id` | `OfficerDriveDetailPage` | Detailed recruitment drive dossier with academic cutoff rules and closure dialog |
| `/officer/drives/:id/applications` | `OfficerCandidateReviewPage` | High-density candidate review roster with status filter tabs and shortlisting action |
| `/officer/applications/:id` | `OfficerApplicationDetailPage` | Full-page candidate application dossier with honors CGPA indicator |
| `/officer/resources` | `OfficerResourcesPage` | Tabbed resource administration for Interviewers and Campus Interview Rooms |
| `/officer/scheduling` | `OfficerSchedulingStudioPage` | Flagship Interview Scheduling Studio with Algorithmic Preview, Dual Visualization, and Transactional Publication |

---

## 3. The 18 Management Endpoints & Exact Java DTO Mappings

### Domain 1: Corporate Partner Management (4 Endpoints)
1. `GET /api/v1/officer/companies` (Params: `page`, `size`, `search`): Returns `PagedResponse<OfficerCompanyDetailDto>`
2. `GET /api/v1/officer/companies/{id}`: Returns `OfficerCompanyDetailDto`
3. `POST /api/v1/officer/companies`: Accepts `OfficerCompanyCreateDto`, Returns `201 Created`
4. `PUT /api/v1/officer/companies/{id}`: Accepts `OfficerCompanyUpdateDto`, Returns `200 OK`

**Authoritative Record Structure**:
- `OfficerCompanyDetailDto`: `{ id: string, name: string, industry: string, activeDrivesCount: long, totalDrivesCount: long }`
- `OfficerCompanyCreateDto`: `{ id: string, name: string, industry: string }`
- `OfficerCompanyUpdateDto`: `{ name: string, industry: string }`
*(Note: Corporate identifier `id` is uppercase, alphanumeric, max 20 chars, and immutable upon creation).*

### Domain 2: Recruitment Drive Management (5 Endpoints)
5. `GET /api/v1/officer/drives` (Params: `page`, `size`, `companyId`, `activeOnly`): Returns `PagedResponse<OfficerDriveDetailDto>`
6. `GET /api/v1/officer/drives/{id}`: Returns `OfficerDriveDetailDto`
7. `POST /api/v1/officer/drives`: Accepts `OfficerDriveCreateDto`, Returns `201 Created`
8. `PUT /api/v1/officer/drives/{id}`: Accepts `OfficerDriveUpdateDto`, Returns `200 OK`
9. `POST /api/v1/officer/drives/{id}/close`: Returns `200 OK` (Authoritative campaign termination)

**Authoritative Record Structure**:
- `OfficerDriveDetailDto`: `{ id: string, companyId: string, companyName: string, jobTitle: string, minimumCgpa: double, eligibleBranches: Set<string>, applicationDeadline: LocalDate ("YYYY-MM-DD"), closed: boolean, totalApplications: long, shortlistedApplications: long }`
- `OfficerDriveCreateDto`: `{ id: string, companyId: string, jobTitle: string, minimumCgpa: double, eligibleBranches: Set<string>, applicationDeadline: LocalDate }`
- `OfficerDriveUpdateDto`: `{ jobTitle: string, minimumCgpa: double, eligibleBranches: Set<string>, applicationDeadline: LocalDate }`

### Domain 3: Application Review & Shortlisting (3 Endpoints)
10. `GET /api/v1/officer/drives/{id}/applications` (Params: `page`, `size`, `status`): Returns `PagedResponse<OfficerApplicationReviewDto>`
11. `GET /api/v1/officer/applications/{id}`: Returns `OfficerApplicationReviewDto`
12. `POST /api/v1/officer/applications/{id}/shortlist`: Transitions candidate application from `APPLIED`/`ELIGIBLE` -> `SHORTLISTED`.

**Authoritative Record Structure**:
- `OfficerApplicationReviewDto`: `{ id: string, studentId: string, studentName: string, studentEmail: string, branch: string, cgpa: double, driveId: string, jobTitle: string, companyName: string, status: ApplicationStatus, appliedAt: LocalDateTime, remarks: string | null }`
*(Note: `appliedAt` is serialized as an offset-free ISO string `YYYY-MM-DDTHH:mm:ss`, and status reflects the complete 7 `ApplicationStatus` enum).*

### Domain 4: Interview Resource Administration (6 Endpoints)
13. `GET /api/v1/officer/interviewers`: Returns `List<OfficerInterviewerDetailDto>` (**Raw JSON Array**, NOT `PagedResponse`)
14. `POST /api/v1/officer/interviewers`: Accepts `OfficerInterviewerCreateDto`, Returns `201 Created`
15. `PUT /api/v1/officer/interviewers/{id}`: Accepts `OfficerInterviewerUpdateDto`, Returns `200 OK`
16. `GET /api/v1/officer/rooms`: Returns `List<OfficerRoomDetailDto>` (**Raw JSON Array**, NOT `PagedResponse`)
17. `POST /api/v1/officer/rooms`: Accepts `OfficerRoomCreateDto`, Returns `201 Created`
18. `PUT /api/v1/officer/rooms/{id}`: Accepts `OfficerRoomUpdateDto`, Returns `200 OK`

**Authoritative Record Structure**:
- `OfficerInterviewerDetailDto`: `{ id: string, name: string, companyId: string, companyName: string }`
- `OfficerInterviewerCreateDto`: `{ id: string, name: string, companyId: string }`
- `OfficerInterviewerUpdateDto`: `{ name: string, companyId: string }`
- `OfficerRoomDetailDto`: `{ id: string, name: string, capacity: int }`
- `OfficerRoomCreateDto`: `{ id: string, name: string, capacity: int }`
- `OfficerRoomUpdateDto`: `{ name: string, capacity: int }`

---

## 4. Key Workflows & Operational Semantics

### 4.1 Authoritative Drive Closure
- Drive closure is an irreversible state transition performed via `POST /api/v1/officer/drives/{id}/close`.
- The frontend enforces a strict confirmation dialog (`DriveCloseDialog.tsx`) highlighting the consequences:
  - New student applications will be rejected.
  - All submitted applications remain preserved and reviewable.
  - Shortlisted candidates remain eligible for interview scheduling.
  - The closure is recorded in PostgreSQL and cannot be undone via REST.
- `DELETE /api/v1/officer/drives/{id}` does not exist; HireFlow does not allow deleting academic campaigns.

### 4.2 Candidate Review & Individual Shortlisting
- Candidate review is presented via `OfficerCandidateReviewPage.tsx` using a compact TanStack Table.
- Placement officers can inspect complete dossiers (`CandidateDetailDialog.tsx`) and invoke `POST /api/v1/officer/applications/{id}/shortlist`.
- **Bulk Shortlisting Policy**: The backend exposes no bulk-shortlisting endpoints. HireFlow strictly rejects fake client-side bulk loops that make repetitive concurrent requests. Officers review each applicant individually.

### 4.3 Interviewers and Rooms Directory
- Unlike drives or companies, interviewers and rooms return unbounded `List<T>` arrays.
- Client-side search and company filters operate strictly over this in-memory list without fabricating server pagination parameters.
- Room capacity is validated with `min="1"` positive integer constraints.

---

## 5. Security & CSRF Architecture

1. **Role-Based Guards**: `OfficerLayout.tsx` checks `user.role` from the authentication context. Student accounts are rejected before any officer endpoints are queried.
2. **CSRF Enforcement**: All POST/PUT mutations (`createCompany`, `updateCompany`, `createDrive`, `updateDrive`, `closeDrive`, `shortlistApplication`, `createInterviewer`, `updateInterviewer`, `createRoom`, `updateRoom`) include the `X-CSRF-TOKEN` header via `apiClient`.
3. **No Blind Retries**: Errors are surfaced transparently to the officer without looping retry storms.
4. **Development Isolation**: When the backend is offline in local development, controlled fixtures run with an explicit disclaimer log: `[HireFlow Dev] Live backend unreachable; using officer fixture`. Production builds fail loudly rather than silently substituting fixtures.

---

## 6. Verification & Automated Quality Evidence

### 6.1 Vitest Unit & Contract Suite
- **102 tests passed across 6 test suites**:
  - `officer-contract.test.ts`: 24 contract tests validating exact Java response shapes, PagedResponse envelopes, raw arrays, date formatting, and 409 conflict envelopes.
  - `contract-validation.test.ts`: 49 tests for student schemas and boundary guards.
  - `officer-components.test.tsx`: 7 component interaction tests for `CompanyDialog`, `DriveDialog`, `DriveCloseDialog`, and `OfficerStatCard`.
  - `student.test.tsx`: 9 tests.
  - `components.test.tsx`: 9 tests.
  - `api-client.test.ts`: 4 tests.

### 6.2 Playwright End-to-End Suite
- **26 tests passed across the complete application suite**:
  - `tests/e2e/officer-command-center.spec.ts`: 7 tests covering Command Center across viewports (1440px, 1920px, 768px, 390px), Corporate Partner Directory & Search, Recruitment Drive Directory & Closure, Candidate Review Roster & Shortlist Mutation, Resource Administration (Interviewers & Rooms), CAP-8.4 Deferred Page, and Mobile Hamburger Navigation.
  - `tests/e2e/student-hub.spec.ts`: 7 tests.
  - `tests/e2e/showcase.spec.ts`: 12 tests.

### 6.3 Accessibility Audits (Axe Core)
- Axe accessibility audits ran against the Command Center dashboard and Resource administration pages:
  - WCAG 2.1 AA tag compliance: **0 violations**.

---

## 7. Flagship Scope: CAP-8.4 Interview Scheduling Studio (Completed)

Milestone **CAP-8.4** completes the four algorithmic scheduling endpoints and delivers the full **Interview Scheduling Studio**:
- `POST /api/v1/officer/scheduling/preview`: Advisory preview generated by `GreedyScheduler` with 15-minute TTL.
- `POST /api/v1/officer/scheduling/publish`: Transactional publication with per-session persistence handling HTTP 200 (`ALL_CONFIRMED`), 207 (`PARTIAL_SUCCESS`), 409 (`NONE_CONFIRMED`), 410 (`EXPIRED`), and 5xx (`UNCERTAIN`).
- `GET /api/v1/officer/scheduling/published`: Confirmed timetable inspection with drive/company/date filters and standard `PagedResponse` pagination.
- `GET /api/v1/officer/scheduling/published/{id}`: Detailed confirmed session briefing dossier.

The route `/officer/scheduling` renders `OfficerSchedulingStudioPage.tsx`, presenting the **Studio** workspace (configuration form, multi-lane timeline grid, accessible data table, unscheduled candidates breakdown, publication modal, and outcome cards) and the **Confirmed Timetable** browser. Complete architectural specifications, DTO mappings, and state machine flows are documented in [scheduling-studio.md](./scheduling-studio.md).
