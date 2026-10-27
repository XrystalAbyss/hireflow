-- ==============================================================================
-- HireFlow Database Architecture - Initial Schema Migration
-- Flyway Version: V1
-- Description: Core relational schema for students, companies, recruitment drives,
--              eligible branches, applications, interviewers, rooms, and sessions.
-- ==============================================================================

-- 1. Prerequisites: Enable btree_gist extension for scalar + range exclusion constraints
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ------------------------------------------------------------------------------
-- 2. Students Table
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS students (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    branch VARCHAR(20) NOT NULL,
    cgpa NUMERIC(4, 2) NOT NULL,
    graduation_year INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_students_email UNIQUE (email),
    CONSTRAINT chk_student_cgpa CHECK (cgpa >= 0.00 AND cgpa <= 10.00),
    CONSTRAINT chk_student_grad_year CHECK (graduation_year >= 2000 AND graduation_year <= 2100),
    CONSTRAINT chk_student_email_format CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
);

CREATE INDEX IF NOT EXISTS idx_students_branch ON students(branch);
CREATE INDEX IF NOT EXISTS idx_students_active ON students(active);

-- ------------------------------------------------------------------------------
-- 3. Companies Table
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS companies (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    industry VARCHAR(150) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_companies_name UNIQUE (name)
);

-- ------------------------------------------------------------------------------
-- 4. Recruitment Drives Table
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS recruitment_drives (
    id VARCHAR(32) PRIMARY KEY,
    company_id VARCHAR(32) NOT NULL,
    job_title VARCHAR(150) NOT NULL,
    minimum_cgpa NUMERIC(4, 2) NOT NULL,
    application_deadline DATE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_drives_company FOREIGN KEY (company_id) 
        REFERENCES companies(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_drive_min_cgpa CHECK (minimum_cgpa >= 0.00 AND minimum_cgpa <= 10.00)
);

CREATE INDEX IF NOT EXISTS idx_drives_company_id ON recruitment_drives(company_id);
CREATE INDEX IF NOT EXISTS idx_drives_deadline ON recruitment_drives(application_deadline);

-- ------------------------------------------------------------------------------
-- 5. Drive Eligible Branches Table (Normalized 1-to-many relationship)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS drive_eligible_branches (
    drive_id VARCHAR(32) NOT NULL,
    branch VARCHAR(20) NOT NULL,
    PRIMARY KEY (drive_id, branch),
    CONSTRAINT fk_eligible_branches_drive FOREIGN KEY (drive_id) 
        REFERENCES recruitment_drives(id) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_drive_branches_branch ON drive_eligible_branches(branch);

-- ------------------------------------------------------------------------------
-- 6. Applications Table
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS applications (
    id VARCHAR(32) PRIMARY KEY,
    student_id VARCHAR(32) NOT NULL,
    drive_id VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'APPLIED',
    applied_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remarks TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_applications_student FOREIGN KEY (student_id) 
        REFERENCES students(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_applications_drive FOREIGN KEY (drive_id) 
        REFERENCES recruitment_drives(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    -- Requirement: Uniqueness of student applications per recruitment drive
    CONSTRAINT uq_application_student_drive UNIQUE (student_id, drive_id),
    -- Composite unique key to support validated foreign key references from interview_sessions
    CONSTRAINT uq_application_id_student UNIQUE (id, student_id),
    CONSTRAINT chk_application_status CHECK (
        status IN ('APPLIED', 'ELIGIBLE', 'SHORTLISTED', 'SCHEDULED', 'SELECTED', 'REJECTED', 'INELIGIBLE')
    )
);

CREATE INDEX IF NOT EXISTS idx_applications_student_id ON applications(student_id);
CREATE INDEX IF NOT EXISTS idx_applications_drive_id ON applications(drive_id);
CREATE INDEX IF NOT EXISTS idx_applications_status ON applications(status);

-- ------------------------------------------------------------------------------
-- 7. Interviewers Table
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS interviewers (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    company_id VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_interviewers_company FOREIGN KEY (company_id) 
        REFERENCES companies(id) ON DELETE RESTRICT ON UPDATE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_interviewers_company_id ON interviewers(company_id);

-- ------------------------------------------------------------------------------
-- 8. Rooms Table
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rooms (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    capacity INTEGER NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_rooms_name UNIQUE (name),
    CONSTRAINT chk_room_capacity CHECK (capacity > 0)
);

-- ------------------------------------------------------------------------------
-- 9. Interview Sessions Table
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS interview_sessions (
    id VARCHAR(32) PRIMARY KEY,
    application_id VARCHAR(32) NOT NULL,
    student_id VARCHAR(32) NOT NULL,
    interviewer_id VARCHAR(32) NOT NULL,
    room_id VARCHAR(32) NOT NULL,
    start_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    end_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Requirement: Prevent duplicate appointments for same application
    CONSTRAINT uq_session_application UNIQUE (application_id),
    -- Enforce absolute agreement between interview_sessions.student_id and applications.student_id
    CONSTRAINT fk_sessions_application_student FOREIGN KEY (application_id, student_id) 
        REFERENCES applications(id, student_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_sessions_interviewer FOREIGN KEY (interviewer_id) 
        REFERENCES interviewers(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_sessions_room FOREIGN KEY (room_id) 
        REFERENCES rooms(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_session_time_interval CHECK (end_time > start_time),
    -- Temporal Exclusion Constraints (prevent resource & student double-booking)
    CONSTRAINT no_overlapping_student_sessions EXCLUDE USING gist (
        student_id WITH =,
        tsrange(start_time, end_time, '[)') WITH &&
    ),
    CONSTRAINT no_overlapping_interviewer_sessions EXCLUDE USING gist (
        interviewer_id WITH =,
        tsrange(start_time, end_time, '[)') WITH &&
    ),
    CONSTRAINT no_overlapping_room_sessions EXCLUDE USING gist (
        room_id WITH =,
        tsrange(start_time, end_time, '[)') WITH &&
    )
);

CREATE INDEX IF NOT EXISTS idx_sessions_student_id ON interview_sessions(student_id);
CREATE INDEX IF NOT EXISTS idx_sessions_interviewer_id ON interview_sessions(interviewer_id);
CREATE INDEX IF NOT EXISTS idx_sessions_room_id ON interview_sessions(room_id);
CREATE INDEX IF NOT EXISTS idx_sessions_time_range ON interview_sessions(start_time, end_time);

-- ------------------------------------------------------------------------------
-- 10. Interviewer Availabilities Table (Scheduling Engine Info)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS interviewer_availabilities (
    id VARCHAR(32) PRIMARY KEY,
    interviewer_id VARCHAR(32) NOT NULL,
    start_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    end_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_availability_interviewer FOREIGN KEY (interviewer_id) 
        REFERENCES interviewers(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT chk_availability_time_interval CHECK (end_time > start_time),
    CONSTRAINT no_overlapping_interviewer_availability EXCLUDE USING gist (
        interviewer_id WITH =,
        tsrange(start_time, end_time, '[)') WITH &&
    )
);

CREATE INDEX IF NOT EXISTS idx_availability_interviewer_id ON interviewer_availabilities(interviewer_id);

-- ------------------------------------------------------------------------------
-- 11. Timestamp Maintenance Policy Note
-- ------------------------------------------------------------------------------
-- Note: created_at and updated_at columns are preserved across all relational entities.
-- To maintain zero dependence on OS-restricted procedural languages (e.g. Windows Smart App
-- Control blocking plpgsql.dll), application JDBC UPDATE statements are responsible
-- for setting updated_at = CURRENT_TIMESTAMP.

