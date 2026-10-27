-- ==============================================================================
-- HireFlow PostgreSQL Concurrent Transaction & Resource Locking Verification
-- File: src/test/resources/db/test_pg_concurrency.sql
-- Pure SQL execution suite demonstrating locking protocol and buffer gaps
-- ==============================================================================

\set ON_ERROR_STOP off
\pset pager off

\echo '======================================================================'
\echo 'CONCURRENCY TEST 1: Pessimistic Resource Locking Protocol'
\echo 'Simulating Transaction A: Locking resources in strict deterministic order'
\echo '======================================================================'
BEGIN;

-- 1. Lock rooms alphabetically ASC
SELECT id, name FROM rooms WHERE id IN ('RM-101') ORDER BY id ASC FOR UPDATE;

-- 2. Lock interviewers alphabetically ASC
SELECT id, name FROM interviewers WHERE id IN ('INT-001') ORDER BY id ASC FOR UPDATE;

-- 3. Lock students alphabetically ASC
SELECT id, name FROM students WHERE id IN ('STU-101') ORDER BY id ASC FOR UPDATE;

-- 4. Publish Session A (09:00 - 09:45 in RM-101)
INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, start_time, end_time)
VALUES ('SES-CONC-001', 'APP-101', 'STU-101', 'INT-001', 'RM-101', '2026-10-16 09:00:00', '2026-10-16 09:45:00');

UPDATE applications 
SET status = 'SCHEDULED', remarks = 'Confirmed with 15m buffer'
WHERE id = 'APP-101';

COMMIT;

\echo '======================================================================'
\echo 'CONCURRENCY TEST 2: Buffer Gap Collision Detection'
\echo 'Simulating Transaction B: Checking 15-minute buffer gap'
\echo 'Proposed slot: 09:50 - 10:35 in RM-101'
\echo 'Note: tsrange [09:00, 09:45) and [09:50, 10:35) do NOT physically overlap,'
\echo 'but violating the 15-minute buffer requirement (must not book before 10:00)'
\echo '======================================================================'
BEGIN;

-- Transaction B locks resources in deterministic order
SELECT id, name FROM rooms WHERE id IN ('RM-101') ORDER BY id ASC FOR UPDATE;
SELECT id, name FROM interviewers WHERE id IN ('INT-002') ORDER BY id ASC FOR UPDATE;
SELECT id, name FROM students WHERE id IN ('STU-102') ORDER BY id ASC FOR UPDATE;

-- Query authoritative buffer conflict against committed state
-- Target: 09:50 - 10:35 with 15m buffer requirement (effective buffer zone: 09:35 - 10:50)
SELECT s.id AS conflicting_session_id,
       s.room_id,
       s.start_time,
       s.end_time,
       'BUFFER CONFLICT DETECTED: Existing session ends at ' || s.end_time || ', 15m buffer required until ' || (s.end_time + INTERVAL '15 minutes') AS diagnostic
FROM interview_sessions s
WHERE s.room_id = 'RM-101'
  AND (s.start_time < ('2026-10-16 10:35:00'::timestamp + INTERVAL '15 minutes'))
  AND ((s.end_time + INTERVAL '15 minutes') > '2026-10-16 09:50:00'::timestamp);

ROLLBACK;

-- Clean up test records
DELETE FROM interview_sessions WHERE id = 'SES-CONC-001';
UPDATE applications SET status = 'SHORTLISTED', remarks = 'Shortlisted' WHERE id = 'APP-101';

\echo '======================================================================'
\echo 'CONCURRENCY & BUFFER TESTS COMPLETED'
\echo '======================================================================'
