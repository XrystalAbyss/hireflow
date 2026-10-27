# HireFlow Production Deployment Runbook
## Operator Guide: VPS Provisioning, Zero-Downtime Deployment & Maintenance

### 1. Host Server Preparation (Linux VPS)
Recommended specification: **Hetzner Cloud CX22** (2 vCPU, 4GB RAM, 40GB NVMe) or **DigitalOcean Basic Droplet** with **Ubuntu 24.04 LTS**.

#### 1.1 Base System Hardening & Firewall (UFW)
Connect as `root` or an administrative user with `sudo`:
```bash
# Update base system packages
sudo apt update && sudo apt upgrade -y

# Configure UFW Firewall (Default Deny)
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 80/tcp    # HTTP (TLS ACME Challenge & Redirect)
sudo ufw allow 443/tcp   # HTTPS (Production Traffic)
sudo ufw enable
```

#### 1.2 Install Docker Engine & Compose Plugin
```bash
# Install Docker prerequisites
sudo apt install -y ca-certificates curl gnupg

# Add official Docker GPG key & repository
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Enable and start Docker systemd service
sudo systemctl enable --now docker
```

---

### 2. Application Deployment Procedure

#### 2.1 Workspace Setup
Create a dedicated deployment directory `/opt/hireflow` owned by the operational user:
```bash
sudo mkdir -p /opt/hireflow /opt/hireflow/backups
sudo chown -R $USER:$USER /opt/hireflow
cd /opt/hireflow
```

#### 2.2 Secure Environment Injection
Create `/opt/hireflow/.env.production` following the template in `docs/deployment/environment-variables.example.md`:
```bash
nano /opt/hireflow/.env.production
chmod 600 /opt/hireflow/.env.production
```

#### 2.3 Pull and Launch Containers
```bash
# Authenticate to GitHub Container Registry (if using private images)
echo "$GITHUB_TOKEN" | docker login ghcr.io -u "$GITHUB_ACTOR" --password-stdin

# Pull latest release containers
docker compose -f docker-compose.prod.yml --env-file .env.production pull

# Start services in detached mode
docker compose -f docker-compose.prod.yml --env-file .env.production up -d --remove-orphans
```

---

### 3. Post-Deployment Verification Gate
Execute the automated preflight check:
```bash
# 1. Check all 3 containers are healthy
docker compose -f docker-compose.prod.yml --env-file .env.production ps

# 2. Probe public health endpoint through Caddy reverse proxy
curl -fs https://hireflow.example.com/api/v1/health

# 3. Check live logs for unexpected exceptions
docker compose -f docker-compose.prod.yml --env-file .env.production logs --tail=100 backend
```

---

### 4. Automated Daily Database Backups
Set up an automated daily backup job using `cron`:
```bash
crontab -e
```
Add the following entry (runs daily at 02:00 AM UTC):
```bash
0 2 * * * docker exec hireflow-prod-db pg_dump -U postgres_admin -d hireflow_prod -Fc | gzip > /opt/hireflow/backups/hireflow_prod_$(date +\%Y\%m\%d_\%H\%M\%S).dump.gz && find /opt/hireflow/backups -name "*.dump.gz" -mtime +14 -delete
```
This command:
1. Performs a non-blocking binary consistent database dump (`-Fc`).
2. Compresses the output with `gzip`.
3. Retains backups locally for 14 days, pruning older files automatically.
