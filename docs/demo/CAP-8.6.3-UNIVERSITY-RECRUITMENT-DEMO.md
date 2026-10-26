# HIREFLOW — CAP-8.6.3
## University Recruitment Season & Campus Interview Room Demonstration Guide

**Milestone:** CAP-8.6.3
**Classification:** Academic Engineering School Placement Operations & Scheduling Verification
**Target Environment:** Isolated PostgreSQL (`127.0.0.1:55432 / hireflow_cap8_it`)
**Preserved Baseline Anchor:** Commit `5af02432c1457b6697d731bc730d4310ee321688` (Tag: `v0.8.6.2-prototype-snapshot`)

---

### 1. Executive Overview

HireFlow CAP-8.6.3 elevates the verified CAP-8.6.2 prototype into a believable, authentic university campus recruitment season. The implementation provides:

1. **Accurate University Room Inventory:** Exactly 36 verified engineering interview rooms across K Block and L Block, with automated backend enforcement excluding 24 faculty/amenity suites and 9 ground-floor engineering laboratories from interview scheduling.
2. **Believable Recruitment Season (`U26` Namespace):** 6 diverse corporate drives across software engineering, data analytics, AI/ML, and embedded systems, featuring 65 synthetic students, 84 applications exhibiting realistic candidate distributions (`APPLIED`, `SHORTLISTED`, `SCHEDULED`, `REJECTED`, `SELECTED`, `INELIGIBLE`), 12 corporate interviewers, and 15 confirmed interview sessions without overlaps.
3. **Safe & Non-Destructive Seeding Invariants:** Fixed historical seeding defects by eliminating dangerous broad wildcard deletions (`LIKE 'DRV-DEMO-%'`), removing destructive updates (`ON CONFLICT DO UPDATE`), and enforcing strict scoped record manifests. Manually created records and the reserved `DRV-DEMO-VIVA22` viva drive remain strictly protected across repeated fixture executions.

---

### 2. K/L Block University Room Inventory

The university campus engineering complex designates upper-floor rooms in K Block and L Block for placement drives. Ground-floor laboratories and specific faculty offices are strictly non-bookable.

#### 2.1 Approved 36 Engineering Interview Rooms

Each upper floor contains room numbers `01` through `10`. By university governance, rooms `02, 03, 04, 08, 09, 10` are interview-eligible. Rooms `01, 05, 06, 07` are reserved for faculty offices, seminar coordination, and student amenities.

| Block | Floor | Code Prefix | Approved Bookable Interview Rooms | Capacity |
|---|---|---|---|---|
| **K Block** | First Floor | `KF` | `KF02`, `KF03`, `KF04`, `KF08`, `KF09`, `KF10` | 4–6 seats |
| **K Block** | Second Floor | `KS` | `KS02`, `KS03`, `KS04`, `KS08`, `KS09`, `KS10` | 4–6 seats |
| **K Block** | Third Floor | `KT` | `KT02`, `KT03`, `KT04`, `KT08`, `KT09`, `KT10` | 4–6 seats |
| **L Block** | First Floor | `LF` | `LF02`, `LF03`, `LF04`, `LF08`, `LF09`, `LF10` | 4–6 seats |
| **L Block** | Second Floor | `LS` | `LS02`, `LS03`, `LS04`, `LS08`, `LS09`, `LS10` | 4–6 seats |
| **L Block** | Third Floor | `LT` | `LT02`, `LT03`, `LT04`, `LT08`, `LT09`, `LT10` | 4–6 seats |

**Total Approved Engineering Interview Rooms:** Exactly **36**.

#### 2.2 Excluded Campus Facilities

1. **Excluded Faculty & Amenity Rooms (24 rooms):**
   - K Block: `KF01, KF05, KF06, KF07`, `KS01, KS05, KS06, KS07`, `KT01, KT05, KT06, KT07`
   - L Block: `LF01, LF05, LF06, LF07`, `LS01, LS05, LS06, LS07`, `LT01, LT05, LT06, LT07`
2. **Ground Floor Laboratories (9 facilities):**
   - K Block: Labs 1, 2, 3, 4, 5 (`K-LAB-01` through `K-LAB-05`)
   - L Block: Labs 6, 7, 8, 9 (`L-LAB-06` through `L-LAB-09`)
3. **Enforcement Policy:**
   - Evaluated by `com.hireflow.policy.UniversityCampusRoomPolicy`.
   - Any attempt to allocate or persist an interview session in an unapproved room is rejected by `OfficerInterviewResourceController` (HTTP 422) and excluded by `OfficerSchedulingController` during timetable preview generation and publication.

---

### 3. Recruitment Season Fixture Architecture (`U26`)

All CAP-8.6.3 dataset expansion records are isolated under the `U26` namespace to ensure zero collision with baseline prototype data (`DEMO-*`, `E2E-*`, `*-VIVA22`).

#### 3.1 Corporate Recruitment Drives Matrix

| Drive ID | Company ID & Name | Job Title | Min CGPA | Eligible Branches | Status | Applicants | Shortlist | Confirmed |
|---|---|---|---|---|---|---|---|---|
| `DRV-U26-01` | `CMP-U26-01` Aether Cloud Networks | Distributed Systems Engineer - I | 7.50 | CSE, IT | Open | 14 | 4 awaiting | 3 |
| `DRV-U26-02` | `CMP-U26-02` Cognitive Robotics Labs | Machine Learning Research Engineer | 8.00 | CSE, IT, AI/ML | Open | 14 | 4 awaiting | 3 |
| `DRV-U26-03` | `CMP-U26-03` FinMetrics Quant Technologies | Quantitative Data Analyst | 7.00 | CSE, IT, ECE | Open | 14 | 4 awaiting | 3 |
| `DRV-U26-04` | `CMP-U26-04` Silicon Wave Microelectronics | Embedded Systems & Firmware Engineer | 6.80 | ECE, EEE | Open | 14 | 4 awaiting | 3 |
| `DRV-U26-05` | `CMP-U26-05` Zenith Strategy Consultants | Technology Risk Consultant | 7.20 | CSE, IT, ECE, MECH | Open | 14 | 4 awaiting | 3 |
| `DRV-U26-06` | `CMP-U26-06` BioInformatics Core Systems | Bio-Data Platform Engineer | 7.50 | CSE, IT, BIO | **Closed** | 14 | 0 (Concluded) | 0 (4 Selected) |

#### 3.2 Candidate & Application Distribution

- **Total Students Seeded:** 65 synthetic profiles (`STU-U26-001` through `STU-U26-065`).
- **Academic Branches:** CSE (22), IT (14), ECE (12), AI/ML (8), EEE (5), MECH (4) (65 total).
- **CGPA Range:** 6.45 to 9.80 with realistic academic distribution.
- **Privacy Assurance:** All student emails use fictional domains (`@example.test`).
- **Application Status Distribution per Active Drive (14 apps each, 84 U26 total):**
  - `DRV-U26-01`, `02`, `03`, `05`: 4 `APPLIED`, 4 `SHORTLISTED`, 3 `SCHEDULED`, 3 `REJECTED`.
  - `DRV-U26-04`: 3 `APPLIED`, 4 `SHORTLISTED`, 3 `SCHEDULED`, 4 `REJECTED`.
- **Closed Historical Drive (`DRV-U26-06`):**
  - Retains full applicant history: 0 `APPLIED`, 0 `SHORTLISTED`, 0 `SCHEDULED`, 5 `REJECTED`, 4 `SELECTED`, 5 `INELIGIBLE` (14 total).

---

### 4. Safe Fixture Setup & Idempotency Runbook

#### 4.1 Running Fixture Seeding

The setup script guarantees non-destructive execution against the isolated database:

```powershell
# Execute from workspace root
powershell.exe -ExecutionPolicy Bypass -File scripts/demo/Setup-HireFlow-Demo.ps1
```

**Safety Guarantees Enforced:**
1. **Preflight Guard:** Confirms port `55432`, restricted user `hireflow_cap8_app`, and database sentinel `HIREFLOW_CAP8_ISOLATED_INTEGRATION_2026`. Refuses to run if connected to port `5432` or any unverified host.
2. **Exact Manifest Scoped Cleanups:** Only deletes explicitly owned fixture records. Never uses wildcards (`LIKE 'DRV-DEMO-%'`).
3. **Preservation of Manual Edits:** Uses `ON CONFLICT (id) DO NOTHING` for all core entities. Running setup twice does not reset application decisions, interview allocations, or modified room capacities.

#### 4.2 Resetting Fixtures Safely (Administrative / Test Maintenance Only)

> [!CAUTION]
> **Administrative & Maintenance Scope:** Fixture resets are administrative maintenance and automated test verification procedures—they are **NOT** part of the normal classroom demonstration. Do not execute reset scripts during a live faculty evaluation.
>
> **Fixture Ownership Caveat:** Reset scripts delete records matching the exact fixture manifest (`APP-U26-001` through `APP-U26-084`). Manually editing the status or remarks of a fixture-owned record does **not** decouple it from the fixture manifest; it will still be deleted during an administrative reset. Genuine independent applications (created with non-fixture IDs) and independent interview sessions are safeguarded by PL/pgSQL fail-closed guards that automatically abort the transaction if detected.

To reset only the demonstration fixtures in a disposable test environment:

```powershell
# Reset U26 demonstration season only (disposable/test context)
docker exec -i hireflow-cap8-postgres psql -U hireflow_cap8_app -d hireflow_cap8_it < infra/cap8-integration/university-season-reset.sql

# Reset standard demo scenario (explicit manifest, preserves DRV-DEMO-VIVA22)
docker exec -i hireflow-cap8-postgres psql -U hireflow_cap8_app -d hireflow_cap8_it < infra/cap8-integration/demo-scenario-reset.sql
```

---

### 5. Officer Demonstration Walkthrough

#### Step 1: Officer Command Center (`/officer/dashboard`)
1. Log in with officer credentials (`demo.officer`).
2. Verify system statistics: 12 corporate drives, 41 campus facilities, 72 enrolled students, 92 applications, and 21 confirmed interviews (live DB counts reflect current operational baseline).
3. Observe real-time status breakdowns across active and closed drives.

#### Step 2: Drive Directory & Historical Preservation (`/officer/drives`)
1. Browse active corporate drives (`DRV-U26-01` through `DRV-U26-05`).
2. Inspect closed historical drive `DRV-U26-06` (BioInformatics Core Systems).
3. Confirm that closed drives display the distinct "Drive Closed" badge and retain historical candidates.

#### Step 3: Candidate Review & Shortlisting (`/officer/candidates`)
1. Filter candidates by drive `DRV-U26-01` (Aether Cloud Networks).
2. Review applicant list showing 4 `APPLIED`, 4 `SHORTLISTED`, 3 `SCHEDULED`, and 3 `REJECTED`.
3. Inspect student CGPA and branch badges.
4. Demonstrate manual status change on an `APPLIED` candidate to `SHORTLISTED`.

#### Step 4: Campus Facilities & Wing Filtering (`/officer/resources`)
1. Access the Interview Rooms directory.
2. Use the campus wing filter buttons:
   - **All Facilities (41)**: Complete campus inventory.
   - **K Block Engineering (18)**: Shows `KF02`..`KT10` with K-Block styling.
   - **L Block Engineering (18)**: Shows `LF02`..`LT10` with L-Block styling.
   - **Preserved / Legacy (5)**: Baseline and viva interview suites.
3. Observe capacity badges, floor indicators, and location descriptions.

#### Step 5: Scheduling Studio Live Allocation (`/officer/scheduling`)
1. Select active drive `DRV-U26-01` (Aether Cloud Networks).
2. Observe 4 shortlisted candidates currently awaiting scheduling.
3. Select interviewers (`INT-U26-01`, `INT-U26-02`) and rooms (`KF02`, `KF03`).
4. Set window time (e.g., 09:00 to 13:00) with 45-minute duration and 15-minute buffer.
5. Click **Generate Preview**:
   - Executes deterministic `GreedyScheduler`.
   - Generates ephemeral preview token.
   - Previews timetable allocations without mutating the database.
6. **Faculty Demonstration Note on Publication:**
   - In a live presentation, showcasing the **Advisory Preview** is sufficient to prove the greedy scheduling algorithm and conflict checker without permanently mutating the database.
   - If permanent publication is demonstrated, it commits sessions with PostgreSQL exclusion constraint protection and updates candidate statuses to `SCHEDULED`.

---

### 6. Student Demonstration Walkthrough

#### Step 1: Student Opportunity Hub (`/student/dashboard`)
1. Log in with student credentials (`demo.student`).
2. Review active placement season drives with computed eligibility status.

#### Step 2: Drive Eligibility & Application Submission (`/student/drives`)
1. Review open drives:
   - Eligible drives display green **Eligible** badge.
   - Drives with branch restrictions (e.g., `DRV-U26-05` requiring ECE/EEE) display warning if student is in CSE.
   - Closed drive `DRV-U26-06` displays "Closed to new applications".
2. Submit application for an eligible drive and observe immediate status tracking.

#### Step 3: Confirmed Interview Timetable (`/student/interviews`)
1. View confirmed interview appointments.
2. Observe room location details (`KF02 - K Block, First Floor, Room 02`).
3. Confirm interviewer name, scheduled start/end times, and preparation guidance.

---

### 7. HTTP API Contract & Token Hygiene Specifications

The Scheduling Preview & Publication workflow strictly enforces deterministic status mapping:

| Endpoint & Condition | HTTP Status | Response Payload & System Behavior |
|---|:---:|---|
| **POST /preview (Valid)** | **200 OK** | Returns generated timetable proposals and ephemeral 15-minute preview token (`previewId`). No database mutation occurs. |
| **POST /preview (Invalid Window)** | **400 Bad Request** | Returns structured `ApiErrorResponse` when window start is after end time. |
| **POST /preview (Invalid Room)** | **422 Unprocessable** | Rejects unapproved room codes (e.g. faculty suites `01, 05, 06, 07` or ground-floor labs). |
| **POST /publish (All Confirmed)** | **200 OK** | Durably commits all proposed sessions to PostgreSQL. Updates candidate statuses to `SCHEDULED`. |
| **POST /publish (Partial Conflict)** | **207 Multi-Status** | Confirms conflict-free sessions; reports collided appointments with actionable recovery instructions. |
| **POST /publish (All Conflicted)** | **409 Conflict** | Zero appointments confirmed due to temporal resource collision. Transaction rolls back cleanly. |
| **POST /publish (Token Replay)** | **409 Conflict** | `PreviewConsumedException`: Blocked replay of an already published preview token. Preserves idempotency. |
| **POST /publish (Token Expired)** | **410 Gone** | `PreviewExpiredException`: Token exceeded 15-minute TTL. Officer must generate a fresh preview. |
| **POST /publish (Unknown Token)** | **404 Not Found** | `PreviewNotFoundException`: Token does not exist in server cache. |
| **POST /publish (Cross-Officer)** | **403 Forbidden** | `AccessDeniedException`: Attempting to publish another officer's preview token is blocked. |
| **POST /publish (DB Outage)** | **500 Server Error** | `DatabaseException`: Genuine PostgreSQL infrastructure failure during transaction commit. |

---

### 8. Known Limitations & Architectural Safeguards

1. **Laboratory Facilities:** Ground-floor laboratories (`K-LAB-1` through `L-LAB-9`) are internal negative-assertion policy filters in `UniversityCampusRoomPolicy.java` to prevent unintended interview bookings; they are not bookable campus interview rooms.
2. **Ephemeral Preview Tokens:** Scheduling studio preview tokens have a 15-minute validity window (HTTP 410 on expiration; HTTP 409 on replay). Expired previews must be regenerated prior to publication.
3. **Historical Drive Outcomes:** The 4 `SELECTED` candidates in closed historical drive `DRV-U26-06` intentionally represent concluded final recruitment offers from prior seasons, distinct from active drives undergoing live interview scheduling.
4. **Database Isolation Guard:** Seeding and test scripts fail-closed immediately if attempted against non-isolated host databases (e.g., port 5432).
