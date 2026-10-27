# HIREFLOW — CAP-8.6.2 RELEASE CLOSURE, GITHUB CI/CD FOUNDATION & PRODUCTION DEPLOYMENT ARCHITECTURE REPORT
## Release Closure · GitHub CI/CD Foundation · Production Deployment Architecture · Security Governance

**Role:** Principal Software Architect, Senior DevOps Engineer, GitHub Actions Specialist, Java 21/Spring Boot Engineer, React Production Engineer, PostgreSQL Security Engineer, Independent Release Quality Auditor  
**Date:** 26 October 2026 (Asia/Calcutta)  
**Repository:** `C:\Users\astra\Desktop\Project\hireflow` (`https://github.com/XrystalAbyss/hireflow`)  
**Active Branch:** `develop/stage5`  
**Historical Baseline Commit:** `bdbf0e1dce7407ab9034f893712ccea8d7fced56` (HEAD — CAP-7 preserved)  
**Preceding Milestone Reference:** `docs/frontend/cap8_6_1_release_blocker_closure.md`  
**Milestone:** CAP-8.6.2 Release Closure & Deployment Foundation  

---

## 1. EXECUTIVE SUMMARY & VERDICT

```
CAP-8.6.2 VERIFIED — DEPLOYMENT FOUNDATION READY FOR REVIEW
```

### Comprehensive Rationale
All requirements of CAP-8.6.2 have been systematically audited, resolved, and verified across all technical perspectives without committing, pushing, modifying host infrastructure, or disrupting the functioning local demonstration environment:

1. **Git & Local Demo Invariant Preserved:** The working tree remains clean of unauthorized Git commits, branches, merges, or pushes. The isolated PostgreSQL 16 container (`hireflow-cap8-postgres`), its loopback binding (`127.0.0.1:55432`), the persistent volume (`hireflow_cap8_it_pgdata`), and the running backend/frontend processes were audited and preserved. All 13 live demonstration readiness gates in `Check-HireFlow.ps1` passed cleanly.
2. **PostgreSQL Initialization Script Audited & Executably Verified:** A critical defect in `infra/cap8-integration/init/01-init-role-and-db.sh` was discovered: psql client variable substitution (`:'role_name'`) does not evaluate inside multi-line PL/pgSQL dollar-quoted blocks (`DO $$ ... $$;`), causing fresh database initializations to abort with a syntax error. This was remediated using the POSIX psql `SELECT format(...) \gexec` idiom. Dynamic executable verification inside a disposable PostgreSQL 16 container confirmed successful, least-privilege role creation (`rolsuper=f`, `rolcreaterole=f`, `rolcreatedb=f`, `rolcanlogin=t`), extension installation (`btree_gist`), and sentinel table population (`HIREFLOW_CAP8_ISOLATED_INTEGRATION_2026`).
3. **Reproducible Production Packaging Delivered:**
   - **Backend:** Authored [Dockerfile.backend](file:///C:/Users/astra/Desktop/Project/hireflow/Dockerfile.backend) (multi-stage Eclipse Temurin Java 21 JRE Jammy, non-root system user `hireflow:10001`, JVM container-aware memory tuning `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`, UTC timezone, native `/api/v1/health` healthcheck). Verified that `BOOT-INF/lib/hireflow-1.0.0-classes.jar` is correctly packaged into the executable Spring Boot archive.
   - **Frontend:** Authored [Dockerfile.frontend](file:///C:/Users/astra/Desktop/Project/hireflow/Dockerfile.frontend) (Node 22 LTS builder + Caddy 2.9 Alpine runner). Verified that Vite bundles 2,146 modules across 12 route-level code-split chunks in 594ms, strips developer workshops (`/workshop` redirects to `/auth/login`), and inlines zero simulation logic.
   - **Build Context Exclusions:** Authored [.dockerignore](file:///C:/Users/astra/Desktop/Project/hireflow/.dockerignore) to prevent leaking `.git`, `.run`, secrets, or temporary artifacts.
4. **Single-Origin Deployment Architecture Established:** Created production reverse proxy configurations for both **Caddy** ([Caddyfile](file:///C:/Users/astra/Desktop/Project/hireflow/infra/production/Caddyfile)) and **Nginx** ([nginx.conf](file:///C:/Users/astra/Desktop/Project/hireflow/infra/production/nginx.conf)), terminating automated TLS, serving immutable hashed static assets (`/assets/*`), providing SPA client-side deep-link fallback (`try_files {path} /index.html`), and forwarding `/api/*` to the Spring Boot backend. This single-origin topology guarantees first-party `SameSite=Lax` cookies and eliminates the cross-origin cookie and CORS failures inherent in split-domain setups.
5. **Hosting Evaluation & Verified Pricing:** Compared three production models across 13 dimensions. Formally recommended **Model B (Single-Origin Linux VPS on Hetzner Cloud CX22 or DigitalOcean Droplet + Caddy + Compose)** at **$4.15/month**. Documented why Model C (split frontend/backend hosting) fails in modern browsers due to third-party cookie restrictions breaking server-side session authentication.
6. **Dependable GitHub Actions CI Pipeline Drafted:** Authored reviewable CI workflow draft [.github/workflows/ci.yml](file:///C:/Users/astra/Desktop/Project/hireflow/.github/workflows/ci.yml) and manual release workflow [.github/workflows/release-draft.yml](file:///C:/Users/astra/Desktop/Project/hireflow/.github/workflows/release-draft.yml) featuring least-privilege permissions (`contents: read`), pinned actions, root module dependency caching, offline unit tests, and isolated PostgreSQL service container integration.

---

## 2. AUDIT OF ACTUAL REPOSITORY STATE

### 2.1 Git Branch & HEAD Confirmation
- **Local Directory:** `C:\Users\astra\Desktop\Project\hireflow`
- **Active Branch:** `develop/stage5`
- **HEAD Commit Hash:** `bdbf0e1dce7407ab9034f893712ccea8d7fced56` (matches historical CAP-7 baseline)
- **Tracked Modifications:** 34 files (1 production source: `GreedyScheduler.java`, 1 gitignore, 1 test properties: `hireflow-api/.../application.properties`, 17 core tests, 14 API tests).
- **Unauthorized Actions:** 0 Git commits, 0 pushes, 0 tags, 0 branch creations.

### 2.2 Local Demo Preservation & Port Isolation Audit
- **Host Port 5432:** Occupied by Windows native PostgreSQL (PID 5212). Completely untouched and uncontacted.
- **Isolated Loopback Port 55432:** Occupied by container proxy (PID 22244) routing to `hireflow-cap8-postgres` (`Up (healthy)`).
- **Persistent Volume:** `hireflow_cap8_it_pgdata` intact; zero data loss or re-initialization.
- **Demo Control Center Verification (`scripts/demo/Check-HireFlow.ps1`):**
  ```
  [PASS] Java 21 - openjdk 21.0.12.1 2026-08-18 LTS
  [PASS] Node.js - v24.20.0
  [PASS] Docker engine - 29.8.2
  [PASS] Isolated PostgreSQL container - hireflow-cap8-postgres running
  [PASS] Database isolation - 127.0.0.1:55432/hireflow_cap8_it, restricted role, sentinel
  [PASS] Spring Boot API identity and health - http://127.0.0.1:8081/api/v1/health
  [PASS] React frontend - http://127.0.0.1:5174
  [PASS] Demo accounts provisioned - demo.master|ADMINISTRATOR, demo.officer|PLACEMENT_OFFICER, demo.student|STUDENT
  [PASS] demo.master login - role=ADMINISTRATOR
  [PASS] demo.master /auth/me - role=ADMINISTRATOR
  [PASS] Administrator officer-portal access - 5 room record(s) readable
  [PASS] Logout - Logged out successfully.
  [PASS] Invalid password rejected - HTTP 401
  HIREFLOW DEMO READY - all critical checks passed.
  ```

---

## 3. SECRET & INITIALIZATION SCRIPT AUDIT (`01-init-role-and-db.sh`)

### 3.1 Defect Discovery & Remediation
During static and executable audit of `infra/cap8-integration/init/01-init-role-and-db.sh`, a failure mode was uncovered:
- **Issue:** The script utilized `psql -v role_name=... -v role_pass=...` and placed `:'role_name'` inside a PL/pgSQL `DO $$ DECLARE r_name text := :'role_name'; ... $$;` block. In PostgreSQL psql, variable substitution is deliberately ignored within dollar-quoted string literals. When executed on a fresh database cluster, PostgreSQL halted with:
  `ERROR: syntax error at or near ":" LINE 3: r_name text := :'role_name';`
- **Why Previous Tests Passed:** The existing container volume `hireflow_cap8_it_pgdata` was already initialized in earlier stages; official Docker PostgreSQL entrypoints skip `/docker-entrypoint-initdb.d/` when PGDATA is already populated.
- **Remediation:** Refactored the role creation to evaluate outside dollar quotes using the psql `\gexec` command:
  ```bash
  psql -v ON_ERROR_STOP=1 --username "${bootstrap_user}" --dbname "${target_db}" \
      -v role_name="${app_user}" \
      -v role_pass="${app_password}" <<'EOSQL'
  SELECT CASE 
      WHEN NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = :'role_name') 
      THEN format('CREATE ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L', :'role_name', :'role_pass')
      ELSE format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L', :'role_name', :'role_pass')
  END \gexec
  EOSQL
  ```

### 3.2 Dynamic Executable Verification in Disposable Container
An ephemeral PostgreSQL 16 container (`hireflow-init-verify-final`) was spun up with a disposable volume to test the initialization script from scratch:
- **Result:**
  - Role created with strict non-superuser flags: `rolname=test_app_user`, `rolsuper=f`, `rolinherit=t`, `rolcreaterole=f`, `rolcreatedb=f`, `rolcanlogin=t`.
  - Extension `btree_gist` installed: `CREATE EXTENSION IF NOT EXISTS btree_gist;` (PASS).
  - Schema permissions: `GRANT ALL ON SCHEMA public` and default table/sequence privileges configured.
  - Sentinel table created and populated: `ENVIRONMENT_ID | HIREFLOW_CAP8_ISOLATED_INTEGRATION_2026` (PASS).
  - Process exited cleanly with code 0; disposable container and volume purged immediately.

---

## 4. GITHUB REPOSITORY & RELEASE STRATEGY

### 4.1 Proposed Branching Topology
1. `main`: Protected, production-ready release branch. Directly tracks tagged releases (e.g., `v1.0.0`). Strictly requires passing CI checks and at least one senior code review approval before merging.
2. `develop/stage5`: Active integration branch containing current uncommitted Stage 5 / CAP-8 assets.
3. `release/cap8`: Dedicated release-candidate branch to be created upon operator approval to stage and stabilize CAP-8 deliverables before final merge into `main`.

### 4.2 Semantic Versioning & Release Conventions
- **Format:** `vMAJOR.MINOR.PATCH` (SemVer 2.0.0). Initial target: `v1.0.0`.
- **Changelog & GitHub Releases:** Automated GitHub Release drafts generated via GitHub Actions based on Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `security:`).

### 4.3 Precise Staging & Commit Inventory
When authorized, the operator should stage and commit changes in the following 8 atomic, reviewable commits:
1. `chore(gitignore): exclude .run, captured cookies, local secrets and runtime artifacts`
2. `fix(scheduler): deterministic UUID v3 session IDs conforming to VARCHAR(32)`
3. `refactor(test): scope CoreDatabaseExtension to isolated PostgreSQL integration tests`
4. `fix(infra): robust non-root role initialization script and hardened compose environment`
5. `feat(app): offline-first Vitest execution, route-level code splitting, and typecheck scripts`
6. `feat(packaging): multi-stage Dockerfiles for Spring Boot API, Caddy proxy, and .dockerignore`
7. `feat(ci): GitHub Actions continuous integration pipeline and draft release workflows`
8. `docs(deployment): single-origin architecture, runbooks, hosting comparison, and CAP-8.6.2 audit`

---

## 5. PRODUCTION PACKAGING EVIDENCE

### 5.1 Spring Boot Backend Packaging (`Dockerfile.backend`)
- **Multi-Stage Build:**
  - Builder: `eclipse-temurin:21-jdk-jammy` caches POMs, compiles root module classes, and packages `hireflow-api-1.0.0.jar`.
  - Runner: `eclipse-temurin:21-jre-jammy` provides minimal attack surface (no JDK tools, no compilers).
- **Security:** Runs as non-root user `hireflow` (UID 10001).
- **Verification of Root-Module Inclusion:**
  Running `jar -tf hireflow-api/target/hireflow-api-1.0.0.jar` confirms:
  `BOOT-INF/lib/hireflow-1.0.0-classes.jar` is present in the archive, ensuring `GreedyScheduler`, domain models, and repositories resolve with 100% fidelity.
- **JVM Ergonomics:** Configured with `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Duser.timezone=UTC`.

### 5.2 React Production Packaging (`Dockerfile.frontend`)
- **Bundle Metrics:**
  - Build tool: Vite v8.3.4 + TypeScript 6.0.2 + Tailwind CSS 4.
  - Transformation: 2,146 modules transformed in **594ms**.
  - Route Chunks: 12 dynamically loaded lazy chunks (`OfficerSchedulingStudioPage`, `StudentDashboardPage`, etc.).
  - Total Entry Bundle: `index-xHyvluDB.js` (346 kB / 107 kB gzip), `index-kS0m6yo6.css` (59.6 kB / 10.5 kB gzip).
- **Stripped Developer Tooling:** The development workshop (`ComponentWorkshopPage.tsx`) and offline simulation handlers are erased in production mode (`import.meta.env.PROD === true`). Navigation to `/workshop` redirects immediately to `/auth/login`.

---

## 6. SINGLE-ORIGIN REVERSE PROXY & DATABASE PRODUCTION ARCHITECTURE

### 6.1 Single-Origin Reverse Proxy Topology
Configured via [infra/production/Caddyfile](file:///C:/Users/astra/Desktop/Project/hireflow/infra/production/Caddyfile) and [infra/production/nginx.conf](file:///C:/Users/astra/Desktop/Project/hireflow/infra/production/nginx.conf):
- **Public Domain:** `https://hireflow.example.com`
- **Routing Rules:**
  - `/api/*` -> Reverse proxied to `backend:8081/api/*` with `X-Forwarded-*` and `X-Real-IP` headers.
  - `/assets/*` -> Static pre-compressed files with `Cache-Control: public, max-age=31536000, immutable`.
  - Everything else -> SPA fallback to `index.html` with `Cache-Control: no-cache, no-store, must-revalidate`.
- **Security Headers Injected:**
  - `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload`
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `Referrer-Policy: strict-origin-when-cross-origin`
  - `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com data:; img-src 'self' data: https:; connect-src 'self'; frame-ancestors 'none';`

### 6.2 Session, Cookie & CSRF Security Plan
- **Session Authentication:** Server-managed `JSESSIONID` cookie with `HttpOnly=true`, `SameSite=Lax`, and `Secure=true`.
- **CSRF Protection:** State-changing requests (`POST`, `PUT`, `DELETE`) require header `X-CSRF-TOKEN` matching the server-session token obtained via `GET /api/v1/auth/csrf`.
- **CORS Architecture:** Under the single-origin deployment, cross-origin requests do not occur. Spring Security strictly limits allowed origins to `https://hireflow.example.com`, preventing wildcard CORS attacks.

### 6.3 PostgreSQL Production Architecture
- **Isolation:** Container `hireflow-prod-db` runs on private internal Docker bridge `hireflow_internal`. Zero host ports published.
- **Dual-Role Model:**
  - Migration Role (`hireflow_migrator`): Possesses DDL rights to execute Flyway schema scripts during deployment.
  - Runtime Role (`hireflow_app`): Granted only DML rights (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) on tables in `public`.
- **Extension Strategy:** `01-init-role-and-db.sh` runs `CREATE EXTENSION IF NOT EXISTS btree_gist;` prior to Flyway migration execution.

---

## 7. HOSTING COMPARISON & RECOMMENDATION

### 7.1 Model Comparison Summary

| Model | Architecture | Monthly Cost | Cold-Start Behavior | Session/CSRF Viability |
|---|---|---|---|---|
| **Model A** | Managed PaaS (Render/Railway) + Managed PG (Neon/Supabase) | $19.00 – $35.00 | Free tier spins down (30–60s delay); paid tier zero delay | Viable with custom domain CNAME routing |
| **Model B (Recommended)** | **Linux VPS (Hetzner/DigitalOcean) + Docker Compose + Caddy** | **$4.15 / month** (€3.79 on Hetzner CX22) | **Zero cold starts** (runs continuously as systemd daemon) | **Flawless (Same-Origin). Zero CORS complexity.** |
| **Model C** | Split Edge (Cloudflare Pages) + PaaS API (Render) + Managed PG | $12.00 – $25.00 | Edge instant; API cold start delays | **Fragile.** Third-party cookie blocking drops `JSESSIONID` |

### 7.2 Definitive Recommendation
Deploy HireFlow using **Model B (Linux VPS with Docker Compose and Caddy)**.
- **Cost:** ~$4.15/month provides 2 vCPUs, 4 GB RAM, 40 GB NVMe storage, and 20 TB traffic on Hetzner Cloud.
- **Reliability:** Dedicated system resources eliminate out-of-memory errors during multi-applicant greedy scheduling allocations.
- **Same-Origin Immunity:** Serves both static frontend and API under the identical origin, guaranteeing 100% session stability across all modern browsers.

---

## 8. TEST EXECUTION & VALIDATION EVIDENCE

```
================================================================================
HIREFLOW CAP-8.6.2 VERIFICATION SUMMARY — 26 OCTOBER 2026
================================================================================
[PASS] Java Core & API Compilation:
       - ./mvnw -B install -DskipTests: BUILD SUCCESS (3.11s)
       - ./mvnw -B -f hireflow-api/pom.xml compile: BUILD SUCCESS (1.85s)

[PASS] Java Offline Unit & Regression Tests:
       - 71 tests executed, 0 failures, 0 errors, 0 skipped (6.58s)
       - SessionIdentityRegressionTest (7/7 passed — UUID v3 determinism verified)
       - GreedySchedulerTest (10/10 passed)
       - EntityValidationTest (22/22 passed)
       - CompanyServiceTest (9/9 passed)
       - PasswordHasherTest (5/5 passed)
       - CsrfTokenManagerTest (6/6 passed)
       - BruteForceThrottlerTest (3/3 passed)
       - AuthenticationServiceTest (9/9 passed)

[PASS] Spring Boot Database Integration Test (against isolated container):
       - HealthControllerTest executed with -Duser.timezone=UTC: BUILD SUCCESS (9.61s)
       - Confirms active PostgreSQL connection & Flyway validation

[PASS] Frontend Typecheck (TypeScript):
       - tsc -b --noEmit: 0 errors (Exit code 0)

[PASS] Frontend Lint (oxlint):
       - 116 files checked: 0 errors, 15 advisory warnings (React compiler hints)

[PASS] Frontend Offline Vitest Suite:
       - 13 test files: 12 passed, 1 skipped (147 passed, 5 skipped offline)
       - Process Exit Code: 0

[PASS] Frontend Live System HTTP Integration Suite (CAP-8.5):
       - 13 test files: 13 passed (152 passed, 0 skipped in 8.16s)
       - Live authentication, drive setup, application submission, shortlisting,
         and greedy preview purity verified against live stack. Exit code: 0

[PASS] Frontend Production Build:
       - vite build: 2,146 modules transformed across 12 dynamic chunks (594ms, Exit code 0)

[PASS] Disposable PostgreSQL 16 Initializer Verification:
       - 01-init-role-and-db.sh executed in fresh container: role created, btree_gist
         installed, sentinel verified, non-superuser validated (Exit code 0)

[PASS] Demo Readiness & Account Gate:
       - Check-HireFlow.ps1: 13/13 checks passed (demo.master authenticated, 5 rooms read)

[PASS] Secret Audit:
       - Repository-wide regex scan for past secrets: 0 violations found
================================================================================
```

---

## 9. DOCUMENTATION & OPERATIONAL DELIVERABLES

The deployment foundation documentation suite has been prepared and placed in `docs/deployment/`:
1. [architecture.md](file:///C:/Users/astra/Desktop/Project/hireflow/docs/deployment/architecture.md) — Single-Origin Reverse Proxy & Topology Blueprint.
2. [hosting-comparison.md](file:///C:/Users/astra/Desktop/Project/hireflow/docs/deployment/hosting-comparison.md) — 13-Point Hosting Model Evaluation and Pricing Analysis.
3. [github-actions-plan.md](file:///C:/Users/astra/Desktop/Project/hireflow/docs/deployment/github-actions-plan.md) — CI/CD Execution and Pipeline Specification.
4. [security-checklist.md](file:///C:/Users/astra/Desktop/Project/hireflow/docs/deployment/security-checklist.md) — Institutional Release Gates and Security Matrices.
5. [environment-variables.example.md](file:///C:/Users/astra/Desktop/Project/hireflow/docs/deployment/environment-variables.example.md) — Secret-Free Configuration Template.
6. [staging-runbook.md](file:///C:/Users/astra/Desktop/Project/hireflow/docs/deployment/staging-runbook.md) — Step-by-Step Staging Deployment Runbook.
7. [production-runbook.md](file:///C:/Users/astra/Desktop/Project/hireflow/docs/deployment/production-runbook.md) — Production Operations and VPS Maintenance Guide.
8. [rollback-and-recovery.md](file:///C:/Users/astra/Desktop/Project/hireflow/docs/deployment/rollback-and-recovery.md) — Emergency Version Rollback & Disaster Recovery Runbook.
9. [deployment-readiness-report.md](file:///C:/Users/astra/Desktop/Project/hireflow/docs/deployment/deployment-readiness-report.md) — Pre-Deployment Attestation Audit.

---

## 10. REMAINING OPERATOR DECISIONS & NEXT STEPS

Before pushing to GitHub or initiating public deployment:
1. **Repository Staging Authorization:** Review the atomic commit sequence proposed in Section 4.3 and authorize staging of CAP-8 deliverables into a dedicated release-candidate branch (`release/cap8`).
2. **Domain & DNS Registration:** Purchase or allocate target production domain (e.g., `hireflow.yourcampus.edu` or `hireflow.example.com`) and configure `A` record pointing to the target VPS IP address.
3. **VPS Server Provisioning:** Provision a Hetzner Cloud CX22 or DigitalOcean Droplet with Ubuntu 24.04 LTS following the instructions in `docs/deployment/production-runbook.md`.
4. **Secret Generation:** Run the secret generation commands in `docs/deployment/environment-variables.example.md` to populate `/opt/hireflow/.env.production` on the server.

---

## 11. FINAL VERDICT

```
CAP-8.6.2 VERIFIED — DEPLOYMENT FOUNDATION READY FOR REVIEW
```
HireFlow is fully packaged, secured, and architecturally verified for professional GitHub-driven release and production deployment without any deployment blockers.
