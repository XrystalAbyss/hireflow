# HireFlow - Campus Recruitment & Interview Scheduling System

> **Stage 4 Status:** **Stage 4F.1 COMPLETE (CAP-7 Release Preflight, API Contract Reconciliation, and Gate A Checkpoint Ready)**<br/>
> **Release Checkpoints:** `v1.0.0-stage1` (`66826c1`) | `v2.0.0-cap5` (`559ed7b`) | `v3.0.0-cap6` (`30e796e`) | Active Branch: `develop/stage4`<br/>
> **Technology Stack:** **Java 21 LTS**, **Apache Maven**, **Spring Boot 4.1.1**, **Spring Framework 7.0.9**, **Spring Security 7.1.1**, **Spring Data JPA**, **Hibernate ORM 7.4.5.Final**, **Jakarta Servlet API 6.0/6.1**, **Jakarta Server Pages (JSP) 3.1**, **JSTL 3.0**, **Apache Tomcat 10.1.x & Embedded Tomcat 11.0.24**, **BCrypt** (`at.favre.lib:bcrypt:0.10.2`), **PostgreSQL 16**, **HikariCP**, **Flyway**, **Standard Java Swing (Vanilla GUI)**, and **JUnit 5 Jupiter**.<br/>
> **Architecture:** Centralized `ApplicationContext` supporting dual modes (`IN_MEMORY`, `JDBC_DEV`), Jakarta EE MVC Web Portal, Swing Desktop Interface, and the newly established Stage 4 Spring Boot REST API (`hireflow-api` on port `8081`). Flyway V1-V3 migrations strictly own the PostgreSQL relational schema, while Hibernate runs in validation-only mode (`ddl-auto=validate`).<br/>
> **Automated Verification:** **678 Passing Tests** (495 CAP-6 Legacy Tests + 183 Stage 4 Spring Boot Tests, 0 Failures, 0 Errors, 0 Skips).

---

## 1. Project Overview

**HireFlow** is a desktop solution designed for university Training & Placement Cells (TPC) to automate and coordinate campus recruitment activities. The system manages candidate rosters, corporate recruitment drives, dynamic academic eligibility checks, and automated conflict-free interview scheduling.

### Architecture Highlights:
- **Zero Heavyweight Frameworks:** Implemented cleanly in core Java 21, Swing GUI, HikariCP, Flyway, and JUnit 5 (zero Spring Boot, React, Hibernate, or OR-Tools).
- **Dual Runtime Storage Modes via `ApplicationContext`:**
  - **`IN_MEMORY`**: Pure Stage 1 thread-safe in-memory collections (`ConcurrentHashMap`). Works with zero database dependencies, even when PostgreSQL is completely offline.
  - **`JDBC_DEV`**: Production-grade PostgreSQL storage connected to `hireflow_dev` via HikariCP, auto-migrated via Flyway, supporting persistent records across application restarts.
  - **`JDBC_TEST`**: Isolated test mode strictly targeting `hireflow_test` with safety guards preventing accidental desktop execution.
- **Transactional Schedule Publication:** High-concurrency schedule confirmation utilizing deterministic global lock ordering (`Applications` $\rightarrow$ `Rooms` $\rightarrow$ `Interviewers` $\rightarrow$ `Students`), conditional status transitions, and PostgreSQL GiST temporal exclusion constraints preventing double-booking.
- **Integrated TCP Networking Dashboard:** Swing-managed server lifecycle (`NetworkingPanel`) with dynamic port configuration, real-time client/subscriber metrics, uptime tracking, and sanitized diagnostic telemetry.
- **Non-Blocking Swing EDT:** Background processing for server start/stop and algorithmic scheduling powered by `SwingWorker` so the desktop interface never freezes.
- **Sanitized Diagnostics & Security:** Passwords, database connection strings, and candidate PII are never logged, displayed in telemetry, or broadcast over sockets.

---

## 2. Desktop Interface Screens

HireFlow features a modern Swing desktop interface with a dark sidebar navigation, card-based layout, and synchronized real-time indicators:

```
+---------------------------------------------------------------------------------------------------+
|  HIREFLOW - Campus Placement System [PostgreSQL: hireflow_dev]                                    |
+------------------+--------------------------------------------------------------------------------+
|  [HF] HireFlow   |                                                                                |
|                  |   ACTIVE SCREEN CONTENT (CardLayout)                                           |
|  - Dashboard     |                                                                                |
|  - Students      |   - 1. Dashboard: Summary Cards + Live Drives & Applications Tables            |
|  - Companies     |   - 2. Student Registry: Search Roster, Register Form, Edit & Delete Dialogs   |
|    & Drives      |   - 3. Companies & Drives: Tabbed Drive & Corporate Partner Management         |
|  - Applications  |   - 4. Applications: Eligibility Evaluator, Submissions, Status Transitions   |
|  - Scheduler     |   - 5. Interview Scheduler: Parameters, SwingWorker Greedy Engine, Timetable   |
|  - Networking    |   - 6. Networking Dashboard: Server Lifecycle, Live Metrics & Telemetry Log    |
|                  |                                                                                |
|  [Reset Demo]    |   (Disabled in PostgreSQL mode to protect persistent database records)         |
+------------------+--------------------------------------------------------------------------------+
| Status: System Ready | Students: 11 | Drives: 5 | Apps: 6 | Confirmed: 2 | Storage: PostgreSQL ...    |
+---------------------------------------------------------------------------------------------------+
```

- **Screen 1 — Operations Dashboard (`DashboardPanel`):** Live metric cards, active recruitment drives, and recent applications.
- **Screen 2 — Student Management (`StudentPanel`):** Searchable candidate roster, profile registration form, edit and delete dialogs.
- **Screen 3 — Companies & Drives (`CompanyDrivePanel`):** Corporate partner directory and placement drive configuration with branch filters and cutoffs.
- **Screen 4 — Applications & Eligibility (`ApplicationPanel`):** Candidate eligibility evaluator, application submissions, and pipeline status progression (`APPLIED` $\rightarrow$ `SHORTLISTED` $\rightarrow$ `SCHEDULED` $\rightarrow$ `SELECTED`).
- **Screen 5 — Automated Interview Scheduler (`SchedulePanel`):** Configurable scheduling windows, non-blocking `SwingWorker` greedy allocation, timetable preview, conflict inspector, and transactional publication.
- **Screen 6 — Networking Dashboard (`NetworkingPanel`):** Operational TCP server controls (Start/Stop, port configuration, bound interface indicator), live client and subscriber counters, event dispatch metrics, uptime clock, and sanitized in-memory activity stream.

---

## 3. Storage Mode Configuration

HireFlow determines its persistence mode at startup following a strict configuration precedence:
1. Environment variables
2. JVM System Properties (`-Dhireflow.storage.mode=...`)
3. Local configuration file (`hireflow-local.properties`, gitignored)
4. Safe default (`IN_MEMORY`)

### Available Modes:
| Mode | Target | Description | Safe for Restart |
| :--- | :--- | :--- | :--- |
| `IN_MEMORY` | RAM | Pure Stage 1 collections; preloaded with demo data; no DB needed. | Transient |
| `JDBC_DEV` | `hireflow_dev` | Connects to PostgreSQL, runs Flyway migrations, persists records. | **Persistent** |
| `JDBC_TEST`| `hireflow_test`| Reserved for automated integration tests; rejected by GUI launcher. | Disposable |

### Configuration Properties:
| Property Name | Environment Variable | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `hireflow.storage.mode` | `HIREFLOW_STORAGE_MODE` | `IN_MEMORY` | Storage engine (`IN_MEMORY`, `JDBC_DEV`, `JDBC_TEST`) |
| `hireflow.db.url` | `HIREFLOW_DB_URL` | `jdbc:postgresql://localhost:5432/hireflow_dev` | PostgreSQL JDBC connection URL |
| `hireflow.db.user` | `HIREFLOW_DB_USER` | `hireflow_user` | Database user account |
| `hireflow.db.password` | `HIREFLOW_DB_PASSWORD` | *(empty)* | Database user password (never commit) |
| `hireflow.db.pool.maximum-size` | `HIREFLOW_DB_POOL_MAX` | `10` | HikariCP maximum connection pool size |
| `hireflow.db.pool.minimum-idle` | `HIREFLOW_DB_POOL_MIN` | `2` | HikariCP minimum idle connections |

### Setting Up Local Development Credentials:
Create a local file named `hireflow-local.properties` in the project root (this file is gitignored and will never be committed):
```properties
hireflow.storage.mode=JDBC_DEV
hireflow.db.url=jdbc:postgresql://localhost:5432/hireflow_dev
hireflow.db.user=hireflow_user
hireflow.db.password=YOUR_LOCAL_PASSWORD_HERE
hireflow.db.pool.maximum-size=10
hireflow.db.pool.minimum-idle=2
```

---

## 4. Starting PostgreSQL & Database Setup

HireFlow requires PostgreSQL 16 (or 15+) running locally or via network.

### 1. Start PostgreSQL Service
- **Windows:** Start the PostgreSQL service via Windows Services (`services.msc`) or PowerShell:
  ```powershell
  Start-Service postgresql-x64-16
  ```
- **Linux / macOS:**
  ```bash
  sudo systemctl start postgresql
  ```

### 2. Create Databases and Role (One-Time Setup)
Using `psql`:
```sql
CREATE ROLE hireflow_user WITH LOGIN PASSWORD 'YOUR_PASSWORD';

CREATE DATABASE hireflow_dev OWNER hireflow_user;
CREATE DATABASE hireflow_test OWNER hireflow_user;

\c hireflow_dev
CREATE EXTENSION IF NOT EXISTS btree_gist;

\c hireflow_test
CREATE EXTENSION IF NOT EXISTS btree_gist;
```

### 3. Automated Database Migrations
Migrations are managed automatically by Flyway upon application launch or test execution. Migration scripts are located in `src/main/resources/db/migration/`:
- `V1__initial_schema.sql`: Tables, primary keys, foreign keys, btree_gist extension, check constraints, and GiST temporal exclusion constraints.

---

## 5. How to Build & Launch

### Prerequisites:
- **JDK 21** or later (`java --version`, `javac --version`)
- **Apache Maven 3.8+** (`mvn --version`)
- **PostgreSQL 16** (only required for `JDBC_DEV` and `JDBC_TEST` modes)

### Step 1: Compile the Project
```bash
mvn clean compile
```

### Step 2: Run Full Automated Test Suite (266 Tests)
```bash
mvn test
```
Executes all unit, repository, service, scheduler, database concurrency, persistence integration, and TCP networking test suites (including 18 multi-client event broadcasting integration tests).

### Step 3: Package Executable JAR
```bash
mvn clean package
```
Generates `target/hireflow-1.0.0.jar`.

### Step 4: Launch Desktop Application

#### Launch in In-Memory Mode (Zero Database Setup):
```bash
java -Dhireflow.storage.mode=IN_MEMORY -jar target/hireflow-1.0.0.jar
```

#### Launch in PostgreSQL Dev Mode:
```bash
java -Dhireflow.storage.mode=JDBC_DEV -jar target/hireflow-1.0.0.jar
```
*(Or simply configure `hireflow-local.properties` and run without JVM flags).*

### Step 5: Headless / Console Demonstration Mode
For headless environments or quick verification:
```bash
java -jar target/hireflow-1.0.0.jar --demo
```

---

## 6. Verifying Persistence Across Restarts

To verify that records survive across separate application launches:
1. Launch HireFlow in `JDBC_DEV` mode:
   ```bash
   java -Dhireflow.storage.mode=JDBC_DEV -jar target/hireflow-1.0.0.jar
   ```
2. Navigate to **Students** and register a new candidate (e.g. `STU-999`, "Persistent Student", `CSE`, `9.50`).
3. Navigate to **Companies & Drives** and register a corporate partner (e.g. `CMP-999`, "Apex Cloud").
4. Close the application window.
5. Relaunch the application in `JDBC_DEV` mode:
   ```bash
   java -Dhireflow.storage.mode=JDBC_DEV -jar target/hireflow-1.0.0.jar
   ```
6. Check the **Students** and **Companies** panels: the records are immediately loaded from `hireflow_dev`.

---

## 7. Troubleshooting Connection Failures

If PostgreSQL is not running or credentials are incorrect when launching in `JDBC_DEV` mode:
- **Clean Dialog Prompt:** HireFlow catches the connectivity error, strips any sensitive credentials, and presents an interactive prompt:
  > *"Could not connect to PostgreSQL database. Would you like to fall back to IN_MEMORY mode for this session?"*
- **Explicit In-Memory Fallback:** Selecting **Yes** boots the application in transient `IN_MEMORY` mode so users can continue working without interruption. Selecting **No** safely shuts down the application.
- **Common Fixes:**
  1. Confirm the PostgreSQL service is active (`netstat -ano | findstr 5432`).
  2. Verify credentials in `hireflow-local.properties` match your database user.
  3. Verify the `btree_gist` extension exists on `hireflow_dev` (`CREATE EXTENSION IF NOT EXISTS btree_gist;`).

---

## 8. TCP Networking & Operational Swing Dashboard (Stage 2C.3)

HireFlow includes a lightweight, thread-safe TCP networking server built on core Java Sockets (`java.net.Socket`, `ServerSocket`), centralized `ApplicationContext` lifecycle management, and an interactive Swing management dashboard (`NetworkingPanel`):

### Architecture & Safety Hardening:
- **Protocol Version 1.0:** Newline-delimited JSON frames (`\n`) with a 64 KB maximum frame length safeguard (`ProtocolConstants.MAX_FRAME_LENGTH = 65536`).
- **Supported Commands:** `PING`, `SERVER_INFO`, `GET_DRIVES`, `SUBSCRIBE_EVENTS`, `UNSUBSCRIBE_EVENTS`, `HEARTBEAT`.
- **Strict Public Allowlist:** Only approved recruitment events (`JOB_DRIVE_CREATED`, `COMPANY_ADDED`) are broadcast. Candidate PII, applications, scores, and interview sessions are strictly forbidden from network transmission.
- **Bounded Backpressure & Writer Daemons:** Per-subscriber bounded FIFO queues (capacity 50) drained by dedicated daemon threads (`hireflow-subscriber-writer-%d`). Slow or unresponsive subscribers are cleanly unregistered without blocking recruitment services or the Swing EDT.
- **Post-Commit Notification Safety:** Post-commit broadcast hooks are executed strictly outside the transactional rollback block. Any post-commit hook exception is safely caught and logged, guaranteeing that a committed database operation can never be turned into an apparent rollback or failed transaction.
- **Active Acknowledgement Heartbeats:** Heartbeat probes (`HEARTBEAT_PROBE`) sent to idle subscribers require active client acknowledgement (`HEARTBEAT_ACK`), preventing false positives from local OS socket write buffers.
- **Leak-Free Resource Cleanup:** Client disconnections, server stops, or application exit cleanly terminate subscriber writer threads, close active sockets, and release ServerSocket bindings before database connection pool disposal.

---

### Operating the Networking Dashboard & Live Multi-Client Demonstration:

#### Step 1: Launch HireFlow in JDBC Dev Mode
Ensure PostgreSQL 16 is running, then boot HireFlow:
```bash
java -Dhireflow.storage.mode=JDBC_DEV -jar target/hireflow-1.0.0.jar
```
*(HireFlow opens with PostgreSQL dev storage mode active).*

#### Step 2: Open and Operate NetworkingPanel
1. In the left navigation sidebar, click **"Networking"**.
2. Notice the **Server Controls & Lifecycle** card showing:
   - Configurable Listen Port (defaults to `8085`).
   - Server State Badge showing `OFFLINE`.
   - Bound Socket indicator showing `Offline (Not bound)`.
   - Live telemetry counters showing `0` connected, `0` subscribers, `0` events.
3. Click **"▶ Start TCP Server"**.
4. The background `SwingWorker` binds to `127.0.0.1:8085`. The badge flips to a green `RUNNING`, the bound socket displays `127.0.0.1:8085`, and the uptime clock begins incrementing.

#### Step 3: Launch Multiple Java TCP Clients
Open three separate terminal windows and launch client subscribers:

```bash
# Terminal 1 — Client Subscriber A:
java -cp target/hireflow-1.0.0.jar com.hireflow.network.client.NetworkDemoClient 8085 127.0.0.1

# Terminal 2 — Client Subscriber B:
java -cp target/hireflow-1.0.0.jar com.hireflow.network.client.NetworkDemoClient 8085 127.0.0.1

# Terminal 3 — Client Subscriber C:
java -cp target/hireflow-1.0.0.jar com.hireflow.network.client.NetworkDemoClient 8085 127.0.0.1
```
On each client, choose Option `3` (`Subscribe to Public Recruitment Announcements`).  
Observe on the Swing **Networking Dashboard**:
- **Connected Clients** immediately updates to `3`.
- **Active Subscribers** updates to `3`.
- The **Recent Activity & Telemetry Log** table appends entries: `CLIENT_CONNECTED`, `CLIENT_SUBSCRIBED`.

#### Step 4: Verify Persistence and Networking Together
1. In HireFlow's Swing UI, click **"Companies & Drives"** in the sidebar.
2. Select the **"Recruitment Drives"** tab and click **"Create Placement Drive"**.
3. Enter Drive details:
   - Drive ID: `DRV-DEMO-2026`
   - Company: `CMP-001` (Google Cloud)
   - Job Role: `Cloud Systems Architect`
   - Minimum CGPA: `8.50`
   - Eligible Branches: `CSE`, `ECE`
   - Date: 14 days in future
4. Click **"Save Drive"**.
5. **Verify Persistence:** The drive is committed to the PostgreSQL `recruitment_drives` and `drive_eligible_branches` tables.
6. **Verify Broadcast:** Instantly observe all three client terminal consoles outputting:
   ```json
   <<< ASYNC EVENT RECEIVED:
   {
     "version": "1.0",
     "status": "EVENT",
     "data": {
       "eventType": "JOB_DRIVE_CREATED",
       "driveId": "DRV-DEMO-2026",
       "jobRole": "Cloud Systems Architect",
       "companyName": "Google Cloud",
       "minCgpa": 8.5
     }
   }
   ```
7. On the **Networking Dashboard**, observe:
   - **Events Dispatched** increments by `1`.
   - The Activity Log records: `EVENT_DISPATCHED | SUCCESS | Broadcast JOB_DRIVE_CREATED to 3 subscribers`.

#### Step 5: Test Disconnect Isolation and Server Stop
1. Terminate Terminal 1 (`Ctrl+C` or Option `4` to unsubscribe).
2. The dashboard drops to `2` active subscribers.
3. Post another drive from Swing: Subscribers B and C receive the announcement; Subscriber A receives nothing.
4. On the dashboard, click **"■ Stop TCP Server"**.
5. The server cleanly disconnects all remaining clients, unbinds port `8085`, and transitions back to `OFFLINE`.

---

### Architectural Limitations & Security Boundary:
1. **Localhost Binding:** For security and compliance, the TCP server binds strictly to loopback interface `127.0.0.1`, preventing unauthenticated inbound LAN connections.
2. **Best-Effort Delivery:** Sockets provide best-effort streaming delivery without distributed broker acknowledgement (e.g. no Kafka or RabbitMQ persistence layer).
3. **Strict Zero-PII Boundary:** Only public catalog events (`JOB_DRIVE_CREATED`, `COMPANY_ADDED`) are transmitted. Private student applications, CGPAs, disciplinary records, and interview schedules are cryptographically barred from the network broadcast engine.

---

## 9. Historical Verification Milestones & Test Evidence

HireFlow's automated test suite has expanded monotonically across every development milestone, validated against both RAM and PostgreSQL 16:

| Milestone | Scope / Deliverables | Cumulative Passing Tests | Failures / Errors / Skips |
| :--- | :--- | :--- | :--- |
| **Stage 1 Baseline (`66826c1`)** | Domain models, 7 scheduling constraints, Swing GUI, In-Memory repos | **91** (Resolved from 104/114) | 0 / 0 / 0 |
| **Stage 2B.1 (`0756486`)** | HikariCP DataSourceProvider, DatabaseConfig, TransactionManager, Flyway V1 | **114** (+23) | 0 / 0 / 0 |
| **Stage 2B.2** | JdbcCompanyRepository, JdbcRoomRepository, JdbcStudentRepository | **143** (+29) | 0 / 0 / 0 |
| **Stage 2B.3** | JdbcInterviewerRepository, JdbcDriveRepository (normalized branches) | **166** (+23) | 0 / 0 / 0 |
| **Stage 2B.4** | JdbcApplicationRepository, lifecycle state machine, uniqueness constraints | **188** (+22) | 0 / 0 / 0 |
| **Stage 2B.5** | JdbcInterviewSessionRepository, global lock ordering, GiST exclusion constraints | **211** (+23) | 0 / 0 / 0 |
| **Stage 2B.6 (`9c4cca3`)** | ApplicationContext factory, Swing PostgreSQL integration, persistence across restart | **226** (+15) | 0 / 0 / 0 |
| **Stage 2C.1** | Java TCP Server (`HireFlowTcpServer`), framing reader, multiplexed client | **248** (+22) | 0 / 0 / 0 |
| **Stage 2C.2** | Multi-client broadcasting, bounded backpressure (50-queue), candidate privacy filter | **266** (+18) | 0 / 0 / 0 |
| **Stage 2C.3** | Swing Networking Dashboard (`NetworkingPanel`), lifecycle ownership, heartbeat safety | **283** (+17) | 0 / 0 / 0 |
| **Stage 2C.4 (CAP-5 Frozen)** | Full Swing GUI acceptance suite, end-to-end multi-client broadcast & DB restart | **285** (+2) | 0 / 0 / 0 |
| **Stage 3A** | Jakarta Servlets 6.0, JSP 3.1, Tomcat 10.1 WAR, MVC controllers, public drives catalog | **309** (+24) | 0 / 0 / 0 |
| **Stage 3B** | Reusable JSP layout, public drive search/filter/sort, deny-by-default protected portal boundaries (`/student/*`, `/officer/*`), `UserPrincipal`/`UserRole`, container error handling | **331** (+22) | 0 / 0 / 0 |
| **Stage 3C** | Database-backed auth (Flyway V2 `user_accounts`), pure Java BCrypt hashing, Login (`/login`) & Logout (`/logout`), CSRF protection, session ID rotation, BruteForceThrottler, Student & Officer dashboards, Tomcat integration | **375** (+44) | 0 / 0 / 0 |
| **Stage 3D** | Student self-service portal, academic eligibility evaluation, online application submission, application status tracking | **410** (+35) | 0 / 0 / 0 |
| **Stage 3E** | Corporate partner directory (`/officer/companies`), drive management with closure (`/officer/drives`), application review and shortlisting (`/officer/applications`) | **447** (+37) | 0 / 0 / 0 |
| **Stage 3F.1** | Flyway V3 non-destructive drive closure, interviewer management (`/officer/interviewers`), room management (`/officer/rooms`), web scheduling setup & advisory preview (`/officer/scheduling`) | **475** (+28) | 0 / 0 / 0 |
| **Stage 3F.2** | Cryptographic preview state (128-bit CSPRNG, 15-min TTL, multi-tab isolation, one-time anti-replay), transactional interview publication (`POST /officer/scheduling/publish`), Stage 2 lock ordering & GiST protection, confirmed timetable (`GET /officer/scheduling/published`), candidate interview schedule visibility (`GET /student/interviews`) | **494** (+19) | 0 / 0 / 0 |
| **Stage 3G (CAP-6 Accepted)** | 20-step end-to-end browser & PostgreSQL acceptance workflow (`TomcatPostgresE2EWorkflowTest`), scheduling fault-recovery verification, security & privacy audit, release readiness | **495** (+1) | 0 / 0 / 0 |

**Current Test Suite Status:** **495 passed, 0 failed, 0 error, 0 skipped** across all 40 test classes / 73 test suites via `mvn clean verify`.

---

## 10. Jakarta EE Web Portal & Apache Tomcat 10.1 Deployment (Stage 3G / CAP-6 Final Acceptance)

HireFlow Stage 3 delivers the enterprise placement portal, student self-service pipeline, officer recruitment administration, and secure transactional interview timetable publication:

### Architectural Separation (MVC & Security):
- **Model:** Core domain entities, Flyway V2 `user_accounts` table, `UserAccount` entity, `UserAccountRepository` (JDBC and In-Memory), `PasswordHasher` (BCrypt cost 10), `AuthenticationService`, `StudentDriveViewItem`, `StudentApplicationViewItem`, `SchedulingPreviewState`, `PublicationSummaryState`, and `ConfirmedInterviewViewItem`.
- **View:** Modular JSP fragments (`WEB-INF/views/common/header.jspf`, `footer.jspf`, `breadcrumbs.jspf`, `flash.jspf`), public views (`home.jsp`, `drives.jsp`, `about.jsp`), secure authentication view (`login.jsp` with CSRF protection), student portal views (`student/dashboard.jsp`, `student/profile.jsp`, `student/drives.jsp`, `student/applications.jsp`, `student/interviews.jsp`), officer portal views (`officer/dashboard.jsp`, `officer/companies.jsp`, `officer/drives.jsp`, `officer/applications.jsp`, `officer/interviewers.jsp`, `officer/rooms.jsp`, `officer/scheduling-setup.jsp`, `officer/scheduling-preview.jsp`, `officer/published-schedule.jsp`), and sanitized error views (`auth-gate.jsp`, `error.jsp`).
- **Controller:** Standard Jakarta `HttpServlet` classes (`HomeController`, `DriveController`, `AboutController`, `HealthController`, `LoginController`, `LogoutController`, `StudentDashboardController`, `StudentProfileController`, `StudentDriveController`, `StudentApplicationController`, `StudentInterviewController`, `OfficerDashboardController`, `OfficerCompanyController`, `OfficerDriveController`, `OfficerApplicationController`, `OfficerInterviewerController`, `OfficerRoomController`, `OfficerSchedulingController`).
- **Role-Based Authorization & IDOR Defense:** `PortalAuthorizationFilter` intercepts `/student/*`, `/officer/*`, and `/admin/*`. Verifies server-side `UserPrincipal` identity exclusively from `HttpSession`, strictly ignoring query parameter or body tampering. Enforces immediate session invalidation on disabled accounts and applies anti-caching headers (`Cache-Control: no-store, no-cache`) across all private endpoints.
- **Session Lifecycle & CSRF Hardening:** 30-minute session timeout, session ID rotation upon login via `req.changeSessionId()` alongside synchronized `CsrfTokenManager.rotateToken(session)`, `HttpOnly` session cookie flag, context-scoped cookie clearance on logout, remote HTTPS enforcement via `SecurityHeadersFilter`, and cryptographically random CSRF tokens (`_csrf`) required on all state-changing requests (`POST /login`, `POST /logout`, `POST /student/applications`, `POST /officer/*`).
- **Abuse Defense:** Bounded in-memory sliding window rate limiter (`BruteForceThrottler`) mitigating brute-force password guessing attacks without leaking account existence.
- **Preview State Lifecycle & Anti-Replay:** Server-side `SchedulingPreviewState` bound to officer username and HTTP session, identified by a 128-bit cryptographically secure token (`SecureRandom`), bounded to a 15-minute time-to-live (`expiresAt`), managed in an LRU session map for multi-tab isolation, and atomically consumed upon publication to prevent replay attacks.
- **Transactional Publication Semantics:** Publication (`POST /officer/scheduling/publish`) strictly re-validates shortlisting status, interviewer/room availability, and buffer rules. Reuses Stage 2 per-session transactions with deterministic lock ordering (`Applications` $\rightarrow$ `Rooms` $\rightarrow$ `Interviewers` $\rightarrow$ `Students`), conditional status progression (`SHORTLISTED` $\rightarrow$ `SCHEDULED`), and PostgreSQL GiST physical exclusion constraints. Distinguishes published, duplicate skipped, conflict rejected, and infrastructure failure outcomes.
- **Candidate Privacy Boundary:** Student interview view (`GET /student/interviews`) derives candidate identity exclusively from the authenticated session principal, queries only confirmed sessions linked to the student's applications, withholds internal interviewer notes and personal contact info, and serves strictly with anti-caching headers.
- **Lifecycle:** `WebContextListener` (`ServletContextListener`) initializes `ApplicationContext` upon Tomcat startup, runs Flyway migrations, wires `AuthenticationService` and repositories to `ServletContext`, and cleanly closes resources on shutdown.

---

### Building and Deploying to Apache Tomcat:

#### 1. Build the Dual Artifacts
```bash
mvn clean verify
```
This builds both:
- `target/hireflow-1.0.0.war` — deployable Jakarta EE web application archive (~8.5 MB).
- `target/hireflow-1.0.0-desktop.jar` — standalone executable desktop Swing application (~365 KB).

#### 2. Deploy to Apache Tomcat 10.1
1. Ensure Apache Tomcat 10.1.x is installed (requires Java 17+ / Java 21).
2. Copy the generated WAR file into Tomcat's `webapps` folder:
   ```bash
   cp target/hireflow-1.0.0.war $CATALINA_HOME/webapps/hireflow.war
   ```
3. Start Tomcat:
   ```bash
   # Linux / macOS:
   $CATALINA_HOME/bin/startup.sh

   # Windows:
   %CATALINA_HOME%\bin\startup.bat
   ```
4. Configure PostgreSQL access: Ensure PostgreSQL is running on `localhost:5432` with database `hireflow_dev`. If custom credentials are required, configure via environment variable `HIREFLOW_DB_PASSWORD` or place `hireflow-local.properties` in Tomcat's working directory.

#### 3. Test Endpoints in Browser
- **Portal Landing Page:** `http://localhost:8080/hireflow/` or `http://localhost:8080/hireflow/home`
- **Public Recruitment Drives Catalog:** `http://localhost:8080/hireflow/drives`
- **Catalog Search & Filtering:** `http://localhost:8080/hireflow/drives?q=Engineer&branch=CSE&status=ACTIVE&sort=deadline`
- **About & Placement Policies:** `http://localhost:8080/hireflow/about`
- **Login Page:** `http://localhost:8080/hireflow/login` (CSRF-protected login form)
- **Protected Student Dashboard:** `http://localhost:8080/hireflow/student/dashboard` (requires `STUDENT` role)
- **Student Academic Profile:** `http://localhost:8080/hireflow/student/profile` (read-only academic profile)
- **Student Recruitment Drives:** `http://localhost:8080/hireflow/student/drives` (evaluated eligibility & online apply forms)
- **Student Application Statuses:** `http://localhost:8080/hireflow/student/applications` (candidate pipeline tracking)
- **Student Confirmed Interview Schedule:** `http://localhost:8080/hireflow/student/interviews` (candidate-isolated confirmed appointments)
- **Protected Officer Dashboard:** `http://localhost:8080/hireflow/officer/dashboard` (requires `PLACEMENT_OFFICER` or `ADMINISTRATOR` role)
- **Corporate Partners Directory & Management:** `http://localhost:8080/hireflow/officer/companies` (view, register, edit companies)
- **Recruitment Drive Management & Closure:** `http://localhost:8080/hireflow/officer/drives` (view, create, edit, close drives)
- **Applicant Review & Candidate Shortlisting:** `http://localhost:8080/hireflow/officer/applications` (search, filter, evaluate eligibility, shortlist candidates)
- **Corporate Interviewers Management:** `http://localhost:8080/hireflow/officer/interviewers` (view, register, edit, delete interviewers with referential integrity guard)
- **Interview Venues & Rooms Management:** `http://localhost:8080/hireflow/officer/rooms` (view, create, edit, delete venues with capacity validation and referential integrity guard)
- **Automated Scheduling Setup & Advisory Preview:** `http://localhost:8080/hireflow/officer/scheduling` (configure parameters, inspect qualified candidate pools, generate advisory timetable preview)
- **Transactional Timetable Publication:** `POST http://localhost:8080/hireflow/officer/scheduling/publish` (CSRF-protected, one-time preview token, atomic commit)
- **Confirmed Interview Timetable:** `http://localhost:8080/hireflow/officer/scheduling/published` (confirmed schedule view, bounded drive & date filtering)
- **System Diagnostics API (JSON):** `http://localhost:8080/hireflow/health`
- **Error Page Test:** `http://localhost:8080/hireflow/invalid-route` (returns sanitized 404 page)

#### 4. Running the Desktop Swing UI Concurrently
The desktop application can still be launched directly:
```bash
java -jar target/hireflow-1.0.0-desktop.jar
```
Both desktop and web interfaces can run against the same PostgreSQL database concurrently without conflict.

---

## 9. Stage 4 (CAP-7) — Spring Boot REST API, Supported Framework Modernization & Authentication (In Progress)

Stage 4 establishes a modern enterprise RESTful API built on **Spring Boot 4.1.1** (Spring Framework 7.0.9, Spring Security 7.1.1, Hibernate ORM 7.4.5.Final, and Tomcat 11.0.24 on Java 21 LTS) in an isolated Maven module `hireflow-api`.

> **Note:** Stage 4 is currently **IN PROGRESS** on branch `develop/stage4`. Published release tags (`v1.0.0-stage1`, `v2.0.0-cap5`, `v3.0.0-cap6`) and packaging for the Jakarta EE WAR and desktop JAR remain fully intact and verified with zero regression.

### Reproducible Full Project Build (Clean Checkout)
The entire multi-artifact project can be deterministically compiled, tested, and packaged from a fresh checkout:
```bash
# Windows:
build-all.cmd

# Linux / macOS:
./build-all.sh
```
This builds and verifies all 4 release artifacts:
1. `target/hireflow-1.0.0.war` (Jakarta EE 10 Servlet 6.0 Web Portal)
2. `target/hireflow-1.0.0-desktop.jar` (Stage 2 Swing Desktop GUI)
3. `target/hireflow-1.0.0-classes.jar` (Attached core classes consumed by `hireflow-api`)
4. `hireflow-api/target/hireflow-api-1.0.0.jar` (Spring Boot 4.1.1 standalone executable)

### Running the Spring Boot REST API
```bash
# Run the Spring Boot API service on dedicated port 8081:
java -jar hireflow-api/target/hireflow-api-1.0.0.jar

# Or run directly via Maven:
mvn -f hireflow-api/pom.xml spring-boot:run
```

### Stage 4 REST Endpoints:

#### Public Catalogs & Operational Health:
- `GET http://localhost:8081/api/v1/health` — Read-only service and database health check (`UP`).
- `GET http://localhost:8081/api/v1/companies` — Public corporate partner catalog with bounded pagination and sorting.
- `GET http://localhost:8081/api/v1/companies/{id}` — Single corporate partner profile (404 for unknown IDs).
- `GET http://localhost:8081/api/v1/drives` — Public recruitment drives catalog with `companyId` and `activeOnly` filters.
- `GET http://localhost:8081/api/v1/drives/{id}` — Detailed recruitment drive specifications with eligible branches.

#### Session-Based REST Authentication & Security Hardening:
- `GET http://localhost:8081/api/v1/auth/csrf` — Fetches session-bound CSRF token (`X-CSRF-TOKEN`) and establishes/refreshes session.
- `POST http://localhost:8081/api/v1/auth/login` — Authenticates against existing PostgreSQL `user_accounts` using BCrypt (`at.favre.lib:bcrypt:0.10.2`), executes Spring Security 7 `CompositeSessionAuthenticationStrategy` (`ChangeSessionIdAuthenticationStrategy` + `CsrfAuthenticationStrategy`) to rotate the session ID (`SID_A` $\rightarrow$ `SID_B`), rejects obsolete pre-auth session IDs, and returns sanitized `UserProfileDto`. Requires valid `X-CSRF-TOKEN`.
- `GET http://localhost:8081/api/v1/auth/me` — Returns the authenticated principal's verified identity and roles (`STUDENT`, `PLACEMENT_OFFICER`, `ADMINISTRATOR`). Enforces continuous validity check via `AccountStatusFilter`.
- `POST http://localhost:8081/api/v1/auth/logout` — Requires CSRF token, invalidates session, clears Spring Security context, and expires the `JSESSIONID` cookie (`Max-Age=0`).
- **Continuous Session Revocation (`AccountStatusFilter`):** Intercepts every authenticated request, re-validating account enabled status, brute-force lockouts, and role consistency directly against PostgreSQL. Mid-session account disablement or role tampering immediately invalidates the session and returns 401 Unauthorized.
- **Cookie Security:** Hardened with `HttpOnly`, `SameSite=Lax`, `Path=/`, `server.servlet.session.tracking-modes=cookie` (preventing session ID exposure in URLs), and configurable TLS enforcement via `server.servlet.session.cookie.secure=${HIREFLOW_COOKIE_SECURE:false}`.

#### Authenticated Student Workflows (Stage 4C):
- `GET http://localhost:8081/api/v1/student/profile` — Retrieves the authenticated student's academic profile (id, name, branch, cgpa, passingYear, active). Excludes internal credentials and password hashes.
- `GET http://localhost:8081/api/v1/student/drives` — Lists all recruitment drives with real-time student-specific eligibility evaluations, application statuses, and company details.
- `GET http://localhost:8081/api/v1/student/drives/{id}/eligibility` — Evaluates real-time academic and operational eligibility for a recruitment drive (reusing `EligibilityService` directly without logic duplication).
- `GET http://localhost:8081/api/v1/student/applications` — Retrieves paginated, deterministically sorted list of applications owned by the authenticated student.
- `GET http://localhost:8081/api/v1/student/applications/{id}` — Retrieves student-owned application details. Non-owned applications return `404 Not Found` to prevent application ID guessing.
- `POST http://localhost:8081/api/v1/student/applications` — Atomically submits a job application within `hireflowJdbcTransactionManager.runInTransaction` delegating to `ApplicationService`. Authoritative drive status revalidated under PostgreSQL row lock (`FOR SHARE`) preventing race conditions with concurrent placement officer drive closure (`UPDATE ... SET is_closed = true`). Protected by CSRF (`X-CSRF-TOKEN`). Concurrency-defended by PostgreSQL constraint `uq_application_student_drive`. Returns `201 Created` with `Location` header. Duplicate attempts return `409 Conflict`.
- `GET http://localhost:8081/api/v1/student/dashboard` — High-level summary of total applications, pending/selected counts, eligible drives, and recent submissions.
- **Anti-Caching Protection:** All `/api/v1/student/**` routes emit `Cache-Control: no-cache, no-store, must-revalidate`.

#### Placement Officer Management Workflows (Stage 4D.1):
- `GET http://localhost:8081/api/v1/officer/companies` — Lists corporate partners with bounded pagination (`page`, `size`) and deterministic sorting (`name`, `industry`, `id`).
- `GET http://localhost:8081/api/v1/officer/companies/{id}` — Retrieves corporate partner profile details.
- `POST http://localhost:8081/api/v1/officer/companies` — Registers a new company. Enforces uniqueness on ID and corporate name (`409 Conflict`). Returns `201 Created` with `Location` header.
- `PUT http://localhost:8081/api/v1/officer/companies/{id}` — Updates corporate partner metadata.
- `GET http://localhost:8081/api/v1/officer/drives` — Lists all recruitment drives with optional `companyId` filtering and bounded pagination.
- `GET http://localhost:8081/api/v1/officer/drives/{id}` — Detailed recruitment drive profile with criteria and closure status.
- `POST http://localhost:8081/api/v1/officer/drives` — Creates a recruitment drive. Validates company existence, CGPA bounds [0.0, 10.0], nonempty branch list, and future deadline.
- `PUT http://localhost:8081/api/v1/officer/drives/{id}` — Updates editable drive fields. Closed drives reject edits (`400 Bad Request`).
- `POST http://localhost:8081/api/v1/officer/drives/{id}/close` — Manually closes an active recruitment drive under PostgreSQL row locking. Reclosing is idempotent (`200 OK`).
- `GET http://localhost:8081/api/v1/officer/drives/{id}/applications` — Lists candidate applications submitted for a recruitment drive with applicant academic profiles.
- `GET http://localhost:8081/api/v1/officer/applications/{id}` — Detailed candidate application record.
- `POST http://localhost:8081/api/v1/officer/applications/{id}/shortlist` — Advances application status from `APPLIED` to `SHORTLISTED` via atomic PostgreSQL conditional update (`UPDATE applications SET status = 'SHORTLISTED' WHERE id = ? AND status = 'APPLIED'`). Non-APPLIED or concurrent race loss returns `409 Conflict`.
- `GET http://localhost:8081/api/v1/officer/interviewers` — Lists corporate interviewers with optional `companyId` filter.
- `POST http://localhost:8081/api/v1/officer/interviewers` — Registers corporate interviewers linked to validated corporate partners.
- `PUT http://localhost:8081/api/v1/officer/interviewers/{id}` — Updates corporate interviewer details.
- `GET http://localhost:8081/api/v1/officer/rooms` — Lists interview venues and rooms.
- `POST http://localhost:8081/api/v1/officer/rooms` — Registers interview venue with validated positive capacity ($\ge 1$).
- `PUT http://localhost:8081/api/v1/officer/rooms/{id}` — Updates interview venue details.
- **Access Control & Anti-Caching:** All `/api/v1/officer/**` routes require `ROLE_PLACEMENT_OFFICER` or `ROLE_ADMINISTRATOR`, enforce CSRF validation on all state mutations (`POST`, `PUT`), reject student or anonymous access, and emit `Cache-Control: no-cache, no-store, must-revalidate`.

#### Officer Interview Scheduling & Timetable Publication Workflows (Stage 4E & 4E.1):
- `POST http://localhost:8081/api/v1/officer/scheduling/preview` — Generates advisory interview timetable preview using `GreedyScheduler` and `ConflictChecker`. Enforces candidate shortlisting qualification, interviewer and room availability, and time window parameters. Guarantees **zero database writes**, zero status changes, and zero false event broadcasts. Issues cryptographically secure, officer-owned preview token (`PREV-<HEX>`) valid for 15 minutes.
- `POST http://localhost:8081/api/v1/officer/scheduling/publish` — Confirms and transactionally publishes an advisory preview schedule. Validates preview token ownership, expiry (15-min TTL), and atomic single-use consumption. Reconstructs sessions server-side and delegates to `InterviewService.publishSchedule` under deterministic lock ordering (`Applications` $\rightarrow$ `Rooms` $\rightarrow$ `Interviewers` $\rightarrow$ `Students`) and PostgreSQL GiST exclusion constraints (`no_overlapping_room_sessions`, `no_overlapping_interviewer_sessions`, `no_overlapping_student_sessions`).
  - **Truthful Outcome Modeling:** Returns `200 OK` for full success (`ALL_CONFIRMED`), `207 Multi-Status` for partial success (`PARTIAL_SUCCESS`), and `409 Conflict` when all proposals conflict (`NONE_CONFIRMED`).
  - **Anti-Replay & Token Lifecycle:** Replay attempts are unconditionally rejected (`409 Conflict`). Tokens are never reset on partial failure.
  - **Diagnostic Redaction:** Raw preview tokens are masked in diagnostic logs (`PREV-****` or `[REDACTED_PREVIEW_TOKEN]`).
  - **Recovery Contract:** Confirmed sessions remain persistently stored in PostgreSQL. Response provides actionable recovery instructions and non-sensitive preview references.
- `GET http://localhost:8081/api/v1/officer/scheduling/published` — Retrieves confirmed interview sessions directly from PostgreSQL with optional filtering by `driveId`, `interviewerId`, `roomId`, or `date`, with bounded pagination and deterministic sorting.
- `GET http://localhost:8081/api/v1/officer/scheduling/published/{id}` — Retrieves detailed confirmed interview appointment by session ID (`404 Not Found` for nonexistent sessions).

#### Student Interview Timetable Workflows (Stage 4E):
- `GET http://localhost:8081/api/v1/student/interviews` — Retrieves authenticated student's confirmed interview appointments with company name, job title, room venue, confirmed start/end times, and authorized interviewer information. Guarantees cross-student timetable isolation. Emits `Cache-Control: no-cache, no-store, must-revalidate`.
- `GET http://localhost:8081/api/v1/student/interviews/{id}` — Retrieves student-owned confirmed interview session details. Non-owned or cross-student session lookups return `404 Not Found` to prevent session enumeration.

### Automated Test Verification:
```bash
# Run CAP-6 legacy regression suite (495 tests):
mvn test

# Run Stage 4E.1 Spring Boot integration, security, MockMvc, concurrency, and E2E suite (183 tests):
mvn -f hireflow-api/pom.xml test

# Run all 678 tests across the entire project and package all 4 artifacts:
build-all.cmd   # Windows
./build-all.sh  # Linux/macOS
```
See full architecture documentation in [docs/cap7-architecture.md](file:///c:/Users/astra/Desktop/Project/hireflow/docs/cap7-architecture.md).
