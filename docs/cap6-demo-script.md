# HireFlow — Stage 3 CAP-6 Live Demonstration & Defense Script

> **Evaluation:** Continuous Assessment Program 6 (CAP-6) Defense  
> **Topic:** HireFlow Stage 3 — Enterprise Web Portal, Two-Phase Advisory Scheduling, Role Security & PostgreSQL GiST Persistence  
> **Target Duration:** 7–10 Minutes Live Demonstration + Evaluator Viva Q&A  
> **Presenters:**  
> 1. **Ishhan Goyal** (Lead Architect — Web Architecture, Dual Packaging & Security)  
> 2. **Ishika Bisht** (Lead Logic & Services — Eligibility Rules, Advisory Scheduling & Conflict Management)  
> 3. **Kirti** (Lead Integration & Database — PostgreSQL ACID Persistence, Container Lifecycle & Student Portal)  
> **Automated Verification:** **495 Passing Tests** | 20-Step Live E2E Container Acceptance  
> **Security Notice:** All demonstrations utilize test accounts. Zero production credentials or sensitive keys are referenced.

---

## 1. Pre-Demo Setup Checklist

Before the evaluators arrive, execute the following verification steps:

- [ ] **1. Java 21 LTS Runtime Verified:** `java -version` returns OpenJDK 21+.
- [ ] **2. Apache Maven Verified:** `mvn -version` returns Maven 3.9+.
- [ ] **3. PostgreSQL Test Database Running:** Service active on localhost:5432 with `btree_gist` extension enabled.
- [ ] **4. Build Artifacts Assembled:** Run `mvn package -DskipTests` to produce `target/hireflow-1.0.0.war` and `target/hireflow-1.0.0-desktop.jar`.
- [ ] **5. Clean Browser Windows Opened:** Chrome, Edge, or Firefox prepared with two separate private/incognito windows:
  - **Window A (Placement Officer):** For corporate drive creation, shortlisting, and scheduling.
  - **Window B (Student):** For profile viewing, eligibility verification, and application submission.
- [ ] **6. Fallback Offline Package Ready:** Standalone desktop JAR ready in case of local network or port binding issues.

---

## 2. Demonstration Flow & Presenter Handoff (8 Minutes Total)

```
[00:00 - 01:30] Phase 1: Dual Architecture & Officer Workspace (Ishhan Goyal)
[01:30 - 03:00] Phase 2: Drive Creation & Resource Management (Ishika Bisht)
[03:00 - 04:30] Phase 3: Student Portal, Dynamic Eligibility & Application (Kirti)
[04:30 - 06:00] Phase 4: Candidate Shortlisting & Advisory Scheduling Preview (Ishika Bisht)
[06:00 - 07:15] Phase 5: Transactional Publication, Student Timetable & DB Verification (Ishhan Goyal)
[07:15 - 08:00] Phase 6: Persistence Under Container Restart & Conclusion (Kirti)
[08:00 - 10:00] Evaluator Viva & Technical Defense Q&A (All Presenters)
```

---

### Phase 1: Dual Architecture & Officer Workspace (00:00 – 01:30)
**Presenter: Ishhan Goyal**

**Spoken Presentation:**
> *"Good morning, Professors. Today we present HireFlow Stage 3 (CAP-6). We have extended our core Java placement scheduling system into a secure enterprise web application running on Apache Tomcat 10.1 and PostgreSQL.*
>
> *A core achievement of CAP-6 is our dual-deployment architecture: from a single unified codebase, Maven generates both `hireflow-1.0.0.war` for browser-based campus placement management and `hireflow-1.0.0-desktop.jar` for local placement office workstations.*
>
> *I am now logging into the web portal as Placement Officer `demo.officer`. Notice that upon submission, our `LoginController` executes `request.changeSessionId()` to defend against session fixation, while `PortalAuthorizationFilter` verifies our cryptographic role binding. We are directed to the Officer Dashboard, showing active drives, pending applications, and confirmed interview sessions."*

**Live Action Steps:**
1. Open Browser Window A and navigate to `http://localhost:8080/hireflow/login`.
2. Enter officer username `demo.officer` and the demonstration passphrase.
3. Click **"Log In"**; point out the transition to `/hireflow/officer/dashboard`.
4. Point out the security indicators: CSRF token in page source and active role badge `PLACEMENT OFFICER`.

---

### Phase 2: Drive Creation & Resource Configuration (01:30 – 03:00)
**Presenter: Ishika Bisht**

**Spoken Presentation:**
> *"Now, the placement office prepares for an upcoming hiring season. I will register a participating corporate partner and launch a recruitment drive with strict qualification criteria.*
>
> *First, under Partner Companies, we register `Stark Industries` in the Aerospace & Defense sector. Next, we create a recruitment drive for `Autonomous Flight Systems Engineer`. We specify a minimum CGPA cutoff of 8.00 and restrict eligible branches to Computer Science (`CSE`) and Aerospace (`AERO`).*
>
> *Finally, we register our interview panelist `Dr. James Rhodes` and reserve `Conference Hall Alpha`. These entities are persisted directly into PostgreSQL through our parameterized JDBC repositories, guarded against SQL injection."*

**Live Action Steps:**
1. Click **"Companies"** -> Click **"Register New Company"**.
   - Name: `Stark Industries` | Industry: `Aerospace & Defense`.
   - Submit form -> Confirm company card appears.
2. Click **"Recruitment Drives"** -> Click **"Create New Drive"**.
   - Company: `Stark Industries` | Job Title: `Autonomous Flight Systems Engineer`.
   - Min CGPA: `8.00` | Eligible Branches: `CSE, AERO` | Deadline: 14 days ahead.
   - Submit form -> Confirm drive appears with active status.
3. Click **"Resources"** -> Add Interviewer `Dr. James Rhodes` and Room `Conference Hall Alpha`.

---

### Phase 3: Student Portal, Dynamic Eligibility & Application (03:00 – 04:30)
**Presenter: Kirti**

**Spoken Presentation:**
> *"Now we switch perspectives to the student experience. In an independent browser window with isolated session cookies, I am logging in as final-year student `demo.student` (Peter Parker, CSE, CGPA 8.75).*
>
> *When Peter navigates to 'Placement Drives', our server-side `EligibilityService` dynamically evaluates his academic credentials against all active drives. Notice that for Stark Industries, Peter receives a green 'ELIGIBLE' badge because his CGPA of 8.75 exceeds the 8.00 threshold and his CSE branch matches the criteria.*
>
> *Peter clicks 'Apply'. His application is recorded with status `APPLIED` in PostgreSQL. Notice that Peter cannot view any other student's applications or internal administrative controls—our `PortalAuthorizationFilter` strictly isolates student data using `UserPrincipal.getStudentId()`."*

**Live Action Steps:**
1. Open Browser Window B (Incognito) and navigate to `http://localhost:8080/hireflow/login`.
2. Log in as student `demo.student`.
3. Click **"My Profile"** -> Highlight verified CGPA (8.75) and branch (CSE).
4. Click **"Placement Drives"** -> Point out the **"ELIGIBLE"** badge on Stark Industries.
5. Click **"Apply Now"** -> Verify confirmation flash message: *"Application submitted successfully"*.
6. Click **"My Applications"** -> Show current status `APPLIED`.

---

### Phase 4: Candidate Shortlisting & Advisory Scheduling Preview (04:30 – 06:00)
**Presenter: Ishika Bisht**

**Spoken Presentation:**
> *"Returning to the Placement Officer window, the officer opens 'Review Applications'. Peter's application appears. After reviewing his credentials, the officer marks his status as `SHORTLISTED`.*
>
> *Now comes the most critical capability of CAP-6: Advisory Two-Phase Scheduling. Generating an interview timetable is complex and requires human officer verification before committing changes. The officer selects the drive, date, time window (09:00 to 12:00), 30-minute slot duration, and 15-minute inter-interview buffer.*
>
> *When the officer clicks 'Generate Preview', our Greedy Interval Scheduler computes optimal allocations in memory. The server displays an 'ADVISORY PREVIEW' table showing Peter allocated to Dr. James Rhodes in Conference Hall Alpha. Crucially, our database remains completely untouched: zero records exist in the `interview_sessions` table, and Peter's status remains `SHORTLISTED` until publication."*

**Live Action Steps:**
1. Switch to Browser Window A (Officer).
2. Click **"Applications"** -> Filter by `Stark Industries`.
3. Locate Peter's application -> Click **"Shortlist Candidate"** (Status updates to `SHORTLISTED`).
4. Navigate to **"Schedule Interviews"**.
5. Select Drive, choose tomorrow's date, 09:00 - 12:00, 30 min duration, 15 min buffer.
6. Select Interviewer (`Dr. James Rhodes`) and Room (`Conference Hall Alpha`).
7. Click **"Generate Proposed Timetable"**.
8. Highlight the preview screen:
   - Yellow **"ADVISORY PREVIEW"** badge.
   - Unique Preview Token (`PREV-...`) with 15-minute validity indicator.
   - Proposed timetable row showing student, interviewer, room, and time window.

---

### Phase 5: Transactional Publication, Student Timetable & DB Verification (06:00 – 07:15)
**Presenter: Ishhan Goyal**

**Spoken Presentation:**
> *"The officer is satisfied with the proposed allocation and clicks 'Confirm & Publish Schedule'.*
>
> *Behind the scenes, `OfficerSchedulingController` consumes the preview token to prevent replay, locks the application and resource rows in deterministic order (`Rooms -> Interviewers -> Students`), revalidates temporal conflicts, inserts the confirmed appointment, and updates Peter's application to `SCHEDULED` in a single atomic transaction.*
>
> *Switching back to Peter's student window, he refreshes his 'My Interviews' tab. He immediately sees his confirmed interview appointment with full details: Stark Industries, Dr. James Rhodes, Conference Hall Alpha, and his exact time slot. Other students cannot see his slot, and Peter cannot see any other candidate's schedule."*

**Live Action Steps:**
1. In Window A (Officer), click **"Confirm & Publish Schedule"**.
2. Point out the green confirmation alert: *"1 interview session successfully published and confirmed."*
3. Point out that the preview token is now marked **CONSUMED**; attempting to re-submit will be safely blocked.
4. Switch to Window B (Student Peter).
5. Click **"My Interviews"** -> Verify that the confirmed interview card appears with venue, interviewer, and time slot.
6. Click **"My Applications"** -> Show that application status has transitioned to `SCHEDULED`.

---

### Phase 6: Persistence Under Container Restart & Conclusion (07:15 – 08:00)
**Presenter: Kirti**

**Spoken Presentation:**
> *"To prove that this state is not stored in transient web server memory, we demonstrate persistence under container restart. We halt the web server, restart Tomcat, and re-query both the database and the web interface.*
>
> *All records—the company, the recruitment drive, Peter's `SCHEDULED` application, and the confirmed interview session—survive the restart intact in PostgreSQL.*
>
> *In summary, HireFlow Stage 3 delivers enterprise web capabilities, bulletproof role security, advisory scheduling, and temporal PostgreSQL integrity while maintaining 100% backward compatibility with our desktop suite. All 495 automated tests pass with zero failures. We are now open for questions."*

**Live Action Steps:**
1. Stop the Tomcat server process.
2. Restart the Tomcat server process.
3. Refresh Browser Window B (Student) -> Log in -> Verify the confirmed interview timetable remains intact.
4. Conclude the presentation and invite questions.

---

## 3. Practical Fallback Procedure (Emergency Plan)

In the event of an unexpected environmental failure during live defense:

### Scenario A: Local Tomcat Port Conflict (Port 8080 Occupied)
- **Immediate Remedy:** Start Tomcat on an alternate port:
  ```bash
  mvn tomcat7:run -Dmaven.tomcat.port=8088
  ```
  Or launch the embedded container test runner:
  ```bash
  mvn test -Dtest=TomcatDeploymentIntegrationTest
  ```

### Scenario B: Local PostgreSQL Database Unavailable
- **Immediate Remedy:** Switch to our verified in-memory fallback mode:
  ```bash
  java -Dhireflow.storage.mode=IN_MEMORY -jar target/hireflow-1.0.0-desktop.jar
  ```
  The desktop application will launch immediately with mock seed data, demonstrating the complete scheduling and eligibility logic offline.

---

## 4. Evaluator Viva Questions & Concise Model Answers

### Q1: Why did your team implement a Two-Phase Advisory Scheduling workflow instead of immediately inserting sessions?
> **Answer (Ishika Bisht):**  
> *"Placement coordinators require the ability to adjust parameters, test alternative time blocks, and inspect unscheduled candidates before committing an official institutional timetable. In our architecture, the preview phase is completely non-destructive—zero database writes occur. Once confirmed, the publication phase commits the preview atomically with pessimistic locking and GiST exclusion constraints."*

### Q2: How do you prevent double-booking if an officer opens the scheduling interface in two browser tabs?
> **Answer (Ishhan Goyal):**  
> *"Each preview generation creates a unique, cryptographically random preview identifier (`PREV-...`) stored in a session-scoped LRU map. Each tab references its own preview identifier. Furthermore, when publication is submitted, `markConsumed()` atomically invalidates that preview identifier inside a synchronized block, preventing duplicate execution. At the database tier, PostgreSQL's temporal GiST exclusion constraint (`tsrange &&`) acts as an absolute physical backstop."*

### Q3: How is student privacy guaranteed in the web layer?
> **Answer (Kirti):**  
> *"All student portal endpoints (`/student/*`) resolve the student identity strictly from the authenticated session's `UserPrincipal.getStudentId()`. We never read student IDs from HTTP request query parameters or form fields. Even if an attacker manipulates URLs or POST parameters, they can only view and mutate their own profile and applications."*

### Q4: Why did you retain the desktop JAR build when transitioning to a web portal?
> **Answer (Ishhan Goyal):**  
> *"Our project demonstrates enterprise dual-tier architecture. High-volume administrative tasks in the placement office benefit from the responsiveness and keyboard productivity of a Swing desktop client, while students and corporate panels require zero-install browser access. Both interfaces share the identical core Java domain services, transaction boundaries, and database schema without any code duplication."*

### Q5: How does your system protect against Cross-Site Request Forgery (CSRF)?
> **Answer (Ishika Bisht):**  
> *"Every state-changing HTTP request (`POST`) requires a valid session-bound CSRF token managed by `CsrfTokenManager`. Tokens are embedded as hidden fields in HTML forms and checked in servlet filters using constant-time comparison (`MessageDigest.isEqual()`) to prevent timing side-channel analysis."*
