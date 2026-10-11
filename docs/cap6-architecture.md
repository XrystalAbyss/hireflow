# HireFlow — Stage 3 CAP-6 Architecture Specification

> **Milestone:** Stage 3G (Full CAP-6 End-to-End Acceptance, Security Audit, Documentation & Release Preparation)  
> **Repository:** `https://github.com/XrystalAbyss/hireflow`  
> **Active Branch:** `develop/stage3`  
> **Release Baseline:** `v2.0.0-cap5` (Commit `559ed7baa9dd6e8382b5b94bbb9ca421284697d4`)  
> **Technology Stack:** Java 21 LTS, Jakarta Servlet API 6.0, JSP 3.1, JSTL 3.0, Apache Tomcat 10.1.x, BCrypt (`at.favre.lib:bcrypt:0.10.2`), HikariCP, Flyway, PostgreSQL 16, JUnit 5 Jupiter, Mockito  
> **Architectural Constraints:** Zero Spring Boot, Zero Spring MVC, Zero Hibernate, Zero React, Zero Heavyweight JavaScript Frameworks  
> **Automated Verification:** **495 Passing Tests** (286 Stage 2 tests + 209 Stage 3 web/auth/portal/officer/scheduling/container tests; 0 Failures, 0 Errors, 0 Skips via `mvn clean verify`)

---

## 1. Architectural Overview & Design Decisions

HireFlow Stage 3 introduces an enterprise Jakarta EE MVC web portal for campus placement coordination while fully preserving the Stage 1 core domain logic and Stage 2 PostgreSQL persistence, Flyway migrations, and Swing desktop interface.

Stage 3F.2 concludes CAP-6 by enabling secure transactional web interview timetable publication from advisory previews (`POST /officer/scheduling/publish`), confirmed schedule viewing (`GET /officer/scheduling/published`), candidate personal appointment visibility (`GET /student/interviews`), multi-tab isolation, cryptographic preview token expiry, and full database GiST temporal overlap protection.

### Dual-Artifact Maven Architecture
A unified architecture with Maven `<packaging>war</packaging>` and secondary `<classifier>desktop</classifier>` executable JAR:
1. **Preserves Working Directory & Configuration Discovery:** All 11 existing JDBC integration test suites resolve `hireflow-local.properties` from the project root without path breakage.
2. **Preserves 100% Test Discovery:** All 286 regression tests and 208 web tests remain discoverable by Surefire under `src/test/java/com/hireflow/...`.
3. **Simultaneous Dual Artifacts:** A single `mvn clean package` or `mvn clean verify` produces both:
   - `target/hireflow-1.0.0.war` — standard Jakarta Servlet 6.0 archive deployable to Apache Tomcat 10.1.x (~8.5 MB).
   - `target/hireflow-1.0.0-desktop.jar` — standalone executable desktop Swing application (`Main-Class: com.hireflow.Main`, ~365 KB).
4. **Decoupled Packaging:** Web-specific classes reside strictly in `com.hireflow.web.*`, JSP views in `WEB-INF/views/`, and static assets in `static/`.

```
+-------------------------------------------------------------------------------------------------------------------+
|                                        APACHE TOMCAT 10.1 (SERVLET 6.0)                                           |
|                                                                                                                   |
|   HTTP GET /             HTTP GET /login           POST /login               GET /student/*     GET /officer/*    |
|   HTTP GET /drives       HTTP GET /about           POST /logout              GET /student/      POST /officer/    |
|        │                         │                      │                    interviews         scheduling/publish|
|        v                         v                      v                           │                  │          |
|  [SecurityHeadersFilter] ───> [UTF-8, CSP, X-Content-Type-Options, X-Frame-Options: DENY, Anti-Sniff]            |
|        │                         │                      │                           │                  │          |
|        │                         │                      │               [PortalAuthorizationFilter]        |
|        │                         │                      │               (Inspects session UserPrincipal,   |
|        │                         │                      │                enforces STUDENT / OFFICER roles, |
|        │                         │                      │                anti-caching headers, denies 403) |
|        │                         │                      │                           │                  │          |
|        v                         v                      v                           v                  v          |
|  [Public Controllers]     [LoginController]      [Login / Logout]           [StudentInterview] [OfficerScheduling] |
|   HomeController           GET: login.jsp         POST /login:               student/           POST /publish:     |
|   DriveController          (issues CSRF)           - CSRF Validation          interviews.jsp     - Preview Token    |
|   AboutController                                  - BruteForceThrottler                         - 15-min TTL       |
|                                                    - AuthenticationService                       - Lock Ordering    |
|                                                      * find by username                          - GiST Constraints |
|                                                      * BCrypt verify                             - PRG Redirect     |
|                                                    - req.changeSessionId()                                        |
|                                                    - set hireflow.user                                            |
|                                                   POST /logout:                                                   |
|                                                    - CSRF Validation                                              |
|                                                    - session.invalidate()                                         |
+-------------------------------------------------------------------------------------------------------------------+
```

---

## 2. Package & Directory Structure

```text
hireflow/
├── pom.xml                                      (Maven build, WAR packaging, Tomcat 10.1, BCrypt 0.10.2)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/hireflow/
│   │   │       ├── context/
│   │   │       │   └── ApplicationContext.java  (Centralized service, DB & Auth container)
│   │   │       ├── db/                          (DatabaseConfig, DataSourceProvider, TransactionManager)
│   │   │       ├── model/                       (Company, Student, RecruitmentDrive, Application, etc.)
│   │   │       ├── repository/                  (JDBC and InMemory repository implementations)
│   │   │       ├── scheduler/                   (GreedyScheduler, ConflictChecker, TimeWindow)
│   │   │       ├── service/                     (RecruitmentDriveService, ApplicationService, InterviewService)
│   │   │       ├── network/                     (TCP sockets, protocol framing, event broadcasting)
│   │   │       ├── ui/                          (Swing desktop MainFrame, panels, UIUtils)
│   │   │       └── web/                         (STAGE 3 CAP-6 WEB APPLICATION & SECURITY)
│   │   │           ├── controller/
│   │   │           │   ├── HomeController.java            (GET / and GET /home -> home.jsp)
│   │   │           │   ├── DriveController.java           (GET /drives with filter/sort -> drives.jsp)
│   │   │           │   ├── AboutController.java           (GET /about -> about.jsp)
│   │   │           │   ├── HealthController.java          (GET /health -> JSON status)
│   │   │           │   ├── LoginController.java           (GET /login -> login.jsp, POST /login -> auth)
│   │   │           │   ├── LogoutController.java          (POST /logout -> session invalidation)
│   │   │           │   ├── StudentDashboardController.java(GET /student/dashboard -> student/dashboard.jsp)
│   │   │           │   ├── StudentProfileController.java  (GET /student/profile -> student/profile.jsp)
│   │   │           │   ├── StudentDriveController.java    (GET /student/drives -> student/drives.jsp)
│   │   │           │   ├── StudentApplicationController.java (GET/POST /student/applications -> student/applications.jsp)
│   │   │           │   ├── StudentInterviewController.java(GET /student/interviews -> student/interviews.jsp)
│   │   │           │   ├── OfficerDashboardController.java(GET /officer/dashboard -> officer/dashboard.jsp)
│   │   │           │   ├── OfficerCompanyController.java  (GET/POST /officer/companies -> officer/companies.jsp)
│   │   │           │   ├── OfficerDriveController.java    (GET/POST /officer/drives -> officer/drives.jsp)
│   │   │           │   ├── OfficerApplicationController.java(GET/POST /officer/applications -> officer/applications.jsp)
│   │   │           │   ├── OfficerInterviewerController.java(GET/POST /officer/interviewers -> officer/interviewers.jsp)
│   │   │           │   ├── OfficerRoomController.java     (GET/POST /officer/rooms -> officer/rooms.jsp)
│   │   │           │   ├── OfficerSchedulingController.java(GET/POST /officer/scheduling/*, /publish, /published)
│   │   │           │   └── ProtectedPortalController.java (Defense-in-depth gate for legacy routes)
│   │   │           ├── dto/
│   │   │           │   ├── PublicDriveDto.java            (Sanitized public drive view model)
│   │   │           │   ├── StudentDriveViewItem.java      (Drive view model with eligibility decision & applied flag)
│   │   │           │   ├── StudentApplicationViewItem.java(Application view model with company & drive details)
│   │   │           │   ├── SchedulingPreviewState.java    (Cryptographic preview token, 15m TTL, isolation & EL getters)
│   │   │           │   ├── PublicationSummaryState.java   (Structured publication outcome, conflict buckets & error detail)
│   │   │           │   └── ConfirmedInterviewViewItem.java(Confirmed timetable view item with candidate & venue details)
│   │   │           ├── filter/
│   │   │           │   ├── SecurityHeadersFilter.java     (Response security headers, remote HTTPS enforcement)
│   │   │           │   └── PortalAuthorizationFilter.java (Role-based access boundary, anti-cache, immediate disabled-account revocation)
│   │   │           ├── listener/
│   │   │           │   └── WebContextListener.java        (ServletContext lifecycle & auth wiring)
│   │   │           ├── security/
│   │   │           │   ├── UserAccount.java               (Persistent authentication entity)
│   │   │           │   ├── UserAccountRepository.java     (Account CRUD repository interface)
│   │   │           │   ├── InMemoryUserAccountRepository.java (Transient mock storage)
│   │   │           │   ├── JdbcUserAccountRepository.java (PostgreSQL JDBC user account storage)
│   │   │           │   ├── PasswordHasher.java            (BCrypt salted hashing, bounds & verify)
│   │   │           │   ├── AuthenticationService.java     (Credential verification & principal issuance, 12-char policy)
│   │   │           │   ├── AuthenticationResult.java      (Structured authentication outcome)
│   │   │           │   ├── CsrfTokenManager.java          (SecureRandom session CSRF tokens & rotation)
│   │   │           │   ├── BruteForceThrottler.java       (Bounded IP/account sliding window rate limiter)
│   │   │           │   ├── AccountProvisioningCli.java    (Guarded CLI test account provisioner)
│   │   │           │   ├── UserPrincipal.java             (Immutable Principal: username, role, studentId)
│   │   │           │   └── UserRole.java                  (Enum: STUDENT, PLACEMENT_OFFICER, ADMINISTRATOR)
│   │   │           └── util/
│   │   │               └── WebSecurityUtil.java           (HTML escaping & safe redirect verification)
│   │   ├── resources/
│   │   │   └── db/migration/
│   │   │       ├── V1__initial_schema.sql       (Stage 2 recruitment schema)
│   │   │       └── V2__user_accounts.sql        (Stage 3C persistent authentication schema)
│   │   └── webapp/
│   │       ├── WEB-INF/
│   │       │   ├── web.xml                      (Servlet 6.0 deployment descriptor, session-config)
│   │       │   └── views/
│   │       │       ├── common/
│   │       │       │   ├── header.jspf          (Global navigation, brand, authenticated user badge, CSRF meta)
│   │       │       │   ├── footer.jspf          (Global footer, copyright, system architecture tag)
│   │       │       │   ├── breadcrumbs.jspf     (Contextual breadcrumb trail)
│   │       │       │   └── flash.jspf           (Flash alert notifications: info, success, warning, error)
│   │       │       ├── student/
│   │       │       │   ├── dashboard.jsp        (Protected student landing page)
│   │       │       │   ├── profile.jsp          (Read-only authenticated student academic profile)
│   │       │       │   ├── drives.jsp           (Student recruitment drives with server-side eligibility & application forms)
│   │       │       │   └── applications.jsp     (Student application pipeline tracking & status badges)
│   │       │       ├── officer/
│   │       │       │   └── dashboard.jsp        (Protected placement officer landing page)
│   │       │       ├── home.jsp                 (Portal overview & feature highlights)
│   │       │       ├── drives.jsp               (Public drives catalog with filter/sort UI & empty states)
│   │       │       ├── about.jsp                (Placement policy, eligibility guidelines, statistics)
│   │       │       ├── login.jsp                (Secure login form with CSRF token)
│   │       │       ├── auth-gate.jsp            (403 Access-denied gate)
│   │       │       └── error.jsp                (Sanitized error display for 400, 403, 404, 500)
│   │       └── static/
│   │           └── css/
│   │               └── hireflow-web.css         (Vanilla CSS design system)
│   └── test/
│       └── java/com/hireflow/
│           ├── (Stage 1 & 2 regression test suites - 285 tests)
│           └── web/                             (STAGE 3 WEB & AUTH TEST SUITES - 117 tests)
│               ├── controller/
│               │   ├── HomeControllerTest.java
│               │   ├── DriveControllerTest.java
│               │   ├── DriveFilterAndSortTest.java
│               │   ├── AboutControllerTest.java
│               │   ├── HealthControllerTest.java
│               │   ├── LoginAndLogoutControllerTest.java
│               │   ├── DashboardControllerTest.java
│               │   ├── StudentProfileControllerTest.java
│               │   ├── StudentDriveControllerTest.java
│               │   └── StudentApplicationControllerTest.java
│               ├── filter/
│               │   ├── SecurityHeadersFilterTest.java
│               │   └── PortalAuthorizationFilterTest.java
│               ├── listener/
│               │   └── WebContextListenerTest.java
│               ├── security/
│               │   ├── PasswordHasherTest.java
│               │   ├── BruteForceThrottlerTest.java
│               │   ├── CsrfTokenManagerTest.java
│               │   ├── AuthenticationServiceTest.java
│               │   └── JdbcUserAccountRepositoryTest.java
│               ├── util/
│               │   └── WebSecurityUtilTest.java
│               └── integration/
│                   └── TomcatDeploymentIntegrationTest.java (31 true Tomcat container tests)
```

---

## 3. Database Schema: Flyway V2 Authentication Migration

Flyway migration `V2__user_accounts.sql` adds persistent user credentials while preserving all existing `V1` tables and records:

```sql
CREATE TABLE IF NOT EXISTS user_accounts (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    student_id VARCHAR(32),
    display_name VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP WITH TIME ZONE,
    
    CONSTRAINT uq_user_accounts_username UNIQUE (username),
    CONSTRAINT chk_user_accounts_role CHECK (role IN ('STUDENT', 'PLACEMENT_OFFICER', 'ADMINISTRATOR')),
    CONSTRAINT fk_user_accounts_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE RESTRICT,
    CONSTRAINT chk_user_accounts_student_binding CHECK (
        (role = 'STUDENT' AND student_id IS NOT NULL) OR
        (role != 'STUDENT' AND student_id IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_user_accounts_username ON user_accounts(username);
CREATE INDEX IF NOT EXISTS idx_user_accounts_role ON user_accounts(role);
CREATE INDEX IF NOT EXISTS idx_user_accounts_student_id ON user_accounts(student_id);
```

### Key Schema Constraints
1. **Case-Insensitive Username Normalization:** Usernames are strictly trimmed and converted to lowercase prior to persistence and lookup.
2. **Student Identity Binding Constraint:** The check constraint `chk_user_accounts_student_binding` enforces at the database level that:
   - Any account with role `STUDENT` **must** specify a valid `student_id` referencing the `students` table.
   - Any account with role `PLACEMENT_OFFICER` or `ADMINISTRATOR` **must not** have a `student_id` linked, preventing officer accounts from impersonating student identities.
3. **No Session Tokens or Plaintext in DB:** Database stores only salted password hashes, account state, and audit timestamps.
4. **Referential Integrity:** `ON DELETE RESTRICT` on `fk_user_accounts_student` prevents deleting active student records without handling account associations.

---

## 4. Credential Hashing & Security Safeguards

HireFlow uses **BCrypt** via the pure Java library `at.favre.lib:bcrypt:0.10.2` (no native binaries required):

- **Algorithm Scheme:** Standard OpenBSD Blowfish-based password hashing (BCrypt).
- **Cost Factor:** 10 (adaptive work factor, providing strong offline attack resistance with ~60ms hash latency).
- **Per-Password Salt:** 16 cryptographically secure random bytes generated per password hash, managed directly by the library format (`$2a$10$...`).
- **Constant-Time Verification:** Verification via `BCrypt.verifyer().verify(...)` executes in constant time, preventing timing-attack side-channels.
- **Input Length Safeguards:**
  - Minimum length: 6 characters.
  - Maximum length: 128 characters.
  - Rejecting excessively long passwords (> 128 characters) immediately prevents CPU-exhaustion denial-of-service against the hashing function.
- **Zero Sensitive Logging:** Plaintext passwords and raw hashes are never included in log statements, URLs, view templates, session attributes, or exception messages.

---

## 5. Authentication Service & Account Provisioning

### Authentication Service (`AuthenticationService`)
Orchestrates login requests across repositories:
1. Normalizes username (lowercase, trimmed).
2. Checks brute-force throttling window for both the username and client IP.
3. Queries `UserAccountRepository` for an enabled account.
4. If found, verifies candidate password hash using `PasswordHasher`.
5. For `STUDENT` accounts, verifies that the referenced `student_id` exists in `StudentRepository`.
6. On success, resets failed attempts, updates `last_login_at`, and returns a `UserPrincipal`.
7. On failure, increments failure count, records throttler attempt, and returns a **generic error message** (`"Invalid username or password."`) without revealing account existence.

### Secure Account Provisioning CLI (`AccountProvisioningCli`)
To prevent hardcoded admin credentials from ever entering version control:
- No default admin or student credentials are seeded automatically on web startup.
- Accounts are created via the explicit CLI command:
  ```bash
  java -Dhireflow.allow.provisioning=true -cp target/hireflow-1.0.0-classes.jar:target/hireflow-1.0.0/WEB-INF/lib/* \
    com.hireflow.web.security.AccountProvisioningCli \
    --username officer1 \
    --password "SecurePassword123" \
    --role PLACEMENT_OFFICER \
    --name "Placement Officer"
  ```
- **Safety Gate:** Fails with `SecurityException` unless `-Dhireflow.allow.provisioning=true` is explicitly provided.
- **Student Binding Validation:** If role is `STUDENT`, requires `--student-id <ID>` and verifies the student exists in the database.

---

## 6. HTTP Session Management & Cookie Policies

### Jakarta `HttpSession` Configuration
Configured in `web.xml`:
```xml
<session-config>
    <session-timeout>30</session-timeout>
    <cookie-config>
        <http-only>true</http-only>
        <secure>false</secure> <!-- set to true in production HTTPS -->
        <name>JSESSIONID</name>
    </cookie-config>
    <tracking-mode>COOKIE</tracking-mode>
</session-config>
```

### Session Security Policies & Hardening (Stage 3D Hardened)
1. **Session ID Rotation (`req.changeSessionId()`):**
   Immediately upon successful credential verification in `LoginController`, `req.changeSessionId()` is invoked to issue a new session token, neutralizing Session Fixation vulnerabilities.
2. **Synchronized CSRF Token Rotation:** Alongside session ID rotation, `CsrfTokenManager.rotateToken(session)` generates a fresh cryptographically random 256-bit CSRF token, ensuring pre-login CSRF tokens cannot be exploited post-login.
3. **HttpOnly Flag:** Enforced container-wide to prevent JavaScript XSS attacks from reading the `JSESSIONID` cookie.
4. **Tracking Mode:** Restricted strictly to `COOKIE`. URL rewriting (`jsessionid=...` query parameters) is completely disabled.
5. **Context-Scoped Cookie Invalidation on Logout:** `LogoutController` invokes `session.invalidate()` and overwrites the `JSESSIONID` cookie with `Max-Age=0` and path scoped strictly to `req.getContextPath()` (or `/` if context path is empty), preventing cross-application cookie pollution.
6. **No Client-Side Trust:** Authentication state is derived exclusively from server-side session attribute `"hireflow.user"`. No cookie or client-supplied value alone can grant authenticated access.
7. **Immediate Disabled Account Revocation:** `PortalAuthorizationFilter` re-queries `UserAccountRepository` on every protected request. If the account was disabled or deleted, the session is invalidated immediately (`session.invalidate()`) and access is terminated with HTTP 403.
8. **Anti-Caching for Authenticated Routes:** `PortalAuthorizationFilter` applies headers to all protected responses:
   - `Cache-Control: no-store, no-cache, must-revalidate, max-age=0`
   - `Pragma: no-cache`
   - `Expires: 0`
9. **Remote HTTPS Enforcement & HSTS:** `SecurityHeadersFilter` verifies `req.isSecure()` or `X-Forwarded-Proto`. Remote (non-localhost/loopback) requests over unencrypted HTTP are rejected with HTTP 403 Forbidden. On HTTPS requests, `Strict-Transport-Security: max-age=31536000; includeSubDomains` is automatically applied.
10. **Cookie SameSite=Lax Wrapper:** `SecurityHeadersFilter` wraps `HttpServletResponse` to append `; SameSite=Lax` on all response `Set-Cookie` headers.
11. **Account Provisioning Password Policy:** `AuthenticationService.provisionAccount(...)` enforces a minimum password length of 12 characters for all newly provisioned accounts, while preserving verification compatibility for legitimate existing hashes.

---

## 7. Role-Based Authorization & Route Matrix

| Route Pattern | Method | Allowed Roles | Unauthenticated Behavior | Wrong Role Behavior | CSRF Protected |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `/` or `/home` | GET | Public (All) | Allowed | Allowed | N/A |
| `/drives` | GET | Public (All) | Allowed | Allowed | N/A |
| `/about` | GET | Public (All) | Allowed | Allowed | N/A |
| `/health` | GET | Public (All) | Allowed | Allowed | N/A |
| `/login` | GET | Public (All) | Allowed (Redirects to dashboard if logged in) | N/A | N/A |
| `/login` | POST | Public (All) | Verifies credentials, rotates session & CSRF | N/A | Yes |
| `/logout` | POST | Authenticated | Redirects to `/login` | N/A | Yes |
| `/student/dashboard` | GET | `STUDENT` | Redirects to `/login?redirect=...` | HTTP 403 Forbidden | N/A |
| `/student/profile` | GET | `STUDENT` | Redirects to `/login?redirect=...` | HTTP 403 Forbidden | N/A |
| `/student/drives` | GET | `STUDENT` | Redirects to `/login?redirect=...` | HTTP 403 Forbidden | N/A |
| `/student/applications` | GET | `STUDENT` | Redirects to `/login?redirect=...` | HTTP 403 Forbidden | N/A |
| `/student/applications` | POST | `STUDENT` | Redirects to `/login?redirect=...` | HTTP 403 Forbidden | Yes |
| `/officer/*` | All | `PLACEMENT_OFFICER`, `ADMINISTRATOR` | Redirects to `/login?redirect=...` | HTTP 403 Forbidden | N/A |
| `/admin/*` | All | `ADMINISTRATOR` | Redirects to `/login?redirect=...` | HTTP 403 Forbidden | N/A |

### Parameter Tampering Immunity & IDOR Prevention
`PortalAuthorizationFilter` and all student controllers derive student identity exclusively from `UserPrincipal.studentId()` held in the server-side `HttpSession`. Any browser-submitted parameter (such as `?studentId=STU-999` or form field `<input name="studentId">`) is completely ignored. Student A cannot view Student B's profile or applications, nor submit applications on behalf of Student B under any circumstances.

---

## 8. CSRF Protection Mechanism (`CsrfTokenManager`)

HireFlow implements a server-side synchronizer token pattern:
1. **Token Generation:** 256-bit cryptographically secure token generated using `java.security.SecureRandom` and Hex-encoded (64 characters).
2. **Session Storage:** Bound to the user's `HttpSession` under attribute `_csrf`.
3. **Form & Meta Inclusion:** Included in HTML forms as `<input type="hidden" name="_csrf" value="${_csrf}">` and in page headers as `<meta name="csrf-token" content="${_csrf}">`.
4. **Token Rotation:** Re-generated on successful authentication (`CsrfTokenManager.rotateToken(session)`) to prevent pre-session token hijacking.
5. **Constant-Time Validation:** Checked via `MessageDigest.isEqual` to prevent timing attacks.
6. **Enforcement:** `POST /login`, `POST /logout`, and `POST /student/applications` strictly enforce CSRF token validity. Missing, blank, or mismatched tokens produce an immediate HTTP 403 Forbidden response.

---

## 9. Student Self-Service Portal Architecture (Stage 3D)

### 1. Student Academic Profile (`GET /student/profile`)
- **Controller:** `StudentProfileController`
- **View:** `WEB-INF/views/student/profile.jsp`
- **Security:** Requires `STUDENT` role. Derives `studentId` exclusively from `principal.getStudentId()`.
- **Fields Displayed:** Student Name, Student ID / Roll Number, Branch, Current CGPA, Active Backlogs, Contact Email, Contact Phone, and Account Verification Status.
- **Privacy & Caching:** Private academic data; responses strictly include `Cache-Control: no-store, no-cache, must-revalidate`. Read-only in Stage 3D; registration and document upload features are deferred.

### 2. Eligible Recruitment Drives Directory (`GET /student/drives`)
- **Controller:** `StudentDriveController`
- **View:** `WEB-INF/views/student/drives.jsp`
- **Model:** `StudentDriveViewItem` (combines `RecruitmentDrive`, `Company`, `EligibilityResult`, and `alreadyApplied` flag).
- **Server-Side Eligibility Evaluation:** Evaluates each drive against the student's authoritative database profile using `EligibilityService.checkEligibility(student, drive)`.
- **Status Badges:**
  - `APPLIED` — Student has already submitted an application for this drive.
  - `ELIGIBLE` — Drive application window is open and student satisfies branch and minimum CGPA criteria.
  - `NOT ELIGIBLE` — Ineligibility reasons clearly displayed (e.g., minimum CGPA unmet, branch excluded, active backlogs).
  - `CLOSED` — Application window has lapsed or drive status is not active.
- **Anti-N+1 Optimization:** Existing student applications are fetched once (`applicationService.getApplicationsByStudent(studentId)`) and indexed into an in-memory set of applied drive IDs, eliminating redundant database queries.

### 3. Online Application Submission (`POST /student/applications`)
- **Controller:** `StudentApplicationController`
- **Transaction & Verification Sequence:**
  1. **Authentication Check:** Session must contain valid `UserPrincipal` with `STUDENT` role and non-null `studentId`.
  2. **CSRF Validation:** Compares submitted `_csrf` parameter against session token using constant-time comparison.
  3. **Input Validation:** Validates presence and format of `driveId`.
  4. **Drive Window Verification:** Confirms drive exists and application deadline has not passed (`drive.isApplicationWindowOpen(now)`).
  5. **Server-Side Eligibility Re-evaluation:** Re-evaluates `EligibilityService.checkEligibility(student, drive)` directly before submission.
  6. **Existing Application Verification:** Verifies no prior application exists for this student and drive.
  7. **Persistence:** Invokes `ApplicationService.submitApplication(studentId, driveId)`.
  8. **Concurrent Race Protection:** Backed by PostgreSQL composite unique constraint `UNIQUE (student_id, drive_id)` in the `applications` table. Two simultaneous requests from the same candidate result in exactly one successful insertion; the duplicate request raises `DuplicateEntityException` and is handled gracefully with an informative user flash notice.
  9. **Post/Redirect/Get (PRG):** Upon completion, redirects to `GET /student/applications` with session flash notice (`flash_success` or `flash_error`), preventing double-submission on browser refresh.

### 4. Application Status Tracking (`GET /student/applications`)
- **Controller:** `StudentApplicationController`
- **View:** `WEB-INF/views/student/applications.jsp`
- **Model:** `StudentApplicationViewItem`
- **Data Isolation:** Strictly queries `applicationService.getApplicationsByStudent(studentId)` where `studentId` is authoritative from the session. A student can never view another student's applications.
- **Lifecycle Statuses & Badges:**
  - `APPLIED` — Application submitted and under placement cell review.
  - `SHORTLISTED` — Candidate meets company screening criteria and is shortlisted for interviews.
  - `INTERVIEW_SCHEDULED` — Interview slot allocated in schedule timetable.
  - `SELECTED` — Candidate accepted and received job offer.
  - `REJECTED` — Candidate not selected in this recruitment cycle.
- **Confidentiality:** Displays company name, job role, submission timestamp, and current status. Internal interview room allocations, panel notes, and officer-only scheduling logs are withheld from student view.

---

---

## 10. Placement Officer Recruitment Management & Shortlisting Architecture (Stage 3E)

Stage 3E establishes the authenticated recruitment workflows for university Training & Placement Cell (TPC) officers:

### 1. Security Hardening & Session Cookie Controls
- **Trusted Reverse Proxy Enforcement:** `SecurityHeadersFilter` strictly validates `X-Forwarded-Proto` against an explicit whitelist of trusted proxy IPs (`hireflow.trusted.proxy.ips`). Spoofed headers from untrusted origins are discarded.
- **Native Tomcat SameSite Cookie Handling:** Configured `org.apache.tomcat.util.http.Rfc6265CookieProcessor` with `sameSiteCookies="lax"` via `src/main/webapp/META-INF/context.xml`. Generic, fragile string rewriting of `Set-Cookie` headers has been completely removed.
- **CSRF Defense on Mutations:** All officer POST routes require a cryptographically secure token submitted via either `_csrf` or `csrfToken` form parameters, verified using constant-time byte comparisons.
- **Role-Based Authorization:** All officer endpoints are guarded by `PortalAuthorizationFilter`, permitting only `PLACEMENT_OFFICER` and `ADMINISTRATOR` identities. `STUDENT` accounts are rejected with `HTTP 403 Forbidden`.
- **Anti-Cache Policies:** Officer dashboard, corporate registries, drive administration, and candidate rosters emit `Cache-Control: no-cache, no-store, must-revalidate`, `Pragma: no-cache`, and `Expires: 0`.
- **Information Protection:** Sensitive student CGPAs, phone numbers, and candidate rosters are protected behind authenticated sessions and never exposed via public endpoints or TCP broadcasts.

### 2. Officer Dashboard (`GET /officer/dashboard`)
- **Controller:** `OfficerDashboardController`
- **View:** `WEB-INF/views/officer/dashboard.jsp`
- **Model:** `OfficerDashboardMetrics`
- **Bounded Real Metrics:** Computes live metrics directly from authoritative domain services without hardcoded data:
  - Registered Corporate Partners (`companyService.getAllCompanies().size()`)
  - Recruitment Drives (`driveService.getAllDrives().size()`)
  - Active Recruitment Windows (`drive.isDeadlinePassed(now) == false`)
  - Total Candidate Applications (`applicationService.getAllApplications().size()`)
  - Shortlisted Candidates (`application.getStatus() == SHORTLISTED`)
  - Confirmed Interview Slots (`interviewSessionRepository.findAll().size()`)

### 3. Corporate Partner Management (`/officer/companies/*`)
- **Controller:** `OfficerCompanyController`
- **Domain Service:** `CompanyService` backed by `CompanyRepository`
- **Routes:**
  - `GET /officer/companies`: Directory of registered employers.
  - `GET /officer/companies/new`: Form to add a new corporate partner.
  - `POST /officer/companies`: Create partner with ID uniqueness validation and PRG redirect.
  - `GET /officer/companies/{id}/edit`: Form to update existing employer.
  - `POST /officer/companies/{id}/update`: Updates company name and industry classification.
- **Data Integrity:** Primary identifier (`id`) is immutable post-registration. Duplicate IDs are rejected with user-friendly flash messages. Unsafe bulk deletion is not permitted.

### 4. Recruitment Drive Management (`/officer/drives/*`)
- **Controller:** `OfficerDriveController`
- **Domain Service:** `RecruitmentDriveService`
- **Routes:**
  - `GET /officer/drives`: Catalog of drives with linked corporate partner names and application counts.
  - `GET /officer/drives/new`: Form for publishing new drives with eligible branches and minimum CGPA.
  - `POST /officer/drives`: Create drive with atomic branch set persistence.
  - `GET /officer/drives/{id}`: View drive parameters, eligibility thresholds, and applicant metrics.
  - `GET /officer/drives/{id}/edit`: Edit drive thresholds, branches, and application deadlines.
  - `POST /officer/drives/{id}/update`: Updates drive and synchronizes eligible branch set atomically.
  - `POST /officer/drives/{id}/close`: Closes drive window by advancing deadline to yesterday (`LocalDate.now().minusDays(1)`).
- **Drive Closure Semantics:**
  - In strict alignment with the domain model (`recruitment_drives` table has no redundant status column), a drive's openness is determined by its application deadline (`drive.isDeadlinePassed(LocalDate.now())`).
  - Closing a drive sets the deadline to the past, which immediately blocks student submissions in both `StudentApplicationController` and `ApplicationService.submitApplication`.

### 5. Applicant Review & Candidate Shortlisting (`/officer/applications/*`)
- **Controller:** `OfficerApplicationController`
- **Routes:**
  - `GET /officer/applications`: Comprehensive applicant roster across drives with drive filter, status filter, search query, and bounded pagination (`page`, `pageSize` capped at 100).
  - `GET /officer/drives/{id}/applications`: Filtered applicant review specific to a recruitment drive.
  - `POST /officer/applications/{id}/shortlist`: Evaluates academic eligibility and transitions application status to `SHORTLISTED`.
- **Anti-N+1 Query Optimization:** Applications, students, drives, and companies are batch-indexed in memory, eliminating N+1 database queries.
- **Shortlisting State Machine & Concurrency:**
  - **Dynamic Eligibility Verification:** Automatically re-evaluates `EligibilityService.checkEligibility(student, drive)` prior to shortlisting. If a student no longer meets the cutoff (e.g. CGPA dropped or branch excluded), the shortlisting mutation is rejected with an informative error.
  - **Lifecycle Machine:** Enforces valid transitions (`APPLIED -> SHORTLISTED`). Transitions from terminal or scheduled states (`SCHEDULED`, `SELECTED`, `REJECTED`) are blocked.
  - **Idempotency:** Re-shortlisting an already `SHORTLISTED` application safely succeeds without state corruption.
  - **Conditional Update Protection:** Invokes `applicationRepository.updateStatusConditional(id, currentStatus, newStatus)` to prevent lost updates under concurrent officer operations.

### 6. Drive Manual Closure Correction & Flyway V3 Migration
- **Problem Statement:** In Stage 3E, closing a recruitment drive destructively modified `application_deadline` to yesterday, permanently corrupting the original deadline.
- **Flyway V3 Migration (`V3__drive_closure.sql`):**
  - Introduces `is_closed BOOLEAN NOT NULL DEFAULT FALSE` to `recruitment_drives`.
  - Creates index `idx_recruitment_drives_closed` on `(is_closed)`.
  - Fully backward-compatible; existing drives migrate as `is_closed = false`.
- **Domain Invariants & Separation of Concerns:**
  - Original `application_deadline` is preserved unconditionally upon manual closure.
  - A drive is open if and only if `!isClosed() && !isDeadlinePassed(date)`.
  - Distinguishable drive states: `ACTIVE / OPEN`, `MANUALLY CLOSED`, and `EXPIRED (DEADLINE PASSED)`.
  - `ApplicationService.submitApplication` strictly rejects submissions if `drive.isClosed()`.
  - Pre-existing applications remain valid and can be reviewed, evaluated for academic cutoff via `EligibilityService.checkAcademicEligibility`, and shortlisted after closure.
  - Parity across `InMemoryDriveRepository` and `JdbcDriveRepository` (`findActiveDrives` excludes both closed and expired drives).

### 7. Interview Resource Management (`/officer/interviewers/*`, `/officer/rooms/*`)
- **Controllers:** `OfficerInterviewerController`, `OfficerRoomController`
- **Routes:**
  - `GET /officer/interviewers`: Corporate interviewers catalog, partner company lookup, confirmed session count.
  - `GET /officer/interviewers/new`: Interviewer registration form.
  - `POST /officer/interviewers`: Create interviewer with validated ID, name, and registered company ID.
  - `GET /officer/interviewers/{id}/edit` & `POST /officer/interviewers/{id}/update`: Edit existing interviewer.
  - `POST /officer/interviewers/{id}/delete`: Delete interviewer with referential integrity guard.
  - `GET /officer/rooms`: Venue catalog, seating capacity, confirmed session count.
  - `GET /officer/rooms/new`: Venue registration form.
  - `POST /officer/rooms`: Create venue with positive capacity validation.
  - `GET /officer/rooms/{id}/edit` & `POST /officer/rooms/{id}/update`: Edit venue name and capacity.
  - `POST /officer/rooms/{id}/delete`: Delete venue with referential integrity guard.
- **Referential Integrity Guards:**
  - Before deleting any interviewer or room, the controllers inspect `InterviewSessionRepository.findByInterviewerId(id)` and `findByRoomId(id)`.
  - If any confirmed session references the resource, deletion is blocked with a clear error message and 0 mutations occur.

### 8. Web Scheduling Configuration & Advisory Preview (`/officer/scheduling/*`)
- **Controller:** `OfficerSchedulingController`
- **Routes:**
  - `GET /officer/scheduling`: Setup form with drive selection, date picker, start/end operational window, duration select, buffer/gap select, interviewer pool checkboxes, room pool checkboxes.
  - `POST /officer/scheduling/preview`: Generates advisory schedule preview using `GreedyScheduler`.
  - `GET /officer/scheduling/preview`: Views cached session preview.
- **Candidate Pool Qualification:**
  - Only candidates with `SHORTLISTED` status are qualified for scheduling.
  - Candidate pools are re-fetched server-side from `ApplicationRepository.findByDriveId(driveId)` and filtered strictly by status. Client-submitted candidate lists are never trusted.
- **Greedy Scheduler Reuse & Conflict Checking:**
  - Builds `SchedulingRequest` using domain services: shortlisted candidates, selected interviewers, selected rooms, time interval, slot duration, and buffer minutes.
  - Reuses established `ConflictChecker` for student, interviewer, and room double-booking prevention and buffer enforcement.
  - Queries existing confirmed sessions for the scheduling date to prevent double-booking across drives.
- **Advisory Preview Invariant & Zero Database Mutation:**
  - Absolutely NO rows are inserted into `interview_sessions`.
  - Application statuses remain `SHORTLISTED` (no transition to `SCHEDULED`).
  - Proposed timetable is rendered advisory only with clear banner disclaimers.
  - Unscheduled candidates are reported with reasons (e.g., "Insufficient time slots or resources available within scheduling window").
- **Preview State Safety & Concurrency Boundaries:**
  - Proposed timetable is stored in the authenticated user's session as `SchedulingPreviewState`.
  - Bound to the authenticated officer's username.
  - Bounded lifecycle; client cannot tamper with assignments via POST parameters.
  - Zero database transaction held open while officer inspects preview.

---

## 9. Secure Timetable Publication Architecture (`POST /officer/scheduling/publish`)

Stage 3F.2 implements secure web interview timetable publication directly reusing the battle-tested Stage 2 transactional scheduling engine (`InterviewService.publishSchedule`).

### 1. Cryptographic Preview State Lifecycle & Multi-Tab Isolation
- **128-Bit Cryptographic Identifier:** Preview identifiers (`PREV-<32 hex chars>`) are generated using `SecureRandom.getInstanceStrong()`, guaranteeing unguessability against brute-force guessing or IDOR attempts.
- **Configurable Expiration (TTL):** Each preview is stamped with a creation instant and strict 15-minute expiration (`expiresAt = createdAt.plus(15, ChronoUnit.MINUTES)`). Requests referencing expired previews are rejected immediately.
- **Session & Officer Ownership Binding:** Previews are bound to the authenticated `UserPrincipal.getUsername()`. If another officer or an unauthenticated user attempts publication using the preview ID, access is denied (HTTP 403 / redirect with error).
- **Multi-Tab Isolation:** Instead of a single mutable session slot, previews are stored in a synchronized, bounded LRU `LinkedHashMap` (`hireflow.schedulingPreviews`, max 5 entries). Each browser tab retains its unique preview token without cross-tab collision or overwriting.
- **Atomic One-Time Consumption (Anti-Replay):** Upon publication invocation, `preview.markConsumed()` transitions the state atomically. Any subsequent HTTP POST or replayed submission using the same token is rejected with an idempotent advisory message.
- **Zero Client Trust:** The browser submits solely `previewId` and the valid CSRF token (`_csrf`). Candidate IDs, room IDs, interviewer IDs, and time slot intervals are retrieved exclusively from server-side state.

### 2. Transactional Publication Semantics & Concurrency Safety
- **Delegation to Stage 2 `InterviewService`:** Web publication directly executes `interviewService.publishSchedule(sessions, bufferDuration)`. Zero web-only scheduling algorithms or parallel SQL insertion logic were introduced.
- **Independent Session Transactions:** Publication operates per-session transactions. If Candidate A succeeds while Candidate B encounters a conflict, Candidate A's interview session is committed and their application transitions to `SCHEDULED`, while Candidate B remains `SHORTLISTED` without partial corruption.
- **Deterministic Global Resource Lock Ordering:** For each session, locks are acquired in established order:
  $$\text{Applications} \longrightarrow \text{Rooms} \longrightarrow \text{Interviewers} \longrightarrow \text{Students}$$
  Deadlocks between concurrent officer publications are mathematically prevented.
- **Authoritative GiST Exclusion Constraints:** PostgreSQL `EXCLUDE USING gist` physical exclusion constraints on `(interviewer_id WITH =, time_slot WITH &&)` and `(room_id WITH =, time_slot WITH &&)` remain the ultimate database-level defense against double-booking under race conditions.
- **Conditional Status Progression:** Applications are transitioned via `applicationRepository.updateStatusConditional(appId, ApplicationStatus.SHORTLISTED, ApplicationStatus.SCHEDULED)`. If an application is no longer `SHORTLISTED`, insertion is aborted and the session is skipped.
- **Buffer Violation Protection:** The configured buffer (e.g., 15 minutes) is enforced by inspecting adjacent appointments for the candidate, interviewer, and room. Sessions violating buffer rules are rejected.

### 3. Granular Publication Outcomes (`PublicationSummaryState`)
The controller records a structured `PublicationSummaryState` flash attribute capturing:
- `publishedCount`: Proposed sessions successfully committed and saved to PostgreSQL.
- `skippedDuplicateCount`: Candidate already scheduled (`SESSION_EXISTS`).
- `skippedStatusMismatchCount`: Application status changed from `SHORTLISTED` (`STATUS_NOT_SHORTLISTED`).
- `conflictedCount`: Partitioned by root cause:
  - `STUDENT_OVERLAP` (Candidate double-booked)
  - `INTERVIEWER_OVERLAP` (Interviewer double-booked)
  - `ROOM_OVERLAP` (Venue double-booked)
  - `BUFFER_VIOLATION` (Minimum inter-interview buffer breached)
  - `RESOURCE_NOT_FOUND` (Interviewer or room decommissioned)
- `infrastructureErrorCount`: Unexpected database failures (distinguished from normal domain scheduling conflicts).
- Post/Redirect/Get flow redirects to `GET /officer/scheduling/published?driveId=...` to display the flash publication summary.

---

## 10. Persistent Confirmed Timetable & Student Appointment Visibility

### 1. Officer Confirmed Timetable (`GET /officer/scheduling/published`)
- **Authoritative PostgreSQL Source:** Queries confirmed sessions from `InterviewSessionRepository` (`findAll()` / `findByDriveId(driveId)`). Does not read transient preview memory.
- **Bounded Filtering:** Officers can filter confirmed interviews by `driveId` and `date`.
- **Anti-N+1 Batch Metadata Loading:** To avoid N+1 database queries, the controller batch-loads referenced applications, drives, companies, students, interviewers, and rooms in memory sets before mapping to `ConfirmedInterviewViewItem`.
- **Role Guard:** Accessible only to authenticated `PLACEMENT_OFFICER` and `ADMINISTRATOR` roles.

### 2. Student Appointment Visibility (`GET /student/interviews`)
- **Strict Identity Derivation:** Derives candidate identity strictly from `principal.getStudentId()`. Query parameter tampering (e.g. `?studentId=STU-999`) is strictly ignored.
- **Personal Isolation:** Invokes `sessionRepository.findByStudentId(studentId)`. Returns exclusively confirmed appointments linked to the logged-in student's own applications.
- **Zero Exposure of Other Candidates:** A student cannot view interviews of other applicants.
- **Confidentiality Guard:** Withholds interviewer personal phone numbers, email addresses, and confidential internal evaluation notes. Displays only company name, job role, date, start/end time, venue/room, and status.
- **Anti-Caching:** Enforces `Cache-Control: no-store, no-cache, must-revalidate` and `Pragma: no-cache`.

---

## 11. Verification & Test Evidence

### Authoritative Surefire XML Test Suite Reconciliation
| Suite Category | Test Class / Suite | Tests | Failures | Errors | Skips |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Stage 1 & 2 Regressions** | Models, Repositories, Scheduler, Services, Concurrency, Networking, Swing UI | **286** | 0 | 0 | 0 |
| **Stage 3 Web (Public)** | `HomeControllerTest`, `DriveControllerTest`, `DriveFilterAndSortTest`, `AboutControllerTest`, `HealthControllerTest` | **18** | 0 | 0 | 0 |
| **Stage 3 Filters & Context**| `SecurityHeadersFilterTest`, `PortalAuthorizationFilterTest`, `WebContextListenerTest`, `WebSecurityUtilTest` | **17** | 0 | 0 | 0 |
| **Stage 3 Auth & Security** | `PasswordHasherTest` (5), `BruteForceThrottlerTest` (3), `CsrfTokenManagerTest` (6), `AuthenticationServiceTest` (9), `JdbcUserAccountRepositoryTest` (6) | **29** | 0 | 0 | 0 |
| **Stage 3 Login / Logout** | `LoginAndLogoutControllerTest` | **8** | 0 | 0 | 0 |
| **Stage 3 Domain Services** | `CompanyServiceTest` (9), `RecruitmentDriveClosureTest` (5) | **14** | 0 | 0 | 0 |
| **Stage 3 Officer Management**| `DashboardControllerTest` (4), `OfficerCompanyControllerTest` (8), `OfficerDriveControllerTest` (9), `OfficerApplicationControllerTest` (12), `OfficerResourceControllerTest` (10) | **43** | 0 | 0 | 0 |
| **Stage 3 Student Portal** | `StudentProfileControllerTest` (4), `StudentDriveControllerTest` (2), `StudentApplicationControllerTest` (6) | **12** | 0 | 0 | 0 |
| **Stage 3F.1-3F.2 Scheduling** | `OfficerSchedulingControllerTest` (7), `OfficerPublicationSecurityTest` (10) | **17** | 0 | 0 | 0 |
| **Stage 3F.2 Student View** | `StudentInterviewControllerTest` | **5** | 0 | 0 | 0 |
| **Stage 3F.2 PostgreSQL Concurrency** | `JdbcSchedulingPublicationConcurrencyTest` (GiST exclusion, atomic rollback, multi-officer race, context restart persistence) | **3** | 0 | 0 | 0 |
| **Stage 3 Container Tests** | `TomcatDeploymentIntegrationTest` (42), `TomcatPostgresE2EWorkflowTest` (1) | **43** | 0 | 0 | 0 |
| **TOTAL** | **40 Test Classes / 73 Test Suites** | **495** | **0** | **0** | **0** |

#### Reconciliation of Test Totals:
- **Baseline at Stage 3F.1 Completion:** 475 reported tests.
  - Regression Subtotal Correction: 285 baseline + 1 new test (`JdbcInterviewerDriveRepositoryTest.testDriveManualClosureSurvivesDatabaseRestart`) = 286.
  - Filters & Config Subtotal Correction: 5 (`SecurityHeadersFilterTest`) + 7 (`PortalAuthorizationFilterTest`) + 3 (`WebContextListenerTest`) + 2 (`WebSecurityUtilTest`) = 17 (previously reported as 16).
  - Exact Subtotal Sum: $286 + 18 + 17 + 29 + 8 + 14 + 43 + 12 + 7 + 41 = 475$ tests.
- **Stage 3F.2 Additions (+19 Tests):**
  - `OfficerPublicationSecurityTest`: +10 tests (CSRF, expiration, multi-tab isolation, cross-officer isolation, anti-replay, conflict reporting, atomic status changes).
  - `StudentInterviewControllerTest`: +5 tests (candidate personal visibility, candidate-to-candidate isolation, role denial for officer/anonymous).
  - `JdbcSchedulingPublicationConcurrencyTest`: +3 tests (two officers publishing overlapping schedules with GiST exclusion, partial failure with feasible session publication, context restart persistence).
  - `TomcatDeploymentIntegrationTest`: +1 test (`testOfficerPublicationWorkflowAndStudentAppointmentVisibilityInContainer`).
- **Stage 3G Additions (+1 Test):**
  - `TomcatPostgresE2EWorkflowTest`: +1 test (complete 20-step browser & PostgreSQL acceptance workflow, application shutdown and restart persistence).
- **Final Authoritative Count:** **495 Passing Tests** (0 Failures, 0 Errors, 0 Skips).

---

## 12. Final CAP-6 Acceptance & Readiness

With Stage 3G complete, all functional and non-functional requirements of **CAP-6 (HireFlow Web Application Architecture)** are fully realized and verified:
1. **Public Catalog & Search (3A-3B):** Clean MVC architecture, SEO tags, responsive JSP views, and search/filter/sort capabilities.
2. **Persistent Authentication & Security (3C):** Flyway V2 `user_accounts`, pure Java BCrypt, session ID rotation, CSRF token rotation, BruteForceThrottler, and role-based filters.
3. **Student Self-Service Portal (3D):** Academic profile review, server-side eligibility checks, online application submissions with PRG flow, and pipeline status tracking.
4. **Officer Recruitment Administration (3E):** Corporate partner directory, recruitment drive management, candidate review, cutoff evaluation, and shortlisting.
5. **Interview Resource Management & Preview (3F.1):** Flyway V3 non-destructive drive closure, interviewer and venue catalogs with referential integrity guards, and GreedyScheduler advisory preview generation.
6. **Transactional Schedule Publication & Timetable (3F.2):** Cryptographic preview tokens, multi-tab isolation, Stage 2 transactional publication reuse, PostgreSQL GiST exclusion protection, confirmed timetable viewing, and candidate personal interview visibility.
7. **End-to-End Acceptance & Security Audit (3G):** Verified complete 20-step browser & database lifecycle against real embedded Tomcat 10.1 and PostgreSQL `hireflow_test`, server restart survival, comprehensive security audit, and dual WAR/JAR production build.

### Production Constraints Maintained:
- **Zero Heavyweight Frameworks:** Implemented cleanly in core Java 21, Jakarta Servlet 6.0, JSP 3.1, JSTL 3.0, HikariCP, Flyway, and JUnit 5 (zero Spring Boot, React, Hibernate, or Spring Security).
- **Dual Packaging:** Single Maven build produces both `target/hireflow-1.0.0.war` (~8.6 MB) and `target/hireflow-1.0.0-desktop.jar` (~471 KB).
- **Zero Data Loss / Dev Contamination:** All integration tests execute strictly against `hireflow_test` with teardown routines; `hireflow_dev` records remain pristine.
- **Branch & Tags:** Branch `develop/stage3` is active; frozen release tags `v1.0.0-stage1` and `v2.0.0-cap5` remain untouched.
- **Commit Boundary:** Zero commits, tags, pushes, or merges have been executed without explicit user instruction.



