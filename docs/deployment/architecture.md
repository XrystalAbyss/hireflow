# HireFlow Production Deployment Architecture
## Single-Origin Reverse Proxy, Spring Boot REST API, and Isolated PostgreSQL

### 1. Architectural Philosophy
HireFlow implements a monolithic, institutional-grade placement and interview management system. To minimize operational friction while maximizing security, performance, and accessibility, the production deployment is designed as a **Single-Origin Topology**:
- Public clients communicate exclusively with a single HTTPS endpoint (e.g. `https://hireflow.example.com`).
- The reverse proxy terminates TLS, applies institutional HTTP security headers, serves pre-compressed immutable React static assets, and proxies `/api/*` requests internally to the Spring Boot application server.
- The PostgreSQL database resides on an isolated private container network, bound to no public host interfaces.
- No Kubernetes, Redis, microservices, or distributed message brokers are introduced, satisfying the lean operational mandate while handling concurrent placement drive scheduling with deterministic sub-second latency.

```
                      INTERNET / USER BROWSER
                                 │
                   HTTPS :443    │  HTTP :80 (301 Redirect)
                                 ▼
         ┌───────────────────────────────────────────────────┐
         │          EDGE REVERSE PROXY (Caddy / Nginx)       │
         │  - Automated Let's Encrypt TLS (TLS 1.2 / 1.3)    │
         │  - Same-Origin Gateway: https://hireflow.example   │
         │  - Strict Content-Security-Policy & HSTS          │
         │  - Gzip / Zstandard Static Compression            │
         └─────────────┬───────────────────────┬─────────────┘
                       │                       │
      /assets/* & /    │                       │ /api/v1/*
      (Static SPA)     ▼                       ▼ (Reverse Proxy)
         ┌──────────────────────────┐   ┌──────────────────────────┐
         │   REACT 19 FRONTEND      │   │ SPRING BOOT 3.3.4 (API)  │
         │ - Static Assets (/srv)   │   │ - Eclipse Temurin 21 JRE │
         │ - Route-level code split │   │ - Port 8081 (Internal)   │
         │ - Client deep-link fallbk│   │ - Server-side Sessions   │
         │ - Workshop stripped out  │   │ - CSRF Token Repository  │
         │ - SameSite Lax Cookies   │   │ - GreedyScheduler Engine │
         └──────────────────────────┘   └─────────────┬────────────┘
                                                      │
                                                      │ JDBC :5432
                                                      │ (Private Bridge Network)
                                                      ▼
                                        ┌──────────────────────────┐
                                        │  POSTGRESQL 16 DATABASE  │
                                        │ - btree_gist Extension   │
                                        │ - Non-root App User Role │
                                        │ - Flyway Migrations      │
                                        │ - Dedicated Persistent   │
                                        │   Volume Storage         │
                                        └──────────────────────────┘
```

---

### 2. Network Topology & Port Allocations

| Node / Service | Internal Interface | External (Host) Interface | Protocol / Purpose |
|---|---|---|---|
| **Reverse Proxy** | `proxy:80`, `proxy:443` | `0.0.0.0:80`, `0.0.0.0:443` | Public HTTP/HTTPS gateway, TLS termination |
| **Backend API** | `backend:8081` | None (Unpublished) | Internal REST API, business logic, scheduling |
| **Database** | `db:5432` | None (Unpublished) | Internal PostgreSQL storage, ACID transactions |

**Security Benefit:** By never publishing the backend (`8081`) or database (`5432`) ports to the public host network:
1. Port scanners and automated internet bots cannot probe the Spring Boot runtime or database directly.
2. Cross-origin attacks are structurally eliminated because the browser communicates exclusively with the same origin (`https://hireflow.example.com`).
3. Session cookies (`JSESSIONID`) remain strictly first-party (`SameSite=Lax`, `Secure`, `HttpOnly`), immune to third-party cookie restrictions.

---

### 3. Frontend Production Packaging
- **Engine:** Vite 8 + React 19 + TypeScript.
- **Route Splitting:** 12 route-level dynamic chunks loaded lazily via `React.lazy` and `React.Suspense` with an accessible spinner (`SuspenseLoader`).
- **Asset Immutability:** Generated CSS and JS files include cryptographic content hashes (e.g. `index-kS0m6yo6.css`, `index-xHyvluDB.js`). Reverse proxy serves these with `Cache-Control: public, max-age=31536000, immutable`.
- **SPA Fallback:** All non-asset routes fallback to `/index.html` with `Cache-Control: no-cache, no-store, must-revalidate`.
- **Stripped Developer Tooling:** The development component workshop (`/workshop`) and demo simulation engines are erased at build time via `import.meta.env.DEV` conditions; `/workshop` redirects immediately to `/auth/login`.

---

### 4. Backend Production Packaging
- **Runtime:** Eclipse Temurin OpenJDK 21 LTS (Headless JRE Jammy).
- **Security Boundary:** Non-root system account `hireflow` (UID 10001).
- **Packaging Chain:** Root classes WAR module is compiled and installed into the Maven pipeline (`com.hireflow:hireflow:1.0.0:classes`), ensuring `hireflow-api` packages all domain models, repositories, and the deterministic `GreedyScheduler` engine into `BOOT-INF/lib/hireflow-1.0.0-classes.jar`.
- **Memory Management:** Container-aware JVM ergonomics configured via `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0`.
- **Graceful Shutdown:** Configured for 20-second graceful request draining upon `SIGTERM`.

---

### 5. PostgreSQL Storage & Extensions
- **Version:** PostgreSQL 16.
- **Required Extension:** `btree_gist` is mandatory for PostgreSQL exclusion constraints that prevent overlapping interview sessions on rooms and interviewers (`V1__initial_schema.sql`).
- **Security Model:** Segregated into a migration/schema-owner role and an unprivileged application role (`hireflow_app`) possessing only DML permissions (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) on tables and sequences in `public`.
