# HireFlow Live Demo - Presenter Cheat Sheet

Audience: university placement faculty / panel.
Target length: 7-10 minutes.
Mode: LIVE DEMO (real Spring Boot API, real PostgreSQL, real authentication and persistence).

Passwords are never stored in this document. They live only in the gitignored file
`.run/demo.local.json` on the presenting machine, which is created by
`Setup-HireFlow-Demo.cmd`.

---

## 1. Before you present (one time per machine)

| # | Action | Expected result |
|---|--------|-----------------|
| 1 | Double-click `Setup-HireFlow-Demo.cmd` | `[PASS] ... demo.master ... demo.officer ... demo.student ... provisioned`, then `[PASS] Fictional demo scenario seeded` |
| 2 | Double-click `Check-HireFlow.cmd` | Ends with `HIREFLOW DEMO READY - all critical checks passed.` |
| 3 | Open `.run/demo.local.json` and note the three demo usernames/passwords | Private, local only, never committed |

If step 2 prints `HIREFLOW DEMO NOT READY`, start `Launch-HireFlow.cmd` first, then repeat steps 1-2.

---

## 2. Starting the demo (one click)

1. Double-click `Launch-HireFlow.cmd`.

2. Wait for the line `READY: LIVE | http://127.0.0.1:5174/auth/login`.
3. The browser opens automatically on the HireFlow sign-in screen.

Backend health: <http://127.0.0.1:8081/api/v1/health>
Frontend: <http://127.0.0.1:5174>

To stop everything afterwards, double-click `Stop-HireFlow.cmd`.

---

## 3. Run of show (7-10 minutes)

| Time | Beat | What you do | What to say |
|------|------|-------------|-------------|
| 0:00 | One-click startup | Show the `READY:` line already printed | "A single command verifies Java 21, Node, the isolated PostgreSQL container, port ownership, backend health and frontend readiness before it declares the system ready." |
| 0:45 | Student login | Sign in as `demo.student` | "Real HTTP session, real CSRF token, real BCrypt verification against PostgreSQL." |
| 1:30 | Opportunity exploration | Student Hub -> Recruitment Drives | "Only drives this student is actually eligible for are surfaced, with the company, role and deadline." |
| 2:30 | Eligibility explanation | Open a drive -> eligibility breakdown | "Eligibility is computed by the backend from CGPA, branch and drive criteria - the UI only explains the decision." |
| 3:30 | Application submission | Submit the application -> confirmation modal | "Submission is idempotent and rejected outside the deadline; the drive rules are enforced server-side." |
| 4:30 | Officer shortlisting | Sign out, sign in as `demo.officer` -> Recruitment Drives -> review applicants -> Shortlist | "The placement officer moves the candidate through the authorised status transition only." |
| 5:30 | Resource administration | Resource Pools | "Interviewers and rooms are first-class resources with capacity and ownership." |
| 6:15 | GreedyScheduler | Scheduling Studio -> select drive -> pick date/interviewer/room -> Generate Advisory Preview | "The engine produces an advisory timetable; nothing has been written to the database yet." |
| 7:00 | Preview vs committed | Point at the advisory banner and the empty confirmed list | "Preview is explicitly uncommitted. You can regenerate it as many times as you like." |
| 7:45 | Publication | Review & Publish -> acknowledge -> Confirm & Publish | "Publication is the only step that writes interview sessions, inside one transaction with GiST-backed conflict protection." |
| 8:30 | Student confirmation | Sign in again as `demo.student` -> Interview Timetable | "The student sees the same committed appointment that the officer just published - the round trip closes." |
| 9:30 | Close | Optional: `Check-HireFlow.cmd` on screen | "Every critical layer is re-verified by an automated check, including a real administrator login. The check updates authentication audit timestamps." |

---

## 4. Demo accounts

| Username | Role | Used for |
|----------|------|----------|
| `demo.master` | ADMINISTRATOR | Independent administrator sign-in; can also open the officer surface through genuine authorization |
| `demo.officer` | PLACEMENT_OFFICER | Drives, candidate review, shortlisting, resources, scheduling studio |
| `demo.student` | STUDENT (record `DEMO-STU-01`) | Opportunities, eligibility, application submission, interview timetable |
| `student` (display name Student) | STUDENT (record `DEMO-STU-LOGIN`) | Simple requested demo login; use `demo.student` for the prepared full journey |

Seeded scenario: 5 students across CSE/IT/ECE/MECH, 4 companies, 4 recruitment drives
(one closed), 7 applications, 4 interviewers, 3 rooms, 3 confirmed interview sessions,
1 shortlisted candidate still awaiting a slot, and 1 deliberately ineligible application.

---

## 5. Feature talking points

- **Fail-closed environment guard.** The launcher refuses to start against anything that is not the isolated
  `hireflow_cap8_it` database on `127.0.0.1:55432` with the restricted non-superuser role.
- **No mocks in LIVE mode.** The UI talks to the real API; there is no fixture fallback.
- **Authorization is server-side.** A student who types an officer URL is refused by the backend, not just hidden in the UI.
- **CSRF is issued per session.** Login rotates the session id; the CSRF token is re-read after every transition.
- **Scheduling integrity.** `interview_sessions` is protected by GiST exclusion constraints, so double-booking a student,
  interviewer or room is impossible at the database level.
- **Idempotent demo data.** Re-running setup inserts zero duplicate rows.

---

## 6. Common questions

**"Is this simulated?"**
No. LIVE mode uses the real Spring Boot service, the real PostgreSQL 16 container, real BCrypt hashes and real persistence.
The one-click launcher opens the live application only.

**"Where is the database?"**
An isolated PostgreSQL 16 container (`hireflow-cap8-postgres`) bound to `127.0.0.1:55432`, database `hireflow_cap8_it`.
Any PostgreSQL already on port 5432 on the host is untouched.

**"What happens if two officers schedule the same room?"**
The database refuses the second write through a GiST exclusion constraint; the API returns a conflict rather than corrupting data.

**"Can a student see another student's data?"**
No. Student endpoints are scoped to the authenticated principal's student record.

**"What if the demo laptop dies?"**
Restart it, double-click `Launch-HireFlow.cmd`, and the demo accounts and scenario are still there because they live in the database, not in memory.
Existing demo accounts and records persist across restart.

---

## 7. Recovery steps

| Symptom | Fix |
|---------|-----|
| `HIREFLOW DEMO NOT READY` | Double-click `Launch-HireFlow.cmd`, pick LIVE, wait for `READY:`, then re-run `Check-HireFlow.cmd` |
| Browser did not open | Open <http://127.0.0.1:5174/auth/login> manually |
| Port already in use | Run `Stop-HireFlow.cmd` first; the launcher refuses to adopt processes it did not start |
| Docker container stopped | Start Docker Desktop, then re-run `Launch-HireFlow.cmd` - it starts `hireflow-cap8-postgres` and waits for `healthy` |
| Login rejected | Re-run `Setup-HireFlow-Demo.cmd` to reprovision the demo accounts |
| Need a fresh application journey | Run `scripts/demo/Prepare-HireFlowJourney.ps1 -Round VIVA23` while LIVE is healthy; this preserves existing records |
| Unsure of state | Run `Check-HireFlow.cmd`; it reports every layer and updates authentication audit timestamps |

Recovery commands never touch the host PostgreSQL on port 5432, never delete Docker volumes,
and never terminate processes the launcher does not own.

---

## 8. Safety notes for the presenter

- Do not paste passwords into chat, slides, email or the terminal transcript history.
- Do not commit `.run/demo.local.json`; it is intentionally gitignored.
- The demo administrator uses the genuine `ADMINISTRATOR` role and normal authorization - there is no backdoor account.




