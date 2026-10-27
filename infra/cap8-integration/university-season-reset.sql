-- ==============================================================================
-- HireFlow CAP-8.6.4.1 University Recruitment Season Reset Manifest
-- Isolated Integration Database ONLY (127.0.0.1:55432 / hireflow_cap8_it)
-- ------------------------------------------------------------------------------
-- Removes ONLY records explicitly created by university-season-scenario.sql
-- using exact, primary-key-based ownership manifests.
-- NEVER deletes applications by drive_id membership alone.
-- Fail-closed PL/pgSQL guards abort the transaction if any independently created
-- applications, sessions, room dependencies, or student records exist.
-- Preserves all pre-existing DEMO-* and VIVA22 records completely untouched.
-- ==============================================================================

BEGIN;

-- Safety Guard: Refuse reset if any non-fixture resources or dependencies exist
DO $$
BEGIN
    -- Guard 1: Detect non-fixture applications submitted to U26 drives
    IF EXISTS (
        SELECT 1 FROM applications a
         WHERE a.drive_id IN ('DRV-U26-01', 'DRV-U26-02', 'DRV-U26-03', 'DRV-U26-04', 'DRV-U26-05', 'DRV-U26-06')
           AND a.id NOT IN (SELECT 'APP-U26-' || LPAD(i::text, 3, '0') FROM generate_series(1, 84) AS i)
    ) THEN
        RAISE EXCEPTION 'Reset aborted: independently submitted applications exist for U26 drives.';
    END IF;

    -- Guard 2: Detect non-fixture interview sessions depending on U26 rooms, interviewers, or applications
    IF EXISTS (
        SELECT 1 FROM interview_sessions s
         WHERE s.id NOT IN (SELECT 'SES-U26-' || LPAD(i::text, 2, '0') FROM generate_series(1, 15) AS i)
           AND (s.room_id IN ('KF02','KF03','KF04','KF08','KF09','KF10',
                              'KS02','KS03','KS04','KS08','KS09','KS10',
                              'KT02','KT03','KT04','KT08','KT09','KT10',
                              'LF02','LF03','LF04','LF08','LF09','LF10',
                              'LS02','LS03','LS04','LS08','LS09','LS10',
                              'LT02','LT03','LT04','LT08','LT09','LT10')
                OR s.interviewer_id IN (SELECT 'INT-U26-' || LPAD(i::text, 2, '0') FROM generate_series(1, 12) AS i)
                OR s.application_id IN (SELECT 'APP-U26-' || LPAD(i::text, 3, '0') FROM generate_series(1, 84) AS i))
    ) THEN
        RAISE EXCEPTION 'Reset aborted: independently created interview sessions depend on U26 rooms, interviewers, or applications.';
    END IF;

    -- Guard 3: Detect non-fixture applications referencing U26 students
    IF EXISTS (
        SELECT 1 FROM applications a
         WHERE a.student_id IN (SELECT 'STU-U26-' || LPAD(s::text, 3, '0') FROM generate_series(1, 65) AS s)
           AND a.id NOT IN (SELECT 'APP-U26-' || LPAD(i::text, 3, '0') FROM generate_series(1, 84) AS i)
    ) THEN
        RAISE EXCEPTION 'Reset aborted: non-fixture applications exist for U26 students.';
    END IF;

    -- Guard 4: Protect reserved VIVA22 demonstration records
    IF NOT EXISTS (SELECT 1 FROM recruitment_drives WHERE id = 'DRV-DEMO-VIVA22') THEN
        RAISE EXCEPTION 'Reset aborted: integrity sentinel failed: DRV-DEMO-VIVA22 is missing.';
    END IF;
END $$;

-- 1. Confirmed interview sessions (SES-U26-01 to SES-U26-15 by exact ID)
DELETE FROM interview_sessions
 WHERE id IN (SELECT 'SES-U26-' || LPAD(i::text, 2, '0') FROM generate_series(1, 15) AS i);

-- 2. Applications (All 84 U26 applications by exact identifier, NEVER by drive_id membership)
DELETE FROM applications
 WHERE id IN (SELECT 'APP-U26-' || LPAD(i::text, 3, '0') FROM generate_series(1, 84) AS i);

-- 3. Drive eligible branches
DELETE FROM drive_eligible_branches
 WHERE drive_id IN (
     'DRV-U26-01', 'DRV-U26-02', 'DRV-U26-03',
     'DRV-U26-04', 'DRV-U26-05', 'DRV-U26-06'
 );

-- 4. Recruitment drives (Exact 6 U26 drives)
DELETE FROM recruitment_drives
 WHERE id IN (
     'DRV-U26-01', 'DRV-U26-02', 'DRV-U26-03',
     'DRV-U26-04', 'DRV-U26-05', 'DRV-U26-06'
 );

-- 5. Corporate interviewers (Exact 12 U26 interviewers)
DELETE FROM interviewers
 WHERE id IN (SELECT 'INT-U26-' || LPAD(i::text, 2, '0') FROM generate_series(1, 12) AS i);

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

-- 7. Corporate companies (Exact 6 U26 companies)
DELETE FROM companies
 WHERE id IN (
     'CMP-U26-01', 'CMP-U26-02', 'CMP-U26-03',
     'CMP-U26-04', 'CMP-U26-05', 'CMP-U26-06'
 );

-- 8. Student profiles (Exact 65 U26 student IDs generated without range predicates)
DELETE FROM students
 WHERE id IN (SELECT 'STU-U26-' || LPAD(s::text, 3, '0') FROM generate_series(1, 65) AS s);

COMMIT;
