-- ==============================================================================
-- HireFlow Demo Scenario Seed (isolated CAP-8.5.2 integration database ONLY)
-- ------------------------------------------------------------------------------
-- Idempotent: safe to re-run. Every record uses a stable DEMO-* identifier and
-- ON CONFLICT DO NOTHING, so the scenario is never duplicated.
-- Contains believable fictional campus-recruitment data, including eligibility
-- variation, shortlisted candidates, one fully confirmed interview, and
-- deliberately unscheduled shortlisted candidates.
-- ==============================================================================

BEGIN;

-- 1. Students across academic branches -----------------------------------------
INSERT INTO students (id, name, email, branch, cgpa, graduation_year, active) VALUES
    ('DEMO-STU-01', 'Aarav Sharma', 'aarav.sharma@demo.hireflow.edu', 'CSE', 8.75, 2026, TRUE),
    ('DEMO-STU-02', 'Diya Nair', 'diya.nair@demo.hireflow.edu', 'IT', 9.10, 2026, TRUE),
    ('DEMO-STU-03', 'Rohan Iyer', 'rohan.iyer@demo.hireflow.edu', 'ECE', 7.60, 2026, TRUE),
    ('DEMO-STU-04', 'Meera Krishnan', 'meera.krishnan@demo.hireflow.edu', 'IT', 8.20, 2026, TRUE),
    ('DEMO-STU-05', 'Kabir Malhotra', 'kabir.malhotra@demo.hireflow.edu', 'MECH', 6.90, 2026, TRUE)
ON CONFLICT (id) DO NOTHING;

-- 2. Companies ----------------------------------------------------------------
INSERT INTO companies (id, name, industry) VALUES
    ('CMP-DEMO-01', 'Northwind Software Labs', 'Software Engineering'),
    ('CMP-DEMO-02', 'Helios Semiconductors', 'VLSI and Embedded Systems'),
    ('CMP-DEMO-03', 'Vertex Analytics', 'Data and Business Analytics'),
    ('CMP-DEMO-04', 'Meridian Consulting Group', 'Management Consulting')
ON CONFLICT (id) DO NOTHING;

-- 3. Recruitment drives -------------------------------------------------------
INSERT INTO recruitment_drives (id, company_id, job_title, minimum_cgpa, application_deadline, is_closed) VALUES
    ('DRV-DEMO-01', 'CMP-DEMO-01', 'Software Development Engineer - I', 7.50, DATE '2027-02-28', FALSE),
    ('DRV-DEMO-02', 'CMP-DEMO-02', 'VLSI Design Engineer', 7.00, DATE '2027-03-05', FALSE),
    ('DRV-DEMO-03', 'CMP-DEMO-03', 'Data Analyst', 6.50, DATE '2027-03-12', FALSE),
    ('DRV-DEMO-04', 'CMP-DEMO-04', 'Business Analyst', 8.00, DATE '2027-01-15', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO drive_eligible_branches (drive_id, branch) VALUES
    ('DRV-DEMO-01', 'CSE'), ('DRV-DEMO-01', 'IT'),
    ('DRV-DEMO-02', 'ECE'),
    ('DRV-DEMO-03', 'CSE'), ('DRV-DEMO-03', 'IT'), ('DRV-DEMO-03', 'ECE'),
    ('DRV-DEMO-04', 'CSE'), ('DRV-DEMO-04', 'IT'), ('DRV-DEMO-04', 'MECH')
ON CONFLICT DO NOTHING;

-- 4. Applications with varied eligibility and shortlisting outcomes -----------
INSERT INTO applications (id, student_id, drive_id, status, remarks) VALUES
    ('APP-DEMO-01', 'DEMO-STU-01', 'DRV-DEMO-01', 'SCHEDULED', 'Strong systems fundamentals; scheduled for the technical panel.'),
    ('APP-DEMO-02', 'DEMO-STU-01', 'DRV-DEMO-03', 'SHORTLISTED', 'Shortlisted; awaiting a free interview slot on the analytics panel.'),
    ('APP-DEMO-03', 'DEMO-STU-02', 'DRV-DEMO-01', 'SCHEDULED', 'Top-quartile aptitude score; scheduled for the technical panel.'),
    ('APP-DEMO-04', 'DEMO-STU-02', 'DRV-DEMO-03', 'APPLIED', 'Awaiting eligibility review.'),
    ('APP-DEMO-05', 'DEMO-STU-03', 'DRV-DEMO-02', 'SCHEDULED', 'Relevant VLSI coursework; scheduled for the design panel.'),
    ('APP-DEMO-06', 'DEMO-STU-04', 'DRV-DEMO-03', 'APPLIED', 'Awaiting eligibility review.'),
    ('APP-DEMO-07', 'DEMO-STU-05', 'DRV-DEMO-04', 'INELIGIBLE', 'Does not meet the 8.00 CGPA threshold for this closed drive.')
ON CONFLICT (id) DO NOTHING;

-- 5. Interviewers associated with companies -----------------------------------
INSERT INTO interviewers (id, name, company_id) VALUES
    ('INT-DEMO-01', 'Dr. Grace Hopper', 'CMP-DEMO-01'),
    ('INT-DEMO-02', 'Mr. Alan Kay', 'CMP-DEMO-01'),
    ('INT-DEMO-03', 'Ms. Radhika Menon', 'CMP-DEMO-02'),
    ('INT-DEMO-04', 'Mr. Samuel Fernandes', 'CMP-DEMO-03')
ON CONFLICT (id) DO NOTHING;

-- 6. Rooms with realistic capacities ------------------------------------------
INSERT INTO rooms (id, name, capacity) VALUES
    ('RM-DEMO-01', 'Placement Block - Interview Room 101', 4),
    ('RM-DEMO-02', 'Placement Block - Interview Room 204', 6),
    ('RM-DEMO-03', 'Placement Block - Panel Room 302', 2)
ON CONFLICT (id) DO NOTHING;

-- 7. Confirmed interview sessions (non-overlapping resources) -----------------
--    Deliberately leaves APP-DEMO-02, APP-DEMO-04 and APP-DEMO-06 unscheduled.
INSERT INTO interview_sessions
    (id, application_id, student_id, interviewer_id, room_id, start_time, end_time) VALUES
    ('SES-DEMO-01', 'APP-DEMO-01', 'DEMO-STU-01', 'INT-DEMO-01', 'RM-DEMO-01', TIMESTAMP '2027-03-15 09:00:00', TIMESTAMP '2027-03-15 09:45:00'),
    ('SES-DEMO-02', 'APP-DEMO-03', 'DEMO-STU-02', 'INT-DEMO-02', 'RM-DEMO-01', TIMESTAMP '2027-03-15 10:00:00', TIMESTAMP '2027-03-15 10:45:00'),
    ('SES-DEMO-03', 'APP-DEMO-05', 'DEMO-STU-03', 'INT-DEMO-03', 'RM-DEMO-02', TIMESTAMP '2027-03-16 09:30:00', TIMESTAMP '2027-03-16 10:15:00')
ON CONFLICT (id) DO NOTHING;

COMMIT;
