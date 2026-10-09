# HireFlow - Campus Recruitment & Interview Scheduling System

> **Stage 1 Status:** **COMPLETED & FROZEN — Core Java + Collections + Swing Prototype**  
> **Release Checkpoint:** `v1.0.0-stage1`  
> **Technology Stack:** **Java 21 LTS**, **Apache Maven**, **Standard Java Swing (Vanilla GUI)**, and **JUnit 5 Jupiter**.  
> **Architecture:** In-Memory Thread-Safe Repository Layer (`ConcurrentHashMap`), Zero External Frameworks.

---

## 1. Project Overview

**HireFlow** is a desktop solution designed for university Training & Placement Cells (TPC) to automate and coordinate campus recruitment activities. The system manages candidate rosters, corporate recruitment drives, dynamic academic eligibility checks, and automated conflict-free interview scheduling.

### Stage 1 Scope & Architecture Highlights:
- **Zero Heavyweight Frameworks:** Implemented cleanly in core Java 21, Swing GUI, and JUnit 5 (no Spring Boot, external databases, OR-Tools, Docker, or web technologies in Stage 1).
- **Clean Layered Architecture:** Strict separation between UI (`ui`), Business Logic (`service`, `scheduler`), Persistence Contracts (`repository`), and Domain Data (`model`).
- **In-Memory Collection Store:** Thread-safe, repository-patterned in-memory storage using Java Collections (`ConcurrentHashMap`, `ArrayList`), ready for seamless Stage 2 JDBC database replacement.
- **Algorithmic Scheduling Engine:** A greedy interval scheduling algorithm coupled with conflict detection preventing double-booking of students, interviewers, and interview rooms across 7 hard constraints.
- **Responsive EDT Architecture:** Long-running scheduler computations run asynchronously via `SwingWorker`, ensuring the Swing Event Dispatch Thread (EDT) is never blocked.
- **Full Inter-Screen Reactive Synchronization:** Modifications across any panel (registering a student, adding a drive, shortlisting an application, or publishing an interview schedule) immediately refresh tables and metrics across all screens.
- **Real Service Invocations:** Every button, action, and filter directly invokes real business services and repositories. No decorative or mock buttons.

---

## 2. Desktop Interface Screens

HireFlow Stage 1 features a desktop interface with a persistent dark sidebar navigation, responsive card-based layout, and synchronized status indicators:

```
+---------------------------------------------------------------------------------------------------+
|  HIREFLOW - Campus Placement System                                                               |
+------------------+--------------------------------------------------------------------------------+
|  [HF] HireFlow   |                                                                                |
|                  |   ACTIVE SCREEN CONTENT (CardLayout)                                           |
|  - Dashboard     |                                                                                |
|  - Students      |   - 1. Dashboard: Summary Cards + Live Drives & Applications Tables            |
|  - Companies     |   - 2. Student Registry: Search Roster, Register Form, Edit & Delete Dialogs   |
|    & Drives      |   - 3. Companies & Drives: Tabbed Drive & Corporate Partner Management         |
|  - Applications  |   - 4. Applications: Eligibility Evaluator, Submissions, Status Transitions   |
|  - Scheduler     |   - 5. Interview Scheduler: Parameters, SwingWorker Greedy Engine, Timetable   |
|                  |                                                                                |
|  [Reset Demo]    |                                                                                |
+------------------+--------------------------------------------------------------------------------+
| Status: System Ready | Students: 11 | Drives: 5 | Apps: 6 | Confirmed: 0 | Java 21 EDT Active     |
+---------------------------------------------------------------------------------------------------+
```

### Screen 1: Operations Dashboard (`DashboardPanel`)
- **Key Metrics Cards:** Live summary cards displaying Registered Students, Partner Companies, Active Drives, Applications, and Scheduled Sessions.
- **Active Drives Table:** Dynamic table listing all ongoing recruitment drives with cutoffs and deadlines.
- **Recent Applications Table:** Overview of candidate applications with current statuses.
- **Quick Action Buttons:** Direct shortcuts to register students, post new drives, or launch the scheduling engine.

### Screen 2: Student Management (`StudentPanel`)
- **Search & Filter:** Real-time search across candidate names, institutional email, student ID, and branch.
- **Candidate Roster Table:** Tabular view of student records with custom cell rendering and alternating row striping.
- **Registration Form:** Form with client-side input validation (name format, email syntax, CGPA range `0.0`–`10.0`, graduation year, active enrollment).
- **Edit Dialog:** Modal window to update existing candidate academic details.
- **Delete Record:** Confirmation dialog to safely remove records while enforcing repository referential checks.

### Screen 3: Companies & Recruitment Drives (`CompanyDrivePanel`)
- **Tab 1 — Recruitment Drives:** Table of drives showing company, job title, cutoff CGPA, allowed branches, deadline, and active/expired status. Form to register new drives with company dropdown selection.
- **Tab 2 — Partner Companies:** Corporate partner directory with company ID, name, and industry. Form to onboard new companies and remove obsolete corporate profiles.

### Screen 4: Applications & Academic Eligibility (`ApplicationPanel`)
- **Interactive Eligibility Evaluator:** Select any candidate and target drive, click **"Check Eligibility"**, and view detailed diagnostic pass/fail messages explaining criteria (CGPA cutoff, branch authorization, active enrollment, deadline compliance).
- **Enforced Application Submission:** Submitting an application invokes the real `ApplicationService` which strictly rejects ineligible candidates and duplicate applications.
- **Candidate Pipeline Lifecycle:** Action buttons to transition application statuses:
  - `APPLIED` $\rightarrow$ `SHORTLISTED` $\rightarrow$ `SCHEDULED` $\rightarrow$ `SELECTED` / `REJECTED`.
  - State machine validation prevents illegal jumps (e.g. scheduling an unshortlisted candidate).

### Screen 5: Automated Interview Scheduling (`SchedulePanel`)
- **Parameter Configuration:** Adjustable spinners for start hour (09:00), end hour (17:00), interview duration (mins), and buffer gap (mins).
- **Non-Blocking Background Execution:** Uses `SwingWorker` so the UI remains fully responsive with an animated progress bar during schedule computation.
- **Greedy Interval Engine:** Allocates candidate shortlists to qualified interviewers and available rooms while strictly honoring 7 hard constraints (no overlapping student slots, no overlapping interviewer sessions, no room collisions, availability window boundaries, non-zero durations, pre-existing appointment protection, shortlist prerequisite).
- **Proposed Timetable Table:** Lists allocated sessions with session ID, application ID, student name, company, interviewer, room, and time slot.
- **Unscheduled Conflicts Table:** Outlines unscheduled applications and reasons (e.g. not shortlisted, window exhausted).
- **Publish & Confirm Action:** Commits proposed sessions into `InterviewSessionRepository` and updates application statuses to `SCHEDULED`.

---

## 3. Directory & Package Structure

```
hireflow/
├── pom.xml                                   # Maven build file (Java 21, JUnit 5)
├── README.md                                 # Project documentation and launch instructions
├── docs/
│   ├── requirements.md                       # Software requirements specification (SRS)
│   ├── team-roles.md                         # Team responsibilities and assignment matrix
│   ├── test-cases.md                         # Test cases for services and UI interactions
│   ├── stage1-technical-report.md            # Comprehensive technical architecture & quality report
│   └── cap3-demo-script.md                   # 10-Minute live presentation & defense script
│
├── src/
│   ├── main/
│   │   └── java/com/hireflow/
│   │       ├── Main.java                     # Application bootstrap (GUI or CLI demo mode)
│   │       ├── model/                        # Domain entities & enums
│   │       │   ├── Student.java
│   │       │   ├── Company.java
│   │       │   ├── RecruitmentDrive.java
│   │       │   ├── Application.java
│   │       │   ├── ApplicationStatus.java
│   │       │   ├── Interviewer.java
│   │       │   ├── InterviewSession.java
│   │       │   └── Room.java
│   │       ├── repository/                   # Persistence interfaces & in-memory stores
│   │       │   ├── CrudRepository.java
│   │       │   ├── StudentRepository.java
│   │       │   ├── CompanyRepository.java
│   │       │   ├── DriveRepository.java
│   │       │   ├── ApplicationRepository.java
│   │       │   ├── InterviewerRepository.java
│   │       │   ├── RoomRepository.java
│   │       │   ├── InterviewSessionRepository.java
│   │       │   ├── AbstractInMemoryRepository.java
│   │       │   ├── InMemoryStudentRepository.java
│   │       │   ├── InMemoryCompanyRepository.java
│   │       │   ├── InMemoryDriveRepository.java
│   │       │   ├── InMemoryApplicationRepository.java
│   │       │   ├── InMemoryInterviewerRepository.java
│   │       │   ├── InMemoryRoomRepository.java
│   │       │   └── InMemoryInterviewSessionRepository.java
│   │       ├── service/                      # Business logic services
│   │       │   ├── StudentService.java
│   │       │   ├── RecruitmentDriveService.java
│   │       │   ├── EligibilityService.java
│   │       │   ├── ApplicationService.java
│   │       │   ├── SampleDataLoader.java
│   │       │   └── event/                    # Domain event bus & observer pattern
│   │       │       ├── HireFlowEvent.java
│   │       │       ├── HireFlowEventListener.java
│   │       │       └── HireFlowEventType.java
│   │       ├── scheduler/                    # Scheduling algorithms & constraints
│   │       │   ├── SchedulingEngine.java
│   │       │   ├── GreedyScheduler.java
│   │       │   ├── ConflictChecker.java
│   │       │   ├── SchedulingRequest.java
│   │       │   ├── SchedulingResult.java
│   │       │   ├── TimeWindow.java
│   │       │   └── UnscheduledApplication.java
│   │       ├── exception/                    # Exception hierarchy
│   │       │   ├── HireFlowException.java
│   │       │   ├── EntityNotFoundException.java
│   │       │   ├── DuplicateEntityException.java
│   │       │   ├── ValidationException.java
│   │       │   └── SchedulingConflictException.java
│   │       └── ui/                           # Java Swing desktop user interface
│   │           ├── UIUtils.java              # Styling, fonts, palettes, and components
│   │           ├── MainFrame.java            # Main window, sidebar navigation, card switching
│   │           ├── DashboardPanel.java       # Metric counters & overview tables
│   │           ├── StudentPanel.java         # Candidate management & search
│   │           ├── CompanyDrivePanel.java    # Company and recruitment drive management
│   │           ├── ApplicationPanel.java     # Eligibility evaluator & applications table
│   │           └── SchedulePanel.java        # Background greedy scheduler & timetables
│   └── test/
│       └── java/com/hireflow/                # Automated JUnit 5 unit & integration tests (91 Tests)
│           ├── acceptance/
│           │   └── Stage1AcceptanceRegressionTest.java
│           ├── model/EntityValidationTest.java
│           ├── repository/RepositoryOperationsTest.java
│           ├── service/ServiceOperationsTest.java
│           ├── scheduler/GreedySchedulerTest.java
│           ├── ui/UITest.java
│           └── Stage1WorkflowIntegrationTest.java
```

---

## 4. Prerequisites

- **Java Development Kit (JDK):** Version **21** or later (`java --version`, `javac --version`)
- **Apache Maven:** Version **3.8+** (`mvn --version`)

---

## 5. How to Build & Run

### Step 1: Compile the Project
```bash
mvn clean compile
```

### Step 2: Run Automated Unit & Integration Tests (91 Tests)
```bash
mvn test
```
All 91 tests across the 7 test suites execute with 0 failures and 0 errors.

### Step 3: Package into Executable JAR
```bash
mvn clean package
```
This builds `target/hireflow-1.0.0.jar` with all manifests and dependencies configured.

### Step 4: Launch the Desktop GUI Application
Run the packaged JAR:
```bash
java -jar target/hireflow-1.0.0.jar
```
*(On systems with graphical displays, HireFlow opens the full desktop Swing interface directly.)*

### Step 5: Optional — Run Console Demonstration Mode
For headless environments, CI/CD runners, or command-line demonstrations:
```bash
java -jar target/hireflow-1.0.0.jar --demo
```
This runs the complete step-by-step console test script:
1. Lists drives and partner companies.
2. Evaluates candidate academic eligibility.
3. Submits an eligible application.
4. Demonstrates rejection of ineligible applications (CGPA cutoff, branch mismatch, expired deadline).
5. Demonstrates rejection of duplicate applications.
6. Executes the greedy interview scheduling engine with conflict detection.

---

## 6. Preloaded Sample Demonstration Data

Upon launch, HireFlow automatically initializes with a sample dataset:
- **11 Students:** Representing CSE, IT, ECE, MECH, and CIVIL with varying CGPAs (6.50 – 9.50) and active/inactive statuses.
- **4 Companies:** Google Cloud, Microsoft, Goldman Sachs, Tata Motors.
- **5 Recruitment Drives:** High-cutoff SDE drives, medium-cutoff cloud engineering drives, Core engineering drives, and an expired drive for validation testing.
- **3 Technical Interviewers:** Designated per company (Dr. Sarah Chen, Marcus Vance, David Sterling).
- **2 Interview Rooms:** Tech Lab A — Booth 1 & Booth 2.
- **6 Applications:** 5 shortlisted applications ready for scheduling, and 1 non-shortlisted application to demonstrate prerequisite enforcement.

*You can restore this standard dataset at any time by clicking **"Reset Demo Data"** at the bottom of the sidebar.*
