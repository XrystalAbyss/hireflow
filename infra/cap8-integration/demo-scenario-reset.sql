-- ==============================================================================
-- HireFlow Demo Scenario Reset (isolated CAP-8 integration database ONLY)
-- ------------------------------------------------------------------------------
-- Removes ONLY records explicitly created by demo-scenario.sql using an exact,
-- dependency-aware manifest.
-- Preserves manually created and reserved viva records (such as DRV-DEMO-VIVA22,
-- CMP-DEMO-VIVA22, RM-DEMO-VIVA22, INT-DEMO-VIVA22, APP-DEMO-VIVA22, SES-DEMO-VIVA22-*).
-- Deletion order strictly respects foreign-key dependencies.
-- ==============================================================================

BEGIN;

-- 1. Interview sessions created by demo-scenario.sql
DELETE FROM interview_sessions
 WHERE id IN ('SES-DEMO-01', 'SES-DEMO-02', 'SES-DEMO-03');

-- 2. Applications created by demo-scenario.sql
DELETE FROM applications
 WHERE id IN (
     'APP-DEMO-01', 'APP-DEMO-02', 'APP-DEMO-03', 'APP-DEMO-04',
     'APP-DEMO-05', 'APP-DEMO-06', 'APP-DEMO-07'
 );

-- 3. Drive eligible branches for demo drives
DELETE FROM drive_eligible_branches
 WHERE drive_id IN ('DRV-DEMO-01', 'DRV-DEMO-02', 'DRV-DEMO-03', 'DRV-DEMO-04');

-- 4. Recruitment drives created by demo-scenario.sql
DELETE FROM recruitment_drives
 WHERE id IN ('DRV-DEMO-01', 'DRV-DEMO-02', 'DRV-DEMO-03', 'DRV-DEMO-04');

-- 5. Interviewers created by demo-scenario.sql
DELETE FROM interviewers
 WHERE id IN ('INT-DEMO-01', 'INT-DEMO-02', 'INT-DEMO-03', 'INT-DEMO-04');

-- 6. Rooms created by demo-scenario.sql
DELETE FROM rooms
 WHERE id IN ('RM-DEMO-01', 'RM-DEMO-02', 'RM-DEMO-03');

-- 7. Companies created by demo-scenario.sql
DELETE FROM companies
 WHERE id IN ('CMP-DEMO-01', 'CMP-DEMO-02', 'CMP-DEMO-03', 'CMP-DEMO-04');

-- 8. Demo student user accounts (only if referencing DEMO-STU-01)
DELETE FROM user_accounts
 WHERE username = 'demo.student' AND student_id = 'DEMO-STU-01';

-- 9. Students created by demo-scenario.sql
DELETE FROM students
 WHERE id IN ('DEMO-STU-01', 'DEMO-STU-02', 'DEMO-STU-03', 'DEMO-STU-04', 'DEMO-STU-05');

COMMIT;
