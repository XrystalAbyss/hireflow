# HIREFLOW — Final Faculty Demonstration Checklist & Rehearsal Runbook
## Comprehensive Evaluation Guide for University Advanced Java Examination

**Milestone:** CAP-8.6.4.1 Final Academic Submission Sign-Off  
**Target Repository:** `C:\Users\astra\Desktop\Project\hireflow`  
**Branch:** `develop/stage5` | **Signed-off Commit:** `4c01f8525a62cc5b4846c862facbcfb10b7c3e88`  
**Certified Release Tag:** `v0.8.6.4.1-academic-signoff`  
**Evaluation Role:** Student Presenters & Faculty Evaluation Panel  

---

## 1. Preflight Verification (Execute Before Evaluation)

Run the non-destructive readiness preflight script from the project root:

```cmd
Check-HireFlow.cmd
```

Or via PowerShell:
```powershell
powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File scripts/demo/Check-HireFlow.ps1
```

### Expected Preflight Output:
- `[PASS] Java 21 - openjdk 21.0.12.1 LTS`
- `[PASS] Node.js - v24.20.0`
- `[PASS] Docker engine - 29.8.2`
- `[PASS] Isolated PostgreSQL container - hireflow-cap8-postgres running`
- `[PASS] Database isolation - 127.0.0.1:55432/hireflow_cap8_it, restricted role, sentinel`
- `[PASS] Spring Boot API identity and health - http://127.0.0.1:8081/api/v1/health (UP)`
- `[PASS] React frontend - http://127.0.0.1:5174`
- `[PASS] Demo accounts provisioned - demo.master, demo.officer, demo.student`
- `[PASS] demo.master login - role=ADMINISTRATOR`
- `[PASS] demo.master /auth/me - role=ADMINISTRATOR`
- `[PASS] Administrator officer-portal access - 41 room record(s) readable`
- `[PASS] Logout - Logged out successfully.`
- `[PASS] Invalid password rejected - HTTP 401`
- `HIREFLOW DEMO READY - all critical checks passed.`

---

## 2. Standard 10-Minute Demonstration Sequence

### Phase 1: Problem Formulation & Architecture (Minutes 0:00 – 1:30)
- **Objective:** Introduce the multi-dimensional campus placement resource allocation challenge.
- **Presenter Statement:** *"HireFlow addresses the challenge of scheduling hundreds of candidate interviews across dozens of corporate drives without room collisions, student overlaps, or interviewer double-booking."*
- **Slide / Visual:** Present the 5-Stage Evolution model:
  `Stage 1: Core OOP & Swing` $\rightarrow$ `Stage 2: JDBC & PostgreSQL` $\rightarrow$ `Stage 3: Jakarta Servlets` $\rightarrow$ `Stage 4: Spring Boot REST` $\rightarrow$ `Stage 5: React 19 Scheduling Studio`.

### Phase 2: Core Java & Swing Desktop Interface (Minutes 1:30 – 3:00)
- **Objective:** Prove Java 21 foundation, Generics, Collections, and Swing EDT safety.
- **Action:** Open or demonstrate Swing desktop jar (`target/hireflow-1.0.0-desktop.jar`).
- **Code Locations to Show Examiners:**
  - `src/main/java/com/hireflow/model/TimeSlot.java`: Java 21 Record with immutable compact constructor validation.
  - `src/main/java/com/hireflow/repository/Repository.java`: Generic repository interface (`<T, ID>`).
  - `src/main/java/com/hireflow/repository/InMemoryRepository.java`: `ConcurrentHashMap` for lock-free thread-safe in-memory caching.
  - `src/main/java/com/hireflow/gui/SchedulePanel.java`: `SwingWorker` running `GreedyScheduler` asynchronously so the Swing EDT never freezes.

### Phase 3: Relational Persistence, JDBC & Database Constraints (Minutes 3:00 – 4:30)
- **Objective:** Demonstrate JDBC connection pooling, ACID transaction boundaries, and PostgreSQL GiST exclusion constraints.
- **Code Locations to Show Examiners:**
  - `src/main/java/com/hireflow/db/DataSourceProvider.java`: HikariCP connection pool with bounded pool size and connection leak detection.
  - `src/main/resources/db/migration/V1__initial_schema.sql`:
    - Point out `ex_room_temporal_overlap` and `ex_interviewer_temporal_overlap` using `btree_gist` and `tsrange`.
    - Explain: *The database engine physically rejects concurrent appointment overlaps at the storage engine level.*
  - `src/main/java/com/hireflow/repository/jdbc/JdbcApplicationRepository.java`: `PreparedStatement` with parameterized queries inside `try-with-resources`.

### Phase 4: Student Portal & Real-Time Eligibility Evaluator (Minutes 4:30 – 6:30)
- **Objective:** Demonstrate candidate experience, role-based security, and academic eligibility engine.
- **Action:** Open browser at `http://localhost:5174`.
- **Flow:**
  1. Log in as `demo.student` (`student123`).
  2. View **Student Dossier**: Branch (`CSE`), CGPA (`8.75`), Roll Number.
  3. Navigate to **Recruitment Drives**:
     - `DRV-U26-01` (Aether Cloud Networks): Shows **Eligible** badge (minimum CGPA 7.50, allowed branches CSE, IT).
     - `DRV-U26-04` (Silicon Wave Microelectronics): Shows **Ineligible** warning because allowed branches are ECE, EEE.
     - `DRV-U26-06` (BioInformatics Core Systems): Shows **Drive Closed** badge; rejects submissions.
  4. Submit application for an open drive; show immediate status update to `APPLIED`. Show that submitting a second time is rejected by duplicate prevention.

### Phase 5: Officer Command Center & Flagship Scheduling Studio (Minutes 6:30 – 8:30)
- **Objective:** Demonstrate Placement Officer management and deterministic Greedy Scheduling.
- **Action:** Log out and log in as `demo.officer` (`officer123`).
- **Flow:**
  1. **Officer Dashboard (`/officer/dashboard`)**: Show live metrics: 12 corporate drives, 41 interview rooms, 72 enrolled students, 92 applications, 21 confirmed interviews.
  2. **Candidate Review (`/officer/candidates`)**:
     - Filter candidates by drive `DRV-U26-01` (Aether Cloud Networks).
     - Observe applicant breakdown (4 `APPLIED`, 4 `SHORTLISTED`, 3 `SCHEDULED`, 3 `REJECTED`).
     - Demonstrate transitioning an applicant to `SHORTLISTED`.
  3. **Campus Facilities (`/officer/resources`)**:
     - Filter rooms by **K Block** (18 rooms), **L Block** (18 rooms), and **Preserved Legacy** (5 rooms).
     - Explain institutional policy: 36 approved engineering rooms (`KF02`–`LT10`), ground-floor labs and faculty offices strictly prohibited.
  4. **Scheduling Studio (`/officer/scheduling`)**:
     - Select drive `DRV-U26-01`.
     - Observe 4 shortlisted candidates awaiting allocation.
     - Select rooms (`KF02`, `KF03`) and corporate interviewers (`INT-U26-01`, `INT-U26-02`).
     - Set window: 09:00 to 13:00, 45m duration, 15m transition buffer.
     - Click **Generate Advisory Schedule**:
       - *Highlight to examiners:* An ephemeral preview token (`PREV-...`) was generated in-memory. **Zero database writes occurred.**
       - Inspect the interactive multi-wing time grid and accessible data matrix.
  5. **Demonstration Note on Publication:**
     - For classroom evaluation, showcasing the **Advisory Preview** proves the deterministic greedy scheduling algorithm without altering preserved database records.
     - If permanent publication is demonstrated, the system commits sessions under PostgreSQL GiST exclusion constraint protection.

### Phase 6: Student Timetable Verification & Testing Defense (Minutes 8:30 – 10:00)
- **Objective:** Verify end-to-end appointment visibility and defend software quality.
- **Action:** Log back in as `demo.student` and open **Interview Timetable** (`/student/interviews`).
- **Demonstrate:** Confirmed interview appointment with room code (`KF02`), interviewer name, and exact appointment time.
- **Quality & Testing Summary to Present:**
  - **Core Java Backend:** 522 passing tests (JUnit 5, Mockito, Tomcat).
  - **Spring Boot API:** 198 passing tests (MockMvc, HikariCP, Security).
  - **React UI Components:** 147 passing tests (Vitest, React Testing Library).
  - **Browser E2E:** 16 passing tests (Playwright, 5 responsive viewports, automated WCAG 2.1 AA scans).
  - **Grand Total:** **883 automated tests passing** (0 failures, 6 skipped; 26 real service tests, 857 unit/mock/isolated).
- **Git Checkpoints & Recovery:** Protected historical snapshot tags `v0.8.6.2`, `v0.8.6.3`, `v0.8.6.4`, and final sign-off tag `v0.8.6.4.1-academic-signoff`.

---

## 3. Key Code Symbols & Files Quick Reference

| Concept | File Location | Key Symbol / Method |
|---|---|---|
| **Immutable Value Object** | `src/main/java/com/hireflow/model/TimeSlot.java` | `record TimeSlot(LocalDateTime start, LocalDateTime end)` |
| **Encapsulation & Validation** | `src/main/java/com/hireflow/model/Student.java` | `validateCgpa(double cgpa)` |
| **Generic Repository Abstraction** | `src/main/java/com/hireflow/repository/Repository.java` | `public interface Repository<T, ID>` |
| **Thread-Safe Memory Caching** | `src/main/java/com/hireflow/repository/InMemoryRepository.java` | `ConcurrentHashMap<ID, T> storage` |
| **Deterministic Greedy Scheduler** | `src/main/java/com/hireflow/scheduler/GreedyScheduler.java` | `schedule(ScheduleRequest request)` |
| **Double-Booking Conflict Checker** | `src/main/java/com/hireflow/scheduler/ConflictChecker.java` | `hasConflict(InterviewSession session)` |
| **Asynchronous EDT Safety** | `src/main/java/com/hireflow/gui/SchedulePanel.java` | `new SwingWorker<ScheduleResult, Void>()` |
| **Connection Pool Management** | `src/main/java/com/hireflow/db/DataSourceProvider.java` | `HikariConfig`, `HikariDataSource` |
| **PostgreSQL GiST Constraints** | `src/main/resources/db/migration/V1__initial_schema.sql` | `EXCLUDE USING gist (room_id WITH =, ...)` |
| **Session & CSRF Security** | `hireflow-api/src/main/java/com/hireflow/api/config/SecurityConfig.java` | `SecurityFilterChain`, `CookieCsrfTokenRepository` |
| **Institutional Campus Policy** | `src/main/java/com/hireflow/policy/UniversityCampusRoomPolicy.java` | `validateRoomForCreation(Room room)` |

---

## 4. Emergency Fallback & Recovery Plan

| Scenario | Primary Triage | Fallback Action |
|---|---|---|
| **Browser or Node.js failure** | Check terminal running Vite on port `5174` | Run standalone desktop GUI: `java -jar target/hireflow-1.0.0-desktop.jar` |
| **Docker container stopped** | Run `docker start hireflow-cap8-postgres` | Run Swing app in `IN_MEMORY` mode (zero database dependency) |
| **Port 8081 or 5174 conflict** | Check with `netstat -ano \| findstr :8081` | Kill conflicting stray process or run desktop JAR directly |
| **Network completely offline** | Not an issue—the entire stack is 100% local | Docker, PostgreSQL, Spring Boot, and Vite run entirely on `127.0.0.1` |
| **Preserved evidence inspection** | `.run/evidence/fixture-screenshots/` | Walk examiners through high-resolution viewport screenshots and test logs |

---

## 5. Demonstration Golden Rules

1. **Never touch host PostgreSQL port 5432.** All HireFlow persistence runs on `127.0.0.1:55432`.
2. **Do not run reset scripts during a presentation.** Reset scripts are administrative test maintenance procedures.
3. **Prefer Advisory Preview for scheduling.** Demonstrating the Advisory Preview proves the algorithmic greedy engine without changing preserved demo records.
4. **Emphasize Java primacy.** Java 21, Collections, JDBC, and Spring Boot provide the entire computational and transactional backbone; React acts as the presentation layer.
5. **Never describe GreedyScheduler as AI or globally optimal.** It is a deterministic greedy heuristic operating in polynomial time $O(N \log N + N \cdot R \cdot I)$.
