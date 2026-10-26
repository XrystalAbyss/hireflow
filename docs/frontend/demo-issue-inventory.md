# Demo readiness audit - 2026-10-21

Baseline: develop/stage5 at bdbf0e1dce7407ab9034f893712ccea8d7fced56.
All existing source/configuration and uncommitted work copied to `.run/preservation/demo-start`; previous whole-tree backups also remain intact. No Git history operations.

Priority | Finding | Classification | Planned correction
---|---|---|---
P0 | No single launch/stop entry point or process ownership records | Missing automation | PS 5.1 scripts with bounded checks, identity records, atomic lifecycle lock and retained logs
P0 | Default API configuration permits host 5432 | Configuration risk | Launcher explicitly pins both datasource and core configuration to the verified 55432 database; never edits local configuration
P0 | Simulation exists only as developer flags; frontend normally proxies real API | Demo-mode gap | Explicit launcher selection, separate port, blocked proxy, persistent visible simulation notice; production excludes simulation
P1 | Browser journey selects visible Candidates text but aria-label is Review applications | Incorrect test selector | Match accessible name and run the complete real journey
P1 | Fixture screenshot tests write to a former developer's external directory | Test setup | Redirect new screenshots inside ignored project-local evidence; preserve existing captures
P1 | Existing Java server is pre-existing and must not be owned by the launcher | Lifecycle constraint | Verify and reuse only safely identifiable services; never stop reused processes
P1 | Existing test records accumulate; demonstrated state must be explained | Demo preparation | Preserve fixtures; provide a separately invoked additive API preparation command and role workflow
P2 | Performance and responsive evidence needs a fresh baseline | Verification gap | Measure cold/warm/restart and route timings; inspect screenshots at five widths
P2 | Existing lint warnings and accessibility suite disables contrast | Verification limitation | Record lint warnings; run additional live accessibility checks with contrast enabled

Observed environment: Java 21.0.12.1, Node 24.20.0, Maven 3.9.9, Spring Boot 4.1.1. Docker PostgreSQL 16 is running with the exact authorized loopback binding. The unrelated AttendPlan frontend on port 5173 is preserved. No current Docker/WSL repair is required.

The combined verification immediately preceding this mission passed core 499, API 186, frontend unit 140 and live HTTP 5. Its browser journey failed at candidate navigation and is not counted as passed. These logs are historical input to the final fresh verification.
