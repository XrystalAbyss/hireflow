# HIREFLOW — CAP-8.6.4
## Initial Repository Forensics, Quality Audit & Risk Register

**Milestone:** CAP-8.6.4 — Final Product Excellence, Engineering Hardening & Academic Release  
**Auditor / Roles:** Principal Java Architect, QA Lead, PostgreSQL Engineer, Security Auditor, UX Lead  
**Audit Timestamp:** 2026-10-26T20:45:00+05:30  
**Baseline Commit Anchor:** `fb7d994a8f8db1b6b7e207096ee042339e7df2b9` (`develop/stage5`, `v0.8.6.3-university-season-snapshot`)  
**Preserved Prior Snapshot:** `5af02432c1457b6697d731bc730d4310ee321688` (`v0.8.6.2-prototype-snapshot`)  

---

### 1. Repository Starting State

- **Branch:** `develop/stage5` (synchronized with `origin/develop/stage5` at `fb7d994`).
- **Working Tree:** Clean, 0 untracked files, 0 uncommitted changes.
- **Git History Integrity:** Linear milestone progression from Stage 1 (`66826c1`) through Stage 5 CAP-8.6.3 (`fb7d994`).
- **Protected Recovery Tags:**
  - `v0.8.6.2-prototype-snapshot` ➔ `5af02432c1457b6697d731bc730d4310ee321688` (Verified)
  - `v0.8.6.3-university-season-snapshot` ➔ `fb7d994a8f8db1b6b7e207096ee042339e7df2b9` (Verified)
- **Database Status:**
  - Container: `hireflow-cap8-postgres` (healthy on `127.0.0.1:55432`)
  - Database: `hireflow_cap8_it`
  - Host Port 5432: Strictly untouched / disconnected
  - Verified Initial Logical Dump: `hireflow_cap8_it_pre_cap864_20261026_204011.dump` (281,269 bytes, SHA-256 `420D2285976E97265C27D3C175E87F2E9B17E0F3D6E52B367EB006A3C0308DE0`, 607 catalog entries)

---

### 2. Verified Runtime Entity Counts (Live PostgreSQL `hireflow_cap8_it`)

| Entity Table | Baseline (CAP-8.6.2) | Season Fixture (U26) | Verified Live Total |
|---|---:|---:|---:|
| `companies` | 6 | 6 | **12** |
| `recruitment_drives` | 6 | 6 | **12** |
| `students` | 7 | 65 | **72** |
| `applications` | 8 | 84 | **92** |
| `interviewers` | 6 | 12 | **18** |
| `rooms` | 5 | 36 | **41** |
| `interview_sessions` | 6 | 15 | **21** |

---

### 3. Identified Defects, Inconsistencies & Technical Debt

| ID | Category | Severity | Description | Root Cause / Impact | Target Remediation |
|---|---|:---:|---|---|---|
| **R-01** | Documentation / Fixture Drift | **P0** | Demo guide (`CAP-8.6.3-UNIVERSITY-RECRUITMENT-DEMO.md`) references fictional company names ("CloudScale", "Apex Financial", etc.) that do not match committed SQL fixture and PostgreSQL runtime records ("Aether Cloud Networks", "Cognitive Robotics Labs", etc.). | Copy-paste documentation drift between draft and final fixture commit. High confusion during live presentation. | Update demo manual, test scripts, and reports to match authoritative SQL fixture and runtime DB. |
| **R-02** | Room Policy Authorization | **P1** | `UniversityCampusRoomPolicy.isLegacyOrTestRoom()` accepted any room ID starting with `RM-` or `E2E-`, allowing officers to register arbitrary non-campus room codes through `POST /api/v1/officer/rooms`. | Over-permissive prefix check instead of an exact set of known legacy rooms. | Enforce that newly registered rooms must strictly match the approved 36 campus engineering room codes (`KF02`..`LT10`), while legacy rooms are restricted to an explicit immutable set of historical IDs. |
| **R-03** | Reset Safety / Scope | **P1** | `university-season-reset.sql` used lexical range filters (`WHERE id >= 'APP-U26-001' AND id <= 'APP-U26-084'`). Lexical string comparisons in SQL can inadvertently match unexpected keys or miss non-padded identifiers. | Fragile string-range deletion predicates. | Replace with explicit `IN (...)` manifests and add foreign-key safety check asserting that no user sessions depend on target records before deletion. |
| **R-04** | API MockMvc Test Coverage | **P1** | `OfficerInterviewResourceController` had no dedicated standalone MockMvc test suite in `hireflow-api/src/test/`. | Room controller tests were primarily covered via integration rather than isolated MockMvc contract tests. | Add `OfficerInterviewResourceControllerMockMvcTest` testing room registration, 422 rejections for unapproved/faculty/lab codes, and capacity validation. |
| **R-05** | Frontend Search/Filter Scope | **P2** | Student application list and officer candidate filters operated client-side across the currently loaded page rather than across the full dataset. | Paginated data was filtered post-fetch in component memory. | Implement server-side filtering or clarify search bounds in UI so users are never misled into thinking a 10-row page represents the entire search domain. |
| **R-06** | Scheduling Studio UX / Backlog Clarity | **P2** | Scheduling Studio lacked clear inline explanations of why specific shortlisted candidates were not scheduled (e.g. interviewer availability limits, time window constraints). | Unscheduled candidates were listed in preview but without granular cause badges. | Add explicit reason pills and capacity explanations in the preview cards. |
| **R-07** | Accessibility (WCAG 2.2 AA) | **P2** | Mobile viewport (360px–390px) table scrolling and modal focus trapping needed verification across all updated officer and student pages. | High information-density tables require responsive card fallbacks or dedicated horizontal swipe affordances. | Audit viewports 360px, 390px, 768px, 1024px, 1440px, 1920px. Ensure keyboard accessibility and accessible landmarks. |
| **R-08** | Academic Presentation Depth | **P2** | Need comprehensive concept mapping connecting Java Collections, Generics, Swing EDT/SwingWorker, JDBC, and HikariCP to exact code lines for the external examiner viva. | Students/evaluators need crisp traceability from academic syllabus concepts to real Java 21 files. | Deliver `HIREFLOW-ADVANCED-JAVA-CONCEPT-MAPPING.md` and 50-question viva prep guide in `docs/academic/`. |

---

### 4. Risk Prioritization & Action Plan

- **Phase 2 (Immediate):** Resolve R-01 (Documentation sync), R-02 (Legacy room policy narrowing), R-03 (Reset script exactness), R-04 (MockMvc test addition).
- **Phases 3–5 (Architecture, DB, Security):** Audit Flyway, HikariCP, CSRF, and session isolation.
- **Phases 6–8 (UX, Visual, Accessibility):** Polish Scheduling Studio, candidate review, mobile responsive cards, and contrast.
- **Phases 9–11 (Performance & Academic):** Benchmark latency, document greedy scheduler Big-O, map Java concepts to code.
- **Phases 12–18 (Verification & Release):** Run test suites, browser e2e journeys, create Git bundle, and tag `v0.8.6.4-final-academic-prototype`.
