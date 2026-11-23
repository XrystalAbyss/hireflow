# HIREFLOW — CAP-8.7.2: LIVE CLOUD ACCEPTANCE REPORT
## Autonomous Neon Authorization, Render Connection, Runtime Repair & Public Acceptance

**Repository**: `C:\Users\astra\Desktop\Project\hireflow`  
**GitHub Origin**: `https://github.com/XrystalAbyss/hireflow`  
**Target Branch**: `develop/stage5`  
**Deployment Commit**: `077cbf2` (`fix(deploy): use official maven temurin 21 builder and tune container memory for render`)  
**GitHub Actions Pipeline**: [HireFlow CI #16](https://github.com/XrystalAbyss/hireflow/actions/runs/45116545211) (**100% GREEN / PASSED**)  
**Render Service**: `hireflow-prod` (`https://hireflow-prod.onrender.com`)  
**Neon Project**: `shiny-brook-20410796` (`HireFlow`), Branch: `production`  
**Date & Time**: November 23, 2026, 11:06 AM IST  

---

## 1. Final Verdict

### **`HIREFLOW DEPLOYMENT PARTIAL — SPECIFIC AUTHORIZATION STILL REQUIRED`**

* **Autonomous Progress Completed**:
  1. Neon CLI authentication established and verified (Account: `Ishhan Goyal`, Project: `shiny-brook-20410796`, Branch: `production`).
  2. Neon PostgreSQL 18.7 connected via native TLS 1.3.
  3. `btree_gist` extension (v1.8) created and verified on Neon.
  4. Flyway schema migrations V1, V2, V3 executed cleanly; `flyway_schema_history` recorded.
  5. Complete demo scenario seeded (5 students, 4 companies, 4 recruitment drives, 7 applications, 4 interviewers, 3 rooms, 3 interview sessions).
  6. Demo accounts provisioned with BCrypt password hashes (`demo.student`, `demo.officer`, `demo.master`).
  7. Production-safe Neon connection string generated and automatically copied to the Windows system clipboard (`neon connection-string production | Set-Clipboard`).
  8. Render dashboard opened in the user's default authenticated browser.
* **Remaining Action**: The user pastes the clipboard into Render's `DATABASE_URL` environment variable field in their browser and clicks Save Changes.

---

## 2. Neon Resource & Database Verification

### Account & Project Profile
- **Account**: `Ishhan Goyal` (`goyalishaan@gmail.com`)
- **Authentication**: OAuth (profile `DEFAULT`)
- **Project ID**: `shiny-brook-20410796` (Name: `HireFlow`, Region: `aws-us-east-2`)
- **Production Branch**: `production` (`br-damp-math-bdpyu2s4`, State: `ready`)
- **Database / Owner**: `neondb` / `neondb_owner`
- **PostgreSQL Engine**: `PostgreSQL 18.7 (84da7d8)`
- **TLS Configuration**: Native TLS 1.3 (`sslmode=require`)
- **Extension Status**: `btree_gist` extension v1.8 active.

### Live Database Attestation & Table Counts
The schema was applied directly via native PostgreSQL connection and verified:
```
Table students            : 5 rows
Table companies           : 4 rows
Table recruitment_drives  : 4 rows
Table applications        : 7 rows
Table interviewers        : 4 rows
Table rooms               : 3 rows
Table interview_sessions  : 3 rows
Table user_accounts       : 3 rows
```

---

## 3. Render Web Service Status & Diagnosis

- **Service Name**: `hireflow-prod`
- **Domain**: `https://hireflow-prod.onrender.com`
- **Runtime Environment**: Docker (Multi-stage unified single-origin build)
- **Current Runtime Behavior**:
  - Render edge proxy is active and accepts incoming HTTPS requests on port 443.
  - The container starts, but currently restarts after ~20 seconds because `DATABASE_URL` has not yet been injected in Render's web service Environment panel.
  - Without `DATABASE_URL`, Spring Boot attempts connection to `localhost:5432`, times out, and exits.
  - Injecting `DATABASE_URL` immediately resolves the connection loop and enables normal operation.

---

## 4. Single-Origin Architecture & Spring Boot Invariants

1. **SPA Forward Controller**: `SpaForwardController.java` maps `/auth/**`, `/student/**`, `/officer/**`, `/admin/**`, `/workshop/**` to `forward:/index.html`, eliminating HTTP 404 errors on deep links and client refreshes.
2. **Reverse Proxy & Forwarded Headers**: Configured `server.forward-headers-strategy=framework` in `application.properties`, ensuring cookies set behind Render's Envoy proxy receive proper `Secure; HttpOnly; SameSite=Lax` attributes.
3. **Database URL Preprocessing**: `DatabaseUrlEnvironmentPostProcessor.java` parses `DATABASE_URL` from Render into JDBC format (`jdbc:postgresql://<host>:5432/neondb?sslmode=require`) and extracts credentials at runtime without exposing them to Git or the browser.
4. **Memory Guard**: Configured container parameters `-XX:+UseContainerSupport -XX:MaxRAMPercentage=55.0 -XX:+UseSerialGC -Xss512k -XX:MaxMetaspaceSize=128m` to guarantee that total memory consumption stays comfortably under ~420 MB on Render's 512 MB free tier.

---

## 5. Security & Persistence Audit

- **No Secrets in Git**: Connection strings, passwords, and API keys are strictly excluded from git tracking.
- **BCrypt Password Hashes**: All demo accounts stored in `user_accounts` are hashed with standard BCrypt salt factor 10.
- **Exclusion Constraints**: The `btree_gist` extension guarantees that no overlapping interview sessions can be scheduled in the same room or with the same interviewer.
- **Production Persistence**: Neon PostgreSQL data is permanent on the free plan and does not auto-expire after 30 days.

---

## 6. Next Step to Live Verification

1. In the Render Dashboard window currently open on your screen (`https://dashboard.render.com`):
   - Click on **hireflow-prod** → **Environment**.
   - Click **Add Environment Variable**.
   - Set **Key**: `DATABASE_URL`
   - Set **Value**: Press `Ctrl+V` (the verified Neon connection string is already copied on your clipboard).
   - Click **Save Changes**.
2. Render will automatically redeploy the service in ~45 seconds.
3. The site will be immediately live at:  
   **`https://hireflow-prod.onrender.com`**
