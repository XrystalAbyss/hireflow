# HireFlow Student Opportunity Hub — Experience Blueprint

The Student Opportunity Hub empowers undergraduate and graduate candidates with transparent, institutionally vetted recruitment discovery, deterministic eligibility feedback, friction-free application submission, and real-time interview timetable access.

---

## 1. Key Principles for Student Experience

1. **Deterministic Eligibility Transparency**:
   Students must never be left guessing why they cannot apply. The UI directly queries `GET /api/v1/student/drives/{id}/eligibility` and renders a clear scorecard:
   - Minimum CGPA required vs. Student's official CGPA (e.g., `Required: 7.50 | Yours: 8.24` [Pass] or `Required: 8.00 | Yours: 7.60` [Fail]).
   - Eligible branches list vs. Student's department (e.g., `Allowed: [CSE, ECE] | Yours: CSE` [Pass]).
   - Deadline countdown indicator with explicit date and time.
   - Discrete itemized reasons from `reasons: string[]`.

2. **Immutable Academic Profile**:
   The student profile is authoritative data synchronized from university records (`GET /api/v1/student/profile`). The UI displays it prominently as verified institutional data with a helper note: *"To report CGPA or branch discrepancies, contact the University Placement Cell."* (Preventing confusion over why editing profile is unavailable).

3. **Application Integrity**:
   - Single-click application submission with modal confirmation.
   - Prevents duplicate applications if `alreadyApplied === true`.
   - Displays real-time status progression badge (`APPLIED`, `SHORTLISTED`, `SCHEDULED`).

4. **Confirmed Interview Timetable**:
   - Displays confirmed interview sessions (`GET /api/v1/student/interviews`).
   - Cards present Company Name, Job Title, Interviewer Name, Room Location, Date, and Duration.
   - Provides chronological agenda view with "Next Upcoming Interview" highlight card.

---

## 2. Screen & Component Architecture

### View 1: Student Dashboard (`/student/dashboard`)
- **Header**: Institutional greetings, student roll number, verified branch, and current CGPA badge.
- **Top Metrics Row**:
  - Total Eligible Drives (`totalEligibleDrives`)
  - Submitted Applications (`totalApplications`)
  - Shortlisted Applications (`shortlistedCount`)
  - Next Confirmed Interview (Count or nearest timestamp)
- **Primary Section**: Tabbed view of "Eligible Drives" and "Active Applications".
- **Recent Activity Drawer**: Shows recently submitted applications (`recentApplications`).

### View 2: Drive Detail & Eligibility Inspection Modal
- Triggered by clicking any drive card in the catalog.
- Displays full job description, salary/stipend info, drive status, and application deadline.
- Calls `GET /api/v1/student/drives/{id}/eligibility`:
  - If `canApply === true`: Green "Eligible to Apply" callout with active "Submit Application" button.
  - If `canApply === false`: Clear banner with exact failure causes (e.g., *"CGPA 7.10 does not meet required 7.50"*, *"Mechanical Engineering not in eligible branches"*).
  - If `alreadyApplied === true`: Status pill showing current application state (`APPLIED`, `SHORTLISTED`).

### View 3: Interview Timetable (`/student/interviews`)
- Chronological list of interview sessions:
  - Date and Time Slot (`startTime` to `endTime` formatted deterministically from local date-time without unintended timezone conversion).
  - Room Assignment (`roomName`).
  - Interviewer (`interviewerName`).
  - Company & Position (`companyName` — `jobTitle`).
  - Application status indicator (derived from `ApplicationStatus`, e.g., `SCHEDULED`, `SELECTED`).
