# HIREFLOW — CAP-8.6.1 RELEASE-BLOCKER CLOSURE & FINAL VERIFICATION REPORT
## Release-Blocker Closure · Demo Recovery · Security Audit · Infrastructure Status

**Role:** Principal Release Engineer, Senior Java Developer, PostgreSQL Security Auditor, Windows/Docker Engineer, Independent QA Lead  
**Audit Date:** 26 October 2026 (Asia/Calcutta, 02:00)  
**Repository:** `C:\Users\astra\Desktop\Project\hireflow`  
**Active Branch:** `develop/stage5`  
**Historical Baseline Commit:** `bdbf0e1dce7407ab9034f893712ccea8d7fced56` (HEAD — CAP-7 baseline preserved)  
**Preceding Milestone Reference:** `docs/frontend/cap8_6_release_preflight_report.md`  
**Milestone:** CAP-8.6.1 Release-Blocker Closure  

---

## 1. EXECUTIVE SUMMARY & VERDICT

```
CAP-8.6.1 CONDITIONAL — INFRASTRUCTURE OR TESTS PENDING
```

### Rationale
All code defects, secret exposures, Vitest offline exit-code anomalies, scheduler ID regression requirements, and documentation inaccuracies identified in CAP-8.6 have been **completely resolved and verified**:
1. **Secret Exposure Eradicated:** The embedded database password in `init/01-init-role-and-db.sql` was replaced with a POSIX shell secret-delivery script `init/01-init-role-and-db.sh` using environment/secret files and parameterized `psql` variables. Plaintext credentials across compose configuration, test seeding scripts, and documentation reports were sanitized. A repository-wide secret scan confirmed **0 violations**.
2. **Deterministic Scheduler IDs Audited & Verified:** A 7-point regression test suite in `SessionIdentityRegressionTest.java` was implemented and passed (`7/7 tests passed in 0.109s`). The 32-character hexadecimal UUID v3 generation satisfies PostgreSQL's `VARCHAR(32)` column constraint, guarantees stability across repeated preview invocations, eliminates collisions across concurrent preview counters, and preserves identical slot/interviewer/room allocation behavior.
3. **Vitest Offline Opt-In Corrected:** `live-backend-integration.test.ts` was refactored with `describe.skipIf(!isExplicitLiveOptIn)`. In offline mode, the suite skips cleanly without throwing; `npm run test` executes all 147 unit/contract tests across 12 files, skips 5 live HTTP tests, and **exits with process exit code 0**. In live mode, invalid or missing environments fail visibly with exit code 1 without fallback to port 5432.
4. **Core Database Isolation Refactored:** `CoreDatabaseExtension.java` was scoped to only intercept PostgreSQL-backed integration test classes, allowing all 71 offline Java unit tests to pass cleanly in 5.39s.
5. **Infrastructure Status:** Docker Desktop Linux engine remains offline because WSL2 cannot mount the virtual disk for the `Ubuntu` distribution (`C:\Users\astra\AppData\Local\wsl\{14248f57-384d-4cfc-acf0-265ce4600dae}\ext4.vhdx`: File not found). Per strict Phase F authorization constraints, no destructive WSL unregistration or elevated service manipulation was attempted. Live runtime database tests and live `demo.master` browser login remain pending until the operator resolves Docker Desktop availability.

---

## 2. PHASE A — GIT INVENTORY & DISCREPANCY RESOLUTION

### 2.1 Git Repository Confirmation
- **Root Directory:** `C:/Users/astra/Desktop/Project/hireflow`
- **Current Branch:** `develop/stage5`
- **HEAD Commit Hash:** `bdbf0e1dce7407ab9034f893712ccea8d7fced56` (matches CAP-7 baseline)
- **Unauthorized Operations:** Zero Git resets, cleans, commits, pushes, merges, or tags executed.

### 2.2 Resolution of CAP-8.6 Tracked File Discrepancy
The previous CAP-8.6 report contained an arithmetic discrepancy in Section 1.2:
> *"Total modified tracked files: 34 (1 production source, 32 test files, 1 infrastructure config, 1 gitignore)"* — which sums to 35.

A machine-derived audit of `git status --porcelain=v1` establishes the exact classification:

| Category | File Count | Exact File Paths |
|---|---|---|
| **Production Java Source** | **1** | `src/main/java/com/hireflow/scheduler/GreedyScheduler.java` |
| **Repository Git Config** | **1** | `.gitignore` |
| **Test Configuration Resource** | **1** | `hireflow-api/src/test/resources/application.properties` |
| **Core Module Java Tests** | **17** | `src/test/java/com/hireflow/acceptance/Stage2FinalAcceptanceTest.java`<br>`src/test/java/com/hireflow/context/ApplicationContextLifecycleTest.java`<br>`src/test/java/com/hireflow/context/JdbcDevRestartVerification.java`<br>`src/test/java/com/hireflow/context/JdbcPersistenceIntegrationTest.java`<br>`src/test/java/com/hireflow/db/DatabaseInfrastructureTest.java`<br>`src/test/java/com/hireflow/db/FlywayAndConcurrencyTest.java`<br>`src/test/java/com/hireflow/db/JdbcSchedulingPublicationConcurrencyTest.java`<br>`src/test/java/com/hireflow/network/HireFlowEventBroadcastingTest.java`<br>`src/test/java/com/hireflow/network/HireFlowTcpServerTest.java`<br>`src/test/java/com/hireflow/repository/JdbcApplicationRepositoryTest.java`<br>`src/test/java/com/hireflow/repository/JdbcCompanyRoomStudentRepositoryTest.java`<br>`src/test/java/com/hireflow/repository/JdbcInterviewSessionRepositoryTest.java`<br>`src/test/java/com/hireflow/repository/JdbcInterviewerDriveRepositoryTest.java`<br>`src/test/java/com/hireflow/ui/NetworkingPanelIntegrationTest.java`<br>`src/test/java/com/hireflow/web/integration/TomcatPostgresE2EWorkflowTest.java`<br>`src/test/java/com/hireflow/web/listener/WebContextListenerTest.java`<br>`src/test/java/com/hireflow/web/security/JdbcUserAccountRepositoryTest.java` |
| **API Module Java Tests** | **14** | `hireflow-api/src/test/java/com/hireflow/api/controller/OfficerControllerMockMvcTest.java`<br>`hireflow-api/src/test/java/com/hireflow/api/controller/OfficerSchedulingControllerMockMvcTest.java`<br>`hireflow-api/src/test/java/com/hireflow/api/controller/StudentControllerMockMvcTest.java`<br>`hireflow-api/src/test/java/com/hireflow/api/controller/StudentInterviewControllerMockMvcTest.java`<br>`hireflow-api/src/test/java/com/hireflow/api/officer/OfficerPostgresConcurrencyAndIntegrityIntegrationTest.java`<br>`hireflow-api/src/test/java/com/hireflow/api/officer/OfficerWorkflowRealHttpIntegrationTest.java`<br>`hireflow-api/src/test/java/com/hireflow/api/officer/SchedulingConcurrencyAndIntegrityPostgresTest.java`<br>`hireflow-api/src/test/java/com/hireflow/api/officer/SchedulingWorkflowRealHttpIntegrationTest.java`<br>`hireflow-api/src/test/java/com/hireflow/api/officer/TransactionalEventConsistencyIntegrationTest.java`<br>`hireflow-api/src/test/java/com/hireflow/api/security/PostgresAuthenticationIntegrationTest.java`<br>`hireflow-api/src/test/java/com/hireflow/api/security/SessionFixationAndRevocationRealHttpIntegrationTest.java`<br>`hireflow-api/src/test/java/com/hireflow/api/student/DriveClosureAndSubmissionConcurrencyPostgresTest.java`<br>`hireflow-api/src/test/java/com/hireflow/api/student/StudentProcessRestartPersistenceRealHttpTest.java`<br>`hireflow-api/src/test/java/com/hireflow/api/student/StudentWorkflowRealHttpIntegrationTest.java` |
| **Total Tracked Modifications** | **34** | $1 + 1 + 1 + 17 + 14 = 34$ |

**Root Cause of Past Inconsistency:** The previous report author counted `application.properties` twice (once in test configuration, and once inside a generic "32 test files" bucket) while miscalculating $17 + 14 = 31$ Java test files as 32.

### 2.3 Untracked Content Inventory (233 Files Across 17 Root Entries)
- `.agents/` (12 files) — 6 specialized engineering skills
- `hireflow-app/` (165 files) — React 19 frontend application
- `docs/` (21 files) — Blueprints, testing guides, runbooks, reports
- `infra/` (11 files) — Docker Compose, init scripts, preflight guards, provisioning tools
- `scripts/` (9 files) — Demo launcher and validation PowerShell scripts
- Root CMD scripts (4 files) — `Launch-HireFlow.cmd`, `Check-HireFlow.cmd`, `Stop-HireFlow.cmd`, `Setup-HireFlow-Demo.cmd`
- Test support & regression additions (11 files):
  - `hireflow-api/src/test/java/.../testsupport/` (3 files) & `META-INF/` (1 file)
  - `src/test/java/.../SessionIdentityRegressionTest.java` (1 file)
  - `src/test/java/.../testsupport/` (3 files), `junit-platform.properties` (1 file), `META-INF/` (2 files)

---

## 3. PHASE B — CREDENTIAL EXPOSURE REMEDIATION

### 3.1 PostgreSQL Initialization Secret Delivery (`01-init-role-and-db.sh`)
Official PostgreSQL Docker images do not interpolate environment variables within raw `.sql` files mounted to `/docker-entrypoint-initdb.d/`.

**Remediation Executed:**
1. Deleted insecure file `infra/cap8-integration/init/01-init-role-and-db.sql` which contained literal hardcoded password `hireflow_it_app_secret_2026`.
2. Authored POSIX `/bin/bash` initialization script `infra/cap8-integration/init/01-init-role-and-db.sh`:
   - Checks `APP_DB_PASSWORD_FILE` (Docker secrets convention) or `APP_DB_PASSWORD`.
   - Fails closed with exit code 1 if no password is provided.
   - Uses `psql -v role_name=... -v role_pass=...` and PL/pgSQL `format('CREATE ROLE %I ... PASSWORD %L')` to avoid leaking credentials via command-line arguments or process inspection.
   - Installs `btree_gist`, sets default privileges, and seeds `_integration_sentinel`.
   - Validated via `bash -n` syntax check (PASS).
3. **Preservation of Existing Container Volume:** On existing initialized containers, Docker entrypoint does not re-execute init scripts. The working volume `hireflow_cap8_it_pgdata` remains valid and unperturbed.

### 3.2 Docker Compose Configuration
Removed hardcoded fallback credentials from `infra/cap8-integration/docker-compose.yml`:
```yaml
    environment:
      POSTGRES_USER: ${POSTGRES_BOOTSTRAP_USER:-postgres}
      POSTGRES_PASSWORD: ${POSTGRES_BOOTSTRAP_PASSWORD:?POSTGRES_BOOTSTRAP_PASSWORD is required}
      POSTGRES_DB: hireflow_cap8_it
      APP_DB_USER: ${HIREFLOW_IT_APP_USER:-hireflow_cap8_app}
      APP_DB_PASSWORD: ${HIREFLOW_IT_APP_PASSWORD:?HIREFLOW_IT_APP_PASSWORD is required}
      APP_DB_PASSWORD_FILE: ${HIREFLOW_IT_APP_PASSWORD_FILE:-}
```

### 3.3 Java Tooling Remediation
Updated `MigrateDatabase.java` and `SeedIsolatedData.java` to read credentials dynamically from `System.getProperty("hireflow.it.pass")` or `System.getenv("HIREFLOW_IT_APP_PASSWORD")`, failing with an `IllegalStateException` rather than falling back to hardcoded demo secrets.

### 3.4 Report & Documentation Sanitization
Redacted plaintext credentials from:
- `docs/frontend/cap8_5_2_final_integration_report.md`
- `docs/frontend/cap8_6_release_preflight_report.md`

### 3.5 Verification Scan
Repository-wide regex scan for past secrets (`hireflow_it_app_secret_2026`, `hireflow_bootstrap_admin_secret`) executed across all tracked and untracked files: **0 occurrences found.**

---

## 4. PHASE C — SCHEDULER DETERMINISTIC ID AUDIT & REGRESSION

### 4.1 Production Change Analysis (`GreedyScheduler.java`)
```diff
-                        String sessionId = String.format("SES-PROP-%03d", propIdCounter++);
+                        // Application-scoped IDs remain deterministic across previews and do not
+                        // collide when separate scheduling requests start their own counters.
+                        String sessionId = java.util.UUID.nameUUIDFromBytes(
+                                ("hireflow:interview:" + app.getId()).getBytes(java.nio.charset.StandardCharsets.UTF_8))
+                                .toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
```

### 4.2 Security, Contract & Database Audit Findings
- **Database Column Length:** `interview_sessions.id` is `VARCHAR(32)` (`V1__initial_schema.sql:138`). The 128-bit MD5 hex representation without hyphens is **exactly 32 characters**, strictly fitting the column without truncation.
- **Preview Determinism:** `UUID.nameUUIDFromBytes` is a deterministic RFC 4122 UUID v3 generator. Generating an advisory timetable multiple times for the same application produces the identical proposed session ID.
- **Cross-Request Collision Resistance:** The sequential counter reset to `SES-PROP-001` on every call, creating ID collisions between concurrent preview requests. The UUID v3 scheme isolates IDs to the application ID namespace. (Note: collision resistance is bounded by 128-bit hash properties; mathematical absolute uniqueness is not claimed).
- **Prefix Dependencies:** Grep search confirmed zero production or test code dependencies on `SES-PROP-*`.
- **UI & Schema Constraints:** TypeScript types (`types/api.ts`) define `sessionId: string`. Frontend tables, cards, and route params treat `sessionId` as an opaque string.
- **Sorting & Pagination:** Queries order by `start_time ASC, id ASC`. The session ID acts only as a deterministic tie-breaker; pagination semantics are unaffected.
- **Publication Safety & Idempotency:** `InterviewService.publishSchedule` checks `sessionRepository.findByApplicationId(appId)`. Previews are in-memory calculations causing 0 database mutations; re-publishing an already scheduled session is safely intercepted and recorded in `skippedDuplicateApplicationIds`.

### 4.3 7-Point Regression Test Execution (`SessionIdentityRegressionTest.java`)
All 7 regression scenarios passed via `./mvnw -Dtest=SessionIdentityRegressionTest test`:

| Test Case | Scenario Verified | Result |
|---|---|---|
| `Test 1` | Stable ID for same application across 3 repeated previews | **PASS** |
| `Test 2` | Distinct IDs for distinct applications (`APP-001`, `APP-002`, `APP-003`) | **PASS** |
| `Test 3` | Conformance to `VARCHAR(32)`: length == 32, matches `^[A-F0-9]{32}$` | **PASS** |
| `Test 4` | Transactional publication persists session and transitions application to `SCHEDULED` | **PASS** |
| `Test 5` | Duplicate/replay handling skips repeated publication without duplicate DB rows | **PASS** |
| `Test 6` | Existing confirmed sessions prevent duplicate appointments | **PASS** |
| `Test 7` | Slot/interviewer/room allocations remain identical for representative multi-candidate inputs | **PASS** |

---

## 5. PHASE D — VITEST OFFLINE BEHAVIOR & PROCESS EXIT CODE

### 5.1 Root Cause of Exit Code 1
In CAP-8.6, `live-backend-integration.test.ts` threw `Error('Explicit isolated integration opt-in required')` inside `beforeAll`. In Vitest, an exception in `beforeAll` fails the entire suite file, causing the Node process to exit with code 1 despite all 147 offline unit tests passing.

### 5.2 Remediation
Refactored `hireflow-app/src/test/live-backend-integration.test.ts` to gate the entire describe block:
```ts
const optInValue = process.env.HIREFLOW_ALLOW_ISOLATED_IT
const isExplicitLiveOptIn = Boolean(optInValue && optInValue.trim() !== '')

describe.skipIf(!isExplicitLiveOptIn)('HireFlow Live System Real-HTTP Integration Suite (CAP-8.5)', () => {
  beforeAll(async () => {
    if (optInValue !== '55432/hireflow_cap8_it') {
      throw new Error(`Explicit isolated integration opt-in required: expected '55432/hireflow_cap8_it', got '${optInValue}'`)
    }
    // Docker container inspect and database sentinel validation...
  })
})
```

### 5.3 Verified Execution Modes

#### Offline Mode (Standard Unit / CI Execution)
- **Command:** `npm run test` (or `npm.cmd run test`)
- **Execution:**
  - Test files: 12 passed, 1 skipped (13 total)
  - Tests: **147 passed, 5 skipped** (152 total)
  - Process Exit Code: **0** (PASS)
  - Network activity: Zero TCP connections to Docker or PostgreSQL.

#### Live Isolated Mode (Explicit Opt-In)
- **Command:**
  - Windows CMD: `set HIREFLOW_ALLOW_ISOLATED_IT=55432/hireflow_cap8_it && npm run test`
  - PowerShell: `$env:HIREFLOW_ALLOW_ISOLATED_IT="55432/hireflow_cap8_it"; npm.cmd run test`
- **Behavior:**
  - Database safety guard runs first (`docker inspect hireflow-cap8-postgres`).
  - Sentinel check confirms `hireflow_cap8_it|hireflow_cap8_app|HIREFLOW_CAP8_ISOLATED_INTEGRATION_2026`.
  - If Docker is offline or configuration is invalid: **Fails visibly with Process Exit Code 1**.
  - Does NOT silently skip when explicitly requested.
  - Rejects port 5432 or non-loopback bindings.

---

## 6. PHASE E — CI READINESS & MODULE DEPENDENCIES

### 6.1 Maven Dependency Chain
Root Maven module `pom.xml` builds `com.hireflow:hireflow:1.0.0:classes`. Module `hireflow-api/pom.xml` declares a direct dependency on this classifier (`hireflow-api/pom.xml:56-60`).
- **Defect in Prior Draft CI:** `./mvnw compile` in root does not install the jar into `~/.m2/repository`. A subsequent `./mvnw -f hireflow-api/pom.xml compile` on clean CI runners fails with missing artifact errors.
- **Required CI Build Sequence:**
  ```bash
  ./mvnw -B install -DskipTests -q
  ./mvnw -B -f hireflow-api/pom.xml compile -q
  ```

### 6.2 JUnit 5 Test Selection & Offline Java Tests
- The draft CI workflow assumed `./mvnw test -Dgroups="!requires-postgres"` would exclude PostgreSQL integration tests. However, zero test classes in the repository had `@Tag("requires-postgres")`.
- `CoreDatabaseExtension.java` was previously registered globally via Java SPI (`META-INF/services/org.junit.jupiter.api.extension.Extension`), causing all offline tests to abort.
- `CoreDatabaseExtension.java` was updated to explicitly target the 17 JDBC integration classes or `@Tag("requires-postgres")`.
- **Verified Offline Java Test Suite:** 71 unit tests across core classes pass offline in 5.39s (`SessionIdentityRegressionTest`, `GreedySchedulerTest`, `EntityValidationTest`, `CompanyServiceTest`, `CsrfTokenManagerTest`, `PasswordHasherTest`, `BruteForceThrottlerTest`, `AuthenticationServiceTest`).
- Database integration tests (17 in core, 14 in API) must be run in a dedicated job with an active PostgreSQL service container.

---

## 7. PHASE F — DOCKER & WSL INFRASTRUCTURE ASSESSMENT

### 7.1 Diagnostic Evidence
- **Docker Client:** Installed (Version 29.8.2, context `desktop-linux`).
- **Docker Daemon Pipe:** `//./pipe/dockerDesktopLinuxEngine` not listening.
- **Windows Service:** `com.docker.service` is `Stopped`.
- **Port 5432:** Occupied by PID 5212 (`postgres.exe` — Windows host native PostgreSQL). Preserved; never contacted.
- **Port 55432:** Free. No listeners.
- **WSL Distributions:**
  ```
    NAME              STATE       VERSION
  * Ubuntu            Stopped     2
    docker-desktop    Stopped     2
  ```
- **WSL Disk Mount Error:**
  Launching the default WSL distribution fails with:
  `Failed to attach disk 'C:\Users\astra\AppData\Local\wsl\{14248f57-384d-4cfc-acf0-265ce4600dae}\ext4.vhdx' to WSL2: The system cannot find the file specified. Error code: Bash/Service/CreateInstance/MountDisk/ERROR_FILE_NOT_FOUND`

### 7.2 Safety Invariant Adherence
Per Phase F instructions, no unauthorized destructive actions were performed:
- Did NOT unregister `Ubuntu` or `docker-desktop`.
- Did NOT reinstall WSL.
- Did NOT reset Docker Desktop.
- Did NOT delete or recreate virtual disks.
- Did NOT prune Docker volumes.
- Did NOT modify Windows system services with elevated privileges.

### 7.3 Recommended Operator Recovery Action
1. Open PowerShell / CMD as Administrator.
2. Check WSL status: `wsl.exe --status`.
3. If the `Ubuntu` distribution is orphaned or was relocated, either point it to its valid VHDX location or set `docker-desktop` as the default distribution: `wsl.exe --set-default docker-desktop`.
4. Launch Docker Desktop from the Start menu and allow its engine to reach a green "Running" state.
5. Once running, execute `Check-HireFlow.cmd` to verify all 9 live integration gates.

---

## 8. PHASE G — ADMINISTRATOR ACCOUNT STATUS

| Dimension | Specification | Verification Status |
|---|---|---|
| **Account Identity** | `demo.master` | Source-verified in `ProvisionDemoAccounts.java` |
| **Role** | `ADMINISTRATOR` | Source-verified; grants full officer & system access |
| **Password Storage** | BCrypt hash | Source-verified; never stored in plaintext |
| **Runtime Persistence** | Isolated PostgreSQL | Prior live verification passed 22-24 October; current runtime verification **CONDITIONAL** (pending Docker engine) |
| **Secret Management** | `.run/demo.local.json` | Excluded by `.gitignore:24`; never committed |

---

## 9. EXECUTED VERIFICATION EVIDENCE (THIS AUDIT)

```
================================================================================
VERIFICATION SUITE SUMMARY — 26 OCTOBER 2026
================================================================================
[PASS] Java Core & API Compile:
       - ./mvnw -B install -DskipTests: BUILD SUCCESS (3.04s)
       - ./mvnw -B -f hireflow-api/pom.xml compile: BUILD SUCCESS (1.91s)

[PASS] Java Offline Unit Tests:
       - 71 tests executed, 0 failures, 0 errors, 0 skipped (5.40s)
       - SessionIdentityRegressionTest (7/7 passed)
       - GreedySchedulerTest (10/10 passed)
       - EntityValidationTest (22/22 passed)
       - CompanyServiceTest (9/9 passed)
       - PasswordHasherTest (5/5 passed)
       - CsrfTokenManagerTest (6/6 passed)
       - BruteForceThrottlerTest (3/3 passed)
       - AuthenticationServiceTest (9/9 passed)

[PASS] Frontend Typecheck (TypeScript):
       - tsc -b --noEmit: 0 errors (Exit code 0)

[PASS] Frontend Lint (oxlint):
       - 116 files checked: 0 errors, 15 advisory warnings (React compiler hints)

[PASS] Frontend Unit & Contract Tests (Vitest, Offline):
       - 13 test files: 12 passed, 1 skipped
       - 152 tests: 147 passed, 5 skipped
       - Process Exit Code: 0 (Fixed from prior exit code 1)

[PASS] Frontend Production Build:
       - vite build: 2146 modules transformed, assets bundled (944ms, Exit code 0)

[PASS] PowerShell Launcher Diagnostics:
       - 11 scripts parsed via AST: 0 syntax errors
       - Test-HireFlowLauncher.ps1: 17/17 tests passed

[PASS] Security Scan:
       - 0 hardcoded secrets or exposed integration passwords across repository
================================================================================
```

---

## 10. REMAINING ITEM TRACKER (P0–P3)

| Priority | ID | Component | Description | Resolution Status |
|---|---|---|---|---|
| **P0** | BLK-1 | Docker Infrastructure | Docker Desktop engine offline due to WSL2 `Ubuntu` missing VHDX | **OPERATOR ACTION REQUIRED** (See Section 7.3) |
| **P1** | BLK-2 | Live Verification | Live browser E2E and `demo.master` login re-attestation | Blocked by BLK-1 |
| **P2** | SEC-1 | Credential Exposure | Plaintext passwords in SQL init and integration reports | **CLOSED / RESOLVED** (Section 3) |
| **P2** | TST-1 | Vitest Exit Code | Offline test run exit code 1 false-positive | **CLOSED / RESOLVED** (Section 5) |
| **P2** | SCH-1 | Scheduler ID Audit | Deterministic UUID v3 regression suite and column length audit | **CLOSED / RESOLVED** (Section 4) |
| **P3** | DOC-1 | Algorithm Complexity | Imprecise O(NxM) notation in historical report | **CLOSED / CORRECTED** (Section 11) |
| **P3** | DOC-2 | CSRF Terminology | Corrected "double-submit" to server-session token pattern | **CLOSED / CORRECTED** (Section 11) |

---

## 11. DOCUMENTATION CORRECTIONS & ACCURACY RECORD

1. **GreedyScheduler Complexity:** Worst-case complexity is $O(N \times T \times I \times R \times S)$ where $N$ = shortlisted applications, $T$ = time slots, $I$ = interviewers, $R$ = rooms, and $S$ = active sessions. For campus placement drives (tens of applicants, modest room pools), execution is practically linear in $N$.
2. **UUID Uniqueness Semantics:** Proposed session IDs are generated using MD5-based name UUID v3 over `"hireflow:interview:" + app.getId()`. The 32 hex character string guarantees cross-preview determinism and eliminates sequential counter collisions across concurrent requests. Absolute mathematical collision freedom is not claimed.
3. **CSRF Architecture:** Authentication and mutation workflows utilize server-session-stored CSRF tokens (`/api/v1/auth/csrf` + `X-CSRF-TOKEN` header), not client-side double-submit cookies.
4. **Accessibility Qualifications:** The automated axe-core audit verified 0 violations across 11 routes and 5 viewports for automated WCAG 2.1 A/AA rules. Full WCAG 2.1 AA conformance requires subsequent manual screen reader (NVDA/JAWS) and keyboard navigation audits.

---

## 12. CLEAN LOGICAL COMMIT PROPOSAL (DO NOT COMMIT YET)

When staging is authorized by the operator, the changes should be committed in the following logical sequence:

1. `chore(gitignore): add .run, cookies.txt and local secret exclusions`
2. `fix(scheduler): deterministic UUID v3 session IDs with full regression suite`
3. `refactor(test): scope CoreDatabaseExtension to PostgreSQL integration tests`
4. `feat(infra): secure initialization shell script and strict docker-compose environment`
5. `feat(app): clean offline Vitest opt-in and typecheck scripts`
6. `feat(launcher): demo launcher, checker, setup, and safe fail-closed scripts`
7. `feat(agents): HireFlow institutional design, UX, API, and QA agent skills`
8. `docs: sanitized integration reports, testing architecture, and CAP-8.6.1 closure report`

---

## 13. FINAL VERDICT & NEXT STEPS

```
CAP-8.6.1 CONDITIONAL — INFRASTRUCTURE OR TESTS PENDING
```

**Next Steps for Staging Approval:**
1. Operator resolves the WSL2 `Ubuntu` virtual disk path or sets `docker-desktop` as default.
2. Operator starts Docker Desktop.
3. Operator runs `Check-HireFlow.cmd` — which will automatically start the approved isolated container `hireflow-cap8-postgres`, verify database sentinels, validate `demo.master`, run smoke tests, and confirm live browser readiness.
4. Upon successful run of `Check-HireFlow.cmd`, transition milestone to:
   `CAP-8.6.1 VERIFIED — READY FOR STAGING APPROVAL`
