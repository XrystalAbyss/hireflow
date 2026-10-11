# HireFlow - Campus Recruitment & Interview Scheduling System

> **Stage 3 Status:** **Stage 3G COMPLETE (Full CAP-6 End-to-End Acceptance, Security Audit, Documentation & Release Preparation)**  
> **Release Checkpoints:** `v1.0.0-stage1` (`66826c1`) | `v2.0.0-cap5` (`559ed7b`) | Active Branch: `develop/stage3`  
> **Technology Stack:** **Java 21 LTS**, **Apache Maven**, **Jakarta Servlet API 6.0**, **Jakarta Server Pages (JSP) 3.1**, **JSTL 3.0**, **Apache Tomcat 10.1.x**, **BCrypt** (`at.favre.lib:bcrypt:0.10.2`), **PostgreSQL 16**, **HikariCP**, **Flyway**, **Standard Java Swing (Vanilla GUI)**, and **JUnit 5 Jupiter**.  
> **Architecture:** Centralized `ApplicationContext` supporting dual modes (`IN_MEMORY`, `JDBC_DEV`), Jakarta EE MVC Web Portal (`/`, `/drives`, `/about`, `/health`, `/login`, `/logout`), persistent database authentication (`user_accounts`), session ID rotation, CSRF token validation & rotation, brute-force throttling, role-based authorization filters (`STUDENT`, `PLACEMENT_OFFICER`, `ADMINISTRATOR`), immediate disabled-account revocation, student self-service portal (`/student/profile`, `/student/drives`, `/student/applications`, `/student/interviews`), placement officer recruitment management portal (`/officer/dashboard`, `/officer/companies`, `/officer/drives`, `/officer/applications`, `/officer/interviewers`, `/officer/rooms`, `/officer/scheduling`, `/officer/scheduling/publish`, `/officer/scheduling/published`), Flyway V3 drive closure semantics preserving original deadlines, interview resource management with referential integrity guards, cryptographic preview tokens (128-bit CSPRNG, 15-minute TTL, multi-tab isolation, one-time anti-replay consumption), Stage 2 transactional interview publication with deterministic global resource lock ordering and PostgreSQL GiST exclusion constraints, candidate-isolated student interview schedule visibility, verified 20-step end-to-end browser & database lifecycle, unified WAR and desktop JAR packaging, an integrated Swing networking dashboard (`NetworkingPanel`), and an explicit-lifecycle TCP server.  
> **Automated Verification:** **495 Passing Tests** (0 Failures, 0 Errors, 0 Skips across 40 test classes / 73 test suites via `mvn clean verify`).

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
