# Production Environment Variables Specification
## Template & Secret Generation Guide for HireFlow Deployments

> [!CAUTION]
> Never commit actual production `.env.production` files, secrets, or certificates to Git.
> Store production credentials exclusively on the host server with `chmod 600` permissions.

### 1. Production Environment Configuration (`.env.production`)

```bash
# ==============================================================================
# HIREFLOW PRODUCTION ENVIRONMENT CONFIGURATION
# Copy to /opt/hireflow/.env.production on target host
# Permissions: chmod 600 /opt/hireflow/.env.production
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Host & Domain Routing
# ------------------------------------------------------------------------------
HIREFLOW_DOMAIN=hireflow.example.com
HTTP_PORT=80
HTTPS_PORT=443

# ------------------------------------------------------------------------------
# 2. Database Infrastructure (PostgreSQL 16)
# ------------------------------------------------------------------------------
POSTGRES_DB=hireflow_prod
POSTGRES_BOOTSTRAP_USER=postgres_admin

# Generate with: openssl rand -base64 32
POSTGRES_BOOTSTRAP_PASSWORD=REPLACE_WITH_HIGH_ENTROPY_BOOTSTRAP_SECRET

# Application Runtime User (Least Privilege)
APP_DB_USER=hireflow_app

# Generate with: openssl rand -base64 32
APP_DB_PASSWORD=REPLACE_WITH_HIGH_ENTROPY_APP_RUNTIME_SECRET

# ------------------------------------------------------------------------------
# 3. Spring Boot API Configuration
# ------------------------------------------------------------------------------
SPRING_PROFILES_ACTIVE=prod
PORT=8081

# JDBC Connection string to internal Compose database container
HIREFLOW_DB_URL=jdbc:postgresql://db:5432/hireflow_prod
HIREFLOW_DB_USER=hireflow_app
HIREFLOW_DB_PASSWORD=REPLACE_WITH_HIGH_ENTROPY_APP_RUNTIME_SECRET

# Hikari Connection Pool Sizing (Tuned for 4GB VPS)
HIREFLOW_DB_POOL_MAX=15
HIREFLOW_DB_POOL_MIN=3
HIREFLOW_DB_CONN_TIMEOUT=20000

# Cookie Security & CORS Allowlisting
HIREFLOW_COOKIE_SECURE=true
HIREFLOW_API_CORS_ALLOWED_ORIGINS=https://hireflow.example.com

# ------------------------------------------------------------------------------
# 4. Initial System Administrator Provisioning
# ------------------------------------------------------------------------------
# In production, this password is used once to initialize the master administrator
# account and must be rotated immediately via the portal.
# Generate with: openssl rand -base64 24
HIREFLOW_ADMIN_INITIAL_PASSWORD=REPLACE_WITH_SECURE_ADMIN_INITIAL_PASSWORD

# ------------------------------------------------------------------------------
# 5. JVM Ergonomics
# ------------------------------------------------------------------------------
JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Duser.timezone=UTC"
```

---

### 2. Secret Generation Runbook

Run these commands on the production Linux host to generate cryptographically secure values:

```bash
# Generate Bootstrap Database Secret
openssl rand -hex 24

# Generate Application Database Secret
openssl rand -hex 24

# Generate Initial Administrator Password
openssl rand -base64 18 | tr -dc 'a-zA-Z0-9!@#%^&*' | head -c 20
```
