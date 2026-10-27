# HireFlow

**Campus Recruitment & Interview Scheduling Platform**

HireFlow is a **full-stack recruitment coordination system** for university placement teams and students. It connects recruitment drives, eligibility checks, applications, candidate shortlisting, interviewer and room resources, and interview scheduling in one workflow.

The current application combines **Java 21, Spring Boot, PostgreSQL, React and TypeScript**. The repository also retains its earlier **Java Swing desktop app, JDBC infrastructure, TCP networking, and Servlet/JSP portal**. Those earlier implementations are part of the project's architectural history—not substitutes for the full-stack product.

> **Project status (October 2026):** Final academic prototype and local demonstration release. The core app and browser workflows have substantial *reported local test coverage*; public hosting and institutional production adoption have **not** been established. The source code is available, but this repository does not represent an officially deployed university service.

## What HireFlow does

**For students**
- Browse recruitment drives and view company, role, deadline, minimum CGPA and eligible branches.
- Receive backend-evaluated eligibility information; submit eligible applications.
- Track application status and view published interview appointments.

**For placement officers**
- Maintain partner companies, drives, interviewers and rooms.
- Review applications and shortlist candidates.
- Generate **advisory, non-persistent** interview schedules.
- Review conflicts and unscheduled candidates, then explicitly publish confirmed appointments.
- Inspect confirmed interview timetables and scheduling outcomes.

**For administrators**
- Use role-protected administrative access. The current demonstration is an officer-managed recruitment model; it does **not** provide a separate corporate recruiter portal.

The scheduling engine uses a **deterministic greedy heuristic**, not artificial intelligence or machine learning. It checks candidate, interviewer, room, availability and time-window constraints; it does **not** guarantee a globally optimal timetable. PostgreSQL transaction handling and exclusion constraints provide an additional safety layer when appointments are published.

## Screens and product flow

The primary web experience is the React application in `hireflow-app/`, backed by the Java API in `hireflow-api/`.

| Student journey | Placement officer journey |
| --- | --- |
| Sign in → explore opportunities → check eligibility → apply → track status → see interviews | Sign in → manage drives → review applicants → shortlist → choose resources → preview schedule → publish → inspect timetable |

**Scheduling preview ≠ publication.** Preview produces a temporary plan without persisting appointments. Publication is a separate server-side operation that revalidates resources; it can report full success, partial success, conflicts, missing or expired previews. An officer must review the outcome rather than assuming every candidate was scheduled.

For verified screenshots and demo-specific descriptions, see [the frontend demo runbook](docs/frontend/demo-runbook.md) and the [faculty presentation checklist](docs/academic/HIREFLOW-FINAL-FACULTY-DEMO-CHECKLIST.md). These refer to a local demonstration environment; screenshots are not proof of a public deployment.

## Technology and architecture

| Layer | Technology | Responsibility |
| --- | --- | --- |
| Core language and domain | Java 21, OOP, generics, Collections | Entities, rules, abstractions and scheduling engine |
| Desktop client | Java Swing, JTable, CardLayout, SwingWorker | Preserved standalone desktop workflow and Java GUI demonstration |
| Persistence | JDBC, HikariCP, PostgreSQL 16, Flyway | Repositories, migrations, pooling, transaction and overlap protection |
| Legacy web | Jakarta Servlets/JSP, Tomcat | Earlier authenticated Java browser portal |
| REST backend | Spring Boot 4.1.1, Spring Security, Jakarta Validation | HTTP APIs, DTOs, session authentication, CSRF and authorization |
| Modern frontend | React 19, TypeScript, Vite, Tailwind CSS, TanStack Query, React Router, Zod | Student and placement officer browser interfaces |
| Quality tooling | JUnit, MockMvc, Vitest, Playwright, axe-core | Automated unit, integration, contract, browser and accessibility checks |
| Local demo | PowerShell/CMD launchers, Docker PostgreSQL | Reproducible Windows demonstration and isolated integration database |

**Important architecture distinctions**
- Swing is the historical **desktop GUI**; JDBC is the Java **database access API**; PostgreSQL stores the **persistent records**.
- React is the current **web presentation layer**; Java handles domain decisions and scheduling.
- Spring Boot and Hibernate/JPA **are present in the project**. The application also retains explicit JDBC repositories for academic and architectural reasons.
- Authentication uses **server-side sessions** (`JSESSIONID`) with Spring Security and CSRF protection—not a stateless JWT-only design.
- A legacy or fixture route does not automatically imply publicly available production functionality.

### Repository layout

```text
hireflow/
├── src/main/java/             # Java domain, scheduler, JDBC, Swing, TCP, legacy web
├── src/test/java/             # Core Java and integration tests
├── src/main/resources/db/     # Flyway database migrations
├── hireflow-api/              # Spring Boot REST backend
├── hireflow-app/              # React + TypeScript frontend
├── infra/cap8-integration/    # Guarded local PostgreSQL integration infrastructure
├── scripts/demo/              # Windows local demo launcher and checks
├── docs/academic/             # Code mapping, demo script, viva and faculty checklist
├── docs/frontend/             # Browser architecture, UX and demo notes
├── docs/deployment/           # Deployment planning, not proof of live hosting
└── .github/workflows/         # GitHub Actions configuration
```

### Evolution, not competing products

| Stage | Focus | Historical checkpoint |
| --- | --- | --- |
| 1 | Core Java, Collections and Swing | `v1.0.0-stage1` |
| 2 | JDBC, PostgreSQL, pooling, transactions, TCP | `v2.0.0-cap5` |
| 3 | Servlet/JSP web portal | `v3.0.0-cap6` |
| 4 | Spring Boot REST, Spring Security, session/CSRF | `develop/stage4` |
| 5 | React/TypeScript browser product integrated with Java API | `develop/stage5` |

Historical tags and branches remain useful for following the implementation step-by-step, but **the Stage-1 README on `main` is not an accurate overview of Stage 5** until the integration work is reviewed and merged.

## Run HireFlow locally

**Prerequisites:** JDK 21, Maven or the included Maven wrapper, Node.js compatible with the checked-in frontend dependencies, and PostgreSQL 16 for persisted operation. Docker is used by the project-specific isolated demo scripts.

**Windows demo (recommended when the isolated demo infrastructure has already been provisioned):**

```powershell
# Run from the repository root
.\Check-HireFlow.cmd
.\Launch-HireFlow.cmd
```

The configured local demonstration uses:
- Frontend: `http://127.0.0.1:5174`
- API health: `http://127.0.0.1:8081/api/v1/health`
- Isolated PostgreSQL: `127.0.0.1:55432 / hireflow_cap8_it`

**Do not point demo or destructive test scripts at an unrelated PostgreSQL installation.** The repository's integration procedures expect explicit database identity and isolation checks. Do not publish local database passwords or reuse weak demonstration accounts for external deployment.

**Build and inspect from source:**

```powershell
# Root Java project
.\mvnw.cmd -DskipTests package

# REST API (after root Java artifacts are available)
.\mvnw.cmd -f hireflow-api/pom.xml -DskipTests package

# React client
cd hireflow-app
npm ci
npm run typecheck
npm run lint
npm test
npm run build
```

For complete backend configuration, test database prerequisites, real browser integration and safe start/stop operations, read the project [demo runbook](docs/frontend/demo-runbook.md) and [integration infrastructure README](infra/cap8-integration/README.md). A successful frontend build alone does not start the API or database.

## Tests and verification

The CAP-8.6.4.1 academic sign-off **reported locally**:

| Test layer | Passed | Skipped |
| --- | ---: | ---: |
| Core Java, desktop and legacy web | 522 | 0 |
| Spring Boot API | 198 | 0 |
| React/Vitest | 147 | 5 |
| Playwright browser suites | 16 | 1 |
| **Total** | **883** | **6** |

Those figures are a **historical local test report**, not a guarantee that all tests pass on every machine or commit. Check current CI runs and reproduce the relevant test suites before changing the default branch or making a release claim. Automated axe checks cover specific detectable accessibility issues; they do not certify full WCAG conformance.

Test and release evidence: [CAP-8.6.4 test matrix](docs/finalization/CAP-8.6.4-TEST-MATRIX.md) · [finalization reports](docs/finalization/) · [academic handoff](docs/academic/).

## Security, data and product boundaries

- Spring Security enforces authenticated sessions, CSRF checks, roles and relevant resource ownership.
- Passwords are hashed; do not commit or reuse local demonstration credentials.
- Database constraints complement Java checks, especially for overlapping interviews.
- Demo seed data is **synthetic**; company names and candidates are illustrative and do not imply real recruiting partnerships or an authorized university deployment.
- Campus room codes in the demo are **configured scenario assumptions** unless separately confirmed by institutional stakeholders.
- Public deployment, institutional access, formal security certification and production-scale performance are **not claimed**.
- Multi-round interview pipelines, recruiter self-service and global schedule optimization are outside the current supported feature scope.

## Why the project matters

The product goal is practical: reduce repetitive placement-office coordination and make student interview allocations easier to review and explain. The engineering goal is to preserve a clear, auditable progression from Java Collections and Swing to JDBC/transactions, secure REST endpoints and a usable full-stack browser application.

For a faculty walkthrough, start with the [10-minute demonstration script](docs/academic/HIREFLOW-10-MINUTE-DEMO-SCRIPT.md) and the [faculty checklist](docs/academic/HIREFLOW-FINAL-FACULTY-DEMO-CHECKLIST.md). For a product review, start with the **student → officer → scheduling preview → confirmed timetable** workflow rather than the Java course rubric.

## Repository status

- **Current full-stack development branch:** [`develop/stage5`](../../tree/develop/stage5)
- **Code sign-off checkpoint:** `v0.8.6.4.1-academic-signoff`
- **Cloud deployment:** Not verified / not presented as live
- **Adoption:** University-use concept; no formal institution rollout claimed

Earlier stage documents remain intentionally preserved as historical records. The root README describes the **overall current platform**, not just one stage.

---

*HireFlow is an independently developed campus recruitment prototype. References to university workflows and sample companies describe the demonstration scenario, not official endorsements or production integrations.*
