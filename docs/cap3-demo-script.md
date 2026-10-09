# HireFlow — Stage 1 CAP-3 Live Demonstration Script

> **Evaluation:** Continuous Assessment Program (CAP-3) Live Defense  
> **Topic:** HireFlow Stage 1 Desktop Prototype (Java 21 + Swing + Maven)  
> **Team Structure:** 3 Engineers (Roles: Architecture/Core OOP, Services & Business Logic, Algorithms & GUI/Threading)  
> **Target Duration:** 10 Minutes Total (8 Mins Walkthrough + 2 Mins Defense Q&A)

---

## 1. Team Role Allocation

| Member | Focus Area | Demonstrated Modules |
| :--- | :--- | :--- |
| **Member 1: Architecture & Data Access** | Core OOP, Domain Models, Repository Pattern, In-Memory Storage, Maven Build | Launch, Architecture Overview, Student Management Screen, Company & Drives Screen |
| **Member 2: Business Logic & Rules** | Eligibility Engine, Application Service, Exception Handling, Validation | Eligibility Evaluator, Application Submissions, Ineligible & Duplicate Rejection, Shortlisting |
| **Member 3: Algorithms & Desktop UI** | Greedy Scheduling Engine, Conflict Detection, Swing GUI, Concurrency/EDT | Interview Scheduling, Non-blocking SwingWorker, Timetable Inspection, Conflict Diagnostics, Publishing |

---

## 2. Minute-by-Minute Demonstration Walkthrough

```
Timeline (10 Minutes Total):
[00:00 - 01:30] Member 1: Project Architecture, Maven Build & Launch, Dashboard Overview
[01:30 - 03:30] Member 1: Student Registry (Search, Validation, Add, Edit, Delete)
[03:30 - 05:30] Member 2: Drives, Eligibility Evaluator, Enforced Rejections, Shortlisting
[05:30 - 08:00] Member 3: Greedy Scheduler, SwingWorker Async Run, Overlap Verification, Publishing
[08:00 - 10:00] All Members: Evaluator Q&A Defense & Stage 2 Transition
```

---

### Act 1: Launch & Dashboard Overview (00:00 – 01:30)
**Presenter:** Member 1  
**Action:**
1. Open terminal and run:
   ```bash
   java -jar target/hireflow-1.0.0.jar
   ```
2. Explain the window layout:
   - *"Notice the persistent dark sidebar on the left with instant section routing using Swing's `CardLayout`."*
   - *"The Dashboard immediately displays live metric counters backed by our in-memory repositories: 11 Registered Students, 4 Partner Companies, 4 Active Recruitment Drives, and 6 Candidate Applications."*
   - *"Tables use alternating row shading, non-editable cell models, and left padding for high readability."*

---

### Act 2: Student Registry & Validation (01:30 – 03:30)
**Presenter:** Member 1  
**Action:**
1. Click **"Students"** in the sidebar.
2. In the Search bar, type `Alice` $\rightarrow$ table instantly filters to Alice Smith. Type `ECE` $\rightarrow$ filters to ECE candidates. Click **"Clear"** $\rightarrow$ restores full list.
3. Test Input Validation in the **"Register New Candidate"** form:
   - Fill ID: `STU-200`, Name: `Ada Lovelace`, Email: `invalid-email`, Branch: `CSE`, CGPA: `12.5`, Grad Year: `2026`.
   - Click **"Register Student"** $\rightarrow$ Show the validation dialog: *"CGPA must be between 0.0 and 10.0 and email must be valid."*
   - Correct email to `ada@univ.edu` and CGPA to `9.60`.
   - Click **"Register Student"** $\rightarrow$ Success notification appears; `STU-200` immediately populates the table; Dashboard count increments.
4. Select `STU-200`, click **"Edit Student Profile"**, change branch to `IT`, save, and verify update.

---

### Act 3: Drives, Eligibility & Candidate Lifecycle (03:30 – 05:30)
**Presenter:** Member 2  
**Action:**
1. Click **"Companies & Drives"** in the sidebar.
   - Point out Tab 1 (Recruitment Drives) showing active status vs. expired deadlines (`DRV-105` marked Expired).
   - Show Tab 2 (Partner Companies) with Google Cloud, Microsoft, Goldman Sachs, and Tata Motors.
2. Click **"Applications"** in the sidebar.
3. **Demonstrate Interactive Eligibility Evaluator:**
   - Candidate: `Alice Smith (STU-101)` | Target Drive: `Google Cloud SDE-1 (DRV-101)`
   - Click **"Check Eligibility"** $\rightarrow$ Green banner displays: *"PASS: Student satisfies all academic cutoff, departmental, and deadline criteria."*
   - Switch Candidate to `Hannah Abbott (STU-108)` (CGPA 6.80) $\rightarrow$ Click **"Check Eligibility"** $\rightarrow$ Red banner displays: *"FAIL: Student CGPA (6.80) does not meet minimum cutoff of 8.00."*
   - Switch Candidate to `Fiona Gallagher (STU-106)` (MECH) $\rightarrow$ Red banner: *"FAIL: Branch 'MECH' not allowed."*
4. **Demonstrate Non-Bypassable Application Rejection:**
   - Attempt to click **"Submit Application"** for Hannah Abbott $\rightarrow$ Rejected with `ValidationException` dialog.
   - Select Alice Smith and attempt to apply for `Google Cloud (DRV-101)` $\rightarrow$ Rejected with `DuplicateEntityException`: *"Student STU-101 has already applied for DRV-101."*
5. **Demonstrate State Machine Shortlisting:**
   - Select an application with status `APPLIED` from the table.
   - Click **"Shortlist Candidate"** $\rightarrow$ Status transitions to `SHORTLISTED`.
   - Show that illegal state transitions (e.g. jumping straight to `SELECTED`) are blocked.

---

### Act 4: Greedy Scheduling & Conflict Detection (05:30 – 08:00)
**Presenter:** Member 3  
**Action:**
1. Click **"Interview Scheduler"** in the sidebar.
2. Point out the parameter controls:
   - Start Hour: `09:00`, End Hour: `17:00`, Duration: `45 mins`, Buffer: `15 mins`.
3. Click **"Generate Interview Schedule"**:
   - Point out that the UI **does not freeze** — an indeterminate progress bar pulses while `SwingWorker` executes the greedy slot search on a background thread.
4. **Inspect Proposed Timetable (Tab 1):**
   - Show allocated sessions with student name, company, interviewer, assigned booth, and distinct non-overlapping time intervals.
5. **Verify Zero Conflicts (Act 4 Key Defense Point):**
   - *"Notice Alice Smith applied and was shortlisted by two different companies (Google and Microsoft). Look at her schedule: Google interview is at 10:00–10:45 in Booth 1, and Microsoft interview is at 09:00–09:45 in Booth 2. Our scheduler prevented candidate double-booking!"*
   - *"Interviewer Dr. Sarah Chen has back-to-back sessions spaced with exactly the 15-minute buffer."*
6. **Inspect Unscheduled Applications (Tab 2):**
   - Click Tab 2 $\rightarrow$ point out unshortlisted applications listed with clear diagnostic reasons: *"Application status is not SHORTLISTED."*
7. **Publish Roster:**
   - Click **"Confirm & Publish Schedule"** $\rightarrow$ Sessions are committed into `InterviewSessionRepository`. Application statuses update to `SCHEDULED`, and the Dashboard session counter increments.

---

### Act 5: Evaluator Defense Q&A Cheatsheet (08:00 – 10:00)

#### Q1: "Why did you use SwingWorker instead of running the scheduler on the main thread?"
> **Answer:** *"Swing uses a single Event Dispatch Thread (EDT) for event handling and rendering. Executing combinatorial scheduling algorithms on the EDT freezes the UI, making the application unresponsive. `SwingWorker` moves the heavy scheduling logic to a background worker thread and delivers results back to the table model on the EDT once completed."*

#### Q2: "How is your architecture prepared for Stage 2 database persistence?"
> **Answer:** *"All business services depend strictly on repository interfaces extending `CrudRepository<T, ID>`, not concrete implementations. In Stage 2, we will create `JdbcStudentRepository`, `JdbcDriveRepository`, etc., without modifying a single line of business logic in `StudentService`, `EligibilityService`, or `ApplicationService`."*

#### Q3: "How do you enforce that no application bypasses eligibility checks?"
> **Answer:** *"In `ApplicationService.submitApplication()`, the very first operation after fetching the student and drive is calling `eligibilityService.checkEligibility()`. If `isEligible()` returns false, a `ValidationException` is thrown before any `Application` record can be instantiated or added to the repository."*

#### Q4: "What collections did you use and why?"
> **Answer:** *"Our in-memory repositories use `ConcurrentHashMap` for thread-safe $O(1)$ CRUD access. The drive model uses `Set<String>` for $O(1)$ branch authorization. The application lifecycle uses `EnumSet` for bitmask status transitions, and repository query methods return unmodifiable lists via `Collections.unmodifiableList()` to maintain defensive encapsulation."*
