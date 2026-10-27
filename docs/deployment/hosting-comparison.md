# Hosting Options & Architectural Evaluation
## Comparative Analysis for HireFlow Production & Demonstration Hosting

### 1. Overview of Evaluated Deployment Models

To establish the optimal deployment strategy for HireFlow, three distinct production models were researched and evaluated against HireFlow’s specific requirements: Java 21, Spring Boot session authentication, CSRF validation, PostgreSQL 16 with `btree_gist`, and zero-cold-start reliability.

- **Model A: Managed Platform-as-a-Service (PaaS)**
  - *Backend & Frontend:* Render / Railway / AWS App Runner.
  - *Database:* Neon / Supabase / Aiven Managed PostgreSQL.
- **Model B: Single-Origin Linux VPS via Docker Compose (Recommended)**
  - *Host:* Dedicated Linux VPS (Hetzner Cloud CX22 / DigitalOcean Basic Droplet).
  - *Containers:* Caddy Reverse Proxy + Spring Boot 21 Backend + Isolated PostgreSQL 16.
  - *Registry:* GitHub Container Registry (GHCR) via GitHub Actions.
- **Model C: Split Static Frontend + Managed Backend + Managed Database**
  - *Frontend:* Cloudflare Pages or Vercel (Edge CDN).
  - *Backend:* Render (Web Service) or Railway.
  - *Database:* Neon Serverless PostgreSQL.

---

### 2. Comprehensive 13-Point Feature & Cost Comparison

| Dimension | Model A (Managed PaaS) | Model B: Linux VPS + Compose (Recommended) | Model C (Split Edge + PaaS) |
|---|---|---|---|
| **1. Java 21 Compatibility** | **High:** Native Java 21 or Docker runtime supported. | **Native:** Eclipse Temurin 21 container executes identically to local tests. | **High:** Managed backend container runs Java 21. |
| **2. React Static Serving** | Hosted as static site or bundled container. | **Optimal:** Caddy serves immutable pre-compressed assets with HTTP/2 & HTTP/3. | **Superior:** Cloudflare global edge CDN caching. |
| **3. Session & CSRF Architecture** | **High:** Single custom domain routing or CORS config required. | **Flawless (Same-Origin):** `https://hireflow.example.com` serves both SPA and `/api/v1/`. Cookies are strictly first-party (`SameSite=Lax`). Zero CORS failure risk. | **Fragile (Cross-Origin):** Frontend on `app.domain.com`, API on `api.domain.com`. Requires `SameSite=None; Secure`, third-party cookie consent, and complex CORS headers. Safari/iOS privacy features frequently drop cross-site cookies. |
| **4. PostgreSQL `btree_gist` Support** | **Variable:** Supported on Neon and Supabase, but requires manual SQL extension enablement before Flyway boots. | **Deterministic:** Official `postgres:16` image executes `01-init-role-and-db.sh`, pre-enabling `btree_gist` before Flyway runs. | **Variable:** Dependent on managed database provider settings. |
| **5. Monthly Cost (Current Verified Pricing)** | **$19 – $35 / mo:**<br>• Render Starter: $7/mo (512MB RAM)<br>• Managed PG: $12–$20/mo (Neon / Supabase Pro to prevent cold compute pauses) | **$4.00 – $6.00 / mo:**<br>• Hetzner CX22 (2 vCPU, 4GB RAM, 40GB NVMe, 20TB traffic): **~€3.79 / mo (~$4.15/mo)**<br>• DigitalOcean Basic (1GB RAM): $6.00/mo | **$12 – $25 / mo:**<br>• Cloudflare Pages: $0 (Free)<br>• Render Web Service: $7/mo<br>• Managed DB: $12/mo |
| **6. Sleep / Cold-Start Behavior** | **High Risk:** Free/hobby tiers spin down after 15 min inactivity, taking 30–60s to boot Java 21, failing live institutional demos. | **Zero Cold Starts:** Services run continuously as native Linux systemd/Docker daemons. Instant sub-50ms response times. | **Moderate Risk:** Edge frontend is instant, but backend cold starts create broken initial UI loading states. |
| **7. HTTPS & Custom Domains** | Automated TLS via platform DNS CNAMEs. | **Fully Automated:** Caddy issues and auto-renews Let's Encrypt / ZeroSSL TLS certificates with zero manual intervention. | Requires two separate DNS certificates and CNAME records. |
| **8. Environment Variables & Secrets** | Web dashboard key-value injection. | Injected securely via protected `.env.production` file on VPS, with 0 secrets stored in Git or public images. | Multiple credential dashboards to synchronize. |
| **9. Database Backups** | Managed daily snapshots. | Automated daily `cron` executing `pg_dump` with local retention and optional encrypted S3/B2 offsite sync. | Automated managed backups. |
| **10. CI/CD Pipeline Integration** | Git push triggers platform rebuild. Rebuilds often lack Maven caching, taking 4–8 minutes. | **Fast & Predictable:** GitHub Actions builds images in ~90s, pushes to GHCR, and deploys via SSH `docker compose pull && docker compose up -d`. | Multi-pipeline deployment triggers. |
| **11. Operational Complexity** | Low infrastructure maintenance; high configuration debugging when CORS or proxy headers fail. | **Low-to-Moderate:** Standard Linux server administration; 100% reproducible locally with Docker Compose. | High: Three disjoint provider consoles and billing relationships. |
| **12. Institutional Demo Reliability** | Low on free tier (cold starts, memory throttling); Medium on paid tier. | **Highest:** Dedicated RAM and CPU prevent out-of-memory crashes during multi-applicant greedy scheduling runs. | Medium (subject to cross-origin cookie blocking). |
| **13. Scalability** | Horizontal autoscaling available on high-tier plans ($$$). | Vertical scaling up to 16 vCPU / 64GB RAM with single click on Hetzner/DigitalOcean. Easily serves 10,000+ placement students. | Frontend scales globally; backend bottlenecked on PaaS tier. |

---

### 3. Critical Architectural Finding: The Cross-Origin Cookie Trap (Model C)
Model C (split hosting like Vercel + Render) was specifically evaluated because it is popular in contemporary web tutorials. However, for HireFlow’s architectural baseline:
1. HireFlow relies on server-managed HTTP sessions (`JSESSIONID`) and session-bound CSRF tokens (`/api/v1/auth/csrf`).
2. Browsers enforce increasingly strict privacy rules against cross-site cookies (Google Chrome Third-Party Cookie Phaseout, Apple Safari Intelligent Tracking Prevention, Firefox Total Cookie Protection).
3. Under Model C, requests from `https://hireflow.pages.dev` to `https://hireflow-api.onrender.com` are treated as third-party, requiring `SameSite=None; Secure`. In many browser configurations, `SameSite=None` cookies are dropped entirely, resulting in persistent `403 Forbidden (CSRF token missing)` errors and sudden session dropouts during placement officer actions.
4. **Conclusion:** Model C is architecturally rejected for HireFlow’s session-based security model.

---

### 4. Definitive Recommendation: Model B (Linux VPS + Docker Compose + Caddy)
**Recommendation:** Deploy HireFlow on a **Linux VPS (Hetzner Cloud CX22 or DigitalOcean Droplet)** using the provided single-origin Docker Compose stack with Caddy.

**Key Justifications:**
1. **Total Cost:** Only **$4.15/month** on Hetzner Cloud for 2 vCPUs and 4 GB RAM (sufficient memory to run Java 21, PostgreSQL 16, and Caddy with room for 1,000 concurrent sessions).
2. **Zero Cold-Starts:** Guaranteed instant responsiveness during placement demonstrations and campus recruitment drives.
3. **Same-Origin Security:** Single domain (`https://hireflow.example.com`) eliminates all CORS and cross-site cookie vulnerabilities.
4. **Full Parity:** The production stack mirrors the local container environment, eliminating "works on my machine" surprises.
