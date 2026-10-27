# HireFlow Machine-Grounded Frontend API Contract (CAP-7 Baseline)

This document specifies the authoritative REST API contract implemented by the Spring Boot backend (`hireflow-api`, Spring Boot parent version 4.1.1, Java 21) at commit `bdbf0e1dce7407ab9034f893712ccea8d7fced56` (v4.0.0-cap7).

---

## 1. Global Architectural & Security Policies

1. **Base URL**: Default local development backend runs at `http://localhost:8081`. The frontend React application accesses API routes under `/api/v1/*`.
2. **Session Authentication**:
   - Backed by Spring Security session cookies (`JSESSIONID`).
   - Cookie management requires `credentials: 'include'` on all fetch/XHR requests.
   - Session fixation protection rotates `JSESSIONID` upon successful login.
3. **CSRF Protection & Token Lifecycle**:
   - All state-changing HTTP requests (`POST`, `PUT`, `DELETE`) require the `X-CSRF-TOKEN` header.
   - The token must be fetched via `GET /api/v1/auth/csrf` on application bootstrap.
   - **Session Rotation Invariant**: Upon successful login (`POST /api/v1/auth/login`), Spring Security invalidates the session and rotates the CSRF token. The frontend MUST immediately refresh its CSRF state via `GET /api/v1/auth/csrf`.
   - **No Blind Retries**: Do not blindly retry failed mutations on HTTP 403. Classify the error (session expired, invalid token, or forbidden role) and prompt the user or handle session expiration safely.
   - **Privacy Policy**: Keep preview identifiers and CSRF tokens out of browser URLs, query parameters, console logs, analytics payloads, error reporting, screenshots, and persistent storage (`localStorage`/`sessionStorage`). Store strictly in memory.
4. **Error Envelope**:
   - Uniform `ApiErrorResponse` record:
     ```json
     {
       "status": 409,
       "error": "Conflict",
       "message": "Double-booking detected for interviewer ...",
       "path": "/api/v1/officer/scheduling/publish",
       "timestamp": "2026-10-19T01:30:00Z"
     }
     ```
5. **Pagination**:
   - Standard envelope `PagedResponse<T>` (used on `GET /api/v1/student/applications`, `GET /api/v1/companies`, `GET /api/v1/drives`, etc.):
     ```json
     {
       "content": [ ... ],
       "page": 0,
       "size": 20,
       "totalElements": 45,
       "totalPages": 3,
       "last": false
     }
     ```
   - Standard query parameters: `page` (0-indexed, default 0), `size` (default 20, max bounded).
   - **Critical Distinction**: Some student endpoints return a raw JSON `List<T>` array (e.g. `GET /api/v1/student/drives` and `GET /api/v1/student/interviews`), NOT `PagedResponse<T>`.

---

## 2. Complete 40-Route Machine Inventory

### Group 1: Authentication & Health (5 routes)
- `POST /api/v1/auth/login`
  - Request: `{ "username": "string", "password": "string" }`
  - Response (200): `UserProfileDto { username, displayName, role, studentId, lastLoginAt }`
  - Headers: Requires `X-CSRF-TOKEN`. Rotates `JSESSIONID`. Frontend refreshes CSRF immediately after.
- `POST /api/v1/auth/logout`
  - Request: None
  - Response (200): `{ "message": "Logged out successfully" }`
  - Headers: Requires `X-CSRF-TOKEN`. Invalidates `JSESSIONID`. Clears client auth store and caches.
- `GET /api/v1/auth/me`
  - Response (200): `UserProfileDto` (current authenticated principal)
  - Response (401): Unauthenticated
- `GET /api/v1/auth/csrf`
  - Response (200): `{ "token": "string", "parameterName": "_csrf", "headerName": "X-CSRF-TOKEN" }`
  - Permits public unauthenticated invocation to bootstrap CSRF cookie/token.
- `GET /api/v1/health`
  - Response (200): `{ "status": "UP", "timestamp": "...", "database": "CONNECTED", "version": "1.0.0" }`

### Group 2: Public Catalogs (4 routes, Public Read-Only)
- `GET /api/v1/companies` (Params: `page`, `size`) -> `PagedResponse<CompanyDto>`
- `GET /api/v1/companies/{id}` -> `CompanyDto { id, name, industry, website, contactEmail }`
- `GET /api/v1/drives` (Params: `page`, `size`) -> `PagedResponse<RecruitmentDriveDto>`
- `GET /api/v1/drives/{id}` -> `RecruitmentDriveDto { id, companyId, companyName, jobTitle, jobDescription, minCgpa, eligibleBranches, applicationDeadline, status }`

### Group 3: Student Workflows (9 routes, Requires Role: `STUDENT`)
- `GET /api/v1/student/dashboard`
  - Response (200): `StudentDashboardDto { profile: StudentProfileDto, totalEligibleDrives: long, totalApplications: long, shortlistedCount: long, recentApplications: List<StudentApplicationDetailDto> }`
- `GET /api/v1/student/profile`
  - Response (200): `StudentProfileDto { id: string, name: string, email: string, branch: string, cgpa: double, graduationYear: int, active: boolean }`
  - Identifier is `id`, NOT `studentId`.
  - Read-only institutional record. No external registrar integration claims or fake registrar seals.
- `GET /api/v1/student/drives`
  - Response (200): `List<StudentDriveItemDto>` (Raw JSON Array — NOT PagedResponse; no `page`/`size` parameters on backend controller)
  - Item Fields: `id: string`, `companyId: string`, `companyName: string`, `jobTitle: string`, `minimumCgpa: double`, `eligibleBranches: Set<string>`, `applicationDeadline: LocalDate ("YYYY-MM-DD")`, `closed: boolean`, `deadlinePassed: boolean`, `hasApplied: boolean`, `existingApplicationId: string | null`, `existingApplicationStatus: ApplicationStatus | null`, `academicallyEligible: boolean`, `acceptingApplications: boolean`, `canApply: boolean`, `eligibilityReasons: List<string>`
  - Note: Identifier is `id`, NOT `driveId`. Apply condition is `canApply`, NOT `eligible`.
- `GET /api/v1/student/drives/{id}/eligibility`
  - Response (200): `StudentDriveEligibilityDto { driveId: string, studentId: string, academicallyEligible: boolean, acceptingApplications: boolean, alreadyApplied: boolean, canApply: boolean, existingApplicationId: string | null, existingApplicationStatus: ApplicationStatus | null, minimumCgpa: double, studentCgpa: double, eligibleBranches: Set<string>, studentBranch: string, applicationDeadline: LocalDate ("YYYY-MM-DD"), closed: boolean, deadlinePassed: boolean, reasons: List<string> }`
  - Note: Authoritative field name is `reasons`, NOT `eligibilityReasons`.
- `POST /api/v1/student/applications`
  - Request: `ApplicationSubmitRequestDto { "driveId": "string" }`
  - Response (201 Created): `StudentApplicationDetailDto { id: string, studentId: string, driveId: string, companyId: string, companyName: string, jobTitle: string, status: ApplicationStatus, appliedAt: LocalDateTime, remarks: string | null }`
  - Identifier is `id`, NOT `applicationId`. `appliedAt` is Java `LocalDateTime` (serialized as offset-free ISO string `YYYY-MM-DDTHH:mm:ss`, without `Z` or timezone offset).
  - Response (400/409): Ineligible or duplicate application conflict.
  - Requires: `X-CSRF-TOKEN`
- `GET /api/v1/student/applications` (Params: `page`, `size`)
  - Response (200): `PagedResponse<StudentApplicationDetailDto>`
  - Envelope: `content`, `page`, `size`, `totalElements`, `totalPages`, `last`
- `GET /api/v1/student/applications/{id}`
  - Response (200): `StudentApplicationDetailDto`
- `GET /api/v1/student/interviews`
  - Response (200): `List<StudentInterviewDto>` (Raw JSON Array — NOT PagedResponse; no server pagination parameters)
  - Item Fields: `sessionId: string`, `applicationId: string`, `studentId: string`, `studentName: string`, `studentBranch: string`, `driveId: string`, `jobTitle: string`, `companyName: string`, `interviewerId: string`, `interviewerName: string`, `roomId: string`, `roomName: string`, `startTime: LocalDateTime`, `endTime: LocalDateTime`, `status: ApplicationStatus`
  - Note on Status: `status` is a Java `String` populated by `StudentController` using the related application's `app.getStatus().name()`. Permitted values are the complete 7 `ApplicationStatus` enum constants (`APPLIED`, `ELIGIBLE`, `INELIGIBLE`, `SHORTLISTED`, `SCHEDULED`, `REJECTED`, `SELECTED`). There is no separate interview-status enum.
  - Note on Timestamps: `startTime` and `endTime` preserve Java `LocalDateTime` offset-free serialization semantics (no timezone offset or UTC assumption unless backend provides).
- `GET /api/v1/student/interviews/{id}`
  - Response (200): `StudentInterviewDto`

### Group 4: Placement Officer Workflows (22 routes, Requires Role: `PLACEMENT_OFFICER` or `ADMINISTRATOR`)
- **Company Management (4 routes):**
  - `GET /api/v1/officer/companies` (Params: `page`, `size`, `search`) -> `PagedResponse<OfficerCompanyDetailDto>`
  - `GET /api/v1/officer/companies/{id}` -> `OfficerCompanyDetailDto { id: string, name: string, industry: string, activeDrivesCount: long, totalDrivesCount: long }`
  - `POST /api/v1/officer/companies` (Req: `OfficerCompanyCreateDto { id: string, name: string, industry: string }`) -> 201 Created
  - `PUT /api/v1/officer/companies/{id}` (Req: `OfficerCompanyUpdateDto { name: string, industry: string }`) -> 200 OK
  - *Constraints*: Company identifier `id` is uppercase, alphanumeric, max 20 chars, and immutable upon creation.
- **Recruitment Drive Management (5 routes):**
  - `GET /api/v1/officer/drives` (Params: `page`, `size`, `companyId`, `activeOnly`) -> `PagedResponse<OfficerDriveDetailDto>`
  - `GET /api/v1/officer/drives/{id}` -> `OfficerDriveDetailDto { id: string, companyId: string, companyName: string, jobTitle: string, minimumCgpa: double, eligibleBranches: Set<string>, applicationDeadline: LocalDate ("YYYY-MM-DD"), closed: boolean, totalApplications: long, shortlistedApplications: long }`
  - `POST /api/v1/officer/drives` (Req: `OfficerDriveCreateDto { id: string, companyId: string, jobTitle: string, minimumCgpa: double, eligibleBranches: Set<string>, applicationDeadline: LocalDate }`) -> 201 Created
  - `PUT /api/v1/officer/drives/{id}` (Req: `OfficerDriveUpdateDto { jobTitle: string, minimumCgpa: double, eligibleBranches: Set<string>, applicationDeadline: LocalDate }`) -> 200 OK
  - `POST /api/v1/officer/drives/{id}/close` -> 200 OK (Authoritative drive closure; irreversible; sets `closed = true`)
  - *Constraints*: No salary, package, or interview rounds in CAP-7 backend schema. Identifier `id` and `companyId` are immutable during editing.
- **Application Review & Individual Shortlisting (3 routes):**
  - `GET /api/v1/officer/drives/{id}/applications` (Params: `page`, `size`, `status`) -> `PagedResponse<OfficerApplicationReviewDto>`
  - `GET /api/v1/officer/applications/{id}` -> `OfficerApplicationReviewDto { id: string, studentId: string, studentName: string, studentEmail: string, branch: string, cgpa: double, driveId: string, jobTitle: string, companyName: string, status: ApplicationStatus, appliedAt: LocalDateTime, remarks: string | null }`
  - `POST /api/v1/officer/applications/{id}/shortlist` -> 200 OK (Transitions candidate from `APPLIED` / `ELIGIBLE` -> `SHORTLISTED`)
  - *Constraints*: Only individual candidate shortlisting is supported. Bulk status transitions or arbitrary status editing endpoints do NOT exist on the backend.
- **Resource Administration (6 routes):**
  - `GET /api/v1/officer/interviewers` -> `List<OfficerInterviewerDetailDto>` (Raw JSON Array — NOT PagedResponse; no server pagination parameters)
    - Item Fields: `id: string, name: string, companyId: string, companyName: string`
  - `POST /api/v1/officer/interviewers` (Req: `OfficerInterviewerCreateDto { id: string, name: string, companyId: string }`) -> 201 Created
  - `PUT /api/v1/officer/interviewers/{id}` (Req: `OfficerInterviewerUpdateDto { name: string, companyId: string }`) -> 200 OK
  - `GET /api/v1/officer/rooms` -> `List<OfficerRoomDetailDto>` (Raw JSON Array — NOT PagedResponse; no server pagination parameters)
    - Item Fields: `id: string, name: string, capacity: int`
  - `POST /api/v1/officer/rooms` (Req: `OfficerRoomCreateDto { id: string, name: string, capacity: int }`) -> 201 Created
  - `PUT /api/v1/officer/rooms/{id}` (Req: `OfficerRoomUpdateDto { name: string, capacity: int }`) -> 200 OK
  - *Constraints*: Resource lists return raw JSON arrays. Deletion or availability scheduling endpoints do not exist in CAP-7 management routes. Room capacity must be a positive integer (`min = 1`).
- **Interview Scheduling Engine (4 routes — CAP-8.4 Completed):**
  - `POST /api/v1/officer/scheduling/preview`
    - Request: `OfficerSchedulingPreviewRequestDto { driveId: string, date: LocalDate ("YYYY-MM-DD"), startTime: LocalTime ("HH:mm"), endTime: LocalTime ("HH:mm"), durationMinutes?: number (1-240, default: 45), bufferMinutes?: number (0-120, default: 15), interviewerIds?: string[], roomIds?: string[] }`
    - Response (200 OK): `OfficerSchedulingPreviewResponseDto { previewId: string, driveId: string, jobTitle: string, companyName: string, date: LocalDate, startTime: LocalTime, endTime: LocalTime, durationMinutes: number, bufferMinutes: number, expiresAt: Instant, totalRequested: number, totalScheduled: number, totalUnscheduled: number, proposedSessions: ProposedSessionDto[], unscheduledCandidates: UnscheduledCandidateDto[] }`
    - Constraints: Evaluated in-memory with 15-minute TTL (`expiresAt`). Returns offset-free campus `LocalDateTime` appointment slots (`YYYY-MM-DDTHH:mm:ss`).
  - `POST /api/v1/officer/scheduling/publish`
    - Request: `OfficerSchedulingPublishRequestDto { previewId: string }`
    - Response (200 OK / 207 Multi-Status): `OfficerPublicationSummaryDto { attemptId: string, previewId: string, previewReference: string | null, driveId: string, jobTitle: string, companyName: string, outcome: PublicationOutcome, recoveryInstructions: string | null, publishedAt: Instant, totalProposals: number, publishedCount: number, skippedDuplicatesCount: number, conflictsCount: number, errorsCount: number, unattemptedCount: number, confirmedSessionIds: string[], publishedSessions: PublishedSessionDetailDto[], skippedDuplicateApplicationIds: string[], conflictRejections: PublicationConflictDetailDto[], infrastructureErrors: string[] }`
    - Status Codes: 200 (`ALL_CONFIRMED`), 207 (`PARTIAL_SUCCESS`), 409 (`NONE_CONFIRMED` or already consumed), 410 (`EXPIRED`), 5xx (`UNCERTAIN`).
    - Constraints: Atomically consumes the preview token from cache. Non-atomic per-session persistence against PostgreSQL GiST exclusion constraints.
  - `GET /api/v1/officer/scheduling/published` (Params: `driveId`, `companyId`, `date`, `page`, `size`)
    - Response (200 OK): `PagedResponse<OfficerInterviewDetailDto>`
  - `GET /api/v1/officer/scheduling/published/{id}`
    - Response (200 OK): `OfficerInterviewDetailDto { sessionId: string, applicationId: string, studentId: string, studentName: string, studentBranch: string, driveId: string, jobTitle: string, companyName: string, interviewerId: string, interviewerName: string, roomId: string, roomName: string, startTime: LocalDateTime, endTime: LocalDateTime, status: ApplicationStatus }`
    - Constraints: Identifier is `sessionId`. `status` reflects the authoritative `ApplicationStatus` of the associated application.

---

## 3. Runtime Contract Validation & Live Integration Status (CAP-8.5 Baseline)

1. **Zod Runtime Invariants (`src/features/student/api/schemas.ts`)**:
   - The frontend validates incoming server payloads at network boundaries using Zod schemas (`StudentDriveItemArraySchema`, `StudentInterviewArraySchema`, `PagedStudentApplicationResponseSchema`, `StudentDriveEligibilitySchema`, `StudentProfileSchema`, `StudentDashboardSchema`).
   - If an endpoint returns a malformed response or non-conforming shape, validation fails loudly with a typed error rather than silently rendering an empty or corrupted view.

2. **Authoritative Field Names Verified Against Backend Models**:
   - Drive Item identifier is `id` (NOT `driveId`).
   - Drive Item eligibility flag is `canApply` (NOT `eligible`).
   - Drive Eligibility collection is `reasons` (NOT `eligibilityReasons`).
   - Application Detail identifier is `id` (NOT `applicationId`).
   - Profile identifier is `id` (NOT `studentId`).
   - Scheduling Session identifier is `sessionId` (NOT `interviewId`).

3. **CAP-8.4 Contract Resolutions (B1, B2, B3)**:
   - **B1 (404 Not Found vs 410 Gone)**:
     - When the Spring Boot backend restarts, the in-memory preview cache is wiped clean. Requests to publish an evicted or unknown preview produce a `PreviewNotFoundException`, mapped to `HTTP 404 Not Found`.
     - An expired preview that still resides in cache produces a `PreviewExpiredException`, mapped to `HTTP 410 Gone`.
     - The frontend treats both status codes safely by classifying them into dedicated outcome states (`NOT_FOUND_EVICTED` and `EXPIRED`), informing the officer that the preview is invalid and offering a single-click action to generate a fresh preview.
   - **B2 (Preview Consumption & Replay Defense)**:
     - When publication is triggered, `SchedulingPreviewCacheService.java` atomically marks the cached preview as consumed. It does not delete the cache entry immediately.
     - Any replay attempt produces a `PreviewConsumedException`, mapped to `HTTP 409 Conflict`.
     - The frontend explicitly distinguishes this replay error from domain GiST conflicts (`NONE_CONFIRMED`), displaying a dedicated consumed-preview banner that directs the officer to the confirmed timetable.
   - **B3 (Design-Token Verification)**:
     - Verified that `tokens.css` and `index.css` strictly reflect the approved Institutional × Contemporary palette:
       - **Midnight Navy**: `#17324B` (`--color-navy-900`)
       - **Academic Teal**: `#176B69` (`--color-teal-600`)
       - **Warm Ivory**: `#F6F4EE` (`--color-ivory-100`)
       - **Muted Brass**: `#C6A46B` (`--color-brass-500`)
     - An earlier documentation discrepancy in CAP-8.4 has been resolved: zero CSS drift exists in the codebase.

4. **Live Integration Verification Status**:
   - **`CONTRACT VERIFIED — REAL SPRING BOOT & POSTGRESQL ACCEPTED`**
   - Verified live over HTTP against real Spring Boot 4.1.1 API (port 8081) backed by PostgreSQL 16 (`hireflow_test` on port 5432).
   - Real transaction persistence confirmed for Student authentication, CSRF exchange, application submission, candidate shortlisting, GreedyScheduler preview generation, publication persistence into `interview_sessions`, and student timetable visibility.
   - Zero test bypasses, zero mock shortcuts in the live integration harness.

