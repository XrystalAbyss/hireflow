# HireFlow demo readiness — 22 October 2026 (Asia/Calcutta)

**Verdict: Ready for the 23 October local LIVE demo on the tested Windows machine**, provided `Launch-HireFlow.cmd` reaches READY and `Check-HireFlow.cmd` passes immediately before the presentation. The one-click launcher now opens LIVE only and stops if Docker/WSL is unavailable. No commit, push, merge, tag, or release was made.

## Actual verification

| Layer | Executed result | Evidence |
| --- | --- | --- |
| Isolated PostgreSQL | Preflight passed: `postgres:16` via `127.0.0.1:55432`, database `hireflow_cap8_it`, non-superuser role and sentinel. Port 5432 was not targeted. | `.run/evidence/check-after-restart.log`; preflight console output |
| Core Java | 499 passed, 0 failed/errors/skipped. | `.run/evidence/core-tests-recovered.log` |
| API Java | 186 passed, 0 failed/errors/skipped after repairing a test fixture assumption. | `.run/evidence/api-tests-corrected.log` |
| Frontend unit and real HTTP | 150 passed (145 unit plus 5 genuine HTTP integration tests) with explicit isolated opt-in. The HTTP test observed simultaneous publication statuses 200/409 and one persisted scheduled session. | `.run/evidence/frontend-all-demo-final.log`, `.run/evidence/http-persistence-final.json` |
| Build and lint | Production build passed; lint exited 0 with existing advisory warnings. | `.run/evidence/build-demo-final.log`, `.run/evidence/lint-demo-final.log` |
| Full LIVE browser | 40-step Student → Officer → Scheduling → Student journey passed using the demo accounts. Administrator browser subtest was intentionally skipped; its API login/role was verified by the check command. | `.run/evidence/live-journey-final.log` |
| Simple Student browser | `student` login, Student profile, officer endpoint forbidden, and logout revocation passed in a real browser. | `.run/evidence/simple-student.log`, `.run/evidence/simple-student-after-restart.log` |
| LIVE responsive/a11y audit | 1 complete audit passed across 60 route/viewport combinations at 390/768/1024/1440/1920 px. All 11 audited routes had zero automated WCAG 2.1 A/AA violations at 1440 px; no measured horizontal overflow or page errors. Mean route measurement 1.06 s, maximum 1.77 s on this machine. | `.run/evidence/live-audit-final.log`, `.run/evidence/live-audit/` |
| Fixture visual/a11y | 35 passed on separate port 5184 with fixture/simulation opt-in. These are not evidence of backend persistence. | `.run/evidence/fixture-demo-final-pass.log` |
| Launcher safety and readiness | 17 safety tests passed; `Check-HireFlow.cmd` passed all critical live checks including roles, login/logout, invalid password and health. | `.run/evidence/launcher-demo-final.log`, `.run/evidence/check-after-restart.log` |

The simple Student account is a separate fictional record `DEMO-STU-LOGIN` in the isolated database, display name **Student**. Its requested weak password is not stored in source. Provisioning verified database identity before writing, stored a BCrypt hash, and changed no other account. The browser test confirmed it is usable and cannot access officer data.

## Fixes made during final audit

The responsive audit identified a narrow-screen interview session ID overflow; the interview card now wraps its ID and footer. The previous 1440px accessibility audit identified table-sort semantics, action labels and contrast issues; those were corrected and the audit rerun to passing. A fixture selector was updated to match the corrected company action label. Initial attempts that failed are retained in `.run/evidence/` and are not counted as passes.

## Limits and presenter gate

This is a local development-stack demo, not a release or deployment. Automated axe checks cover the routes and sizes above; they do not replace a full manual accessibility review. The separate SIMULATION suite uses fixtures and must be described as such. The presenter should run `Check-HireFlow.cmd` shortly before the audience arrives and use the [demo runbook](demo-runbook.md). Docker Desktop/WSL availability remains a host dependency; the launcher can use ordinary installed-Docker startup but will stop on unresolved WSL disk errors. A historical simulation-launch result remains in `.run/evidence/simulation-launch-final.log`; the current one-click launcher no longer exposes that option. The prepared `VIVA22` drive has not been consumed by the automated journey. A final launcher stop/restart completed in 11.1 seconds and the post-restart readiness check passed; see `.run/evidence/live-restart-final.stdout.log` and `.run/evidence/check-after-restart.log`.



## Live-only launcher and sign-in update — 22 October 2026

The one-click launcher now starts the real application directly at `/auth/login`, with no `1`/`2` choice or simulation fallback. The sign-in page now shows the student recruitment path alongside the unchanged authentication form. The Student Opportunity Hub now presents its four API-backed counts in one divided overview instead of four icon cards. Its entrance motion is suppressed by the operating system's reduced-motion preference. The visual direction draws on the free reference galleries at ThreeUI and 21st.dev and on GSAP's motion vocabulary; no third-party component code or paid assets were copied into the repository.

Executed after this update: production build passed; lint exited 0 with existing advisory warnings; all 150 frontend tests passed with explicit `HIREFLOW_ALLOW_ISOLATED_IT=55432/hireflow_cap8_it`; the genuine 40-step Playwright journey passed again after both interface changes against the isolated PostgreSQL container (administrator browser case intentionally skipped); the login page had no measured horizontal overflow and zero automated WCAG 2.1 A/AA axe violations at 1440px and 390px. Screenshots: `.run/evidence/live-design-login-1440.png` and `.run/evidence/live-design-login-390.png`. Prior core/API results above were not rerun for this CSS/layout-only update. No commit or release was made.



## Four-perspective continuation — 24 October 2026

See [four-perspective-verification-2026-10-24.md](four-perspective-verification-2026-10-24.md) for the student, officer, officer-managed recruiter, and developer review. This pass added near-live refresh, honest unavailable states, a phone-friendly officer campaign view, a real cross-session persistence test, and exact-ID cleanup. The combined live browser suite had 2 passes and 1 intentional administrator-browser skip; the administrator account passed the independent live readiness check. Frontend tests: 151/151. Core and API Java suites were not rerun in this pass.

