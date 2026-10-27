# HireFlow Machine-Grounded Frontend API Contract (CAP-7 Baseline)

This document represents the authoritative, machine-verified REST API contract provided by the Spring Boot backend (`hireflow-api`) at commit `bdbf0e1dce7407ab9034f893712ccea8d7fced56` (v4.0.0-cap7).

---

## 1. Global Architectural & Security Policies

1. **Base URL**: Default local development backend runs at `http://localhost:8081`. The frontend React application accesses API routes under `/api/v1/*`.
2. **Session Authentication**:
   - Backed by Spring Security session cookies (`JSESSIONID`).
   - Cookie management requires `credentials: 'include'` (or `withCredentials: true`) on all fetch/XHR requests.
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
   - Standard envelope `PagedResponse<T>`:
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

---

## 2. Complete 40-Route Machine Inventory

### Group 1: Authentication & Health (5 routes)
- `POST /api/v1/auth/login`
  - Request: `{ "username": "string", "password": "string" }`
  - Response (200): `UserProfileDto { username, displayName, role, studentId, lastLoginAt }`
  - Headers: Requires `X-CSRF-TOKEN`. Rotates `JSESSIONID`.
- `POST /api/v1/auth/logout`
  - Request: None
  - Response (200): `{ "message": "Logged out successfully" }`
  - Headers: Requires `X-CSRF-TOKEN`. Invalidates `JSESSIONID`.
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
  - `status` is populated by `StudentController` from the underlying application's `ApplicationStatus.name()`. All 7 valid `ApplicationStatus` values (`APPLIED`, `ELIGIBLE`, `INELIGIBLE`, `SHORTLISTED`, `SCHEDULED`, `REJECTED`, `SELECTED`) are handled safely.
  - Preserves Java `LocalDateTime` serialization semantics (offset-free ISO local date-time string `YYYY-MM-DDTHH:mm:ss`, no `Z` or timezone offset).
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

## 3. Feature-to-Endpoint Compatibility Matrix

| User Capability / Workflow | Backend Endpoint Support | Classification | Frontend Implementation Strategy |
| :--- | :--- | :--- | :--- |
| **Authentication & Profile** | `POST /login`, `GET /me`, `POST /logout` | **Fully Supported** | Session-cookie backed authentication store with automatic `/me` hydration on startup. Refresh CSRF post-login. |
| **CSRF Handling** | `GET /auth/csrf` + header `X-CSRF-TOKEN` | **Fully Supported** | In-memory token management. Rotated on login. Safe error classification on 403 without blind retries. |
| **Browse Drives (Student)** | `GET /student/drives` | **Fully Supported** | Bounded paginated catalog with branch/CGPA pre-filtering. |
| **Inspect Eligibility Reasons** | `GET /student/drives/{id}/eligibility` | **Fully Supported** | Structured breakdown card showing CGPA, branch, deadline, and boolean reasons list. |
| **Apply to Drive** | `POST /student/applications` | **Fully Supported** | Single-click apply with modal confirmation and instant local status update. |
| **Student Timetable** | `GET /student/interviews`, `GET /student/interviews/{id}` | **Fully Supported** | Chronological interview timeline with company, interviewer, room, and start/end slots. |
| **Edit Student Profile** | **None** (`PUT /student/profile` does not exist) | **Read-Only** | Display institutional profile as immutable record. No fake registrar integrations or seals. |
| **Create/Update Companies** | `POST /officer/companies`, `PUT /officer/companies/{id}` | **Fully Supported** | Modal drawer form with validation. |
| **Delete Companies** | **None** (`DELETE` endpoint does not exist) | **Unsupported** | Do not render delete action; display active status badge instead. |
| **Close Recruitment Drive** | `POST /officer/drives/{id}/close` | **Fully Supported** | "Close Drive" action with confirmation dialog. |
| **Delete Recruitment Drive** | **None** (`DELETE` endpoint does not exist) | **Unsupported** | Do not render delete button; closing drive is the authoritative termination action. |
| **Candidate Shortlisting** | `POST /officer/applications/{id}/shortlist` | **Fully Supported** | Candidate table action row with optimistic UI update to `SHORTLISTED`. |
| **Bulk Status Updates** | **None** (`/bulk-status` does not exist) | **Unsupported** | **Explicitly Unsupported**: No client-side bulk shortlisting loops. Individual row shortlisting only. |
| **Reject Application** | **None** (Only `/shortlist` exists in REST) | **Unsupported** | Do not render reject buttons; unshortlisted candidates remain in `APPLIED` status. |
| **Register Rooms & Interviewers**| `POST/PUT /officer/rooms`, `POST/PUT /officer/interviewers` | **Fully Supported** | Resource management cards with inline editing. |
| **Generate Scheduling Preview**| `POST /officer/scheduling/preview` | **Fully Supported** | Parameter form triggering ephemeral preview with 15-minute TTL. Tokens kept strictly in memory. |
| **Drag-and-Drop Slot Rearranging**| **None** (Backend expects preview token publication) | **Unsupported** | **Explicitly Reject**: Timetable is generated algorithmically by GreedyScheduler. Preview is immutable. |
| **Publish Timetable** | `POST /officer/scheduling/publish` | **Fully Supported** | Transmits previewId; handles per-session persistence outcomes: 200 (all), 207 (partial), and 409 (none). |
| **Inspect Published Timetables**| `GET /officer/scheduling/published` | **Fully Supported** | Daily/drive filtered schedule grid with session inspection. |
