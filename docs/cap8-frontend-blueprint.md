# HIREFLOW CAP-8.0: FRONTEND INTELLIGENCE & PRODUCT BLUEPRINT
**Institution-Grade Frontend Architecture, Product UX Research, Visual Identity & Agent Skills**

---

## 1. Workspace Safety Audit & Environment Baseline

| Parameter | Observed Value | Audit Evaluation |
| :--- | :--- | :--- |
| **Canonical Repository Root** | `C:/Users/astra/Desktop/Project/hireflow` | Verified via `git rev-parse --show-toplevel` |
| **Active Branch** | `develop/stage4` | Stable development branch |
| **HEAD Commit** | `bdbf0e1dce7407ab9034f893712ccea8d7fced56` | Official CAP-7 published commit |
| **Git Working Tree Status** | Clean (`nothing to commit, working tree clean`) | Unchanged prior to CAP-8 blueprint |
| **Node.js Environment** | `v24.20.0` | Modern LTS/Current |
| **NPM Environment** | `11.19.0` (invoked via `npm.cmd` on Windows) | Verified functional |
| **Existing Frontend Assets** | Legacy Jakarta EE JSP/Servlet views (`src/main/webapp/WEB-INF/views/`) and CSS | Zero pre-existing React / modern JS build files |
| **Unrelated Filesystem Audit** | Detected untracked empty directory placeholders (`Windows`, `Program Files`, `8086`, etc.) in the workspace | **Safety Isolation Confirmed**: Zero modifications, deletions, or moves permitted |

---

## 2. Machine-Grounded Backend API Contract & Compatibility Matrix

The Spring Boot backend (`hireflow-api`) defines exactly 40 machine-verified REST routes across 5 categories, protected by session cookies (`JSESSIONID`) and `X-CSRF-TOKEN` for state mutations.

### 2.1 Route Inventory Summary (40 Routes)
- **Authentication (4):** `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`, `GET /api/v1/auth/csrf`
- **Health (1):** `GET /api/v1/health`
- **Public Catalogs (4):** `GET /api/v1/companies`, `GET /api/v1/companies/{id}`, `GET /api/v1/drives`, `GET /api/v1/drives/{id}`
- **Student Workflows (9):** `GET /profile`, `GET /drives`, `GET /drives/{id}/eligibility`, `POST /applications`, `GET /applications`, `GET /applications/{id}`, `GET /dashboard`, `GET /interviews`, `GET /interviews/{id}`
- **Placement Officer Workflows (22):**
  - Company Management (4): `GET /companies`, `GET /companies/{id}`, `POST /companies`, `PUT /companies/{id}`
  - Recruitment Drive Management (5): `GET /drives`, `GET /drives/{id}`, `POST /drives`, `PUT /drives/{id}`, `POST /drives/{id}/close`
  - Application Review & Shortlisting (3): `GET /drives/{id}/applications`, `GET /applications/{id}`, `POST /applications/{id}/shortlist`
  - Resource Administration (6): `GET /interviewers`, `POST /interviewers`, `PUT /interviewers/{id}`, `GET /rooms`, `POST /rooms`, `PUT /rooms/{id}`
  - Interview Scheduling Engine (4): `POST /scheduling/preview`, `POST /scheduling/publish`, `GET /scheduling/published`, `GET /scheduling/published/{id}`

### 2.2 Feature Compatibility & Implementation Strategy
1. **Student Profile**: Read-only verification (`GET /student/profile`). The frontend displays official institutional verification badges and directs students to the Placement Cell for corrections.
2. **Deterministic Eligibility**: Powered by `GET /student/drives/{id}/eligibility`. Directly renders CGPA comparisons, branch matching, and discrete itemized reasons.
3. **Application Lifecycle**: Single-click application (`POST /student/applications`) and shortlisting (`POST /officer/applications/{id}/shortlist`). Unsupported transitions (e.g. arbitrary bulk status mutations or direct candidate rejections via REST) are omitted.
4. **Drive Termination**: Closed authoritatively via `POST /officer/drives/{id}/close`. Drive deletion is omitted to safeguard data lineage.
5. **Interview Scheduling**: Generated algorithmically via `POST /officer/scheduling/preview` using `GreedyScheduler` (zero DB mutation, 15-min countdown timer). Published transactionally via `POST /officer/scheduling/publish` handling `ALL_CONFIRMED` (200), `PARTIAL_SUCCESS` (207), and `NONE_CONFIRMED` (409). Manual drag-to-reschedule is rejected.

---

## 3. Product UX Research: Modern Institutional Systems

### 3.1 Industry Reference Review
We examined established patterns from leading higher education and enterprise platforms:
- **University Student Information Systems (SIS)** (Workday Student, Ellucian Banner, Canvas): Emphasize academic verification, unmistakable deadlines, student agency, and calm, unhurried typography.
- **Campus Recruitment & Placement Systems** (Handshake, Symplicity): Require high-density tabular reviews, strict branch/GPA eligibility checks, and clear institutional oversight.
- **Modern Applicant Tracking Systems (ATS)** (Greenhouse, Lever): High-velocity candidate shortlisting, clean audit trails, and deterministic workflow pipelines.
- **Enterprise Scheduling & Operations** (Google Calendar, Linear, Retool): Dense time-slot allocations, conflict transparency, and zero layout shift.

### 3.2 What HireFlow Adopts vs. What HireFlow Rejects
- **Adopted**:
  - Dense tabular scanning (36px-40px rows) with tabular figures for GPAs and metrics.
  - Transparent eligibility breakdown explaining *why* a candidate qualifies or fails.
  - Algorithmic preview verification with a visible countdown timer before committing database changes.
  - Granular multi-status publication reporting (distinguishing confirmed appointments from conflict rejections).
  - Calm institutional color harmony with high-contrast readable surfaces.
- **Rejected**:
  - **No fake university branding**: No invented college crests, fictional testimonials, or fake placement percentage claims.
  - **No AI SaaS clichés**: Zero neon glowing borders, zero purple/cyan mesh gradients, zero frosted-glass overlays over illegible content.
  - **No unsupported UI interactions**: Zero drag-and-drop timetable reordering (which contradicts backend algorithm token publication).
  - **No destructive phantom buttons**: Zero delete buttons for companies or drives where backend endpoints do not exist.

---

## 4. Visual Identity & Design Tokens System
**Direction: Institutional × Contemporary**

### 4.1 Foundations & Tokens
- **Core Palette**:
  - Midnight Navy: `#17324B` (Brand, Primary Headers, Primary Actions)
  - Academic Teal: `#176B69` (Accents, Links, Focus Rings, Active States)
  - Warm Ivory: `#F6F4EE` (Canvas, Surface Base)
  - Muted Brass: `#C6A46B` (Honors, High-CGPA highlights, Advisory Preview Token Badges)
  - Warm Slate Scale: `#FAF9F6` (`50`) through `#141310` (`900`)
- **Typography Pairing**:
  - Headings / Editorial: `Newsreader` or `Literata` (SIL Open Font License)
  - UI / Body / Tables: `Plus Jakarta Sans` or `Inter` (SIL Open Font License) with `tnum` tabular numbers
  - Monospace: `JetBrains Mono` (Apache 2.0) for IDs, preview tokens, and ISO timestamps
- **Spatial Grid & Elevation**:
  - 4px baseline rhythm (`4px`, `8px`, `12px`, `16px`, `20px`, `24px`, `32px`, `48px`).
  - Subtle, crisp border radii: `4px` (inputs/buttons), `6px` (cards/tables), `9999px` (status pills).
  - Diffused institutional elevation: soft navy-tinted shadows, zero neon glow.
- **Theme Strategy**:
  - Institutional Light-first default.
  - Dark mode supported via `data-theme="dark"` maintaining high-contrast 7:1 text readability.

---

## 5. Three Defining Experiences

### Experience A: Student Opportunity Hub (`/student/*`)
- **Student Dashboard**: Quick roll-up of verified CGPA, eligible drives, active applications, and upcoming interviews.
- **Drive Discovery & Eligibility Card**: Real-time evaluation of `GET /student/drives/{id}/eligibility` displaying GPA scorecard, branch eligibility, deadline status, and actionable application modal.
- **Application Status Pipeline**: Real-time stage badges (`APPLIED`, `SHORTLISTED`, `SCHEDULED`).
- **Confirmed Interview Agenda**: Chronological agenda view showing company, interviewer, assigned room, date, and time slot.

### Experience B: Placement Officer Command Center (`/officer/*`)
- **Partner Company & Drive Management**: Dense, sortable listings, creation modals, and authoritative drive closure actions.
- **Candidate Shortlisting Roster**: High-density table of applicants with GPA highlights and one-click `SHORTLISTED` status transitions.
- **Campus Resource Registry**: Management tables for corporate interviewers and campus room capacities.

### Experience C: Interview Scheduling Studio (`/officer/scheduling`)
- **Configuration Form**: Select drive, date, time window, slot duration (default 45 min), buffer (default 15 min), interviewers, and rooms.
- **Ephemeral Advisory Preview**: Generates in-memory timetable proposal with `PREV-...` token and 15-minute countdown timer. Displays allocation matrix and unscheduled candidate explanations.
- **Transactional Publication**: Confirms publication via `POST /scheduling/publish`. Safely handles:
  - `ALL_CONFIRMED` (200): All slots committed.
  - `PARTIAL_SUCCESS` (207): Partial commitment with retryable conflict details.
  - `NONE_CONFIRMED` (409): Zero appointments committed due to GiST resource exclusion clashes.

---

## 6. AI Coding-Agent Skills Architecture

Six specialized workspace skills have been created under `.agents/skills/` adhering to the Antigravity progressive disclosure standard:

```
.agents/skills/
├── hireflow-design-director/
│   ├── SKILL.md
│   └── references/design-tokens.md
├── hireflow-student-ux/
│   ├── SKILL.md
│   └── references/student-workflows.md
├── hireflow-officer-ux/
│   ├── SKILL.md
│   └── references/officer-workflows.md
├── hireflow-api-integration/
│   ├── SKILL.md
│   └── references/api-contract.md
├── hireflow-visual-qa/
│   ├── SKILL.md
│   └── references/visual-qa-checklist.md
└── hireflow-accessibility/
    ├── SKILL.md
    └── references/a11y-patterns.md
```

### Progressive Disclosure & Token Efficiency Strategy
- Each `SKILL.md` is strictly concise (< 50 lines), containing high-level behavioral directives and frontmatter descriptions.
- Detailed specifications, tokens, and contracts live in `references/*.md` subdirectories.
- Agents only read relevant reference files on demand, eliminating prompt bloat across turns.
