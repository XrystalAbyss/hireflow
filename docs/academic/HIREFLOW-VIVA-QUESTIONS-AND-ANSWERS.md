# HIREFLOW — Academic Viva Questions & Authoritative Answers
## 55 Essential Questions for Advanced Java & Full-Stack System Evaluation
**Milestone:** CAP-8.6.4 Final Academic Release  
**Target:** External Java Examiners, Technical Viva Panels, Academic Reviewers

---

### Section 1: Java Core & Object-Oriented Programming (Q1 – Q6)

#### Q1: What is the significance of using Java Records in HireFlow (e.g., `TimeWindow`)?
**Answer:** Java Records provide compact, immutable data carriers with auto-generated constructors, getters, `equals()`, and `hashCode()`, enforcing invariant validation directly in compact constructors without boilerplate.

#### Q2: How does HireFlow achieve abstraction between storage mechanisms?
**Answer:** Through the generic `CrudRepository<T, ID>` interface, which decouples domain and scheduling services from whether records reside in in-memory collections, JDBC, or Spring Data.

#### Q3: Where is polymorphism demonstrated in the repository layer?
**Answer:** Subclasses like `InMemoryApplicationRepository` and `JdbcApplicationRepository` implement `ApplicationRepository` differently while being treated uniformly by higher-level services.

#### Q4: Why are domain model fields declared `private` with defensive copies?
**Answer:** To guarantee encapsulation so external callers cannot mutate internal state (such as allowed branches in a recruitment drive) outside controlled domain methods.

#### Q5: How are Java 21 features used in HireFlow?
**Answer:** Through Java Records, Pattern Matching for `switch` in exception handling, and modern Stream APIs across service layers.

#### Q6: What is defensive copying and where is it applied?
**Answer:** Returning a new copy of mutable data structures (e.g. `new HashSet<>(allowedBranches)`) in getters to prevent callers from modifying internal entity collections.

---

### Section 2: Java Collections Framework (Q7 – Q12)

#### Q7: Why is `ConcurrentHashMap` used instead of `HashMap` in `AbstractInMemoryRepository`?
**Answer:** `ConcurrentHashMap` provides thread-safe operations using fine-grained segment/bucket locking, preventing race conditions during concurrent reads and writes without locking the entire map.

#### Q8: Where is `TreeSet` or custom `Comparator` sorting used in HireFlow?
**Answer:** In `GreedyScheduler`, where a `Comparator` sorts applications by student ID, drive ID, and application ID. CGPA is not used as a scheduling priority.

#### Q9: Why is `ArrayList` chosen for candidate and session lists?
**Answer:** `ArrayList` provides $O(1)$ random access time and low memory overhead, ideal for iterative time-slot evaluation and indexed timetable rendering.

#### Q10: How are duplicate drive applications prevented using collections in memory?
**Answer:** Through `Set<String>` collections containing combined keys of `studentId + ":" + driveId` tested before insertion.

#### Q11: What is the time complexity of looking up eligible branches in `RecruitmentDrive`?
**Answer:** $O(1)$ average time complexity because allowed academic branches are stored in a `HashSet<String>`.

#### Q12: Why are `List` interfaces used as return types rather than concrete `ArrayList`?
**Answer:** To adhere to the principle of programming to an interface rather than an implementation, allowing flexible collection substitutions without affecting calling code.

---

### Section 3: Java Swing Desktop UI (Q13 – Q17)

#### Q13: What is the Event Dispatch Thread (EDT) and why is it critical in Swing?
**Answer:** The EDT is the single thread responsible for handling GUI rendering and events; executing long-running calculations on the EDT freezes the UI and causes unresponsiveness.

#### Q14: How does HireFlow run greedy scheduling in Swing without freezing the interface?
**Answer:** By offloading scheduling computation to a `SwingWorker` background thread (`doInBackground()`) and updating GUI components only in `done()` back on the EDT.

#### Q15: Which Swing layout manager is used for screen transitions in the desktop application?
**Answer:** `CardLayout` is used in `HireFlowDesktopApp` to switch cleanly between student registration, drive management, and scheduling panels.

#### Q16: How does `JTable` display candidate application data?
**Answer:** A `DefaultTableModel` binds domain attributes to table columns, while custom table cell renderers apply color badges according to application status (`SHORTLISTED`, `SCHEDULED`).

#### Q17: What role do `ActionListener` and lambda expressions play in Swing?
**Answer:** They handle user interaction events (such as button clicks) concisely by implementing functional listener interfaces using modern Java lambda syntax.

---

### Section 4: JDBC, Transactions & PostgreSQL (Q18 – Q24)

#### Q18: What is HikariCP and why was it chosen for HireFlow?
**Answer:** HikariCP is a lightweight, high-performance JDBC connection pool delivering ultra-fast connection acquisition, fail-fast startup checks, and automatic leak detection.

#### Q19: How does HireFlow guard against SQL injection attacks in JDBC?
**Answer:** By exclusively using parameterized `PreparedStatement` with placeholder bindings (`?`) for all user-supplied values, ensuring input cannot alter SQL syntax.

#### Q20: What are PostgreSQL GiST exclusion constraints and why are they used?
**Answer:** Exclusion constraints (`EXCLUDE USING gist`) with `btree_gist` and `tsrange` guarantee at the database level that no two sessions share the same room or interviewer over overlapping intervals.

#### Q21: How are relational transactions demarcated in JDBC?
**Answer:** By setting `connection.setAutoCommit(false)`, executing statements within `try-with-resources`, calling `connection.commit()` on success, and invoking `connection.rollback()` in the `catch` block.

#### Q22: What happens if a database connection fails during publication?
**Answer:** The active transaction is rolled back, no partial appointments are retained, and a `DatabaseException` is propagated to return an explicit error.

#### Q23: How are database schema changes managed across team environments?
**Answer:** Using Flyway database migrations (`V1__initial_schema.sql`, `V2__user_accounts.sql`, etc.) which version-control and apply SQL scripts deterministically on startup.

#### Q24: How does HireFlow prevent deadlocks during concurrent multi-session publications?
**Answer:** By acquiring row-level locks on rooms and interviewers in a deterministic, sorted order (`SELECT ... FOR UPDATE ORDER BY id ASC`) before committing.

---

### Section 5: Java Networking & Socket Architecture (Q25 – Q28)

#### Q25: How does the HireFlow Stage 1 networking protocol communicate events?
**Answer:** Through TCP client/server sockets (`ServerSocket` and `Socket`) exchanging structured newline-delimited JSON or text message frames over a dedicated port.

#### Q26: Why is TCP used rather than UDP for placement broadcast notifications?
**Answer:** TCP guarantees ordered, reliable, connection-oriented packet delivery, ensuring critical interview updates are not dropped or corrupted in transit.

#### Q27: How are server socket connections handled concurrently in Stage 1?
**Answer:** A thread-per-connection or thread pool model (`ExecutorService`) accepts incoming client sockets and delegates event handling to worker threads.

#### Q28: How does the web layer handle real-time server-to-client updates in Stage 5?
**Answer:** Via Server-Sent Events (SSE) streaming updates over HTTP directly to the browser UI without full-page reloads.

---

### Section 6: Spring Boot REST API & Spring Security (Q29 – Q35)

#### Q29: What is the role of Spring Boot in HireFlow's architecture?
**Answer:** Spring Boot provides the enterprise REST API runtime, automated dependency injection, declarative transaction management, and unified configuration.

#### Q30: How does Spring Security authenticate users in HireFlow?
**Answer:** Using a standard cookie-based `JSESSIONID` session mechanism where `DaoAuthenticationProvider` verifies credentials against the database using BCrypt.

#### Q31: How is Cross-Site Request Forgery (CSRF) mitigated in HireFlow?
**Answer:** Using Spring Security's `HttpSessionCsrfTokenRepository` for synchronizer tokens stored server-side in the HTTP session, validated using the `X-CSRF-TOKEN` header on state-changing requests. Cookie flags are a separate defense.

#### Q32: What are the three security roles enforced across HireFlow endpoints?
**Answer:** `ROLE_STUDENT` (applicant self-service), `ROLE_PLACEMENT_OFFICER` (drive, company, candidate, and scheduling management), and `ROLE_ADMINISTRATOR` (system administration).

#### Q33: How does `GlobalExceptionHandler` benefit API design?
**Answer:** It intercepts unhandled exceptions globally and formats them into uniform `ApiErrorResponse` JSON payloads with appropriate HTTP status codes and diagnostic paths.

#### Q34: What is the purpose of `AccountStatusFilter`?
**Answer:** It checks the authenticated user's active/disabled status against PostgreSQL on every request, immediately terminating sessions of disabled accounts (HTTP 401).

#### Q35: Why does HireFlow return HTTP 207 Multi-Status for partial publication?
**Answer:** To inform the placement officer that some appointments succeeded while specific conflicts prevented others, returning itemized confirmed and rejected session details.

---

### Section 7: Algorithmic Scheduling & Complexity (Q36 – Q41)

#### Q36: What scheduling algorithm is implemented in HireFlow?
**Answer:** A deterministic greedy interval scheduling algorithm (`GreedyScheduler`) with multi-resource constraint checking.

#### Q37: Is the GreedyScheduler guaranteed to produce a globally optimal schedule?
**Answer:** No. It picks feasible early slots and resources in deterministic ID order, without global backtracking or guaranteed maximum placement. It does not prioritize CGPA.

#### Q38: What is the computational time complexity of the GreedyScheduler?
**Answer:** A simplified bound is $O(N\log N + N\cdot S\cdot I\cdot R\cdot C)$ where $C$ is the cost of inspecting existing sessions for conflicts. Actual complexity depends on implementation and session pool size.

#### Q39: What is the two-phase scheduling workflow in HireFlow?
**Answer:** Phase 1 generates a non-mutating advisory preview with an unpredictable, short-lived server-side preview token in memory; Phase 2 durably commits validated appointments to PostgreSQL upon officer confirmation.

#### Q40: What happens if two officers attempt to publish conflicting previews concurrently?
**Answer:** Concurrent publication is revalidated and database constraints prevent double-booking. Outcomes can include HTTP 200, 207 partial success, or 409 conflicts depending on the token and transaction state.

#### Q41: How are schedule preview tokens secured against replay attacks?
**Answer:** Preview tokens are single-use (`PREV-...`) with a 15-minute expiration; once consumed or expired, replayed submission attempts are rejected with HTTP 409 or HTTP 410.

---

### Section 8: Quality Assurance & Testing Engineering (Q42 – Q46)

#### Q42: What is the total automated test count across the HireFlow repository?
**Answer:** **863 automated tests**: 519 Core Java/Swing tests, 197 Spring Boot API integration tests, and 147 React Vitest tests, plus Playwright E2E suites.

#### Q43: How does `SafeTestDatabase` prevent test suites from polluting demonstration data?
**Answer:** It creates disposable PostgreSQL schemas (`cap852_core_<hash>`), applies Flyway migrations per test class, and executes tests in complete schema isolation.

#### Q44: What is verified in the 40-step Playwright live full-stack acceptance test?
**Answer:** The complete lifecycle: student login $\rightarrow$ drive exploration $\rightarrow$ application submission $\rightarrow$ officer shortlisting $\rightarrow$ room allocation $\rightarrow$ preview generation $\rightarrow$ publication $\rightarrow$ student timetable verification.

#### Q45: What accessibility standard does HireFlow target and test?
**Answer:** WCAG 2.1 AA standards, validated automatically via `@axe-core/playwright` across desktop and mobile viewports with keyboard focus trapping.

#### Q46: What is a characterization test and why was it used during refactoring?
**Answer:** A test that pins down the existing observable behavior of a working historical component to ensure changes do not introduce unintended behavioral regressions.

---

### Section 9: Architectural Trade-Offs & Academic Defense (Q47 – Q51)

#### Q47: Why did HireFlow choose session cookies over JWTs for authentication?
**Answer:** University placement applications are stateful, centralized enterprise intranets requiring instant server-side revocation on account disablement, which sessions provide natively without token blacklisting overhead.

#### Q48: Why was raw JDBC preserved alongside Spring Boot rather than replacing it with Hibernate?
**Answer:** To explicitly demonstrate low-level relational mechanics, fine-grained transaction control, and SQL constraint mapping required for university Advanced Java evaluation.

#### Q49: Why wasn't a microservice architecture adopted?
**Answer:** A modular monolith minimizes network latency, avoids distributed transaction complexity (2PC/Sagas), and guarantees ACID transactional scheduling across rooms and candidates.

#### Q50: How does HireFlow ensure the React frontend does not duplicate business logic?
**Answer:** The Java backend remains the authoritative validation and computation engine; the frontend acts strictly as a presentation and user-interaction layer.

#### Q51: How does the application enforce the university 36-room policy?
**Answer:** `UniversityCampusRoomPolicy` explicitly approves only the 36 standard engineering interview rooms across K-Block and L-Block (`KF02` - `LT10`), rejecting faculty offices and unaccredited labs.

---

### Section 10: Limitations & Future Scope (Q52 – Q55)

#### Q52: What is a known limitation of the current single-round interview pipeline?
**Answer:** HireFlow currently supports single-round technical/HR interview scheduling; multi-stage sequential rounds (e.g. Round 1 Coding $\rightarrow$ Round 2 Technical $\rightarrow$ Round 3 HR) are architected for future milestones.

#### Q53: How could scheduling optimization be enhanced in future versions?
**Answer:** By incorporating constraint satisfaction solvers (e.g., OptaPlanner or Integer Linear Programming) to optimize global room utilization across hundreds of concurrent drives.

#### Q54: What is the current deployment boundary of the application?
**Answer:** HireFlow is packaged for local demonstration and intranet campus evaluation on isolated containers; internet public cloud deployment is intentionally deferred.

#### Q55: Why is the project considered complete as an academic prototype?
**Answer:** All core functional recruitment workflows, 5 evolutionary Java stages, 863 automated tests, and 5-perspective usability gates have been rigorously verified and frozen at `v0.8.6.4-final-academic-prototype`.
