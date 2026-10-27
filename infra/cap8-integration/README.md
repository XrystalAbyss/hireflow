# HireFlow CAP-8.5.2 Isolated PostgreSQL 16 Integration Environment

## 1. Overview & Isolation Architecture

This directory provides the authoritative, fail-closed integration environment for HireFlow Stage 5 real-system acceptance.

```
Host (Windows 11)
├── 127.0.0.1:5432  --> Existing host PostgreSQL (hireflow_dev, hireflow_test, aurel_*) [STRICTLY PROTECTED]
└── 127.0.0.1:55432 --> Dedicated Docker container: hireflow-cap8-postgres
                        ├── Database: hireflow_cap8_it
                        ├── Role: hireflow_cap8_app (NOSUPERUSER, NOCREATEDB, NOCREATEROLE)
                        ├── Extension: btree_gist
                        ├── Sentinel: _integration_sentinel (HIREFLOW_CAP8_ISOLATED_INTEGRATION_2026)
                        └── Volume: hireflow_cap8_it_pgdata
```

## 2. Security & Privilege Boundaries

- **Network Binding:** Bound exclusively to loopback (`127.0.0.1:55432`). It is never exposed on `0.0.0.0` or accessible across LAN.
- **Role Isolation:** The application connects exclusively as `hireflow_cap8_app`, which is explicitly forbidden from superuser, role creation, or database creation privileges.
- **Fail-Closed Guard (`preflight-guard.ps1`):** Rejects any connection targeting port 5432, superuser roles, or databases without the sentinel marker.

## 3. Operations

### 3.1 Starting the Environment
```powershell
docker compose up -d
```

### 3.2 Executing the Safety Guard
```powershell
.\preflight-guard.ps1
```

### 3.3 Starting Spring Boot Against the Isolated Environment
```powershell
$env:HIREFLOW_DB_URL="jdbc:postgresql://127.0.0.1:55432/hireflow_cap8_it"
$env:HIREFLOW_DB_USER="hireflow_cap8_app"
$env:HIREFLOW_DB_PASSWORD="<application_role_password>"
.\mvnw.cmd -f hireflow-api/pom.xml spring-boot:run
```

### 3.4 Cleanup
```powershell
docker compose stop
# Note: Named volume hireflow_cap8_it_pgdata is preserved unless explicitly removed.
```
