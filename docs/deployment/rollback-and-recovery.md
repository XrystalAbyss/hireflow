# HireFlow Rollback & Disaster Recovery Runbook
## Emergency Incident Response, Version Rollback & Database Restoration

### 1. Incident Severity & Trigger Criteria
A rollback or emergency recovery procedure must be initiated if any of the following conditions occur post-deployment:
1. **Healthcheck Failure:** `/api/v1/health` fails to return HTTP 200 within 2 minutes of container start.
2. **Elevated Authentication Failures:** Widespread HTTP 403 / 500 errors during `/api/v1/auth/login` or session token retrieval.
3. **Data Integrity Anomalies:** Flyway migration fails with checksum mismatch or unapplied script errors.
4. **Out of Memory (OOM):** The JVM is terminated by the Linux OOM-killer due to unexpected memory leaks.

---

### 2. Container Version Rollback (Sub-60 Second Recovery)
Because HireFlow uses immutable version tags in GHCR, rolling back the application code requires zero compilation:

#### Step 1: Update Version Pin in `.env.production`
Edit `/opt/hireflow/.env.production` and point the image tag to the last known healthy release (e.g. from `v1.1.0` back to `v1.0.0`):
```bash
sed -i 's/HIREFLOW_IMAGE_TAG=v1.1.0/HIREFLOW_IMAGE_TAG=v1.0.0/g' /opt/hireflow/.env.production
```

#### Step 2: Restart Containers with Previous Version
```bash
docker compose -f docker-compose.prod.yml --env-file .env.production pull backend proxy
docker compose -f docker-compose.prod.yml --env-file .env.production up -d --no-deps backend proxy
```

#### Step 3: Verify Restoration
```bash
curl -fs https://hireflow.example.com/api/v1/health
```

---

### 3. Database Disaster Recovery & Full Restoration Procedure

If the primary database volume is corrupted or data is inadvertently destroyed, restore from the latest compressed snapshot:

#### Step 1: Stop Application Backend
Stop the Spring Boot backend to prevent incoming client writes during database restoration:
```bash
docker compose -f docker-compose.prod.yml --env-file .env.production stop backend
```

#### Step 2: Identify and Decompress Target Backup
```bash
# List available backups in chronological order
ls -lh /opt/hireflow/backups/*.dump.gz

# Select target file (e.g., hireflow_prod_20261026_020000.dump.gz)
gunzip -k /opt/hireflow/backups/hireflow_prod_20261026_020000.dump.gz
```

#### Step 3: Restore Database Schema & Data via `pg_restore`
```bash
# Copy uncompressed dump into database container
docker cp /opt/hireflow/backups/hireflow_prod_20261026_020000.dump hireflow-prod-db:/tmp/restore.dump

# Terminate active client connections to hireflow_prod
docker exec -i hireflow-prod-db psql -U postgres_admin -d postgres -c \
  "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'hireflow_prod' AND pid <> pg_backend_pid();"

# Drop and recreate clean target database
docker exec -i hireflow-prod-db psql -U postgres_admin -d postgres -c "DROP DATABASE IF EXISTS hireflow_prod;"
docker exec -i hireflow-prod-db psql -U postgres_admin -d postgres -c "CREATE DATABASE hireflow_prod OWNER postgres_admin;"

# Execute pg_restore
docker exec -i hireflow-prod-db pg_restore -U postgres_admin -d hireflow_prod --clean --if-exists --no-owner /tmp/restore.dump

# Re-grant unprivileged application permissions
docker exec -i hireflow-prod-db psql -U postgres_admin -d hireflow_prod -c "
  GRANT ALL ON SCHEMA public TO hireflow_app;
  GRANT ALL ON ALL TABLES IN SCHEMA public TO hireflow_app;
  GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO hireflow_app;
"

# Clean up temporary dump in container
docker exec -i hireflow-prod-db rm -f /tmp/restore.dump
```

#### Step 4: Restart Backend & Confirm Operational Health
```bash
docker compose -f docker-compose.prod.yml --env-file .env.production up -d backend
docker compose -f docker-compose.prod.yml --env-file .env.production logs -f backend
```
Verify that the backend connects cleanly, Flyway confirms validation, and `GET /api/v1/health` reports `status: UP`.
