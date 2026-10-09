# HireFlow Stage 1 Technical Architecture & Quality Report

> **Project:** HireFlow - Campus Recruitment and Interview Scheduling System  
> **Course / Academic Milestone:** B.Tech Advanced Java (Stage 1 Desktop Prototype)  
> **Environment:** OpenJDK 21 LTS, Apache Maven 3.9+, Java Swing, JUnit 5 Jupiter  
> **Date of Evaluation:** October 2026

---

## 1. Executive Summary

HireFlow Stage 1 delivers a functional, zero-framework Java 21 desktop application demonstrating core Advanced Java engineering competencies. The system orchestrates candidate rosters, company recruitment drives, academic eligibility rules, candidate shortlisting, and greedy interview scheduling with conflict detection.

All operations execute without external persistence frameworks (Spring Boot, Hibernate, databases, OR-Tools, cloud services). An in-memory repository architecture isolates domain data structures, enabling seamless replacement with JDBC in Stage 2.

```
+---------------------------------------------------------------------------------------------------------+
|                                  HIREFLOW ARCHITECTURAL TIERS                                           |
+---------------------------------------------------------------------------------------------------------+
|  [PRESENTATION TIER]                                                                                    |
|  Swing UI: MainFrame (CardLayout, Sidebar), DashboardPanel, StudentPanel, CompanyDrivePanel,           |
|            ApplicationPanel, SchedulePanel (SwingWorker asynchronous task)                              |
+---------------------------------------------------------------------------------------------------------+
|  [APPLICATION & DOMAIN SERVICES]                                                                        |
|  StudentService | RecruitmentDriveService | EligibilityService | ApplicationService                    |
|  Domain Event Bus: HireFlowEvent, HireFlowEventListener, HireFlowEventType                              |
+---------------------------------------------------------------------------------------------------------+
|  [ALGORITHMIC SCHEDULING ENGINE]                                                                        |
|  SchedulingEngine (Interface) <--- GreedyScheduler (Implementation)                                     |
|  ConflictChecker (Interval overlap, resource collisions, capacity, buffer enforcement)                 |
+---------------------------------------------------------------------------------------------------------+
|  [PERSISTENCE ABSTRACTION TIER]                                                                         |
|  CrudRepository<T, ID> <--- AbstractInMemoryRepository<T, ID> (Thread-safe ConcurrentHashMap)            |
|  StudentRepo | CompanyRepo | DriveRepo | ApplicationRepo | InterviewerRepo | RoomRepo | SessionRepo     |
+---------------------------------------------------------------------------------------------------------+
|  [DOMAIN MODEL & EXCEPTIONS]                                                                            |
|  Student, Company, RecruitmentDrive, Application, Interviewer, InterviewSession, Room                   |
|  Enums & Records: ApplicationStatus, TimeWindow, UnscheduledApplication, EligibilityResult              |
|  Custom Exceptions: HireFlowException, ValidationException, EntityNotFoundException, etc.              |
+---------------------------------------------------------------------------------------------------------+
```

---

## 2. Advanced Java OOP Principles Applied

| OOP Concept | Project Implementation | Concrete Examples & Symbol References |
| :--- | :--- | :--- |
| **Encapsulation & Defensive Design** | Private attributes, controlled accessor methods, defensive copies on collection accessors, and constructor boundary validation. | In [`Student`](file:///c:/Users/astra/Desktop/Project/hireflow/src/main/java/com/hireflow/model/Student.java), CGPA is restricted to $[0.0, 10.0]$, graduation year is bounded, and email requires format validation. In [`RecruitmentDrive`](file:///c:/Users/astra/Desktop/Project/hireflow/src/main/java/com/hireflow/model/RecruitmentDrive.java), `eligibleBranches` returns `Collections.unmodifiableSet(eligibleBranches)`. |
| **Abstraction & Polymorphism** | Abstract contracts decoupling callers from concrete storage and scheduling algorithms. | [`CrudRepository<T, ID>`](file:///c:/Users/astra/Desktop/Project/hireflow/src/main/java/com/hireflow/repository/CrudRepository.java) defines generic persistence behaviors. [`SchedulingEngine`](file:///c:/Users/astra/Desktop/Project/hireflow/src/main/java/com/hireflow/scheduler/SchedulingEngine.java) specifies the contract for schedule generation, implemented polymorphically by [`GreedyScheduler`](file:///c:/Users/astra/Desktop/Project/hireflow/src/main/java/com/hireflow/scheduler/GreedyScheduler.java). |
| **Inheritance & Generics** | Reusable generic data access base classes reducing boilerplate across repositories. | [`AbstractInMemoryRepository<T, ID>`](file:///c:/Users/astra/Desktop/Project/hireflow/src/main/java/com/hireflow/repository/AbstractInMemoryRepository.java) implements core thread-safe CRUD algorithms using a key extractor function (`Function<T, ID>`). Concrete repositories extend this base. |
| **Modern Java Records** | Immutable, concise value types with built-in structural equality, `hashCode`, and accessor semantics. | [`TimeWindow`](file:///c:/Users/astra/Desktop/Project/hireflow/src/main/java/com/hireflow/scheduler/TimeWindow.java), [`UnscheduledApplication`](file:///c:/Users/astra/Desktop/Project/hireflow/src/main/java/com/hireflow/scheduler/UnscheduledApplication.java), [`EligibilityResult`](file:///c:/Users/astra/Desktop/Project/hireflow/src/main/java/com/hireflow/service/EligibilityService.java), and [`HireFlowEvent`](file:///c:/Users/astra/Desktop/Project/hireflow/src/main/java/com/hireflow/service/event/HireFlowEvent.java). |
| **Builder Design Pattern** | Fluent, immutable request formulation for complex multi-parameter scheduling invocations. | [`SchedulingRequest.Builder`](file:///c:/Users/astra/Desktop/Project/hireflow/src/main/java/com/hireflow/scheduler/SchedulingRequest.java) constructs immutable input graphs with defensive copies of collections. |

---

## 3. Java Collections Framework Usage

| Collection Type | Location | Purpose & Architectural Rationale |
| :--- | :--- | :--- |
| `ConcurrentHashMap<ID, T>` | [`AbstractInMemoryRepository`](file:///c:/Users/astra/Desktop/Project/hireflow/src/main/java/com/hireflow/repository/AbstractInMemoryRepository.java) | Thread-safe concurrent storage providing $O(1)$ lookup, insertion, and deletion without locking entire storage maps. |
| `ArrayList<T>` / `List<T>` | Services & Schedulers | Ordered sequential processing of applications, sorted by student ID, drive ID, and application ID for deterministic greedy scheduling. |
| `Collections.unmodifiableList()` | Repositories & Schedulers | Prevents external callers from mutating internal repository views, preserving data integrity. |
| `Set<String>` / `HashSet<String>` | [`RecruitmentDrive`](file:///c:/Users/astra/Desktop/Project/hireflow/src/main/java/com/hireflow/model/RecruitmentDrive.java) | $O(1)$ set containment checks for academic branch authorization (case-insensitive normalized strings). |
| `EnumSet<ApplicationStatus>` | [`ApplicationService`](file:///c:/Users/astra/Desktop/Project/hireflow/src/main/java/com/hireflow/service/ApplicationService.java) | Bit-vector backed sets mapping allowed lifecycle status transitions with minimal memory footprint. |
| `CopyOnWriteArrayList<HireFlowEventListener>` | Service Event Publishers | Thread-safe iteration over event subscribers during high-frequency UI mutations. |
| `Java Stream API` | Across Services & Repositories | Declarative filtering, mapping, sorting, and transformation (e.g. `stream().filter(...).toList()`). |

---

## 4. Java Swing Desktop UI & Event Architecture

### UI Component Hierarchy
1. **Window Shell (`MainFrame`):** Uses `BorderLayout`.
   - **WEST:** Dark styled sidebar (`250px` width) housing branding, navigation menu buttons, and sample data controls.
   - **CENTER:** `CardLayout` container switching between 5 modular screens (`DASHBOARD`, `STUDENTS`, `DRIVES`, `APPLICATIONS`, `SCHEDULE`).
   - **SOUTH:** Status bar reporting live synchronization state, aggregate counts, and active domain event badges.
2. **Dashboard (`DashboardPanel`):** Metric cards using custom matte borders, active drives `JTable`, and recent applications `JTable`.
3. **Student Registry (`StudentPanel`):** Horizontal `JSplitPane` balancing a search-filtered `JTable` against an input form with client-side format validation.
4. **Companies & Drives (`CompanyDrivePanel`):** `JTabbedPane` separating drive configurations from corporate partner onboarding.
5. **Applications & Eligibility (`ApplicationPanel`):** Interactive eligibility diagnostics evaluator paired with candidate pipeline table and status transition controls.
6. **Interview Scheduling (`SchedulePanel`):** Parameter control panel, timetable preview `JTable`, unscheduled conflict diagnostics `JTable`, and confirmed appointment roster.

### Threading & Event Dispatch Thread (EDT) Management
- **EDT Concurrency Rule:** All Swing component instantiations and DOM updates run strictly on the Event Dispatch Thread via `SwingUtilities.invokeLater(...)`.
- **Asynchronous Scheduler Execution:** In [SchedulePanel.java](file:///c:/Users/astra/Desktop/Project/hireflow/src/main/java/com/hireflow/ui/SchedulePanel.java), `GreedyScheduler.schedule()` executes inside `SwingWorker.doInBackground()`. This prevents the UI from freezing during intensive slot combinatorial search.
- **Progress Feedback:** An indeterminate `JProgressBar` activates during computation and hides when `done()` publishes results to the table model on the EDT.
- **Observer Event System:** The `com.hireflow.service.event` package implements an event publisher/subscriber pattern. Domain events (`STUDENT_REGISTERED`, `JOB_DRIVE_CREATED`, `APPLICATION_SUBMITTED`, etc.) dispatch to registered listeners, updating the `MainFrame` status bar in real time.

---

## 5. Algorithmic Scheduling Engine & Conflict Detection

The [`GreedyScheduler`](file:///c:/Users/astra/Desktop/Project/hireflow/src/main/java/com/hireflow/scheduler/GreedyScheduler.java) implements a deterministic greedy interval scheduling algorithm enforcing 7 constraints:

1. **Student Conflict Prohibition:** A student cannot be scheduled for overlapping interviews across any company.
2. **Interviewer Conflict Prohibition:** An interviewer cannot conduct multiple interviews concurrently; optional rest buffers are enforced.
3. **Room Conflict Prohibition:** An interview room cannot host overlapping sessions.
4. **Global & Resource Window Compliance:** Sessions must fit entirely within the operating window and match interviewer availability windows.
5. **Positive Non-Zero Duration:** Zero or negative duration requests are rejected at validation.
6. **Shortlist Prerequisite:** Only applications transitioned to `ApplicationStatus.SHORTLISTED` are scheduled.
7. **Confirmed Appointment Immutability:** Pre-existing confirmed sessions in the roster are protected and never displaced.

---

## 6. Verification & Automated Test Matrix

The test suite consists of **79 automated tests across 6 test classes** executing via `mvn clean test`. All 79 tests pass with 0 errors and 0 failures.

| Test Class | Test Focus | Tests Run | Result |
| :--- | :--- | :---: | :---: |
| [`EntityValidationTest`](file:///c:/Users/astra/Desktop/Project/hireflow/src/test/java/com/hireflow/model/EntityValidationTest.java) | Domain constraints, null safety, boundary checks, and equality semantics across all 7 entities. | 22 | **PASS (22/22)** |
| [`RepositoryOperationsTest`](file:///c:/Users/astra/Desktop/Project/hireflow/src/test/java/com/hireflow/repository/RepositoryOperationsTest.java) | CRUD repository operations, unique ID enforcement, unmodifiable views, query filters, and clear methods. | 16 | **PASS (16/16)** |
| [`ServiceOperationsTest`](file:///c:/Users/astra/Desktop/Project/hireflow/src/test/java/com/hireflow/service/ServiceOperationsTest.java) | Business logic validation, eligibility checking, duplicate rejection, and state machine transitions. | 19 | **PASS (19/19)** |
| [`GreedySchedulerTest`](file:///c:/Users/astra/Desktop/Project/hireflow/src/test/java/com/hireflow/scheduler/GreedySchedulerTest.java) | Algorithmic conflict detection, interval overlaps, multi-interviewer allocation, buffer gaps, and unscheduled diagnostics. | 10 | **PASS (10/10)** |
| [`UITest`](file:///c:/Users/astra/Desktop/Project/hireflow/src/test/java/com/hireflow/ui/UITest.java) | Sample data seeding verification and Swing component lifecycle instantiation without errors. | 2 | **PASS (2/2)** |
| [`Stage1WorkflowIntegrationTest`](file:///c:/Users/astra/Desktop/Project/hireflow/src/test/java/com/hireflow/Stage1WorkflowIntegrationTest.java) | Explicit end-to-end integration tests verifying all 10 required project workflows. | 10 | **PASS (10/10)** |
| **TOTAL** | **Comprehensive Stage 1 Verification Suite** | **79** | **ALL PASSED (79/79)** |

---

## 7. Known Limitations & Recommendations for Stage 2

### Known Limitations in Stage 1
1. **Volatile Memory Persistence:** Data lives in JVM memory. Restarting the desktop application resets all mutations back to standard seed data.
2. **Greedy Local Optimization:** The current greedy scheduler assigns the earliest feasible slot sequentially. While deterministic and fast, it does not perform global backtracking or optimization (e.g. Hungarian algorithm or branch-and-bound) for maximum global slot utilization.
3. **Single Local Session:** Designed as a single-user desktop client without multi-tenancy or concurrent multi-terminal synchronization.

### Stage 2 Recommendations
1. **Persistence Layer (JDBC):** Replace `InMemory...Repository` classes with JDBC implementations targeting PostgreSQL or SQLite. The `CrudRepository<T, ID>` interface ensures that service classes require zero code changes.
2. **Transaction Management:** Implement transactional boundaries for atomic operations (e.g. creating a recruitment drive and allocating interviewer slots atomically).
3. **Advanced Scheduling Heuristics:** Introduce priority-weighted greedy allocation (e.g. prioritizing students with higher CGPA or candidates with fewer available interview windows).
4. **Export & Reporting:** Add PDF/Excel export capabilities for proposed timetables using Apache POI or iText.
