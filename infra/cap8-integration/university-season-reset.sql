-- ==============================================================================
-- HireFlow CAP-8.6.3 University Recruitment Season Reset Manifest
-- Isolated Integration Database ONLY (127.0.0.1:55432 / hireflow_cap8_it)
-- ------------------------------------------------------------------------------
-- Removes ONLY records explicitly created by university-season-scenario.sql
-- using an exact, dependency-aware manifest.
-- Preserves all pre-existing DEMO-* and VIVA22 records completely untouched.
-- ==============================================================================

BEGIN;

-- 1. Confirmed interview sessions (SES-U26-01 to SES-U26-15)
DELETE FROM interview_sessions
 WHERE id IN (
     'SES-U26-01', 'SES-U26-02', 'SES-U26-03', 'SES-U26-04', 'SES-U26-05',
     'SES-U26-06', 'SES-U26-07', 'SES-U26-08', 'SES-U26-09', 'SES-U26-10',
     'SES-U26-11', 'SES-U26-12', 'SES-U26-13', 'SES-U26-14', 'SES-U26-15'
 );

-- 2. Applications (APP-U26-001 to APP-U26-084)
DELETE FROM applications
 WHERE id >= 'APP-U26-001' AND id <= 'APP-U26-084' AND id LIKE 'APP-U26-%';

-- 3. Drive eligible branches
DELETE FROM drive_eligible_branches
 WHERE drive_id IN (
     'DRV-U26-01', 'DRV-U26-02', 'DRV-U26-03',
     'DRV-U26-04', 'DRV-U26-05', 'DRV-U26-06'
 );

-- 4. Recruitment drives
DELETE FROM recruitment_drives
 WHERE id IN (
     'DRV-U26-01', 'DRV-U26-02', 'DRV-U26-03',
     'DRV-U26-04', 'DRV-U26-05', 'DRV-U26-06'
 );

-- 5. Corporate interviewers
DELETE FROM interviewers
 WHERE id IN (
     'INT-U26-01', 'INT-U26-02', 'INT-U26-03', 'INT-U26-04',
     'INT-U26-05', 'INT-U26-06', 'INT-U26-07', 'INT-U26-08',
     'INT-U26-09', 'INT-U26-10', 'INT-U26-11', 'INT-U26-12'
 );

-- 6. Engineering interview rooms (Exact 36 K/L Block inventory)
DELETE FROM rooms
 WHERE id IN (
     'KF02', 'KF03', 'KF04', 'KF08', 'KF09', 'KF10',
     'KS02', 'KS03', 'KS04', 'KS08', 'KS09', 'KS10',
     'KT02', 'KT03', 'KT04', 'KT08', 'KT09', 'KT10',
     'LF02', 'LF03', 'LF04', 'LF08', 'LF09', 'LF10',
     'LS02', 'LS03', 'LS04', 'LS08', 'LS09', 'LS10',
     'LT02', 'LT03', 'LT04', 'LT08', 'LT09', 'LT10'
 );

-- 7. Corporate companies
DELETE FROM companies
 WHERE id IN (
     'CMP-U26-01', 'CMP-U26-02', 'CMP-U26-03',
     'CMP-U26-04', 'CMP-U26-05', 'CMP-U26-06'
 );

-- 8. Student profiles (STU-U26-001 to STU-U26-065)
DELETE FROM students
 WHERE id >= 'STU-U26-001' AND id <= 'STU-U26-065' AND id LIKE 'STU-U26-%';

COMMIT;
