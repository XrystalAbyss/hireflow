# HireFlow Staging Verification Runbook
## Step-by-Step Staging Environment Deployment, Verification & Tear-Down

### 1. Purpose & Staging Scope
The Staging Environment provides a complete, 100% production-equivalent testbed on a single isolated host or disposable cloud instance. It validates:
- Multi-stage Docker packaging for both frontend and backend.
- Automated Caddy TLS / HTTP-to-HTTPS redirect and reverse proxy routing.
- Real PostgreSQL 16 schema initialization via `01-init-role-and-db.sh`.
- Flyway database migration from baseline to latest schema version.
- End-to-end recruitment drive creation, student application, and greedy interview scheduling.

---

### 2. Pre-Deployment Staging Checklist
Before launching staging:
1. Ensure ports `80` and `443` (or staging alternates `8080` and `8443`) are free.
2. Confirm Docker Engine and Docker Compose V2 are installed.
3. Prepare a temporary staging environment file: `.env.staging`.
4. Ensure the working local demo database volume (`hireflow_cap8_it_pgdata`) is not targeted.

---

### 3. Step-by-Step Staging Execution

#### Step 1: Create Staging Configuration File
```bash
cat << 'EOF' > .env.staging
HIREFLOW_DOMAIN=staging.hireflow.local
HTTP_PORT=8080
HTTPS_PORT=8443
POSTGRES_DB=hireflow_staging
POSTGRES_BOOTSTRAP_USER=staging_bootstrap_admin
POSTGRES_BOOTSTRAP_PASSWORD=staging_bootstrap_secret_2026
APP_DB_USER=hireflow_staging_app
APP_DB_PASSWORD=staging_runtime_secret_2026
HIREFLOW_ADMIN_INITIAL_PASSWORD=StagingAdminInitialSecret2026!
HIREFLOW_COOKIE_SECURE=false
HIREFLOW_API_CORS_ALLOWED_ORIGINS=http://localhost:8080
EOF
```

#### Step 2: Build Staging Containers
```bash
docker compose -f infra/production/docker-compose.prod.yml --env-file .env.staging build
```

#### Step 3: Launch Staging Services in Detached Mode
```bash
docker compose -f infra/production/docker-compose.prod.yml --env-file .env.staging up -d
```

#### Step 4: Verify Service Health & Initialization Logs
```bash
# Check container status
docker compose -f infra/production/docker-compose.prod.yml --env-file .env.staging ps

# Inspect Database Initialization & Role Setup
docker logs hireflow-prod-db | grep "HireFlow"

# Inspect Backend Flyway Migrations
docker logs hireflow-prod-backend | grep "Flyway"
```

#### Step 5: Execute Staging Smoke Tests
Run lightweight HTTP probes against the staging proxy:
```bash
# 1. Verify Health Endpoint
curl -fs http://localhost:8080/api/v1/health
# Expected JSON: {"service":"hireflow-api","status":"UP","databaseStatus":"CONNECTED","schemaStatus":"FLYWAY_VALIDATED"}

# 2. Verify CSRF Token Retrieval
curl -fs http://localhost:8080/api/v1/auth/csrf
# Expected JSON: {"headerName":"X-CSRF-TOKEN","parameterName":"_csrf","token":"..."}

# 3. Verify Frontend Static Bundle Serving
curl -Is http://localhost:8080/ | grep "HTTP/1.1 200 OK"
```

---

### 4. Staging Tear-Down Procedure
To cleanly stop and discard staging resources without leaving orphaned containers:
```bash
# Stop containers and remove ephemeral staging network
docker compose -f infra/production/docker-compose.prod.yml --env-file .env.staging down -v

# Remove temporary configuration file
rm -f .env.staging
```
