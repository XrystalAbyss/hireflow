# HIREFLOW — CAP-8.6.2 PROTOTYPE RECOVERY & DISASTER RESTORATION RUNBOOK
## Emergency Recovery Protocols, Standby Restoration Drills & Baseline Reconstitution

**Checkpoint Version:** `v0.8.6.2-prototype-snapshot`  
**Target Branch:** `develop/stage5`  
**Historical CAP-7 Baseline:** `bdbf0e1dce7407ab9034f893712ccea8d7fced56`  
**Primary Repository:** `https://github.com/XrystalAbyss/hireflow`  
**Local Recovery Directory:** `C:\Users\astra\Desktop\Project\HireFlow-Recovery\`  

---

### 1. Recovery Asset Inventory

| Asset Name | Canonical Location / Identifier | Verification / Checksum |
|---|---|---|
| **Git Snapshot Tag** | `v0.8.6.2-prototype-snapshot` | Annotated immutable Git tag |
| **Pre-Verification Backup** | `C:\Users\astra\Desktop\Project\HireFlow-Recovery\pre_verification_snapshot_20261026_183217` | 1,637 files verified against `MANIFEST-SHA256.txt` |
| **Offline Git Bundle** | `C:\Users\astra\Desktop\Project\HireFlow-Recovery\git_bundles\hireflow_stage5_all_refs.bundle` | Verified via `git bundle verify` |
| **Database Logical Dump** | `C:\Users\astra\Desktop\Project\HireFlow-Recovery\database_backups\hireflow_cap8_it_demo_snapshot.dump` | 207,821 bytes, 461 catalog entries, SHA-256: `c9b043bd3ca37d5a24803993c2746f6d445c4a9df0bf71dce66dae9ac9364d58` |

---

### 2. System Prerequisites & Environment Verification

Before executing any recovery procedures, confirm the following core toolchains on the host machine:
1. **Java Runtime:** Eclipse Temurin OpenJDK 21 LTS (`java -version` -> `21.0.12.1+`).
2. **Node.js:** Node.js 20.19+, 22.12+, or 24.x LTS (`node -v` -> `v24.20.0` or `v22.x`).
3. **Container Engine:** Docker Desktop 29.8+ with Linux Container support (`docker info`).
4. **PostgreSQL Client Tools:** PostgreSQL 16 client binaries (`psql --version`, `pg_restore --version`).

---

### 3. Scenario Recovery Runbooks

#### Scenario A: A Later Feature Introduces Bugs or Regressions
*Goal: Revert to the verified prototype baseline cleanly without losing uncommitted exploratory work.*
> [!IMPORTANT]
> Never run `git reset --hard` to roll back changes, as this permanently destroys uncommitted local edits and stash entries.

1. **Option 1: Safe Git Worktree Isolation (Recommended)**
   Create a fresh, isolated parallel directory pointing directly to the immutable snapshot tag:
   ```powershell
   git worktree add -b recovery/stage5-clean ..\hireflow-verified-worktree v0.8.6.2-prototype-snapshot
   cd ..\hireflow-verified-worktree
   ```
2. **Option 2: Fresh Branch Creation**
   If working within the same workspace, branch off the snapshot tag:
   ```powershell
   git checkout -b feature/remediation-from-baseline v0.8.6.2-prototype-snapshot
   ```

---

#### Scenario B: Primary Working Directory Damaged or Accidentally Deleted
*Goal: Reconstruct the complete repository from GitHub or local archives.*

1. **Clone Fresh from GitHub:**
   ```powershell
   git clone https://github.com/XrystalAbyss/hireflow.git C:\Users\astra\Desktop\Project\hireflow-restored
   cd C:\Users\astra\Desktop\Project\hireflow-restored
   git checkout develop/stage5
   ```
2. **Re-Install Dependencies:**
   ```powershell
   # 1. Compile Java Core and Spring Boot API
   .\mvnw.cmd -B install -DskipTests -q
   .\mvnw.cmd -B -f hireflow-api/pom.xml compile -q

   # 2. Rebuild React Frontend Dependencies
   cd hireflow-app
   npm.cmd ci
   npm.cmd run build
   cd ..
   ```
3. **Reapply Local Demo Configuration:**
   If `.run/demo.local.json` was lost, regenerate it with a secure database password or read `scripts/demo/launcher-troubleshooting.md`.

---

#### Scenario C: Remote GitHub Repository Inaccessible or Offline
*Goal: Reconstitute the full Git repository and all commit history using the offline Git bundle.*

1. **Verify Git Bundle Integrity:**
   ```powershell
   git bundle verify "C:\Users\astra\Desktop\Project\HireFlow-Recovery\git_bundles\hireflow_stage5_all_refs.bundle"
   ```
2. **Clone Directly from Local Bundle:**
   ```powershell
   git clone "C:\Users\astra\Desktop\Project\HireFlow-Recovery\git_bundles\hireflow_stage5_all_refs.bundle" C:\Users\astra\Desktop\Project\hireflow-bundle-recovery
   cd C:\Users\astra\Desktop\Project\hireflow-bundle-recovery
   git checkout develop/stage5
   ```
3. **Attach to New Remote when Connectivity is Restored:**
   ```powershell
   git remote set-url origin https://github.com/XrystalAbyss/hireflow.git
   ```

---

#### Scenario D: Demonstration Database Corrupted or Erroneously Modified
*Goal: Restore demo accounts, interview rooms, and drive records from the verified snapshot.*

> [!CAUTION]
> Never restore directly into host port 5432! Ensure you are strictly targeting the isolated loopback port 55432.

1. **Stop Running Backend Processes:**
   ```powershell
   .\Stop-HireFlow.cmd
   ```
2. **Restore Logical Dump into Isolated Container:**
   ```powershell
   # Copy dump into the isolated container
   docker cp "C:\Users\astra\Desktop\Project\HireFlow-Recovery\database_backups\hireflow_cap8_it_demo_snapshot.dump" hireflow-cap8-postgres:/tmp/restore.dump

   # Execute non-destructive restoration via pg_restore inside container
   docker exec -i hireflow-cap8-postgres pg_restore -U postgres -d hireflow_cap8_it --clean --if-exists /tmp/restore.dump

   # Remove temporary file in container
   docker exec -i hireflow-cap8-postgres rm -f /tmp/restore.dump
   ```
3. **Verify Restored Database State:**
   ```powershell
   .\Check-HireFlow.cmd
   ```

---

#### Scenario E: Production Deployment Failure on VPS or Cloud
*Goal: Instant rollback of running application services without data destruction.*

1. **Roll Back Container Images:**
   Update the deployment tag in `/opt/hireflow/.env.production`:
   ```bash
   sed -i 's/HIREFLOW_IMAGE_TAG=.*/HIREFLOW_IMAGE_TAG=v0.8.6.2-prototype-snapshot/g' /opt/hireflow/.env.production
   ```
2. **Restart Standby Service:**
   ```bash
   docker compose -f docker-compose.prod.yml --env-file .env.production pull backend proxy
   docker compose -f docker-compose.prod.yml --env-file .env.production up -d --no-deps backend proxy
   ```
3. **Confirm System Health:**
   ```bash
   curl -fs https://hireflow.example.com/api/v1/health
   ```
   *Note: Application rollback does not undo backward-incompatible database DDL changes. Consult Flyway baseline tables before reverting schema.*

---

### 4. Post-Restoration Verification Suite
After any recovery procedure, run the following verification sequence to confirm 100% operational readiness:
```powershell
# 1. Java Offline Unit & Regression Suite (Expect: 71 passed)
.\mvnw.cmd test "-Duser.timezone=UTC" "-Dtest=SessionIdentityRegressionTest,GreedySchedulerTest,EntityValidationTest,CompanyServiceTest,PasswordHasherTest,CsrfTokenManagerTest,BruteForceThrottlerTest,AuthenticationServiceTest"

# 2. Frontend Offline Vitest Suite (Expect: 147 passed, 5 skipped, Exit code 0)
cd hireflow-app
npm.cmd run test
cd ..

# 3. Full Demo Health & Login Check (Expect: 13/13 PASS)
.\Check-HireFlow.cmd
```
