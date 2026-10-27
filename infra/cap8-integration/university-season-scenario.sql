-- ==============================================================================
-- HireFlow CAP-8.6.3 University Recruitment Season & Room Inventory Seed
-- Isolated Integration Database ONLY (127.0.0.1:55432 / hireflow_cap8_it)
-- ------------------------------------------------------------------------------
-- Namespace: U26 (University Recruitment Season 2026-2027)
-- Invariants:
--  - Idempotent: ON CONFLICT DO NOTHING across all entities.
--  - Exactly 36 engineering interview rooms across K & L Blocks (upper floors).
--  - 6 recruitment drives (including 1 closed drive retaining history).
--  - 6 corporate recruitment partners.
--  - 12 corporate panel interviewers.
--  - 65 realistic synthetic student profiles across CSE, IT, ECE, AI/ML, EEE, MECH.
--  - 84 applications exhibiting realistic status diversity (APPLIED, SHORTLISTED, SCHEDULED, REJECTED, SELECTED, INELIGIBLE).
--  - 15 confirmed non-overlapping interview sessions allocated to K/L rooms.
--  - Preserves all pre-existing DEMO and VIVA22 records untouched.
-- ==============================================================================

BEGIN;

-- ------------------------------------------------------------------------------
-- 1. K & L Block Engineering Interview Rooms (Exact 36 Inventory)
-- Upper floors only: First (F), Second (S), Third (T)
-- Allowed numbers: 02, 03, 04, 08, 09, 10
-- Faculty/amenity numbers (01, 05, 06, 07) and Ground floor labs strictly excluded
-- ------------------------------------------------------------------------------
INSERT INTO rooms (id, name, capacity) VALUES
    -- K Block - First Floor
    ('KF02', 'K Block First Floor - Room KF02', 4),
    ('KF03', 'K Block First Floor - Room KF03', 4),
    ('KF04', 'K Block First Floor - Room KF04', 4),
    ('KF08', 'K Block First Floor - Room KF08', 6),
    ('KF09', 'K Block First Floor - Room KF09', 4),
    ('KF10', 'K Block First Floor - Room KF10', 6),
    -- K Block - Second Floor
    ('KS02', 'K Block Second Floor - Room KS02', 4),
    ('KS03', 'K Block Second Floor - Room KS03', 4),
    ('KS04', 'K Block Second Floor - Room KS04', 4),
    ('KS08', 'K Block Second Floor - Room KS08', 6),
    ('KS09', 'K Block Second Floor - Room KS09', 4),
    ('KS10', 'K Block Second Floor - Room KS10', 6),
    -- K Block - Third Floor
    ('KT02', 'K Block Third Floor - Room KT02', 4),
    ('KT03', 'K Block Third Floor - Room KT03', 4),
    ('KT04', 'K Block Third Floor - Room KT04', 4),
    ('KT08', 'K Block Third Floor - Room KT08', 6),
    ('KT09', 'K Block Third Floor - Room KT09', 4),
    ('KT10', 'K Block Third Floor - Room KT10', 6),
    -- L Block - First Floor
    ('LF02', 'L Block First Floor - Room LF02', 4),
    ('LF03', 'L Block First Floor - Room LF03', 4),
    ('LF04', 'L Block First Floor - Room LF04', 4),
    ('LF08', 'L Block First Floor - Room LF08', 6),
    ('LF09', 'L Block First Floor - Room LF09', 4),
    ('LF10', 'L Block First Floor - Room LF10', 6),
    -- L Block - Second Floor
    ('LS02', 'L Block Second Floor - Room LS02', 4),
    ('LS03', 'L Block Second Floor - Room LS03', 4),
    ('LS04', 'L Block Second Floor - Room LS04', 4),
    ('LS08', 'L Block Second Floor - Room LS08', 6),
    ('LS09', 'L Block Second Floor - Room LS09', 4),
    ('LS10', 'L Block Second Floor - Room LS10', 6),
    -- L Block - Third Floor
    ('LT02', 'L Block Third Floor - Room LT02', 4),
    ('LT03', 'L Block Third Floor - Room LT03', 4),
    ('LT04', 'L Block Third Floor - Room LT04', 4),
    ('LT08', 'L Block Third Floor - Room LT08', 6),
    ('LT09', 'L Block Third Floor - Room LT09', 4),
    ('LT10', 'L Block Third Floor - Room LT10', 6)
ON CONFLICT (id) DO NOTHING;

-- ------------------------------------------------------------------------------
-- 2. Corporate Recruitment Partners
-- ------------------------------------------------------------------------------
INSERT INTO companies (id, name, industry) VALUES
    ('CMP-U26-01', 'Aether Cloud Networks', 'Cloud & Distributed Systems'),
    ('CMP-U26-02', 'Cognitive Robotics Labs', 'AI, Robotics & Computer Vision'),
    ('CMP-U26-03', 'FinMetrics Quant Technologies', 'Quantitative Finance & Analytics'),
    ('CMP-U26-04', 'Silicon Wave Microelectronics', 'VLSI, FPGA & Embedded Systems'),
    ('CMP-U26-05', 'Zenith Strategy Consultants', 'Enterprise Technology & Strategy'),
    ('CMP-U26-06', 'BioInformatics Core Systems', 'Computational Biology & Health-IT')
ON CONFLICT (id) DO NOTHING;

-- ------------------------------------------------------------------------------
-- 3. Recruitment Drives (Including Closed Drive DRV-U26-06)
-- ------------------------------------------------------------------------------
INSERT INTO recruitment_drives (id, company_id, job_title, minimum_cgpa, application_deadline, is_closed) VALUES
    ('DRV-U26-01', 'CMP-U26-01', 'Distributed Systems Engineer - I', 7.50, DATE '2027-04-15', FALSE),
    ('DRV-U26-02', 'CMP-U26-02', 'Machine Learning Research Engineer', 8.00, DATE '2027-04-20', FALSE),
    ('DRV-U26-03', 'CMP-U26-03', 'Quantitative Data Analyst', 7.00, DATE '2027-04-25', FALSE),
    ('DRV-U26-04', 'CMP-U26-04', 'Embedded Systems & Firmware Engineer', 6.80, DATE '2027-05-01', FALSE),
    ('DRV-U26-05', 'CMP-U26-05', 'Technology Risk Consultant', 7.20, DATE '2027-05-10', FALSE),
    ('DRV-U26-06', 'CMP-U26-06', 'Bio-Data Platform Engineer', 7.50, DATE '2026-09-30', TRUE)
ON CONFLICT (id) DO NOTHING;

-- Eligible branches per drive
INSERT INTO drive_eligible_branches (drive_id, branch) VALUES
    ('DRV-U26-01', 'CSE'), ('DRV-U26-01', 'IT'),
    ('DRV-U26-02', 'CSE'), ('DRV-U26-02', 'IT'), ('DRV-U26-02', 'AI/ML'),
    ('DRV-U26-03', 'CSE'), ('DRV-U26-03', 'IT'), ('DRV-U26-03', 'ECE'),
    ('DRV-U26-04', 'ECE'), ('DRV-U26-04', 'EEE'),
    ('DRV-U26-05', 'CSE'), ('DRV-U26-05', 'IT'), ('DRV-U26-05', 'ECE'), ('DRV-U26-05', 'MECH'),
    ('DRV-U26-06', 'CSE'), ('DRV-U26-06', 'IT'), ('DRV-U26-06', 'BIO')
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------------------------
-- 4. Corporate Interviewers
-- ------------------------------------------------------------------------------
INSERT INTO interviewers (id, name, company_id) VALUES
    ('INT-U26-01', 'Dr. Aris Thorne', 'CMP-U26-01'),
    ('INT-U26-02', 'Ms. Elena Rostova', 'CMP-U26-01'),
    ('INT-U26-03', 'Dr. Kenji Sato', 'CMP-U26-02'),
    ('INT-U26-04', 'Ms. Maya Lin', 'CMP-U26-02'),
    ('INT-U26-05', 'Mr. David Sterling', 'CMP-U26-03'),
    ('INT-U26-06', 'Ms. Priya Deshmukh', 'CMP-U26-03'),
    ('INT-U26-07', 'Dr. Rajesh Varma', 'CMP-U26-04'),
    ('INT-U26-08', 'Mr. Liam O''Connor', 'CMP-U26-04'),
    ('INT-U26-09', 'Ms. Sarah Jenkins', 'CMP-U26-05'),
    ('INT-U26-10', 'Mr. Arjun Singhania', 'CMP-U26-05'),
    ('INT-U26-11', 'Dr. Ananya Sen', 'CMP-U26-06'),
    ('INT-U26-12', 'Dr. Marcus Vance', 'CMP-U26-06')
ON CONFLICT (id) DO NOTHING;

-- ------------------------------------------------------------------------------
-- 5. Student Profiles (65 Realistic Synthetic Students)
-- Safe email domain @example.test. Realistic CGPA diversity (6.20 to 9.85).
-- ------------------------------------------------------------------------------
INSERT INTO students (id, name, email, branch, cgpa, graduation_year, active) VALUES
    ('STU-U26-001', 'Aditi Rao', 'aditi.rao@example.test', 'CSE', 9.12, 2026, TRUE),
    ('STU-U26-002', 'Rohan Mukherjee', 'rohan.mukherjee@example.test', 'CSE', 8.85, 2026, TRUE),
    ('STU-U26-003', 'Tanvi Bhat', 'tanvi.bhat@example.test', 'CSE', 8.64, 2026, TRUE),
    ('STU-U26-004', 'Vikram Joshi', 'vikram.joshi@example.test', 'CSE', 7.95, 2026, TRUE),
    ('STU-U26-005', 'Sneha Pillai', 'sneha.pillai@example.test', 'CSE', 8.42, 2026, TRUE),
    ('STU-U26-006', 'Karthik Natarajan', 'karthik.natarajan@example.test', 'CSE', 9.35, 2026, TRUE),
    ('STU-U26-007', 'Ananya Gupta', 'ananya.gupta@example.test', 'CSE', 8.10, 2026, TRUE),
    ('STU-U26-008', 'Siddharth Menon', 'siddharth.menon@example.test', 'CSE', 7.78, 2026, TRUE),
    ('STU-U26-009', 'Pooja Hegde', 'pooja.hegde@example.test', 'CSE', 8.90, 2026, TRUE),
    ('STU-U26-010', 'Varun Kapoor', 'varun.kapoor@example.test', 'CSE', 7.45, 2026, TRUE),
    ('STU-U26-011', 'Neha Deshpande', 'neha.deshpande@example.test', 'CSE', 8.25, 2026, TRUE),
    ('STU-U26-012', 'Arjun Nambiar', 'arjun.nambiar@example.test', 'CSE', 9.60, 2026, TRUE),
    ('STU-U26-013', 'Kavya Seshadri', 'kavya.seshadri@example.test', 'CSE', 8.70, 2026, TRUE),
    ('STU-U26-014', 'Rishi Kulkarni', 'rishi.kulkarni@example.test', 'CSE', 7.30, 2026, TRUE),
    ('STU-U26-015', 'Divya Chandran', 'divya.chandran@example.test', 'CSE', 8.55, 2026, TRUE),
    ('STU-U26-016', 'Manish Agarwal', 'manish.agarwal@example.test', 'CSE', 6.95, 2026, TRUE),
    ('STU-U26-017', 'Ishita Banerji', 'ishita.banerji@example.test', 'CSE', 9.20, 2026, TRUE),
    ('STU-U26-018', 'Gaurav Vats', 'gaurav.vats@example.test', 'CSE', 7.82, 2026, TRUE),
    ('STU-U26-019', 'Shruti Saxena', 'shruti.saxena@example.test', 'CSE', 8.38, 2026, TRUE),
    ('STU-U26-020', 'Abhishek Dubey', 'abhishek.dubey@example.test', 'CSE', 7.15, 2026, TRUE),
    ('STU-U26-021', 'Aishwarya Nair', 'aishwarya.nair@example.test', 'CSE', 9.45, 2026, TRUE),
    ('STU-U26-022', 'Nikhil Choudhury', 'nikhil.choudhury@example.test', 'CSE', 8.05, 2026, TRUE),

    ('STU-U26-023', 'Harshita Pandey', 'harshita.pandey@example.test', 'IT', 8.72, 2026, TRUE),
    ('STU-U26-024', 'Pranav Trivedi', 'pranav.trivedi@example.test', 'IT', 8.15, 2026, TRUE),
    ('STU-U26-025', 'Ritika Kaushik', 'ritika.kaushik@example.test', 'IT', 7.85, 2026, TRUE),
    ('STU-U26-026', 'Akash Sundaram', 'akash.sundaram@example.test', 'IT', 9.05, 2026, TRUE),
    ('STU-U26-027', 'Bhavna Chauhan', 'bhavna.chauhan@example.test', 'IT', 8.40, 2026, TRUE),
    ('STU-U26-028', 'Deepak Rathi', 'deepak.rathi@example.test', 'IT', 7.60, 2026, TRUE),
    ('STU-U26-029', 'Shreya Goswami', 'shreya.goswami@example.test', 'IT', 8.92, 2026, TRUE),
    ('STU-U26-030', 'Mayank Tiwari', 'mayank.tiwari@example.test', 'IT', 7.20, 2026, TRUE),
    ('STU-U26-031', 'Swati Mallick', 'swati.mallick@example.test', 'IT', 8.65, 2026, TRUE),
    ('STU-U26-032', 'Alok Sengupta', 'alok.sengupta@example.test', 'IT', 6.85, 2026, TRUE),
    ('STU-U26-033', 'Meghna Roy', 'meghna.roy@example.test', 'IT', 9.28, 2026, TRUE),
    ('STU-U26-034', 'Kunal Merchant', 'kunal.merchant@example.test', 'IT', 7.90, 2026, TRUE),
    ('STU-U26-035', 'Pallavi Jha', 'pallavi.jha@example.test', 'IT', 8.35, 2026, TRUE),
    ('STU-U26-036', 'Vivek Singhal', 'vivek.singhal@example.test', 'IT', 7.50, 2026, TRUE),

    ('STU-U26-037', 'Tarun Vasudevan', 'tarun.vasudevan@example.test', 'ECE', 8.80, 2026, TRUE),
    ('STU-U26-038', 'Lavanya Srinivas', 'lavanya.srinivas@example.test', 'ECE', 8.25, 2026, TRUE),
    ('STU-U26-039', 'Rahul Venkatesh', 'rahul.venkatesh@example.test', 'ECE', 7.65, 2026, TRUE),
    ('STU-U26-040', 'Gayatri Raman', 'gayatri.raman@example.test', 'ECE', 9.15, 2026, TRUE),
    ('STU-U26-041', 'Aditya Somani', 'aditya.somani@example.test', 'ECE', 7.40, 2026, TRUE),
    ('STU-U26-042', 'Rhea Chakraborty', 'rhea.chakraborty@example.test', 'ECE', 8.50, 2026, TRUE),
    ('STU-U26-043', 'Sanjay Gokhale', 'sanjay.gokhale@example.test', 'ECE', 6.70, 2026, TRUE),
    ('STU-U26-044', 'Anusha Pai', 'anusha.pai@example.test', 'ECE', 8.95, 2026, TRUE),
    ('STU-U26-045', 'Chetan Marathe', 'chetan.marathe@example.test', 'ECE', 7.80, 2026, TRUE),
    ('STU-U26-046', 'Monika Varma', 'monika.varma@example.test', 'ECE', 8.12, 2026, TRUE),
    ('STU-U26-047', 'Kiran Bedi', 'kiran.bedi@example.test', 'ECE', 7.05, 2026, TRUE),
    ('STU-U26-048', 'Naveen Reddy', 'naveen.reddy@example.test', 'ECE', 8.60, 2026, TRUE),

    ('STU-U26-049', 'Aravind Swaminathan', 'aravind.s@example.test', 'AI/ML', 9.55, 2026, TRUE),
    ('STU-U26-050', 'Devika Namboodiri', 'devika.n@example.test', 'AI/ML', 9.10, 2026, TRUE),
    ('STU-U26-051', 'Sameer Qureshi', 'sameer.q@example.test', 'AI/ML', 8.75, 2026, TRUE),
    ('STU-U26-052', 'Zoya Akhtar', 'zoya.a@example.test', 'AI/ML', 8.45, 2026, TRUE),
    ('STU-U26-053', 'Farhan Merchant', 'farhan.m@example.test', 'AI/ML', 7.95, 2026, TRUE),
    ('STU-U26-054', 'Bhavik Shah', 'bhavik.s@example.test', 'AI/ML', 9.80, 2026, TRUE),
    ('STU-U26-055', 'Ira Singhal', 'ira.s@example.test', 'AI/ML', 8.20, 2026, TRUE),
    ('STU-U26-056', 'Omkar Patil', 'omkar.p@example.test', 'AI/ML', 7.35, 2026, TRUE),

    ('STU-U26-057', 'Shantanu Mohapatra', 'shantanu.m@example.test', 'EEE', 8.30, 2026, TRUE),
    ('STU-U26-058', 'Archana Sahu', 'archana.sahu@example.test', 'EEE', 7.90, 2026, TRUE),
    ('STU-U26-059', 'Tushar Panda', 'tushar.panda@example.test', 'EEE', 7.15, 2026, TRUE),
    ('STU-U26-060', 'Lipi Tripathy', 'lipi.tripathy@example.test', 'EEE', 8.65, 2026, TRUE),
    ('STU-U26-061', 'Rajesh Barik', 'rajesh.barik@example.test', 'EEE', 6.80, 2026, TRUE),

    ('STU-U26-062', 'Kartik Sawant', 'kartik.sawant@example.test', 'MECH', 7.85, 2026, TRUE),
    ('STU-U26-063', 'Pranjal Shirke', 'pranjal.shirke@example.test', 'MECH', 8.20, 2026, TRUE),
    ('STU-U26-064', 'Akshat Salunkhe', 'akshat.salunkhe@example.test', 'MECH', 6.45, 2026, FALSE),
    ('STU-U26-065', 'Tejas Mane', 'tejas.mane@example.test', 'MECH', 7.10, 2026, FALSE)
ON CONFLICT (id) DO NOTHING;

-- ------------------------------------------------------------------------------
-- 6. Applications (84 Applications Across 6 Recruitment Drives)
-- Statuses strictly adhere to legitimate domain state model:
--   APPLIED: initial state awaiting review
--   SHORTLISTED: qualifying candidates awaiting scheduling (zero confirmed sessions)
--   SCHEDULED: scheduled candidates with confirmed appointment
--   REJECTED: candidate rejected with reason
--   SELECTED: final accepted candidate (in closed drive)
--   INELIGIBLE: does not meet academic thresholds
-- ------------------------------------------------------------------------------
INSERT INTO applications (id, student_id, drive_id, status, remarks) VALUES
    -- Drive 1: Distributed Systems Engineer - I (Aether Cloud Networks)
    ('APP-U26-001', 'STU-U26-001', 'DRV-U26-01', 'APPLIED', 'Application submitted; resume screening in progress.'),
    ('APP-U26-002', 'STU-U26-002', 'DRV-U26-01', 'APPLIED', 'Application submitted; awaiting technical questionnaire evaluation.'),
    ('APP-U26-003', 'STU-U26-003', 'DRV-U26-01', 'APPLIED', 'Application submitted; academic credentials verified.'),
    ('APP-U26-004', 'STU-U26-004', 'DRV-U26-01', 'APPLIED', 'Application submitted; awaiting coding assessment cut-off.'),
    ('APP-U26-005', 'STU-U26-005', 'DRV-U26-01', 'SHORTLISTED', 'High score in Distributed Systems lab; shortlisted for technical panel interview.'),
    ('APP-U26-006', 'STU-U26-006', 'DRV-U26-01', 'SHORTLISTED', '9.35 CGPA; exceptional algorithms performance. Shortlisted for technical round.'),
    ('APP-U26-007', 'STU-U26-007', 'DRV-U26-01', 'SHORTLISTED', 'Qualified coding round top 10%; awaiting slot allocation in scheduling studio.'),
    ('APP-U26-008', 'STU-U26-023', 'DRV-U26-01', 'SHORTLISTED', 'IT branch high achiever; strong networking projects. Shortlisted for interview.'),
    ('APP-U26-009', 'STU-U26-008', 'DRV-U26-01', 'SCHEDULED', 'Scheduled in K Block Room KF02 with Dr. Aris Thorne.'),
    ('APP-U26-010', 'STU-U26-009', 'DRV-U26-01', 'SCHEDULED', 'Scheduled in K Block Room KF02 with Dr. Aris Thorne.'),
    ('APP-U26-011', 'STU-U26-024', 'DRV-U26-01', 'SCHEDULED', 'Scheduled in K Block Room KF03 with Ms. Elena Rostova.'),
    ('APP-U26-012', 'STU-U26-010', 'DRV-U26-01', 'REJECTED', 'Did not meet technical screening cut-off score (48/100, threshold 70).'),
    ('APP-U26-013', 'STU-U26-016', 'DRV-U26-01', 'REJECTED', 'CGPA 6.95 below drive minimum threshold of 7.50.'),
    ('APP-U26-014', 'STU-U26-020', 'DRV-U26-01', 'REJECTED', 'CGPA 7.15 below drive minimum threshold of 7.50.'),

    -- Drive 2: Machine Learning Research Engineer (Cognitive Robotics Labs)
    ('APP-U26-015', 'STU-U26-049', 'DRV-U26-02', 'APPLIED', 'Application submitted; reviewing computer vision portfolio.'),
    ('APP-U26-016', 'STU-U26-050', 'DRV-U26-02', 'APPLIED', 'Application submitted; verifying publications.'),
    ('APP-U26-017', 'STU-U26-011', 'DRV-U26-02', 'APPLIED', 'Application submitted; resume screening in progress.'),
    ('APP-U26-018', 'STU-U26-026', 'DRV-U26-02', 'APPLIED', 'Application submitted; aptitude verified.'),
    ('APP-U26-019', 'STU-U26-051', 'DRV-U26-02', 'SHORTLISTED', 'Outstanding ML coursework and Kaggle master rank; shortlisted for interview.'),
    ('APP-U26-020', 'STU-U26-052', 'DRV-U26-02', 'SHORTLISTED', 'Strong deep learning project portfolio; shortlisted for robotics panel.'),
    ('APP-U26-021', 'STU-U26-012', 'DRV-U26-02', 'SHORTLISTED', 'Top-ranking CSE candidate; shortlisted for technical round.'),
    ('APP-U26-022', 'STU-U26-013', 'DRV-U26-02', 'SHORTLISTED', 'Published NLP paper; shortlisted for research interview.'),
    ('APP-U26-023', 'STU-U26-053', 'DRV-U26-02', 'SCHEDULED', 'Scheduled in K Block Room KS02 with Dr. Kenji Sato.'),
    ('APP-U26-024', 'STU-U26-054', 'DRV-U26-02', 'SCHEDULED', 'Scheduled in K Block Room KS02 with Dr. Kenji Sato.'),
    ('APP-U26-025', 'STU-U26-015', 'DRV-U26-02', 'SCHEDULED', 'Scheduled in K Block Room KS03 with Ms. Maya Lin.'),
    ('APP-U26-026', 'STU-U26-014', 'DRV-U26-02', 'REJECTED', 'CGPA 7.30 below drive minimum threshold of 8.00.'),
    ('APP-U26-027', 'STU-U26-028', 'DRV-U26-02', 'REJECTED', 'CGPA 7.60 below drive minimum threshold of 8.00.'),
    ('APP-U26-028', 'STU-U26-056', 'DRV-U26-02', 'REJECTED', 'CGPA 7.35 below drive minimum threshold of 8.00.'),

    -- Drive 3: Quantitative Data Analyst (FinMetrics Quant Technologies)
    ('APP-U26-029', 'STU-U26-017', 'DRV-U26-03', 'APPLIED', 'Application submitted; reviewing quantitative aptitude scores.'),
    ('APP-U26-030', 'STU-U26-027', 'DRV-U26-03', 'APPLIED', 'Application submitted; verifying academic record.'),
    ('APP-U26-031', 'STU-U26-037', 'DRV-U26-03', 'APPLIED', 'ECE applicant; submitted math olympiad distinction credentials.'),
    ('APP-U26-032', 'STU-U26-038', 'DRV-U26-03', 'APPLIED', 'Application submitted; reviewing statistical modeling coursework.'),
    ('APP-U26-033', 'STU-U26-018', 'DRV-U26-03', 'SHORTLISTED', 'Top decile quantitative aptitude score; shortlisted for quant panel.'),
    ('APP-U26-034', 'STU-U26-019', 'DRV-U26-03', 'SHORTLISTED', 'Strong econometrics and Python data science background; shortlisted.'),
    ('APP-U26-035', 'STU-U26-029', 'DRV-U26-03', 'SHORTLISTED', '8.92 CGPA; excellent problem-solving record. Shortlisted for quant assessment.'),
    ('APP-U26-036', 'STU-U26-039', 'DRV-U26-03', 'SHORTLISTED', 'ECE background with strong stochastic calculus knowledge; shortlisted.'),
    ('APP-U26-037', 'STU-U26-021', 'DRV-U26-03', 'SCHEDULED', 'Scheduled in L Block Room LF02 with Mr. David Sterling.'),
    ('APP-U26-038', 'STU-U26-031', 'DRV-U26-03', 'SCHEDULED', 'Scheduled in L Block Room LF02 with Mr. David Sterling.'),
    ('APP-U26-039', 'STU-U26-040', 'DRV-U26-03', 'SCHEDULED', 'Scheduled in L Block Room LF03 with Ms. Priya Deshmukh.'),
    ('APP-U26-040', 'STU-U26-032', 'DRV-U26-03', 'REJECTED', 'CGPA 6.85 below drive minimum threshold of 7.00.'),
    ('APP-U26-041', 'STU-U26-043', 'DRV-U26-03', 'REJECTED', 'CGPA 6.70 below drive minimum threshold of 7.00.'),
    ('APP-U26-042', 'STU-U26-061', 'DRV-U26-03', 'REJECTED', 'Ineligible engineering branch (EEE not eligible for Drive 3).'),

    -- Drive 4: Embedded Systems & Firmware Engineer (Silicon Wave Microelectronics)
    ('APP-U26-043', 'STU-U26-041', 'DRV-U26-04', 'APPLIED', 'Application submitted; reviewing microcontrollers project portfolio.'),
    ('APP-U26-044', 'STU-U26-057', 'DRV-U26-04', 'APPLIED', 'EEE applicant; reviewing circuit design credentials.'),
    ('APP-U26-045', 'STU-U26-058', 'DRV-U26-04', 'APPLIED', 'Application submitted; checking VLSI lab performance.'),
    ('APP-U26-046', 'STU-U26-042', 'DRV-U26-04', 'SHORTLISTED', 'High proficiency in Verilog and RTOS; shortlisted for embedded panel.'),
    ('APP-U26-047', 'STU-U26-044', 'DRV-U26-04', 'SHORTLISTED', 'FPGA accelerator design project; shortlisted for technical interview.'),
    ('APP-U26-048', 'STU-U26-045', 'DRV-U26-04', 'SHORTLISTED', 'Strong performance in semiconductor devices; shortlisted.'),
    ('APP-U26-049', 'STU-U26-059', 'DRV-U26-04', 'SHORTLISTED', 'EEE student with practical motor-controller firmware experience; shortlisted.'),
    ('APP-U26-050', 'STU-U26-046', 'DRV-U26-04', 'SCHEDULED', 'Scheduled in L Block Room LS02 with Dr. Rajesh Varma.'),
    ('APP-U26-051', 'STU-U26-047', 'DRV-U26-04', 'SCHEDULED', 'Scheduled in L Block Room LS02 with Dr. Rajesh Varma.'),
    ('APP-U26-052', 'STU-U26-060', 'DRV-U26-04', 'SCHEDULED', 'Scheduled in L Block Room LS03 with Mr. Liam O''Connor.'),
    ('APP-U26-053', 'STU-U26-043', 'DRV-U26-04', 'REJECTED', 'CGPA 6.70 below drive minimum threshold of 6.80.'),
    ('APP-U26-054', 'STU-U26-004', 'DRV-U26-04', 'REJECTED', 'Ineligible branch (CSE applicant applied to ECE/EEE exclusive drive).'),
    ('APP-U26-055', 'STU-U26-010', 'DRV-U26-04', 'REJECTED', 'Ineligible branch (CSE applicant applied to ECE/EEE exclusive drive).'),
    ('APP-U26-056', 'STU-U26-064', 'DRV-U26-04', 'REJECTED', 'Inactive student status and ineligible branch (MECH).'),

    -- Drive 5: Technology Risk Consultant (Zenith Strategy Consultants)
    ('APP-U26-057', 'STU-U26-022', 'DRV-U26-05', 'APPLIED', 'Application submitted; case study screening pending.'),
    ('APP-U26-058', 'STU-U26-033', 'DRV-U26-05', 'APPLIED', 'Application submitted; reviewing leadership credentials.'),
    ('APP-U26-059', 'STU-U26-048', 'DRV-U26-05', 'APPLIED', 'Application submitted; case analysis round scheduled.'),
    ('APP-U26-060', 'STU-U26-062', 'DRV-U26-05', 'APPLIED', 'MECH student applicant; academic credentials verified.'),
    ('APP-U26-061', 'STU-U26-025', 'DRV-U26-05', 'SHORTLISTED', 'High case assessment score; shortlisted for consultant interview round.'),
    ('APP-U26-062', 'STU-U26-034', 'DRV-U26-05', 'SHORTLISTED', 'Strong communication and enterprise systems analysis; shortlisted.'),
    ('APP-U26-063', 'STU-U26-035', 'DRV-U26-05', 'SHORTLISTED', 'Top percentile in analytical reasoning; shortlisted for interview.'),
    ('APP-U26-064', 'STU-U26-063', 'DRV-U26-05', 'SHORTLISTED', 'Mechanical engineering student with dual-competency in data; shortlisted.'),
    ('APP-U26-065', 'STU-U26-036', 'DRV-U26-05', 'SCHEDULED', 'Scheduled in K Block Room KT02 with Ms. Sarah Jenkins.'),
    ('APP-U26-066', 'STU-U26-055', 'DRV-U26-05', 'SCHEDULED', 'Scheduled in K Block Room KT02 with Ms. Sarah Jenkins.'),
    ('APP-U26-067', 'STU-U26-006', 'DRV-U26-05', 'SCHEDULED', 'Scheduled in K Block Room KT03 with Mr. Arjun Singhania.'),
    ('APP-U26-068', 'STU-U26-016', 'DRV-U26-05', 'REJECTED', 'CGPA 6.95 below drive minimum threshold of 7.20.'),
    ('APP-U26-069', 'STU-U26-020', 'DRV-U26-05', 'REJECTED', 'CGPA 7.15 below drive minimum threshold of 7.20.'),
    ('APP-U26-070', 'STU-U26-065', 'DRV-U26-05', 'REJECTED', 'Inactive student status and CGPA 7.10 below drive minimum 7.20.'),

    -- Drive 6: Bio-Data Platform Engineer (BioInformatics Core Systems) [CLOSED DRIVE - PRESERVED HISTORY]
    ('APP-U26-071', 'STU-U26-001', 'DRV-U26-06', 'SELECTED', 'Historical Drive Closed: Offer extended and accepted by candidate.'),
    ('APP-U26-072', 'STU-U26-002', 'DRV-U26-06', 'SELECTED', 'Historical Drive Closed: Offer extended and accepted by candidate.'),
    ('APP-U26-073', 'STU-U26-026', 'DRV-U26-06', 'SELECTED', 'Historical Drive Closed: Offer extended and accepted by candidate.'),
    ('APP-U26-074', 'STU-U26-050', 'DRV-U26-06', 'SELECTED', 'Historical Drive Closed: Offer extended and accepted by candidate.'),
    ('APP-U26-075', 'STU-U26-003', 'DRV-U26-06', 'REJECTED', 'Historical Drive Closed: Round 2 technical panel cut-off not met.'),
    ('APP-U26-076', 'STU-U26-007', 'DRV-U26-06', 'REJECTED', 'Historical Drive Closed: Round 2 technical panel cut-off not met.'),
    ('APP-U26-077', 'STU-U26-011', 'DRV-U26-06', 'REJECTED', 'Historical Drive Closed: Candidate withdrew before final round.'),
    ('APP-U26-078', 'STU-U26-027', 'DRV-U26-06', 'REJECTED', 'Historical Drive Closed: Assessment cut-off not met.'),
    ('APP-U26-079', 'STU-U26-035', 'DRV-U26-06', 'REJECTED', 'Historical Drive Closed: Assessment cut-off not met.'),
    ('APP-U26-080', 'STU-U26-037', 'DRV-U26-06', 'INELIGIBLE', 'Branch ECE not eligible for Bio-Data drive (CSE, IT, BIO only).'),
    ('APP-U26-081', 'STU-U26-038', 'DRV-U26-06', 'INELIGIBLE', 'Branch ECE not eligible for Bio-Data drive (CSE, IT, BIO only).'),
    ('APP-U26-082', 'STU-U26-057', 'DRV-U26-06', 'INELIGIBLE', 'Branch EEE not eligible for Bio-Data drive (CSE, IT, BIO only).'),
    ('APP-U26-083', 'STU-U26-062', 'DRV-U26-06', 'INELIGIBLE', 'Branch MECH not eligible for Bio-Data drive (CSE, IT, BIO only).'),
    ('APP-U26-084', 'STU-U26-063', 'DRV-U26-06', 'INELIGIBLE', 'Branch MECH not eligible for Bio-Data drive (CSE, IT, BIO only).')
ON CONFLICT (id) DO NOTHING;

-- ------------------------------------------------------------------------------
-- 7. Confirmed Interview Sessions (15 Non-Overlapping Appointments)
-- Allocated to engineering interview rooms KF02, KF03, KS02, KS03, LF02, LF03, LS02, LS03, KT02, KT03.
-- Zero double-booking of students, interviewers, or rooms (btree_gist verified).
-- ------------------------------------------------------------------------------
INSERT INTO interview_sessions
    (id, application_id, student_id, interviewer_id, room_id, start_time, end_time) VALUES
    -- Drive 1 Sessions (Monday 2027-03-22)
    ('SES-U26-01', 'APP-U26-009', 'STU-U26-008', 'INT-U26-01', 'KF02', TIMESTAMP '2027-03-22 09:00:00', TIMESTAMP '2027-03-22 09:45:00'),
    ('SES-U26-02', 'APP-U26-010', 'STU-U26-009', 'INT-U26-01', 'KF02', TIMESTAMP '2027-03-22 10:00:00', TIMESTAMP '2027-03-22 10:45:00'),
    ('SES-U26-03', 'APP-U26-011', 'STU-U26-024', 'INT-U26-02', 'KF03', TIMESTAMP '2027-03-22 09:30:00', TIMESTAMP '2027-03-22 10:15:00'),

    -- Drive 2 Sessions (Tuesday 2027-03-23)
    ('SES-U26-04', 'APP-U26-023', 'STU-U26-053', 'INT-U26-03', 'KS02', TIMESTAMP '2027-03-23 09:00:00', TIMESTAMP '2027-03-23 09:45:00'),
    ('SES-U26-05', 'APP-U26-024', 'STU-U26-054', 'INT-U26-03', 'KS02', TIMESTAMP '2027-03-23 10:00:00', TIMESTAMP '2027-03-23 10:45:00'),
    ('SES-U26-06', 'APP-U26-025', 'STU-U26-015', 'INT-U26-04', 'KS03', TIMESTAMP '2027-03-23 09:30:00', TIMESTAMP '2027-03-23 10:15:00'),

    -- Drive 3 Sessions (Wednesday 2027-03-24)
    ('SES-U26-07', 'APP-U26-037', 'STU-U26-021', 'INT-U26-05', 'LF02', TIMESTAMP '2027-03-24 09:00:00', TIMESTAMP '2027-03-24 09:45:00'),
    ('SES-U26-08', 'APP-U26-038', 'STU-U26-031', 'INT-U26-05', 'LF02', TIMESTAMP '2027-03-24 10:00:00', TIMESTAMP '2027-03-24 10:45:00'),
    ('SES-U26-09', 'APP-U26-039', 'STU-U26-040', 'INT-U26-06', 'LF03', TIMESTAMP '2027-03-24 09:30:00', TIMESTAMP '2027-03-24 10:15:00'),

    -- Drive 4 Sessions (Thursday 2027-03-25)
    ('SES-U26-10', 'APP-U26-050', 'STU-U26-046', 'INT-U26-07', 'LS02', TIMESTAMP '2027-03-25 09:00:00', TIMESTAMP '2027-03-25 09:45:00'),
    ('SES-U26-11', 'APP-U26-051', 'STU-U26-047', 'INT-U26-07', 'LS02', TIMESTAMP '2027-03-25 10:00:00', TIMESTAMP '2027-03-25 10:45:00'),
    ('SES-U26-12', 'APP-U26-052', 'STU-U26-060', 'INT-U26-08', 'LS03', TIMESTAMP '2027-03-25 09:30:00', TIMESTAMP '2027-03-25 10:15:00'),

    -- Drive 5 Sessions (Friday 2027-03-26)
    ('SES-U26-13', 'APP-U26-065', 'STU-U26-036', 'INT-U26-09', 'KT02', TIMESTAMP '2027-03-26 09:00:00', TIMESTAMP '2027-03-26 09:45:00'),
    ('SES-U26-14', 'APP-U26-066', 'STU-U26-055', 'INT-U26-09', 'KT02', TIMESTAMP '2027-03-26 10:00:00', TIMESTAMP '2027-03-26 10:45:00'),
    ('SES-U26-15', 'APP-U26-067', 'STU-U26-006', 'INT-U26-10', 'KT03', TIMESTAMP '2027-03-26 09:30:00', TIMESTAMP '2027-03-26 10:15:00')
ON CONFLICT (id) DO NOTHING;

COMMIT;
