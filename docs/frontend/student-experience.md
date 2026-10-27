# HireFlow Student Opportunity Hub Architecture & Implementation (CAP-8.2.1)

This specification documents the complete Student Opportunity Hub frontend architecture, authoritative REST API mappings, UI/UX behavior, mobile responsiveness decisions, verification evidence, and backend baseline boundaries for HireFlow v4.0.0-cap7 (commit `bdbf0e1dce7407ab9034f893712ccea8d7fced56`, Spring Boot parent version 4.1.1, Java 21).

---

## 1. Architectural Overview & Route Inventory

The Student Opportunity Hub is implemented within `hireflow-app/src/features/student/` as an authentic, institution-grade experience for university candidates. It avoids generic job-portal or consumer marketplace aesthetics, adopting HireFlow's **Institutional × Contemporary** design system.

### 1.1 Complete Route Inventory

All student routes are secured under the authenticated `/student` prefix, wrapped by `StudentLayout` and `StudentShell`:

| Route | View Component | Description | Authoritative Endpoint(s) |
| :--- | :--- | :--- | :--- |
| `/student/dashboard` | `StudentDashboardPage` | Executive student landing view: academic profile banner, key recruitment metrics, urgent action notices, open drives preview, and recent applications. | `GET /api/v1/student/dashboard`<br>`GET /api/v1/student/profile` |
| `/student/drives` | `StudentDrivesPage` | Comprehensive recruitment opportunity directory with full-text search, department filter, quick-filter status tabs (`ALL`, `ELIGIBLE`, `APPLIED`, `INELIGIBLE`), and pass/fail indicators. Client-side pagination/filtering over raw JSON array. | `GET /api/v1/student/drives` (Raw array `List<StudentDriveItemDto>`)<br>`GET /api/v1/student/profile` |
| `/student/drives/:id` | `StudentDriveDetailPage` | Deep-dive recruitment drive dossier: verified job title, department eligibility, min CGPA, deadline, authoritative itemized eligibility scorecard, and submission launcher. | `GET /api/v1/student/drives/{id}/eligibility`<br>`GET /api/v1/student/drives` |
| `/student/applications` | `StudentApplicationsPage` | Complete student application history with status filtering (`ALL`, `APPLIED`, `SHORTLISTED`, `SCHEDULED`, `SELECTED`, `REJECTED`, `INELIGIBLE`), desktop tabular grid, and 390px mobile card layouts. | `GET /api/v1/student/applications` (PagedResponse) |
| `/student/applications/:id` | `StudentApplicationDetailPage` | Granular application inspection view with authoritative Status Summary and Audit Milestones view (covering all 7 non-linear states: `APPLIED`, `ELIGIBLE`, `INELIGIBLE`, `SHORTLISTED`, `SCHEDULED`, `REJECTED`, `SELECTED`), interview appointment link, and general candidate advisory notes. | `GET /api/v1/student/applications/{id}` |
| `/student/interviews` | `StudentInterviewsPage` | Confirmed interview timetable displaying verified appointments in chronological sequence, venue rooms, interviewers, and direct session dossier links. Raw JSON array with client-side sorting. | `GET /api/v1/student/interviews` (Raw array `List<StudentInterviewDto>`) |
| `/student/interviews/:id` | `StudentInterviewDetailPage` | Official interview session briefing card containing venue location, panel member, time window, applicant instructions, and candidate preparation guidelines. | `GET /api/v1/student/interviews/{id}` |

---

## 2. Authoritative Student API Contract & Data Transfer Objects

The frontend interacts strictly with the verified Spring Boot controller routes (`StudentController.java`) and their exact Java record DTO definitions. All incoming payloads are runtime-validated at network boundaries via Zod schemas (`src/features/student/api/schemas.ts`).

### 2.1 Mapped DTO Contracts

```typescript
// ApplicationStatus - All 7 Authoritative Backend Enum Values
type ApplicationStatus =
  | 'APPLIED'
  | 'ELIGIBLE'
  | 'INELIGIBLE'
  | 'SHORTLISTED'
  | 'SCHEDULED'
  | 'REJECTED'
  | 'SELECTED';

// StudentProfileDto (Identifier is id, NOT studentId)
interface StudentProfileDto {
  id: string;
  name: string;
  email: string;
  branch: string;
  cgpa: number;
  graduationYear: number;
  active: boolean;
}

// StudentDriveItemDto (Identifier is id, NOT driveId; condition is canApply, NOT eligible)
// Endpoint GET /api/v1/student/drives returns List<StudentDriveItemDto> (JSON Array)
interface StudentDriveItemDto {
  id: string;
  companyId: string;
  companyName: string;
  jobTitle: string;
  minimumCgpa: number;
  eligibleBranches: string[];
  applicationDeadline: string; // ISO-8601 date (YYYY-MM-DD)
  closed: boolean;
  deadlinePassed: boolean;
  hasApplied: boolean;
  existingApplicationId: string | null;
  existingApplicationStatus: ApplicationStatus | null;
  academicallyEligible: boolean;
  acceptingApplications: boolean;
  canApply: boolean;
  eligibilityReasons: string[];
}

// StudentDriveEligibilityDto (Authoritative Backend Breakdown; field is reasons, NOT eligibilityReasons)
interface StudentDriveEligibilityDto {
  driveId: string;
  studentId: string;
  academicallyEligible: boolean;
  acceptingApplications: boolean;
  alreadyApplied: boolean;
  canApply: boolean;
  existingApplicationId: string | null;
  existingApplicationStatus: ApplicationStatus | null;
  minimumCgpa: number;
  studentCgpa: number;
  eligibleBranches: string[];
  studentBranch: string;
  applicationDeadline: string; // ISO-8601 date (YYYY-MM-DD)
  closed: boolean;
  deadlinePassed: boolean;
  reasons: string[];
}

// StudentApplicationDetailDto (Identifier is id, NOT applicationId)
// Endpoint GET /api/v1/student/applications returns PagedResponse<StudentApplicationDetailDto>
interface StudentApplicationDetailDto {
  id: string;
  studentId: string;
  driveId: string;
  companyId: string;
  companyName: string;
  jobTitle: string;
  status: ApplicationStatus;
  appliedAt: string; // ISO-8601 offset-free LocalDateTime (e.g. "2026-10-19T01:30:00")
  remarks: string | null;
}

// StudentInterviewDto (Raw JSON Array List<StudentInterviewDto>)
interface StudentInterviewDto {
  sessionId: string;
  applicationId: string;
  studentId: string;
  studentName: string;
  studentBranch: string;
  driveId: string;
  jobTitle: string;
  companyName: string;
  interviewerId: string;
  interviewerName: string;
  roomId: string;
  roomName: string;
  startTime: string; // ISO-8601 offset-free LocalDateTime
  endTime: string;   // ISO-8601 offset-free LocalDateTime
  status: ApplicationStatus; // Derived from related ApplicationStatus.name()
}
```

---

## 3. Eligibility Breakdown & Evaluation Semantics

Eligibility determination is **strictly authoritative on the backend**. The frontend never calculates eligibility independently or overrides backend flags.

### 3.1 Backend Authoritative Verification
- The backend evaluates:
  1. `academicallyEligible = (studentCgpa >= minimumCgpa) && eligibleBranches.contains(studentBranch)`
  2. `acceptingApplications = !closed && !deadlinePassed`
  3. `alreadyApplied = (existingApplicationId != null)`
  4. `canApply = academicallyEligible && acceptingApplications && !alreadyApplied`
- The `EligibilityBreakdownCard` renders a 3-point scorecard:
  - **Academic CGPA Criterion**: Minimum required CGPA vs Candidate verified CGPA (rendered in JetBrains Mono).
  - **Department Qualification**: Eligible engineering branches vs Candidate registered department.
  - **Drive Window Status**: Application deadline date vs Current time, drive closure flag.
- When `canApply === false`, the component parses and displays specific institutional reasons directly from the backend's `reasons: string[]` collection (e.g. *"Cumulative GPA (7.40) does not satisfy the minimum required threshold of 8.00"*, *"Mechanical Engineering is not an approved department for this drive"*).

---

## 4. Application Submission Lifecycle & State Safety

### 4.1 Mutation Safeguards
- **Explicit Modal Confirmation (`ApplicationConfirmationModal`)**: Candidates cannot submit applications accidentally via a single click. The modal displays:
  - Exact target role and organization.
  - Candidate verified Roll Number, Department, and CGPA.
  - Legal declaration that credentials will be transmitted directly to the corporate recruitment panel.
- **CSRF Token Verification**: All `POST /api/v1/student/applications` requests transmit the in-memory `X-CSRF-TOKEN` header.
- **Authoritative Confirmation**: The UI remains in an accessible pending spinner (`isPending === true`) with disabled buttons until the Spring Boot backend returns `HTTP 201 Created` with the verified `StudentApplicationDetailDto`.
- **Query Cache Invalidation**: Upon receipt of `HTTP 201 Created`, TanStack Query immediately invalidates and refetches:
  - `['student', 'dashboard']`
  - `['student', 'drives']`
  - `['student', 'drives', driveId, 'eligibility']`
  - `['student', 'applications']`
- **Duplicate Submission Recovery & Conflict Handling**:
  - If a student attempts to submit an application for a drive already applied to, or if a concurrent session submits the application, the server responds with `HTTP 409 Conflict` or `HTTP 400 Bad Request`.
  - The frontend captures the `ApiErrorResponse` envelope and renders a clear conflict banner: *"Application submission conflict: A record for this candidate and drive already exists."*
  - The client refetches `useDriveEligibility(driveId)` and `useStudentApplications()` to reconcile authoritative state rather than blindly retrying the POST mutation.

---

## 5. Mobile & Responsive Design Decisions

In compliance with the **Institutional × Contemporary** design rules and the multi-viewport QA matrix:

1. **390px (Mobile Portrait)**:
   - Application History switches from a dense horizontal table to structured card rows with individual action links, preventing horizontal clipping.
   - Company name badges and status badges wrap gracefully (`flex-wrap gap-2`).
   - Action buttons expand to full width (`w-full`) with touch targets strictly $\ge 44\text{px}$.
   - The academic dossier banner collapses into a compact 2x2 grid.
2. **768px (Tablet)**:
   - Drive directory filters wrap smoothly.
   - Key dashboard metrics scale to a 2-column or 4-column balanced layout.
3. **1024px & 1440px (Laptop / Desktop)**:
   - Multi-column recruitment directory with side-by-side eligibility comparison.
   - Application history renders high-density tabular rows with monospace identifier tags.
4. **1920px (Wide Desktop)**:
   - Content max-width constrained to `max-w-7xl mx-auto` to preserve readability and optimal typographic measure.

---

## 6. Accessibility & ARIA Governance

- **Axe-Core Automated Compliance**: Zero violations under WCAG 2.1 AA across all student views.
- **Semantic Live Regions**: Asynchronous loading states and error notifications utilize `aria-live="polite"` or `role="alert"`.
- **Keyboard Navigation & Focus Trapping**: The `ApplicationConfirmationModal` implements Radix UI Dialog primitives with automated focus trap, `aria-labelledby`, `aria-describedby`, and Escape key dismissal.
- **Contrast Ratios**: Body text (`var(--color-navy-800)` on `var(--color-warm-ivory)` / white) achieves $> 7.5:1$ contrast ratio, exceeding the WCAG AA minimum of $4.5:1$.

---

## 7. Quality Assurance & Automated Test Inventory

The implementation is verified by 3 layers of automated tests:

### 7.1 Vitest Unit, Component & Independent Contract Tests (71 Tests Passing)
Located in `hireflow-app/src/test/`:
- `contract-validation.test.ts` (49 independent contract tests):
  - Validates raw JSON array parsing for `GET /api/v1/student/drives` (`StudentDriveItemArraySchema`).
  - Validates raw JSON array parsing for `GET /api/v1/student/interviews` (`StudentInterviewArraySchema`).
  - Validates paginated response envelope for `GET /api/v1/student/applications` (`PagedStudentApplicationResponseSchema`).
  - Enforces exact property names (`id`, `canApply`, `reasons`, `companyId`, `remarks`).
  - Handles nullable existing-application identifiers/statuses and nullable remarks.
  - Handles empty raw arrays and empty paginated results (`totalElements: 0, content: []`).
  - Validates all 7 `ApplicationStatus` enum values (`APPLIED`, `ELIGIBLE`, `INELIGIBLE`, `SHORTLISTED`, `SCHEDULED`, `REJECTED`, `SELECTED`).
  - Validates LocalDate (`YYYY-MM-DD`) and offset-free ISO LocalDateTime wire format (`YYYY-MM-DDTHH:mm:ss` and with subseconds).
  - Rejects timestamps with trailing `Z` or timezone offsets (`+HH:mm`) and invalid calendar dates/times.
  - Verifies deterministic date-time formatting without unintended timezone shifts.
  - Validates application-derived interview statuses (`SCHEDULED`, `SELECTED`, `SHORTLISTED`, `APPLIED`, `REJECTED`, `ELIGIBLE`, `INELIGIBLE`).
  - Strictly rejects invented interview-status strings (`COMPLETED`, `CANCELLED`, `CONFIRMED`, `UNKNOWN`) and ensures unknown statuses do not silently coerce to `SCHEDULED`.
  - Validates application submission ID extraction (`res.id`).
  - Rejects malformed payloads with descriptive Zod validation errors.
  - Validates uniform `ApiErrorResponse` envelopes for HTTP 400, 401, 403, 404, and 409 errors.
- `student.test.tsx`:
  - Renders StudentDossierBanner with read-only academic metrics.
  - Renders DriveCard in eligible state with primary Apply button.
  - Renders DriveCard in ineligible state with disabled CTA and badge.
  - Renders EligibilityBreakdownCard with pass/fail itemized criteria.
  - Shows disqualification reasons for ineligible candidates.
  - Renders InterviewSessionCard with formatted date/time and venue.
  - Opens ApplicationConfirmationModal and submits application.
  - Formats application status badges with correct semantic variants.
  - Handles empty drives gracefully with EmptyState.
- `apiClient.test.ts`: 7 tests verifying CSRF injection, session rotation, and 403 error isolation.
- `components.test.tsx`: 6 tests verifying UI primitives, Badges, and Dialogs.

### 7.2 Playwright End-to-End Suite (19 Tests Passing)
Located in `hireflow-app/tests/e2e/`:
- `student-hub.spec.ts`:
  - `student dashboard displays academic dossier and metrics`
  - `opportunity explorer allows searching and filtering drives`
  - `drive detail view renders comprehensive eligibility breakdown`
  - `application submission flow with confirmation modal and success state`
  - `my applications view displays history with status filters`
  - `interview timetable renders confirmed appointments and session briefing`
  - `responsive audit at 390px mobile viewport across student hub`
- `accessibility.spec.ts`:
  - `student dashboard page has zero axe-core accessibility violations`
  - `recruitment opportunity explorer has zero axe-core accessibility violations`
  - `drive detail page with eligibility breakdown has zero axe-core accessibility violations`
  - `application history page has zero axe-core accessibility violations`
  - `interview timetable page has zero axe-core accessibility violations`
- `showcase.spec.ts`: 7 tests covering component showcase, officer shell, and login flows.

---

## 8. Captured Screenshot Artifacts & Visual QA Reference

All 16 visual QA screenshots are archived in the session artifacts directory:

| Screenshot Artifact | Viewport | Scope / Description |
| :--- | :--- | :--- |
| `student_dashboard_desktop.png` | 1440x900 | Complete student dashboard with dossier banner, KPI metrics, drive previews, and applications. |
| `student_dashboard_mobile.png` | 390x844 | Mobile student dashboard verifying clean 2x2 metric wrap and vertical stack. |
| `student_opportunities_desktop.png` | 1440x900 | Opportunity directory with search bar, department selector, and status tabs. |
| `student_opportunities_mobile.png` | 390x844 | Mobile opportunity directory with full-width action buttons and card layouts. |
| `student_drive_details_desktop.png` | 1440x900 | Deep-dive recruitment drive requirements dossier and eligibility scorecard. |
| `student_eligibility_breakdown.png` | 1440x900 | Detailed pass/fail breakdown card comparing CGPA, Department, and Deadline. |
| `student_application_confirmation_modal.png` | 1440x900 | Radix-backed confirmation modal with candidate declaration and submit action. |
| `student_submission_success_state.png` | 1440x900 | Application success state showing confirmed Application ID and link to applications. |
| `student_ineligible_drive_explanation.png` | 1440x900 | Ineligible drive detail explaining specific CGPA shortfall and branch exclusions. |
| `student_my_applications_desktop.png` | 1440x900 | Tabular application directory with status pills and pagination controls. |
| `student_my_applications_mobile.png` | 390x844 | Mobile application list formatted as clean stacked cards. |
| `student_application_detail_progress.png` | 1440x900 | Individual application status summary and audit milestones view with non-linear notice. |
| `student_interview_timetable_desktop.png` | 1440x900 | Chronological confirmed interview schedule with date, time, room, and panelist. |
| `student_interview_timetable_mobile.png` | 390x844 | Mobile interview schedule cards with access guidelines and session briefing link. |
| `student_interview_detail_briefing.png` | 1440x900 | Formal interview session briefing dossier with room venue and general advisory guidance. |
| `student_empty_state_opportunities.png` | 1440x900 | Calibrated empty state when filtering yields zero matching recruitment drives. |

---

## 9. Known Limitations, Unsupported Features & Live Backend Integration Status

In strict adherence to the authoritative CAP-7 backend baseline (`bdbf0e1dce7407ab9034f893712ccea8d7fced56`), the following capabilities are **explicitly not supported by backend routes** and are intentionally omitted from the frontend:

1. **Student Profile Editing**: Academic records (CGPA, Branch, Student ID, Email) are official institutional registrar data. The backend provides `GET /api/v1/student/profile` but no `PUT` or `PATCH` endpoint. Candidates are instructed to contact the University Placement Cell or Registrar's Office for corrections.
2. **Application Withdrawal / Revocation**: The CAP-7 API provides no `DELETE /api/v1/student/applications/{id}` route. Once submitted, applications enter the institutional recruitment pipeline and cannot be retracted by students.
3. **Interview Rescheduling / Cancellation by Students**: Timetable allocations are managed algorithmically and confirmed by placement officers (`GET /api/v1/student/interviews`). There is no student endpoint to modify, cancel, or swap interview slots.
4. **Document / Resume Upload**: CAP-7 does not implement a multi-part file upload or blob storage service for student resumes. Student qualifications are verified through their institutional academic dossier.
5. **Drag-and-Drop Timetable Rearrangement**: Timetables are deterministic and time-indexed; interactive drag-and-drop scheduling is neither supported by the API nor permitted for students.
6. **Offer Letter Generation / Acceptance**: Offer letter workflows and formal acceptance agreements are beyond the CAP-7 recruitment milestone.
7. **Live Backend Integration Status**:
   - Status: **`CONTRACT RECONCILED — LIVE INTEGRATION PENDING`**
   - The contract reconciliation and Zod runtime schema assertions are 100% verified against committed Spring Boot CAP-7 Java record contracts and MockMvc integration tests.
   - During local system diagnostic inspection, while PostgreSQL was reachable on port 5432, no live Spring Boot application process was running on port 8081. In accordance with safety boundaries, no unknown databases or credentials were reset or modified. Live integration against a running Spring Boot daemon remains pending until an authorized disposable test environment is configured.
