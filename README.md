# HireFlow - Campus Recruitment & Interview Scheduling System

> **Stage 2 Status:** **Stage 2B.6 COMPLETED — Repository Factory, Swing PostgreSQL Integration & Persistence Verification**  
> **Release Checkpoint:** `v1.0.0-stage1` (Frozen Stage 1 Tag preserved) | Active Branch: `develop/stage2`  
> **Technology Stack:** **Java 21 LTS**, **Apache Maven**, **Standard Java Swing (Vanilla GUI)**, **PostgreSQL 16**, **HikariCP**, **Flyway**, and **JUnit 5 Jupiter**.  
> **Architecture:** Centralized `ApplicationContext` supporting dual modes: Stage 1 Pure In-Memory Collections and Stage 2B PostgreSQL ACID Storage with Flyway Migrations, Row-Level Locks, and GiST Temporal Exclusion Constraints.

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
- **Non-Blocking Swing EDT:** Background processing for algorithmic greedy scheduling and schedule publication powered by `SwingWorker` so the desktop interface never freezes.
- **Sanitized Diagnostics & Security:** Passwords and credentials are never logged, displayed in dialogs, or committed to version control.

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
- `V1__init_schema.sql`: Tables, primary keys, foreign keys, btree_gist extension, check constraints, and GiST temporal exclusion constraints.

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

### Step 2: Run Full Automated Test Suite (226 Tests)
```bash
mvn test
```
Executes all unit, repository, service, scheduler, database concurrency, and persistence integration tests.

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

## 8. Current Feature Limitations & Roadmap

- **Stage 1 (Completed):** Pure In-Memory collections, 7 hard scheduling constraints, Swing desktop UI.
- **Stage 2A & 2B (Completed):** PostgreSQL schema, 7 JDBC repositories, HikariCP connection pooling, Flyway migrations, ApplicationContext factory, transactional schedule publication with global locks, persistence verification.
- **Stage 2C (Upcoming):** Multi-client TCP socket server, thread-safe request dispatching, remote protocol integration.
- **Stage 3 (Future):** Comprehensive end-to-end integration and polish.
