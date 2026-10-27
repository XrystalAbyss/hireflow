# HIREFLOW — CAP-8.6.4 Security Review & Hardening Report
**Milestone:** CAP-8.6.4 Final Academic Release  
**Status:** AUDITED, HARDENED & DEFENDED  
**Evaluator:** Security Engineering & Spring Security Specialist

---

## 1. Executive Summary

A comprehensive OWASP-aligned security audit was conducted on the HireFlow local application stack. All authentication mechanisms, authorization boundaries, session lifecycles, CSRF mitigations, resource policies, and data isolation controls were verified.

---

## 2. Authentication & Session Security

### 2.1 Password Storage & Throttling
- **Algorithm:** BCrypt password hashing with high work factor (`PasswordHasher.java` and Spring Security `BCryptPasswordEncoder`).
- **Brute-Force Protection:** `BruteForceThrottler.java` tracks failed login attempts by IP and username, applying progressive delays and locking authentication attempts after successive failures (verified in `BruteForceThrottlerTest.java`).
- **Zero Plaintext Secrets:** Passwords and database credentials are excluded from Git repository history.

### 2.2 Session Security & Account Lifecycle
- **Session Identification:** Secure, random `JSESSIONID` cookie configured with `HttpOnly` and `SameSite=Strict/Lax` flags.
- **Session Fixation:** Spring Security rotates session IDs upon successful authentication (`sessionFixation().changeSessionId()`).
- **Session Invalidation:** Logout endpoint (`POST /api/v1/auth/logout`) terminates server-side session and invalidates CSRF context.
- **Account Disablement Enforcement:** `AccountStatusFilter.java` verifies active account status on every authenticated request; disabled accounts are immediately terminated with HTTP 401.

---

## 3. Role-Based Access Control (RBAC) & Object-Level Authorization

### 3.1 Role Segregation
Enforced via `@PreAuthorize` and Spring Security URL patterns:
- `ROLE_STUDENT`: Permitted access only to `/api/v1/student/**` and public recruitment drives.
- `ROLE_PLACEMENT_OFFICER`: Permitted access to `/api/v1/officer/**`, company management, candidate review, and scheduling studio.
- `ROLE_ADMINISTRATOR`: Permitted access to system administration and maintenance.

### 3.2 Broken Object-Level Authorization (BOLA) Prevention
- **Cross-Student Isolation:** When a student queries an application (`GET /api/v1/student/applications/{id}`) or interview (`GET /api/v1/student/interviews/{id}`), `StudentController` verifies that the target entity's `studentId` matches the authenticated principal's student identity. Unauthorized access returns HTTP 404 (preventing ID enumeration) and emits security warning logs.
- **Cross-Officer Scheduling Isolation:** In `SchedulingPreviewCacheService`, each preview token is tied to `officerUsername`. If another officer attempts to publish or consume the preview, access is denied with HTTP 403 Forbidden.

---

## 4. Cross-Site Request Forgery (CSRF) Mitigation

- **Synchronizer Token Pattern:** State-changing requests (`POST`, `PUT`, `DELETE`) require a valid `X-CSRF-TOKEN` header.
- **Rotation on Login:** Upon authentication, the existing CSRF token is refreshed to bind the new session securely.
- **Verification:** Tested extensively in `CsrfTokenManagerTest` and `OfficerSchedulingControllerMockMvcTest`.

---

## 5. Input Validation & Injection Defenses

### 5.1 SQL Injection Prevention
- **JDBC Layer:** 100% of JDBC database queries utilize parameterized `PreparedStatement` within `try-with-resources`. Zero string-concatenated SQL queries exist in repository classes.
- **PostgreSQL Extension Guard:** Spatial and temporal queries utilize parameterized GiST functions.

### 5.2 Bean Validation & Sanitization
- All REST request DTOs enforce Jakarta Bean Validation constraints (`@NotBlank`, `@Size`, `@Min`, `@NotNull`).
- Date ranges and temporal windows are checked for validity (e.g., `startTime.isBefore(endTime)`).

---

## 6. Campus Venue Policy Governance (Defect 5.2 Resolution)

- **Hardened Validation:** `UniversityCampusRoomPolicy` blocks unauthorized room registrations.
- **Restricted Access:** New rooms can only be registered if they correspond to approved K-Block or L-Block interview rooms (`KF02` - `LT10`). Unaccredited laboratories and offices are rejected at the API boundary with HTTP 400.

---

## 7. Security Audit Checklist Summary

| Security Dimension | Mechanism | Test Verification | Status |
| :--- | :--- | :--- | :---: |
| Password Hashing | BCrypt (10 rounds) | `PasswordHasherTest` | **PASS** |
| Login Brute-Force | `BruteForceThrottler` | `BruteForceThrottlerTest` | **PASS** |
| Session Fixation | `changeSessionId()` | Spring Security Integration | **PASS** |
| CSRF Protection | `X-CSRF-TOKEN` header | `CsrfTokenManagerTest` | **PASS** |
| Role Separation | `@PreAuthorize("hasRole(...)")` | `AuthControllerMockMvcTest` | **PASS** |
| Student Data BOLA | Ownership verification | `StudentWorkflowRealHttpIntegrationTest` | **PASS** |
| Officer Preview BOLA | Officer token binding | `OfficerSchedulingControllerMockMvcTest` | **PASS** |
| SQL Injection | Parameterized `PreparedStatement` | Core JDBC Test Suite | **PASS** |
| Account Disablement | `AccountStatusFilter` | `AccountStatusFilterTest` | **PASS** |
| Room Policy Bypass | `UniversityCampusRoomPolicy` | `UniversityCampusRoomPolicyTest` | **PASS** |
| Database Isolation | Sentinel check on port 55432 | `preflight-guard.ps1` | **PASS** |
