-- ==============================================================================
-- HireFlow Demo Scenario Reset (isolated CAP-8.5.2 integration database ONLY)
-- ------------------------------------------------------------------------------
-- Removes ONLY records created by demo-scenario.sql (all identified by the
-- DEMO- / APP-DEMO- / SES-DEMO- prefix). No other row is touched.
-- Deletion order respects foreign-key dependencies.
-- ==============================================================================

BEGIN;

DELETE FROM interview_sessions
 WHERE id LIKE 'SES-DEMO-%' OR application_id LIKE 'APP-DEMO-%';

DELETE FROM applications
 WHERE id LIKE 'APP-DEMO-%';

DELETE FROM drive_eligible_branches
 WHERE drive_id LIKE 'DRV-DEMO-%';

DELETE FROM recruitment_drives
 WHERE id LIKE 'DRV-DEMO-%';

DELETE FROM interviewers
 WHERE id LIKE 'INT-DEMO-%';

DELETE FROM rooms
 WHERE id LIKE 'RM-DEMO-%';

DELETE FROM companies
 WHERE id LIKE 'CMP-DEMO-%';

-- Demo student accounts depend on the demo student rows.
DELETE FROM user_accounts
 WHERE username IN ('demo.student');

DELETE FROM students
 WHERE id LIKE 'DEMO-STU-%';

COMMIT;
