-- ==============================================================================
-- HireFlow Database Architecture - Development & Demo Seed Data Script
-- Purpose: Populates an isolated development/demo environment with Stage 1
--          seed entities. NOT executed automatically in production Flyway migrations.
-- ==============================================================================

-- 1. Seed Companies
INSERT INTO companies (id, name, industry) VALUES
    ('CMP-001', 'Google Cloud', 'Cloud Computing & AI'),
    ('CMP-002', 'Microsoft', 'Enterprise Software & Cloud'),
    ('CMP-003', 'Goldman Sachs', 'FinTech & Investment Banking'),
    ('CMP-004', 'Tata Motors', 'Automotive & Electric Vehicles')
ON CONFLICT (id) DO NOTHING;

-- 2. Seed Students
INSERT INTO students (id, name, email, branch, cgpa, graduation_year, active) VALUES
    ('STU-101', 'Alice Smith', 'alice.smith@univ.edu', 'CSE', 9.20, 2026, TRUE),
    ('STU-102', 'Bob Jones', 'bob.jones@univ.edu', 'CSE', 8.40, 2026, TRUE),
    ('STU-103', 'Charlie Brown', 'charlie.brown@univ.edu', 'IT', 7.80, 2026, TRUE),
    ('STU-104', 'Diana Prince', 'diana.prince@univ.edu', 'ECE', 8.90, 2026, TRUE),
    ('STU-105', 'Ethan Hunt', 'ethan.hunt@univ.edu', 'ECE', 7.20, 2026, TRUE),
    ('STU-106', 'Fiona Gallagher', 'fiona.g@univ.edu', 'MECH', 8.10, 2026, TRUE),
    ('STU-107', 'George Clark', 'george.clark@univ.edu', 'CIVIL', 7.50, 2026, TRUE),
    ('STU-108', 'Hannah Abbott', 'hannah.a@univ.edu', 'CSE', 6.80, 2026, TRUE),
    ('STU-109', 'Ian Malcolm', 'ian.malcolm@univ.edu', 'IT', 9.50, 2026, TRUE),
    ('STU-110', 'Julia Roberts', 'julia.r@univ.edu', 'CSE', 8.60, 2026, FALSE),
    ('STU-111', 'Kevin Bacon', 'kevin.bacon@univ.edu', 'MECH', 6.50, 2026, TRUE)
ON CONFLICT (id) DO NOTHING;

-- 3. Seed Recruitment Drives
INSERT INTO recruitment_drives (id, company_id, job_title, minimum_cgpa, application_deadline) VALUES
    ('DRV-101', 'CMP-001', 'Software Development Engineer - I', 8.00, CURRENT_DATE + INTERVAL '14 days'),
    ('DRV-102', 'CMP-002', 'Cloud Systems Engineer', 7.50, CURRENT_DATE + INTERVAL '21 days'),
    ('DRV-103', 'CMP-003', 'Quantitative Risk Analyst', 8.50, CURRENT_DATE + INTERVAL '10 days'),
    ('DRV-104', 'CMP-004', 'Automotive Design Engineer', 7.00, CURRENT_DATE + INTERVAL '30 days'),
    ('DRV-105', 'CMP-001', 'Winter Research Intern', 7.00, CURRENT_DATE - INTERVAL '5 days')
ON CONFLICT (id) DO NOTHING;

-- 4. Seed Eligible Branches per Recruitment Drive
INSERT INTO drive_eligible_branches (drive_id, branch) VALUES
    ('DRV-101', 'CSE'),
    ('DRV-101', 'IT'),
    ('DRV-102', 'CSE'),
    ('DRV-102', 'IT'),
    ('DRV-102', 'ECE'),
    ('DRV-103', 'CSE'),
    ('DRV-103', 'IT'),
    ('DRV-103', 'ECE'),
    ('DRV-104', 'MECH'),
    ('DRV-104', 'CIVIL'),
    ('DRV-104', 'ECE'),
    ('DRV-105', 'CSE')
ON CONFLICT (drive_id, branch) DO NOTHING;

-- 5. Seed Clearly Fictional Interviewers
INSERT INTO interviewers (id, name, company_id) VALUES
    ('INT-001', 'Dr. Sarah Chen', 'CMP-001'),
    ('INT-002', 'Marcus Vance', 'CMP-002'),
    ('INT-003', 'David Sterling', 'CMP-003')
ON CONFLICT (id) DO NOTHING;

-- 6. Seed Rooms
INSERT INTO rooms (id, name, capacity) VALUES
    ('RM-101', 'Tech Lab A - Booth 1', 2),
    ('RM-102', 'Tech Lab A - Booth 2', 2)
ON CONFLICT (id) DO NOTHING;

-- 7. Seed Initial Candidate Applications
INSERT INTO applications (id, student_id, drive_id, status, applied_at, remarks) VALUES
    ('APP-101', 'STU-101', 'DRV-101', 'SHORTLISTED', CURRENT_TIMESTAMP, 'Candidate shortlisted for interview'),
    ('APP-102', 'STU-102', 'DRV-101', 'SHORTLISTED', CURRENT_TIMESTAMP, 'Candidate shortlisted for interview'),
    ('APP-103', 'STU-104', 'DRV-102', 'SHORTLISTED', CURRENT_TIMESTAMP, 'Candidate shortlisted for interview'),
    ('APP-104', 'STU-101', 'DRV-102', 'SHORTLISTED', CURRENT_TIMESTAMP, 'Candidate shortlisted for interview'),
    ('APP-105', 'STU-109', 'DRV-102', 'SHORTLISTED', CURRENT_TIMESTAMP, 'Candidate shortlisted for interview'),
    ('APP-106', 'STU-103', 'DRV-102', 'APPLIED', CURRENT_TIMESTAMP, 'Application submitted')
ON CONFLICT (id) DO NOTHING;
