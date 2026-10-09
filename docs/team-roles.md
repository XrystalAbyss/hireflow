# HireFlow Engineering Team - Roles & Responsibilities
## Three-Member B.Tech Advanced Java Project Team

---

### Team Overview
This project structure reflects the engineering division for **HireFlow**, an academic B.Tech Advanced Java capstone prototype. The architecture follows a clean division between Domain Modeling, Algorithmic Scheduling, and UI Presentation.

```
                  +----------------------------------------------+
                  |         Member 1: Lead Architect             |
                  |  Domain Models, Repositories, Exceptions,    |
                  |     Student Service, Dependency Wiring       |
                  +----------------------+-----------------------+
                                         |
                                         v
                  +----------------------------------------------+
                  |    Member 2: Algorithms & Core Logic Lead    |
                  |  Eligibility Rules Engine, Conflict Checker, |
                  |    Greedy Scheduler, JUnit 5 Test Suites     |
                  +----------------------+-----------------------+
                                         |
                                         v
                  +----------------------------------------------+
                  |       Member 3: UI & Integration Lead        |
                  |    Swing Desktop Interface, Event Handlers,  |
                  |    Table Models, Responsive Forms, Seeding   |
                  +----------------------------------------------+
```

---

### Member 1: Lead Architect & Core Domain Engineer
- **Primary Package Ownership:**
  - `com.hireflow.model.*` (`Student`, `Company`, `RecruitmentDrive`, `Application`, `Interviewer`, `InterviewSession`)
  - `com.hireflow.repository.*` (`StudentRepository`, `DriveRepository`, `ApplicationRepository`, InMemory implementations)
  - `com.hireflow.exception.*` (`HireFlowException`, `ValidationException`, `EntityNotFoundException`, etc.)
  - `com.hireflow.service.StudentService`
  - `com.hireflow.Main`
- **Key Deliverables:**
  - Designed pure OOP entity models with immutability where applicable, defensive copying, and valid domain constraints.
  - Implemented the in-memory Repository pattern backed by thread-safe `ConcurrentHashMap` collections, establishing contracts ready for Stage 2 JDBC migration.
  - Defined centralized custom exception hierarchy and input validation standards (email regex, CGPA scale $0.0-10.0$, date logic).
  - Configured Maven build descriptor (`pom.xml`), compiler target Java 21, and project packaging.

---

### Member 2: Algorithms & Scheduling Specialist
- **Primary Package Ownership:**
  - `com.hireflow.service.EligibilityService`
  - `com.hireflow.scheduler.ConflictChecker`
  - `com.hireflow.scheduler.GreedyScheduler`
  - `com.hireflow.service.SchedulingService`
  - `src/test/java/com/hireflow/EligibilityServiceTest`
  - `src/test/java/com/hireflow/SchedulingServiceTest`
- **Key Deliverables:**
  - Engineered the rule-based Eligibility Engine supporting dynamic branch filtering and CGPA cutoffs.
  - Devised the Greedy Interval Interview Scheduling algorithm allocating students to interviewers and rooms across discrete time blocks.
  - Implemented real-time interval conflict detection preventing student, interviewer, and venue double-booking ($O(N \log N)$ sorting and linear greedy passes).
  - Authored comprehensive JUnit 5 test suites validating eligibility thresholds, edge cases, conflict scenarios, and greedy allocation invariants.

---

### Member 3: UI & System Integration Engineer
- **Primary Package Ownership:**
  - `com.hireflow.ui.MainFrame`
  - `com.hireflow.ui.StudentPanel`
  - `com.hireflow.ui.DrivePanel`
  - `com.hireflow.ui.SchedulePanel`
  - `com.hireflow.service.ApplicationService`
- **Key Deliverables:**
  - Built an intuitive Java Swing desktop interface with consistent styling, tabbed views, and status indicators.
  - Implemented dynamic `JTable` rendering, custom table cell formatters, and reactive form listeners.
  - Connected UI action listeners exclusively to service interfaces without tight coupling to repository data structures.
  - Created interactive feedback dialogs (`JOptionPane`) translating business exceptions into user-friendly error messages.
  - Engineered the sample dataset seeder providing instant mock records (students, drives, interviewers) for live demonstration.

---

### Collaboration Conventions & Git Workflow
1. **Branching Strategy:** Feature-branching (`feature/domain-models`, `feature/greedy-scheduler`, `feature/swing-ui`) merged into `main` after clean compilation and unit test execution.
2. **Java 21 Standards:** Utilize modern Java language constructs (switch expressions, pattern matching, records where suitable, lambda expressions, and Java Streams).
3. **Commit Quality:** Every merge requires `mvn clean test` passing with zero test failures or compile warnings.
