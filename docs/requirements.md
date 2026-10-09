# HireFlow - Campus Recruitment & Interview Scheduling System
## Stage 1 Software Requirements Specification (SRS)

---

### 1. Executive Summary & Project Purpose
**HireFlow** is an enterprise-grade desktop management and scheduling system engineered for collegiate Training & Placement Cells (TPC). Developed by a three-member B.Tech Advanced Java engineering team, the platform streamlines the end-to-end placement pipeline: student registry, company recruitment drive management, rule-based academic eligibility verification, candidate application lifecycle, and algorithmic interview scheduling.

**Stage 1 Prototype Objective:**
Deliver a robust, standalone **Java 21 + Maven + Swing** prototype establishing:
- Object-Oriented Domain Architecture (Encapsulation, Polymorphism, Abstraction)
- Layered Separation of Concerns (UI $\leftrightarrow$ Service $\leftrightarrow$ Scheduler $\leftrightarrow$ Repository $\leftrightarrow$ Model)
- Java Collections Framework in-memory storage (with Repository interfaces designed for Stage 2 JDBC integration)
- Robust Exception Handling & Defensive Input Validation
- Conflict-Free Greedy Scheduling Algorithm
- Automated Unit Testing with JUnit 5 Jupiter

---

### 2. Functional Requirements (FR)

#### FR-1: Student Registry Management
- **FR-1.1**: The system shall support registering and updating student profiles with unique Student ID (`STU-XXX`), Full Name, Institutional Email, Department/Branch (e.g., CSE, ECE, IT, MECH, CIVIL), Cumulative Grade Point Average (CGPA, scale $0.00 - 10.00$), Graduation Year, Active Status, and Technical Skill Tags.
- **FR-1.2**: The system shall enforce unique email and ID constraints, rejecting malformed emails or out-of-range CGPAs via custom `ValidationException`.

#### FR-2: Company & Recruitment Drive Management
- **FR-2.1**: The system shall register visiting corporate partners with Company ID (`CMP-XXX`), Name, Industry Domain, and Tier (e.g., *Product*, *Fintech*, *Consulting*, *Core Engineering*).
- **FR-2.2**: The system shall allow creating Recruitment Drives (`DRV-XXX`) associated with a company, specifying Job Role, Minimum CGPA threshold, Allowed Branches/Departments, Drive Date, Drive Status (`PLANNED`, `ONGOING`, `COMPLETED`, `CANCELLED`), and Maximum Candidate Shortlist limits.

#### FR-3: Automated Rule-Based Eligibility Evaluation
- **FR-3.1**: The system shall dynamically compute whether a registered student is eligible for a specific recruitment drive based on:
  - CGPA $\ge$ Drive Minimum CGPA.
  - Student Branch $\in$ Drive Permitted Branches.
  - Student Active Status is `true`.
- **FR-3.2**: The system shall provide structured `EligibilityResult` objects stating status (`ELIGIBLE` or `INELIGIBLE`) along with explicit reason codes (e.g., *“CGPA 7.4 below required 8.0”*, *“Branch MECH not permitted”*).

#### FR-4: Candidate Application Lifecycle
- **FR-4.1**: Students may apply for active drives, transitioning through formal application states:
  $$\text{APPLIED} \longrightarrow \text{ELIGIBLE} / \text{INELIGIBLE} \longrightarrow \text{SHORTLISTED} \longrightarrow \text{SCHEDULED} \longrightarrow \text{SELECTED} / \text{REJECTED}$$
- **FR-4.2**: Duplicate applications for the same Student-Drive pair shall be blocked.

#### FR-5: Interviewer & Resource Registry
- **FR-5.1**: The system shall manage company interview panel members with Interviewer ID (`INT-XXX`), Name, Company, Email, Domain Focus, and Assigned Room / Virtual Booth.

#### FR-6: Conflict-Free Greedy Interview Scheduling Engine
- **FR-6.1**: The system shall automatically generate interview sessions for shortlisted candidates using a greedy slot-allocation algorithm.
- **FR-6.2**: The scheduler shall enforce strict non-overlapping constraints:
  - **No Student Double-Booking:** A student cannot have multiple interviews occurring at intersecting time intervals.
  - **No Interviewer Double-Booking:** An interviewer cannot conduct multiple sessions simultaneously.
  - **No Room/Venue Conflict:** An interview room cannot host concurrent sessions.
- **FR-6.3**: When slot capacity or interviewer availability is exhausted, unassigned candidates shall be clearly reported with overflow status.

#### FR-7: Swing Desktop Graphical Interface
- **FR-7.1**: Provide an intuitive, modern desktop UI (`MainFrame`) featuring tabbed navigation:
  - *Student Management Panel* (`StudentPanel`): Table view, search/filter, student registration form.
  - *Recruitment Drives Panel* (`DrivePanel`): Drive list, drive creation, candidate application & eligibility viewer.
  - *Interview Scheduler Panel* (`SchedulePanel`): One-click automated scheduler trigger, timetable display, slot metrics, and conflict inspector.
- **FR-7.2**: Provide one-click pre-populated sample dataset seeding for rapid demonstration.

---

### 3. Non-Functional Requirements (NFR)

- **NFR-1: Performance & Responsiveness:** In-memory scheduling of 100+ candidates must execute in under $50\text{ ms}$. Swing UI actions must remain non-blocking.
- **NFR-2: Architecture & Modularity:** Strict package boundaries (`model`, `repository`, `service`, `scheduler`, `ui`, `exception`). UI components must never access storage collections directly; all actions pass through service layers.
- **NFR-3: Persistence Readiness:** All repositories expose interface contracts (`findById`, `findAll`, `save`, `deleteById`), hiding the in-memory `Map` backing store so that Stage 2 JDBC implementations can be swapped with zero UI/Service changes.
- **NFR-4: Error Handling & Reliability:** Standardized hierarchy derived from `HireFlowException`. Invalid form inputs yield descriptive Swing error dialogs without unhandled console exceptions.
- **NFR-5: Testability & Quality:** JUnit 5 test suites covering eligibility logic, greedy scheduling allocations, overlapping intervals, and boundary cases.

---

### 4. Technical Specifications
- **JDK Target:** Java 21 LTS
- **Build System:** Apache Maven 3.9+
- **GUI Framework:** Standard Java Swing (`javax.swing`, `java.awt`)
- **Testing Framework:** JUnit 5 (Jupiter 5.10+)
- **Target OS:** Cross-platform (Windows 11, macOS, Linux)
