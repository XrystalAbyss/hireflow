# HireFlow demo runbook — verified 22 October 2026

Demo target: LIVE mode on this Windows machine, using only the isolated PostgreSQL 16 container at `127.0.0.1:55432/hireflow_cap8_it`. The host PostgreSQL service on port 5432 is outside this workflow.

## Start and check

1. Start Docker Desktop if it is not already running. Double-click `Launch-HireFlow.cmd` and wait for `READY: LIVE` at `http://127.0.0.1:5174/auth/login`. A cold start can take about a minute; do not infer readiness from a browser tab alone.
2. Double-click `Check-HireFlow.cmd`. Proceed only when it ends with `HIREFLOW DEMO READY - all critical checks passed.` The check performs real demo-account logins, so it updates their authentication audit timestamps.
3. Keep the terminal open during the presentation. Double-click `Stop-HireFlow.cmd` after the demo. This stops launcher-owned API/frontend processes and preserves the database and evidence.

The launcher always starts the live application and never switches to a fixture preview. Make persistence claims only when the LIVE readiness check is green.

## Accounts

| Login | Password source | Purpose |
| --- | --- | --- |
| `student` (display name **Student**) | Demo-only password supplied by the presenter | Simple fictional student login; verified in browser on 22 October. Student ID `DEMO-STU-LOGIN`. |
| `demo.student` | Gitignored `.run/demo.local.json` | Aarav Sharma; use for the full application → scheduling journey. |
| `demo.officer` | Gitignored `.run/demo.local.json` | Placement officer for review and publication. |
| `demo.master` | Gitignored `.run/demo.local.json` | Administrator access check. |

The simple Student password is intentionally not recorded in tracked files. It is a weak, user-requested demo password and must remain confined to the isolated local database. Never expose port 55432 beyond loopback or reuse these accounts in a shared environment.

## Live journey

Use `demo.student` for the full journey because its fictional CSE profile is linked to the prepared company, resource pool, and drive. The fresh drive `DRV-DEMO-VIVA22` is prepared without an application. Sign in as `demo.student`; open Recruitment Drives, find **Graduate Software Engineer - VIVA22**, inspect eligibility, then apply. Sign out, sign in as `demo.officer`, open that drive's candidates, shortlist the new application, configure the Scheduling Studio with the prepared interviewer `INT-DEMO-VIVA22`, room `RM-DEMO-VIVA22`, and a future date, generate the advisory preview, then publish. Sign out and return as `demo.student` to verify the confirmed appointment. Preview is uncommitted; publication is the write.

The automated 40-step journey uses a new unique drive for each run and passed. It does not consume `DRV-DEMO-VIVA22`. If a practice session consumes the prepared drive, run `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/demo/Prepare-HireFlowJourney.ps1 -Round VIVA23` while LIVE is healthy; it creates only a new fictional round and does not reset prior records. Use the new round's IDs printed in `.run/evidence/prepared-journey.json`.

## Last-mile checks

Before the audience arrives: use the exact LIVE URL, sign in once as `student` to show the simple account, log out, then use `demo.student` for the story. Keep `.run/demo.local.json` private. Avoid running test suites or scenario reset during the presentation. If Docker Desktop reports the WSL missing `ext4.vhdx` error, stop. Do not unregister or repair WSL as part of the demo; the live system cannot be presented until Docker works.

See [demo-readiness-report.md](demo-readiness-report.md) for actual test results and [launcher-troubleshooting.md](launcher-troubleshooting.md) for recovery steps.

