# HireFlow - Stage 1 Quality Assurance & Test Cases

---

### Test Suite Overview
The Stage 1 prototype incorporates automated unit testing built with **JUnit 5 Jupiter** alongside manual validation matrices for Swing user interactions.

---

### 1. Automated Unit Test Cases: EligibilityService (`EligibilityServiceTest.java`)

| Test Case ID | Test Description | Input Data | Expected Result | Assertion / Verification |
| :--- | :--- | :--- | :--- | :--- |
| **TC-ELIG-01** | Student meets exact CGPA and branch requirements | Student CGPA: 8.5, Branch: `CSE`<br>Drive Min CGPA: 7.5, Branches: `[CSE, IT]` | `isEligible() == true` | Status: `ELIGIBLE`, Reason empty |
| **TC-ELIG-02** | Student CGPA strictly below minimum cutoff | Student CGPA: 7.19, Branch: `CSE`<br>Drive Min CGPA: 7.20, Branches: `[CSE]` | `isEligible() == false` | Status: `INELIGIBLE`, Reason mentions CGPA below threshold |
| **TC-ELIG-03** | Exact boundary CGPA match | Student CGPA: 7.50, Branch: `IT`<br>Drive Min CGPA: 7.50, Branches: `[IT]` | `isEligible() == true` | Boundary value condition passes |
| **TC-ELIG-04** | Disallowed academic branch | Student CGPA: 9.20, Branch: `CIVIL`<br>Drive Min CGPA: 7.00, Branches: `[CSE, ECE]` | `isEligible() == false` | Status: `INELIGIBLE`, Reason indicates branch not allowed |
| **TC-ELIG-05** | Case-insensitive branch matching | Student Branch: `cse`<br>Drive Branches: `[CSE]` | `isEligible() == true` | Normalized branch equality passes |
| **TC-ELIG-06** | Inactive student rejected | Student `active == false`, CGPA: 9.5, Branch: `CSE`<br>Drive Min CGPA: 6.0 | `isEligible() == false` | Reason specifies inactive student status |
| **TC-ELIG-07** | Drive allowing all branches (empty branch list or wildcard `*`) | Student Branch: `MECH`<br>Drive Branches: `[]` (unrestricted) | `isEligible() == true` | Unrestricted drive allows any branch |

---

### 2. Automated Unit Test Cases: SchedulingService (`SchedulingServiceTest.java`)

| Test Case ID | Test Description | Input Data | Expected Result | Assertion / Verification |
| :--- | :--- | :--- | :--- | :--- |
| **TC-SCHED-01** | Basic greedy allocation for shortlisted candidates | 2 Shortlisted students, 1 Interviewer, 2 Time Slots (09:00, 10:00) | 2 Sessions scheduled successfully | `sessions.size() == 2`, distinct non-overlapping times |
| **TC-SCHED-02** | Prevention of Interviewer double-booking | 2 Simultaneous candidate requests at same slot with only 1 Interviewer | 1 Scheduled, 1 deferred / queued | No two sessions share same interviewer and time slot |
| **TC-SCHED-03** | Prevention of Student double-booking | Student has existing interview at 09:00 - 10:00; attempted second interview at same time | Conflict detected; second interview placed in next available slot or rejected | No two sessions share same student and overlapping time |
| **TC-SCHED-04** | Capacity exhaustion handling | 5 Shortlisted candidates, 1 Interviewer with only 2 available slots | 2 Scheduled, 3 in overflow list | Sessions count is 2, Unscheduled candidates count is 3 |
| **TC-SCHED-05** | Multi-interviewer parallel scheduling | 4 Candidates, 2 Interviewers, 2 Time Slots (09:00, 10:00) | All 4 Candidates scheduled concurrently across the 2 rooms | Session list count is 4, 2 at 09:00 and 2 at 10:00 |
| **TC-SCHED-06** | Direct ConflictChecker interval overlap verification | Slot A: 09:00-10:00, Slot B: 09:30-10:30 (Partial overlap) | `hasTimeOverlap() == true` | Returns `true` for intersecting intervals |
| **TC-SCHED-07** | Direct ConflictChecker adjacent intervals (no overlap) | Slot A: 09:00-10:00, Slot B: 10:00-11:00 (Back-to-back) | `hasTimeOverlap() == false` | Boundary touching is NOT an overlap |

---

### 3. Desktop UI Manual Acceptance Scenarios

| Test Case ID | Feature | Step-by-Step Action | Expected GUI Behavior |
| :--- | :--- | :--- | :--- |
| **TC-UI-01** | Student Registration | Fill valid student details and click *Register Student* | Student appears immediately in table; form resets; success dialog shown |
| **TC-UI-02** | Student Validation | Submit invalid email (`test@@com`) or CGPA `12.5` | Red validation message / warning dialog appears; record not added |
| **TC-UI-03** | Drive Creation | Create Drive *Google SDE-1*, CGPA 8.0, Branches `CSE, IT` | Drive appears in Drive Table with status `PLANNED` |
| **TC-UI-04** | Candidate Application | Select registered student and drive, submit application | Application registered; eligibility status auto-computed and displayed |
| **TC-UI-05** | Run Scheduler | Click *Generate Schedule* button on SchedulePanel | Timetable table updates with allocated slots, interviewers, and rooms |
| **TC-UI-06** | Demo Seed Data | Click *Load Demo Data* button | Pre-populates 5 students, 2 drives, 3 interviewers, ready for instant test |
