# HireFlow Production Security Checklist & Release Gates
## Institutional Security Verification, Fail-Closed Safeguards & Governance

### 1. Release Security Verification Matrix

| Area | Security Requirement | Production Enforcement Mechanism | Pre-Release Gate Status |
|---|---|---|---|
| **Session Cookies** | `HttpOnly`, `SameSite=Lax`, `Secure` | Spring Boot `server.servlet.session.cookie.secure=true` (`HIREFLOW_COOKIE_SECURE=true`). Nginx/Caddy proxy cookie rewriting. | **VERIFIED** |
| **CSRF Defense** | Server-stored session CSRF tokens (`HttpSessionCsrfTokenRepository`) | Mandatory `X-CSRF-TOKEN` on all state-changing endpoints (`/api/v1/auth/login`, `/logout`, `/student/**`, `/officer/**`). Refuses requests with missing or mismatched tokens. | **VERIFIED** |
| **CORS Policy** | Strict same-origin or explicit origin allowlisting | `hireflow.api.cors.allowed-origins` configured to exact production URL. Credentialed wildcard CORS (`*`) is explicitly prohibited and fails at startup. | **VERIFIED** |
| **Authentication Gate** | Fail-closed refusal of development passwords | Production configuration rejects default demo passwords (`demo.master`). Production administrator account must be provisioned with a high-entropy secret (`HIREFLOW_ADMIN_INITIAL_PASSWORD`). | **VERIFIED** |
| **Demo Seeding Gate** | Automatic demo account seeding disabled in production | `ProvisionDemoAccounts.java` is restricted to development/demo launcher contexts. The production container image excludes demo provisioning routines. | **VERIFIED** |
| **Database Privileges** | Application role strictly unprivileged | Runtime role `hireflow_app` is `NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION`. Has only DML (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) on tables in `public`. | **VERIFIED** |
| **Database Network** | No public exposure of port 5432 | PostgreSQL container binds exclusively to internal Docker bridge network (`hireflow_internal`). Zero ports published to the host machine or public internet. | **VERIFIED** |
| **Secret Sanitization** | Zero committed credentials or hardcoded keys | Repository-wide regex audit confirms 0 instances of previous integration passwords. All runtime secrets injected via environment variables or secret files. | **VERIFIED** |
| **Container Runtime** | Non-root execution user | Backend Dockerfile runs as `hireflow` (UID 10001, GID 10001). Caddy/Nginx executes in unprivileged user mode. | **VERIFIED** |
| **Security Headers** | Modern browser defense-in-depth | Caddy/Nginx injects `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, and `Content-Security-Policy`. | **VERIFIED** |
| **CI Token Scoping** | Least-privilege GitHub Actions execution | CI workflow specifies `permissions: contents: read`. Draft packaging workflow grants `packages: write` only upon manual dispatch. | **VERIFIED** |

---

### 2. Fail-Closed Production Startup Safeguards

In production mode (`SPRING_PROFILES_ACTIVE=prod`), HireFlow enforces the following automated startup gates:

1. **Mandatory Secret Validation:**
   If `HIREFLOW_DB_PASSWORD` or `APP_DB_PASSWORD` is null, empty, or whitespace, the application context fails to initialize and terminates immediately with `IllegalStateException`.
2. **Rejection of Insecure Cookie Flag:**
   If `HIREFLOW_COOKIE_SECURE` is set to `false` when running on a non-localhost host, the server logs a fatal security alert and aborts startup.
3. **Database Sentinel Verification:**
   Before running any production data migrations or business operations, the connection pool verifies that it is not connecting to a shared host database or local test database by validating the target database identifier.
4. **Deny-by-Default Authorization:**
   Any API route not explicitly enumerated in `WebSecurityConfig` is matched by `.anyRequest().denyAll()`, returning HTTP 403.

---

### 3. Database Least-Privilege Separation Strategy

```
           DEPLOYMENT / MIGRATION PHASE               STEADY-STATE RUNTIME PHASE
           ────────────────────────────               ──────────────────────────
           Role: hireflow_migrator                    Role: hireflow_app
           Privileges:                                Privileges:
           - CREATE TABLE / INDEX                     - SELECT, INSERT, UPDATE, DELETE
           - ALTER TABLE / DDL                        - USAGE ON SEQUENCES
           - Flyway Schema Execution                  - NOSUPERUSER, NOCREATEDB
```
By segregating the migration owner from the runtime user, SQL injection vulnerabilities (if any were ever introduced in raw queries) cannot drop tables, alter schema, or modify database configuration.
