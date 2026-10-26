# HireFlow Placement Officer Command Center & Scheduling Studio — Blueprint

The Placement Officer interface is an institution-grade operational hub designed for career services professionals managing corporate partnerships, recruitment campaigns, candidate pools, and high-stakes interview schedules.

---

## 1. Design & Interaction Principles

1. **High Data Density Without Clutter**:
   - Placement officers operate on hundreds of candidates and dozens of drives. Use compact tables (36px-40px rows), clear borders, monospaced roll numbers, and sticky table headers.
   - Avoid excessive whitespace or massive consumer-style cards for operational lists.

2. **Accurate Endpoint Mapping**:
   - Company/Drive deletion is **NOT** available in the backend; render status badges and authoritative closure actions (`POST /officer/drives/{id}/close`) instead.
   - Candidate status modification in REST is specifically **Shortlisting** (`POST /officer/applications/{id}/shortlist`).

3. **Algorithmic Scheduling Respect**:
   - The scheduling engine is an algorithmic `GreedyScheduler` producing advisory previews.
   - **Do not build drag-and-drop manual slot reordering**: Manual drag-to-reschedule is unsupported by the backend preview API. The preview is an all-or-nothing algorithmic proposal tied to a cryptographic token.

4. **Multi-Outcome Publication Resiliency**:
   - Timetable publication (`POST /officer/scheduling/publish`) returns three distinct HTTP outcomes:
     - **200 ALL_CONFIRMED**: All sessions confirmed in PostgreSQL.
     - **207 PARTIAL_SUCCESS**: Mixed outcome. Confirmed sessions are locked in PostgreSQL; unconfirmed remain shortlisted. Display itemized conflict details and recovery link to view published sessions.
     - **409 NONE_CONFIRMED**: Zero sessions confirmed due to concurrent GiST exclusion constraint conflicts or state changes. Advise generating a fresh preview.
   - **Token Expiry (15-min TTL)**: An ephemeral countdown timer displays remaining token validity. If expired, disable the publish button and provide a "Regenerate Preview" action.

---

## 2. Screen & Workflow Architecture

### View 1: Placement Officer Dashboard (`/officer/dashboard`)
- Institutional KPI header: Active Drives, Registered Companies, Total Shortlisted Candidates, Upcoming Scheduled Sessions.
- Quick navigation: Companies, Recruitment Drives, Resources (Rooms/Interviewers), Scheduling Studio.

### View 2: Candidate Review & Shortlisting (`/officer/drives/:id/applications`)
- Header: Drive title, company name, application deadline, and total applications.
- Filter toolbar: Filter by status (`ALL`, `APPLIED`, `SHORTLISTED`), search by student name or roll number.
- High-density table:
  - Roll Number / Student ID (`monospace`)
  - Student Name
  - Branch & Program
  - Verified CGPA (highlighted with honors brass if $\ge$ 8.5)
  - Application Date
  - Current Status Badge
  - Action Column: "Shortlist" button (invokes `POST /applications/{id}/shortlist`, disabled if already `SHORTLISTED` or `SCHEDULED`).

### View 3: Interview Scheduling Studio (`/officer/scheduling`)

#### Step 1: Configuration Drawer / Form
- Drive Selector: Dropdown of active drives with candidate counts.
- Date Picker: Date of interviews (`YYYY-MM-DD`).
- Time Range: Start time (`HH:mm`) and End time (`HH:mm`).
- Slot Durations:
  - Interview Duration (default 45 min, min 1, max 240).
  - Buffer Duration (default 15 min, min 0, max 120).
- Resource Multi-Select Checklist:
  - Corporate Interviewers associated with the company.
  - Campus Interview Rooms with capacities.
- Primary Action: "Generate Advisory Preview" (calls `POST /officer/scheduling/preview`).

#### Step 2: Ephemeral Preview Studio
- Status Banner:
  - Cryptographic Preview Token badge (e.g., `PREV-9F2B****`).
  - Active 15-Minute Expiration Countdown (`expiresAt`).
  - Notice: *"Advisory timetable in process memory. Zero database mutations have occurred."*
- Metric Bar:
  - Total Candidates Requested
  - Successfully Scheduled Count
  - Unscheduled Candidates Count
- Timetable Visualizer:
  - Grid matrix or chronological timeline showing:
    - Time Slot
    - Candidate Name & Roll Number
    - Assigned Interviewer
    - Assigned Room
- Unscheduled Candidate Drawer:
  - Lists candidates not fitted into schedule with algorithmic reason (e.g., *"Insufficient room slots before end time"*).
- Actions:
  - Secondary: "Discard & Reconfigure"
  - Primary: "Confirm & Publish Timetable" (triggers confirmation modal).

#### Step 3: Transactional Publication & Outcome Modal
- Submits `POST /officer/scheduling/publish` with `{ previewId }`.
- Outcome Handling:
  - `ALL_CONFIRMED` (200): Success celebration card, summary counts, "View Confirmed Timetable" link.
  - `PARTIAL_SUCCESS` (207): Warning modal detailing confirmed count vs conflict count. Displays list of conflicted candidates and recovery steps.
  - `NONE_CONFIRMED` (409): Error modal highlighting conflicting resource bookings (PostgreSQL GiST constraint clashes). Instructions to pick alternative date/rooms and generate a fresh preview.
