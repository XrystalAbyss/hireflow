# HireFlow Full-Stack Integration & Operations Guide (CAP-8.5 Baseline)

This document provides authoritative, step-by-step instructions for provisioning, running, developing, building, and verifying the complete HireFlow system across React 19, Spring Boot, and PostgreSQL 16.

---

## 1. Prerequisites & Environment Requirements

- **Operating System**: Windows 11 / macOS / Linux
- **Java Runtime**: OpenJDK `21.0.x` or higher
- **Node.js Runtime**: Node.js `v20.0.0` or higher (Tested and validated on `v24.20.0`)
- **Package Manager**: npm `v10.0.0` or higher (Tested on `11.19.0`)
- **Relational Database**: PostgreSQL `16.x` with `btree_gist` extension (port `5432`)
- **Build Tools**: Apache Maven Wrapper (`./mvnw` or `.\mvnw.cmd`)

---

## 2. Isolated Disposable Test Database Provisioning

HireFlow enforces strict test database isolation. All integration tests and local live verification run exclusively against the authorized disposable test environment:

- **Target Database**: `hireflow_test`
- **Host**: `localhost:5432`
- **User**: `hireflow_user`
- **Flyway Migrations**: Automatically verified upon Spring Boot startup:
  - `V1__initial_schema.sql` (Core relational schema, btree_gist extension, PostgreSQL exclusion constraints)
  - `V2__user_accounts.sql` (User accounts, authentication constraints, student binding)
  - `V3__drive_closure.sql` (Manual drive closure flag and status index)

To verify the test schema from PowerShell:
```powershell
$env:PGPASSWORD='<db_password>'; & 'C:\Program Files\PostgreSQL\16\bin\psql.exe' -h localhost -U hireflow_user -d hireflow_test -c '\dt'
```

---

## 3. Real Backend Startup (Spring Boot API)

Start the authoritative Spring Boot backend on dedicated port **8081**:

```powershell
# From repository root
.\mvnw.cmd -f hireflow-api/pom.xml spring-boot:run
```

Verify backend health and Flyway validation:
```powershell
curl.exe http://localhost:8081/api/v1/health
```
Expected output:
```json
{
  "status": "UP",
  "databaseStatus": "CONNECTED",
  "schemaStatus": "FLYWAY_VALIDATED",
  "storageMode": "JDBC_TEST",
  "securityActive": true
}
```

### Verified Test Accounts

| Role | Username | Password | Notes |
| :--- | :--- | :--- | :--- |
| **Placement Officer** | `e2e.officer` | `OfficerPass123!` | Full administrative access to drives, shortlisting, and scheduling studio |
| **Student** | `e2e.student` | `StudentPass123!` | Alan Turing (STU-2026-001), CSE, CGPA 9.20 |

---

## 4. Frontend Application Startup & Toolchain

From the repository root:

```bash
# 1. Navigate to frontend directory
cd hireflow-app

# 2. Install dependencies
npm install

# 3. Start Vite development server
npm run dev
```

The frontend runs at `http://localhost:5173`:
- **Institutional Login View**: `http://localhost:5173/auth/login`
- **Student Opportunity Hub**: `http://localhost:5173/student/dashboard`
- **Placement Command Center**: `http://localhost:5173/officer/dashboard`
- **Interview Scheduling Studio**: `http://localhost:5173/officer/scheduling`
- **Offline Component Workshop**: `http://localhost:5173/workshop` (development mode only)

---

## 5. Toolchain & Test Commands

| Command | Suite | Expected Result |
| :--- | :--- | :--- |
| `npm run build` | TypeScript Strict Check + Vite Bundle | Production bundle generated with route-level code splitting (index: 426 kB, workshop excluded) |
| `npm run lint` | Oxlint Linter | 0 errors |
| `npm test -- --run` | Vitest Unit, Contract & Integration Suite | **141 tests passed** (9 test files) |
| `npx playwright test` | Playwright E2E & Visual QA Suite | **35 tests passed** (5 spec files across 5 viewports) |
| `.\mvnw.cmd -f hireflow-api/pom.xml test` | Spring Boot API Regression | **183 tests passed** (0 failures, 0 skipped) |
| `.\mvnw.cmd test` | Core Domain & CLI Maven Regression | **495 tests passed** (0 failures, 0 skipped) |

---

## 6. Reverse Proxy & Security Architecture

In `vite.config.ts`, all `/api` requests are proxied same-origin to Spring Boot port 8081:

```typescript
server: {
  port: 5173,
  proxy: {
    '/api': {
      target: 'http://localhost:8081',
      changeOrigin: true,
      secure: false,
    },
  },
}
```

### Security Transport Guarantees
1. **Cookie Isolation**: `JSESSIONID` cookies are HTTP-only and transmitted strictly via same-origin proxy headers.
2. **Session Rotation**: Spring Security performs session fixation protection on login, invalidating pre-login session IDs.
3. **CSRF Protection**: Tokens are retrieved via `GET /api/v1/auth/csrf` and injected into state-changing HTTP requests (`POST`, `PUT`, `DELETE`). Tokens are stored purely in memory and never written to `localStorage` or URL query params.
4. **Token Hygiene**: Scheduling preview tokens (`PREV-xxx`) exist solely in React memory during the active session. Once consumed, the backend enforces single-use replay prevention (HTTP 409). Expired tokens trigger HTTP 410, while evicted/restarted backend tokens return HTTP 404.

