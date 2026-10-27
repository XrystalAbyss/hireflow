# GitHub Actions CI/CD Pipeline Plan
## Continuous Integration, Quality Governance, and Safe Deployment Workflows

### 1. Design Principles
The HireFlow CI/CD architecture is engineered for **dependability, least-privilege security, and complete reproducibility**:
1. **Offline-First Quality Gates:** Fast, deterministic unit tests, linting, and typechecks run without external network or database dependencies in under 60 seconds.
2. **Deterministic Build Order:** The root module (`com.hireflow:hireflow:1.0.0:classes`) is strictly installed before compiling the dependent `hireflow-api` module.
3. **Isolated Integration Services:** Heavyweight database integration tests execute in an ephemeral PostgreSQL 16 container using dynamically generated CI credentials, completely independent of developer workstations.
4. **Least-Privilege Token Permissions:** The default workflow grants only `permissions: contents: read`. Container image publishing requires manual dispatch and utilizes the automatically scoped `GITHUB_TOKEN`.
5. **No Automatic Push Deployments:** Continuous deployment to production is never triggered by raw git pushes; releases require explicit human authorization, protected branches, and semantic version tagging.

---

### 2. Workflow Structure Overview

```
                        PULL REQUEST / MANUAL DISPATCH
                                      │
              ┌───────────────────────┴───────────────────────┐
              ▼                                               ▼
   ┌───────────────────────┐                       ┌───────────────────────┐
   │   frontend-quality    │                       │    backend-offline    │
   │ - Node 22 LTS (npm ci)│                       │ - Java 21 Temurin     │
   │ - tsc -b --noEmit     │                       │ - Maven Cache         │
   │ - oxlint (116 files)  │                       │ - ./mvnw install root │
   │ - vitest run (offline)│                       │ - API compile         │
   │ - vite build          │                       │ - 71 offline tests    │
   └──────────┬────────────┘                       └───────────┬───────────┘
              │                                                │
              ├───────────────────────┬────────────────────────┤
              ▼                       ▼                        ▼
   ┌───────────────────────┐   ┌───────────────────────────────────────────┐
   │    security-audit     │   │           postgres-integration            │
   │ - Forbidden secret    │   │ - Dedicated postgres:16 service container │
   │   regex scanning      │   │ - Fresh init: 01-init-role-and-db.sh      │
   │ - npm audit           │   │ - btree_gist & sentinel verification      │
   │                       │   │ - Live Spring Boot Database Integration   │
   └───────────────────────┘   └───────────────────────────────────────────┘
```

---

### 3. Detailed Job Specifications

#### Job 1: `frontend-quality`
- **Environment:** `ubuntu-latest`, Node.js 22.x LTS.
- **Cache:** `~/.npm` keyed to `hireflow-app/package-lock.json`.
- **Commands:**
  ```bash
  cd hireflow-app
  npm ci
  npm run typecheck    # 0 errors
  npm run lint         # 0 errors (oxlint)
  npm test             # 147 passed, 5 skipped (offline Vitest exit code 0)
  npm run build        # Vite production bundling
  ```

#### Job 2: `backend-offline`
- **Environment:** `ubuntu-latest`, Eclipse Temurin JDK 21.
- **Cache:** `~/.m2/repository` keyed to root and subproject `pom.xml`.
- **Build Sequence:**
  ```bash
  ./mvnw -B install -DskipTests -q
  ./mvnw -B -f hireflow-api/pom.xml compile -q
  ./mvnw -B test -Duser.timezone=UTC -Dtest=SessionIdentityRegressionTest,GreedySchedulerTest,EntityValidationTest,CompanyServiceTest,PasswordHasherTest,CsrfTokenManagerTest,BruteForceThrottlerTest,AuthenticationServiceTest
  ./mvnw -B -f hireflow-api/pom.xml package -DskipTests -q
  ```

#### Job 3: `security-audit`
- **Environment:** `ubuntu-latest`.
- **Secret Scanning:** Scans tracked and untracked repository paths for historical leak patterns (`<REDACTED_APP_SECRET_2026>`, `<REDACTED_BOOTSTRAP_SECRET>`). Fails closed if detected.
- **Dependency Audit:** Audits npm dependencies for known high-severity vulnerabilities.

#### Job 4: `postgres-integration`
- **Service Container:** `postgres:16` on loopback port `55432:5432`.
- **Ephemeral Credentials:** Injected via GitHub Actions runner environment:
  - `POSTGRES_USER: ci_bootstrap_admin`
  - `POSTGRES_PASSWORD: ci_bootstrap_secret_pass`
  - `POSTGRES_DB: hireflow_cap8_it`
  - `APP_DB_USER: hireflow_cap8_app`
  - `APP_DB_PASSWORD: ci_runtime_app_secret_pass`
- **Execution:**
  1. Executes `infra/cap8-integration/init/01-init-role-and-db.sh` using `psql` to create the unprivileged application role, initialize `btree_gist`, and set the sentinel.
  2. Runs real Spring Boot PostgreSQL integration tests:
     ```bash
     ./mvnw -B -f hireflow-api/pom.xml test \
       -Duser.timezone=UTC \
       -Dhireflow.it.pass="ci_runtime_app_secret_pass" \
       -Dtest=HealthControllerTest,AuthControllerMockMvcTest,RoleAuthorizationIntegrationTest
     ```

---

### 4. Release Workflow Plan (`release-draft.yml`)
- Triggered exclusively via **GitHub Actions Manual Dispatch (`workflow_dispatch`)**.
- Requires operator to specify the semantic release tag (e.g., `v1.0.0-rc1`) and a boolean flag `publish_to_ghcr`.
- Builds multi-stage production Docker images using `docker/build-push-action@v5`.
- Authenticates to `ghcr.io` using the repository's short-lived `GITHUB_TOKEN`.
- Emits image tags:
  - `ghcr.io/xrystalabyss/hireflow-backend:v1.0.0-rc1`
  - `ghcr.io/xrystalabyss/hireflow-frontend:v1.0.0-rc1`
