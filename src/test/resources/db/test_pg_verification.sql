-- ==============================================================================
-- HireFlow PostgreSQL Comprehensive Schema & Constraint Verification Test Suite
-- File: src/test/resources/db/test_pg_verification.sql
-- Pure SQL execution suite with granular error inspection
-- ==============================================================================

\set ON_ERROR_STOP off
\pset pager off

\echo '======================================================================'
\echo 'TEST SUITE 1: Verify All Schema Tables and Row Counts'
\echo '======================================================================'
SELECT table_name, 
       (xpath('/row/cnt/text()', xml_count))[1]::text::int as row_count
FROM (
  SELECT table_name, 
         query_to_xml(format('select count(*) as cnt from %I', table_name), false, true, '') as xml_count
  FROM information_schema.tables 
  WHERE table_schema = 'public'
) t
ORDER BY table_name;

\echo '======================================================================'
\echo 'TEST SUITE 2: Duplicate Application Rejection'
\echo 'Expected: SQLSTATE 23505 (unique_violation on uq_application_student_drive)'
\echo '======================================================================'
-- Student STU-101 has already applied to DRV-101 (APP-101).
-- Attempting a second application for STU-101 to DRV-101 MUST be rejected:
INSERT INTO applications (id, student_id, drive_id, status)
VALUES ('APP-DUP-001', 'STU-101', 'DRV-101', 'APPLIED');

\echo '======================================================================'
\echo 'TEST SUITE 3: Room Double-Booking Rejection'
\echo 'Expected: SQLSTATE 23P01 (exclusion_violation on no_overlapping_room_sessions)'
\echo '======================================================================'
-- Step 3a: Insert primary confirmed booking in RM-101 (09:00 - 09:45)
INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time)
VALUES ('SES-TEST-001', 'APP-101', 'STU-101', 'INT-001', 'RM-101', '2026-10-15 09:00:00', '2026-10-15 09:45:00');

-- Step 3b: Attempt conflicting booking for RM-101 at 09:30 - 10:15 (overlap)
INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time)
VALUES ('SES-TEST-002', 'APP-102', 'STU-102', 'INT-002', 'RM-101', '2026-10-15 09:30:00', '2026-10-15 10:15:00');

\echo '======================================================================'
\echo 'TEST SUITE 4: Interviewer Double-Booking Rejection'
\echo 'Expected: SQLSTATE 23P01 (exclusion_violation on no_overlapping_interviewer_sessions)'
\echo '======================================================================'
-- Attempt booking same interviewer (INT-001) in different room (RM-102) at overlapping time (09:15 - 10:00)
INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time)
VALUES ('SES-TEST-003', 'APP-103', 'STU-104', 'INT-001', 'RM-102', '2026-10-15 09:15:00', '2026-10-15 10:00:00');

\echo '======================================================================'
\echo 'TEST SUITE 5: Student Double-Booking Across Different Recruitment Drives'
\echo 'Expected: SQLSTATE 23P01 (exclusion_violation on no_overlapping_student_sessions)'
\echo '======================================================================'
-- Alice (STU-101) is already booked at 09:00 - 09:45 for Google (APP-101).
-- She also has an application to Microsoft (APP-104).
-- Attempting to book Alice for Microsoft at 09:30 - 10:15 in RM-102 with INT-002 MUST be rejected:
INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time)
VALUES ('SES-TEST-004', 'APP-104', 'STU-101', 'INT-002', 'RM-102', '2026-10-15 09:30:00', '2026-10-15 10:15:00');

\echo '======================================================================'
\echo 'TEST SUITE 6: Student Identity Consistency (Composite Foreign Key)'
\echo 'Expected: SQLSTATE 23503 (foreign_key_violation on fk_sessions_application_student)'
\echo '======================================================================'
-- APP-102 belongs to Bob (STU-102).
-- Attempting to insert a session for APP-102 but specifying STU-103 MUST fail:
INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time)
VALUES ('SES-TEST-005', 'APP-102', 'STU-103', 'INT-002', 'RM-102', '2026-10-15 11:00:00', '2026-10-15 11:45:00');

\echo '======================================================================'
\echo 'TEST SUITE 7: Duplicate Session for Same Application'
\echo 'Expected: SQLSTATE 23505 (unique_violation on uq_session_application)'
\echo '======================================================================'
-- APP-101 is already scheduled in SES-TEST-001. Attempting a second session for APP-101:
INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time)
VALUES ('SES-TEST-006', 'APP-101', 'STU-101', 'INT-001', 'RM-101', '2026-10-15 14:00:00', '2026-10-15 14:45:00');

\echo '======================================================================'
\echo 'TEST SUITE 8: Referential Integrity Enforcement (ON DELETE RESTRICT)'
\echo 'Expected: SQLSTATE 23503 (foreign_key_violation on delete student)'
\echo '======================================================================'
-- Student STU-101 has applications and sessions; deletion must be blocked:
DELETE FROM students WHERE id = 'STU-101';

\echo '======================================================================'
\echo 'TEST SUITE 9: Check Constraint Verification'
\echo 'Expected: SQLSTATE 23514 (check_violation on chk_student_cgpa and time)'
\echo '======================================================================'
-- Invalid CGPA (> 10.00)
INSERT INTO students (id, name, email, branch, cgpa, graduation_year, active)
VALUES ('STU-BAD', 'Invalid CGPA', 'bad.cgpa@univ.edu', 'CSE', 11.50, 2026, TRUE);

-- Invalid Session Time Interval (end <= start)
INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time)
VALUES ('SES-BAD-TIME', 'APP-102', 'STU-102', 'INT-002', 'RM-102', '2026-10-15 10:00:00', '2026-10-15 09:30:00');

-- Clean up test session SES-TEST-001
DELETE FROM interview_sessions WHERE id = 'SES-TEST-001';
\echo '======================================================================'
\echo 'TEST SUITE VERIFICATION COMPLETED'
\echo '======================================================================'
