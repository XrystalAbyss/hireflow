# HIREFLOW — Advanced Java Concept-to-Code Traceability Matrix
## Complete Code Mapping for University Advanced Java Evaluation
**Milestone:** CAP-8.6.4 Final Academic Release  
**Target Audience:** University External Examiners, Evaluators, and Code Reviewers  
**Repository Source Truth:** `C:\Users\astra\Desktop\Project\hireflow`

---

## 1. Overview of Academic Evaluation Objectives

This document establishes 1-to-1 traceability between theoretical Advanced Java concepts and their concrete, production-grade implementations in the HireFlow codebase. Each section explains:
- **WHAT** the concept is.
- **WHY** it was selected for this placement problem.
- **WHERE** it is implemented (file, class, method).
- **HOW** it functions in the execution flow.

---

## 2. Comprehensive Concept Traceability Matrix

### 2.1 Object-Oriented Principles: Encapsulation & Immutability
- **Concept:** Information hiding, immutable value objects, record types, and domain state protection.
- **Why Selected:** Recruitment entities (such as candidate applications and interview sessions) must enforce strict invariant validity upon instantiation and forbid uncontrolled field mutation.
- **Implementation in HireFlow:**
  - `src/main/java/com/hireflow/model/Student.java`: Immutable core fields (`id`, `rollNumber`, `email`), defensive getters, and validated CGPA invariants ($0.0 \le \text{cgpa} \le 10.0$).
  - `src/main/java/com/hireflow/model/Application.java`: Encapsulates valid lifecycle state transitions (`APPLIED` $\rightarrow$ `SHORTLISTED` $\rightarrow$ `SCHEDULED` $\rightarrow$ `SELECTED` / `REJECTED`).
  - `src/main/java/com/hireflow/model/TimeSlot.java`: Immutable Java 21 Record verifying start time strictly precedes end time:
    ```java
    public record TimeSlot(LocalDateTime startTime, LocalDateTime endTime) {
        public TimeSlot {
            Objects.requireNonNull(startTime, "Start time cannot be null");
            Objects.requireNonNull(endTime, "End time cannot be null");
            if (!startTime.isBefore(endTime)) {
                throw new IllegalArgumentException("Start time must be before end time");
            }
        }
    }
    ```

### 2.2 Abstraction & Interfaces
- **Concept:** Separation of interface contract from concrete implementation.
- **Why Selected:** Allows identical business logic (e.g. `EligibilityService`, `GreedyScheduler`) to operate seamlessly against in-memory repository implementations (Stage 1), JDBC relational persistence (Stage 2), or Spring Data JPA repositories (Stage 4).
- **Implementation in HireFlow:**
  - `src/main/java/com/hireflow/repository/Repository.java`: Generic repository interface defining standard CRUD operations:
    ```java
    public interface Repository<T, ID> {
        T save(T entity);
        Optional<T> findById(ID id);
        List<T> findAll();
        void deleteById(ID id);
        boolean existsById(ID id);
    }
    ```
  - Sub-interfaces: `ApplicationRepository`, `StudentRepository`, `CompanyRepository`, `RecruitmentDriveRepository`, `InterviewerRepository`, `RoomRepository`, `InterviewSessionRepository`.

### 2.3 Generics & Type Safety
- **Concept:** Compile-time type checking and reusable algorithmic containers.
- **Why Selected:** Eliminates runtime `ClassCastException` and guarantees type safety across heterogeneous repository data stores and pagination models.
- **Implementation in HireFlow:**
  - `src/main/java/com/hireflow/repository/InMemoryRepository.java`: Generic in-memory data store using parameterized types `T` and `ID`:
    ```java
    public abstract class InMemoryRepository<T, ID> implements Repository<T, ID> {
        protected final ConcurrentHashMap<ID, T> storage = new ConcurrentHashMap<>();
        protected abstract ID extractId(T entity);
        ...
    }
    ```
  - `hireflow-api/src/main/java/com/hireflow/api/dto/PagedResponse.java`: Generic DTO for bounded REST pagination (`PagedResponse<T>`).

### 2.4 Java Collections Framework
- **Concept:** High-performance data structures: `List`, `ArrayList`, `Map`, `Set`, `TreeSet`, and `ConcurrentHashMap`.
- **Why Selected:** Fast indexed traversal, unique element deduplication, and thread-safe shared state.
- **Implementation in HireFlow:**
  - **`ConcurrentHashMap`**: Used in `InMemoryRepository` for thread-safe concurrent storage of drives, candidates, and rooms without global synchronization bottlenecks.
  - **`ArrayList` & `List`**: Used in `GreedyScheduler.schedule()` for maintaining ordered applicant rosters and resulting interview sessions.
  - **`Set` & `HashSet`**: Used in `RecruitmentDrive.getAllowedBranches()` for $O(1)$ branch eligibility lookups.
  - **`TreeSet` & `Comparator`**: Used in `GreedyScheduler` for sorting candidate applications deterministically:
    ```java
    Comparator<Application> candidateComparator = Comparator
        .comparingDouble((Application a) -> studentMap.get(a.getStudentId()).getCgpa())
        .reversed()
        .thenComparing(Application::getId);
    ```

### 2.5 Exception Handling & Custom Domain Exceptions
- **Concept:** Checked and unchecked exception hierarchies, graceful failure handling, and fail-closed security.
- **Why Selected:** Placement rules require precise business error reporting (e.g. deadline expired, duplicate application, room double-booking) translated directly into appropriate HTTP/UI status responses.
- **Implementation in HireFlow:**
  - `src/main/java/com/hireflow/exception/HireFlowException.java`: Base runtime exception.
  - Subclasses:
    - `EntityNotFoundException` $\rightarrow$ HTTP 404
    - `ValidationException` $\rightarrow$ HTTP 400
    - `ConflictException` $\rightarrow$ HTTP 409
    - `SchedulingException` $\rightarrow$ HTTP 409 / 500
    - `UnauthorizedAccessException` $\rightarrow$ HTTP 403
  - Handled centrally in REST layer by `hireflow-api/src/main/java/com/hireflow/api/controller/GlobalExceptionHandler.java`.

### 2.6 Desktop GUI: Java Swing Architecture (Stage 1)
- **Concept:** Graphical User Interface using Swing, Abstract Window Toolkit (AWT), Event Dispatch Thread (EDT), and SwingWorker.
- **Why Selected:** Fulfills core Advanced Java requirement for interactive desktop application management independent of web technologies.
- **Implementation in HireFlow:**
  - `src/main/java/com/hireflow/ui/HireFlowDesktopApp.java`: Main entry point launching desktop interface on EDT:
    ```java
    SwingUtilities.invokeLater(() -> {
        HireFlowDesktopApp app = new HireFlowDesktopApp();
        app.setVisible(true);
    });
    ```
  - `CardLayout`: Manages view transitions between Student Registration, Drive Management, Shortlisting Roster, and Scheduling Studio.
  - `JTable` & `DefaultTableModel`: Renders live candidate applicant tables with custom column renderers.
  - `ActionListener`: Handles button click events (`shortlistButton.addActionListener(e -> ...)`).
  - `SwingWorker<T, V>`: Offloads compute-heavy greedy scheduling to a background thread to prevent freezing the EDT.

### 2.7 JDBC, Connection Pooling & HikariCP (Stage 2)
- **Concept:** Java Database Connectivity (JDBC), connection pools, and database resource management.
- **Why Selected:** Production database operations require efficient connection reuse, parameterization against SQL injection, and fail-safe resource cleanup.
- **Implementation in HireFlow:**
  - `src/main/java/com/hireflow/db/DataSourceProvider.java`: Configures and initializes HikariCP DataSource with fail-fast validation.
  - `src/main/java/com/hireflow/repository/JdbcApplicationRepository.java`: Parameterized SQL queries using `PreparedStatement` and `try-with-resources`:
    ```java
    String sql = "SELECT id, student_id, drive_id, status, applied_at, remarks FROM applications WHERE drive_id = ?";
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, driveId);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToApplication(rs));
            }
        }
    }
    ```

### 2.8 Database Transactions & ACID Semantics
- **Concept:** Relational transaction management, commit, rollback, and concurrency isolation.
- **Why Selected:** Publishing multi-session interview timetables must maintain absolute relational consistency; a failure or conflict must be handled safely without leaving partial corrupted data.
- **Implementation in HireFlow:**
  - `src/main/java/com/hireflow/service/InterviewService.java` & `src/main/java/com/hireflow/db/TransactionManager.java`:
    ```java
    try (Connection conn = dataSource.getConnection()) {
        conn.setAutoCommit(false);
        try {
            // 1. Lock and verify resources
            // 2. Persist confirmed sessions
            // 3. Update application states
            conn.commit();
        } catch (Exception ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }
    ```

### 2.9 PostgreSQL Exclusion Constraints & GiST (`btree_gist`)
- **Concept:** Database-level temporal overlap exclusion constraints.
- **Why Selected:** Prevents room, student, and interviewer double-bookings at the physical database layer even under high-concurrency race conditions.
- **Implementation in HireFlow:**
  - `src/main/resources/db/migration/V1__initial_schema.sql`:
    ```sql
    ALTER TABLE interview_sessions
    ADD CONSTRAINT ex_room_temporal_overlap
    EXCLUDE USING gist (room_id WITH =, tsrange(start_time, end_time, '[)') WITH &&);

    ALTER TABLE interview_sessions
    ADD CONSTRAINT ex_interviewer_temporal_overlap
    EXCLUDE USING gist (interviewer_id WITH =, tsrange(start_time, end_time, '[)') WITH &&);
    ```

### 2.10 Jakarta Servlets & JSP Web Layer (Stage 3)
- **Concept:** Java web containers, HTTP request dispatching, filters, and server-side rendering.
- **Why Selected:** Demonstrates the foundational Java web tier prior to Spring Boot automation.
- **Implementation in HireFlow:**
  - `src/main/java/com/hireflow/web/controller/StudentApplicationController.java`: Jakarta Servlet processing `doGet()` and `doPost()`.
  - `src/main/java/com/hireflow/web/filter/PortalAuthorizationFilter.java`: Servlet filter inspecting `HttpSession` for role-based authorization.
  - `src/main/java/com/hireflow/web/security/CsrfTokenManager.java`: Synchronizer token pattern preventing Cross-Site Request Forgery.

### 2.11 Spring Boot, Spring Security & REST API (Stage 4)
- **Concept:** Enterprise dependency injection, RESTful micro-architectures, and declarative security.
- **Why Selected:** Industry-standard architectural tier serving the modern Single Page Application (SPA).
- **Implementation in HireFlow:**
  - `hireflow-api/src/main/java/com/hireflow/api/controller/officer/OfficerSchedulingController.java`: Exposes advisory preview and publication endpoints.
  - `hireflow-api/src/main/java/com/hireflow/api/security/SecurityConfig.java`: Configures `SecurityFilterChain`, BCrypt password encoder, and stateless API endpoints.
  - `AccountStatusFilter.java`: Dynamically validates user account status on every request.

### 2.12 Algorithmic Design: Deterministic Greedy Scheduling
- **Concept:** Greedy approximation algorithm for interval scheduling with multi-resource assignment.
- **Why Selected:** University placement officers require fast, transparent, explainable schedule creation where candidates can clearly understand why they were scheduled at a given time or why a room was assigned.
- **Implementation in HireFlow:**
  - `src/main/java/com/hireflow/scheduler/GreedyScheduler.java`: Main algorithm.
  - `src/main/java/com/hireflow/scheduler/ConflictChecker.java`: Conflict evaluation for students, interviewers, rooms, and buffer intervals.
  - Computational Complexity: $O(N \log N + N \cdot S \cdot I \cdot R)$, where $N$ is candidate count, $S$ is time slots, $I$ is interviewers, and $R$ is rooms.
