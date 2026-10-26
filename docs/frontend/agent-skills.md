# HireFlow Agent Intelligence & Workspace Skills (CAP-8.1 Baseline)

HireFlow establishes specialized agent intelligence via six workspace skills located in `.agents/skills/`. Each skill complies with the Antigravity specification, containing YAML frontmatter (`name`, `description`), a narrowly defined purpose, activation triggers, reference documentation, concrete code examples, failure/rejection criteria, and verification procedures.

---

## 1. Skill Inventory & Governance Matrix

| Skill Name | Canonical Path | Narrow Purpose | Core Reference File |
| :--- | :--- | :--- | :--- |
| **`hireflow-design-director`** | `.agents/skills/hireflow-design-director/SKILL.md` | Oversee Institutional × Contemporary design philosophy, semantic tokens, and typography hierarchy. | `references/design-tokens.md` |
| **`hireflow-student-ux`** | `.agents/skills/hireflow-student-ux/SKILL.md` | Govern student Opportunity Hub, deterministic eligibility evaluation, and read-only academic dossiers. | `references/student-workflows.md` |
| **`hireflow-officer-ux`** | `.agents/skills/hireflow-officer-ux/SKILL.md` | Govern Placement Officer Command Center, candidate shortlisting, and Scheduling Studio timetables. | `references/officer-workflows.md` |
| **`hireflow-api-integration`** | `.agents/skills/hireflow-api-integration/SKILL.md` | Enforce adherence to the 40 Spring Boot endpoints, session fixation rotation, in-memory CSRF, and non-atomic publication. | `references/api-contract.md` |
| **`hireflow-visual-qa`** | `.agents/skills/hireflow-visual-qa/SKILL.md` | Audit layout stability across 5 viewports (390px, 768px, 1024px, 1440px, 1920px) and eliminate visual defects. | `references/visual-qa-checklist.md` |
| **`hireflow-accessibility`** | `.agents/skills/hireflow-accessibility/SKILL.md` | Enforce WCAG 2.2 Level AA compliance, focus trapping in dialogs, visible focus rings, and ARIA live regions. | `references/a11y-patterns.md` |

---

## 2. Shared Directives & Mandatory Review Decisions

All six skills adhere to the following decisions established during the CAP-8.0 review:

1. **Scheduling Publication Semantics**:
   Interview publication is **NOT** atomic across the entire batch. It uses per-session persistence and can partially succeed (HTTP 207 Multi-Status).
2. **Bulk Operations Policy**:
   No automatic client-side bulk shortlisting loops are permitted. The backend provides only individual shortlisting (`POST /api/v1/officer/applications/{id}/shortlist`). Deferred bulk operations must be documented as unsupported.
3. **Student Profile Authenticity**:
   Student academic records are backend-authoritative and read-only. External registrar integration claims or fake registrar seals are strictly prohibited.
4. **Token Privacy**:
   CSRF tokens and scheduling `previewId` must remain strictly in memory and never leak into URLs, query strings, console logs, analytics, persistent storage, or screenshots.
5. **CSRF Refresh on Session Rotation**:
   Upon successful login, Spring Security invalidates the session and rotates the CSRF token. The frontend must immediately refresh its in-memory CSRF state without blind retries on HTTP 403.
6. **WCAG 2.2 AA Enforcement**:
   Accessibility compliance must be measured and verified. Font sizes below 12px are prohibited.

---

## 3. Discoverability & Antigravity IDE Integration

The installed Antigravity environment natively discovers skills placed in `.agents/skills/<skill-name>/SKILL.md`. On session startup, the environment scans this workspace customization root and makes all six skills available for contextual invocation.
