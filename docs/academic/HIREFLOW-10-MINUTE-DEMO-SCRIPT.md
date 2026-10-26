# HIREFLOW — 10-Minute University Viva & Live Demonstration Script
## Precise Presentation Guide for Advanced Java Project Evaluation
**Milestone:** CAP-8.6.4 Final Academic Release  
**Presenters:** Student Engineering Team  
**Evaluation Target:** 10-Minute High-Impact Demonstration & Code Walkthrough

---

## Demonstration Timeline Overview

| Minute | Phase | Focus Area | Technology Demonstrated |
| :---: | :--- | :--- | :--- |
| **0:00 - 1:30** | **Part A: Problem & Motivation** | Recruitment Challenges & Architecture Overview | 5-Stage Architecture Evolution |
| **1:30 - 3:00** | **Part B: Advanced Java Foundations** | OOP Models, Collections & Swing Interface | Java 21, Generics, ConcurrentHashMap, Swing |
| **3:00 - 4:30** | **Part C: Database Persistence** | PostgreSQL, HikariCP & GiST Constraints | JDBC, Transactions, SQL Overlap Constraints |
| **4:30 - 6:30** | **Part D: Student & Officer Portals** | Live Student Application & Eligibility Engine | React 19, Spring Security, CSRF, REST API |
| **6:30 - 8:30** | **Part E: Scheduling Studio** | Advisory Preview & Two-Phase Publication | GreedyScheduler, ConflictChecker, ACID Commit |
| **8:30 - 10:00** | **Part F: Test Engineering & Q&A** | Test Matrix, Recovery Checkpoints & Conclusion | JUnit 5 (716 tests), Playwright E2E, Git Tags |

---

## Minute-by-Minute Script

### Minute 0:00 – 1:30 | Part A: The Campus Placement Challenge
- **Presenter Statement:**
  > *"Good morning, esteemed examiners. Today we present **HireFlow**, an enterprise-grade Campus Recruitment and Interview Scheduling System built across five distinct architectural stages."*
- **Problem Explanation:**
  - Campus placement offices manage thousands of students, overlapping company recruitment drives, strict eligibility criteria (CGPA, departments, deadlines), and scarce venue resources (rooms and interviewers).
  - Manual scheduling results in double-booking conflicts, student overlaps, and administrative chaos.
- **Visual Artifact:** Present the 5-Stage Evolution Architecture Diagram:
  - **Stage 1:** Core Java OOP & Desktop Swing GUI.
  - **Stage 2:** JDBC, PostgreSQL 16 & HikariCP.
  - **Stage 3:** Jakarta Servlets, JSP & Embedded Tomcat.
  - **Stage 4:** Spring Boot 4 REST API with Spring Security.
  - **Stage 5:** Contemporary React 19 / TypeScript Web Interface.

---

### Minute 1:30 – 3:00 | Part B: Advanced Java Foundations & Desktop Swing
- **Presenter Action:** Launch the desktop Swing prototype or present compiled classes:
  - Terminal: `java -jar target/hireflow-1.0.0-desktop.jar`
- **Key Concepts Highlighted:**
  1. **Generics & Generic Repositories:** Point out `Repository<T, ID>` in `src/main/java/com/hireflow/repository/Repository.java`.
  2. **Java Collections:** Explain usage of `ConcurrentHashMap` for lock-free thread safety in `InMemoryRepository`, `TreeSet` with custom comparators for candidate sorting, and `ArrayList` for session rosters.
  3. **Java Swing Architecture:** Show `JFrame`, `CardLayout` navigation, `JTable` rendering live candidate data, and `SwingWorker` ensuring long-running scheduling calculations never freeze the Event Dispatch Thread (EDT).

---

### Minute 3:00 – 4:30 | Part C: Relational Persistence, JDBC & Database Constraints
- **Presenter Statement:**
  > *"In Stage 2, we transitioned from in-memory storage to PostgreSQL 16 using raw JDBC to ensure ACID guarantees."*
- **Key Technical Highlights:**
  1. **HikariCP Connection Pool:** Demonstrate `DataSourceProvider.java` with bounded connection pools and fail-fast startup.
  2. **Parameterized JDBC Queries:** Show `JdbcApplicationRepository.java` utilizing `PreparedStatement` within `try-with-resources` to eliminate SQL injection.
  3. **PostgreSQL GiST Temporal Constraints:** Show database migration `V1__initial_schema.sql`:
     - Highlight `ex_room_temporal_overlap` and `ex_interviewer_temporal_overlap` using `btree_gist` and `tsrange`. Explain to examiners how the database engine physically rejects overlapping appointments even under concurrent threads.

---

### Minute 4:30 – 6:30 | Part D: Live Student & Placement Officer Portals
- **Presenter Action:** Open the web application at `http://localhost:5174`.
- **Step 1: Student Opportunity Hub:**
  - Sign in as student `demo.student` (`Aarav Sharma`).
  - Demonstrate **Student Dossier Banner**: Real verified CGPA, department (CSE), roll number.
  - Navigate to **Recruitment Drives**: Browse corporate drives (e.g., Aether Cloud Networks, Cognitive Robotics Labs).
  - Inspect **Eligibility Breakdown**: Show real-time computation of CGPA threshold ($\ge 8.5$), allowed branch match, and deadline validation.
  - Click **Apply Now**: Complete application; show immediate status update to `APPLIED`. Show that duplicate application is blocked.
- **Step 2: Placement Officer Command Center:**
  - Sign in as placement officer `demo.officer`.
  - View **KPI Cards**: Active drives, candidate backlog, verified interview rooms (36 K/L Block rooms).
  - Navigate to **Candidate Review**: Filter applicants for the drive, inspect Aarav Sharma's profile, and click **Shortlist Candidate**. Application status transitions to `SHORTLISTED`.

---

### Minute 6:30 – 8:30 | Part E: Flagship Scheduling Studio & Two-Phase Publication
- **Presenter Action:** Navigate to **Scheduling Studio** (`/officer/scheduling`).
- **Demonstration Steps:**
  1. **Recruitment Context:** Select the drive containing shortlisted candidates.
  2. **Slot Durations & Buffer:** Configure slot duration (45 mins) and candidate transition buffer (15 mins).
  3. **Venue & Interviewer Selection:** Select engineering interview rooms (`KF02` - `LT10`) and corporate interviewers.
  4. **Generate Advisory Preview:** Click **Generate Advisory Schedule**.
     - Emphasize to examiners: *Zero database writes occurred.* A secure preview token (`PREV-...`) was generated in memory with a 15-minute expiry.
     - Inspect the visual timetable grid and accessible tabular matrix.
  5. **Durable Publication:**
     - Click **Publish Schedule to Campus**.
     - Check the required operational acknowledgement and confirm.
     - Server commits the transactions via JDBC in PostgreSQL.
     - Outcome: `ALL_CONFIRMED` (HTTP 200).
  6. **Cross-Portal Verification:**
     - Re-sign in as student `demo.student`.
     - Open **Interview Timetable**: The newly scheduled interview session appears immediately with room code, interviewer name, and exact appointment time.

---

### Minute 8:30 – 10:00 | Part F: Test Engineering, Disaster Recovery & Conclusion
- **Presenter Action:** Present the automated test summary:
  - **Core Java Suite:** 519 unit, domain, repository, and Swing tests (100% passing).
  - **Spring Boot API Suite:** 197 integration, MockMvc, concurrency, and security tests (100% passing).
  - **Frontend Suite:** 147 Vitest component tests (100% passing).
  - **Playwright E2E Suite:** Multi-viewport visual, accessibility, and 40-step live integration tests.
  - **Total Automated Test Count:** **863 automated tests passing**.
- **Disaster Recovery & Git Anchors:**
  - Historical snapshots safely tagged: `v0.8.6.2-prototype-snapshot`, `v0.8.6.3-university-season-snapshot`.
  - Verified local PostgreSQL logical backups created before modifications.
  - Final Academic Release tag: `v0.8.6.4-final-academic-prototype`.
- **Closing Statement:**
  > *"HireFlow demonstrates that complex scheduling algorithms and modern contemporary user experiences can be grounded firmly in core Advanced Java software engineering principles. We welcome your questions."*

---

## Emergency Fallback Checklist (No Internet / Port Collision)
1. **Database:** Docker PostgreSQL container is fully local (`127.0.0.1:55432`) and requires zero external internet.
2. **Desktop Prototype:** Run `java -jar target/hireflow-1.0.0-desktop.jar` to demonstrate Stage 1 in-memory mode without any network dependency.
3. **Pre-Generated Evidence:** High-resolution screenshots across 5 viewports (390px, 768px, 1024px, 1440px, 1920px) are archived under `.run/evidence/fixture-screenshots/`.
