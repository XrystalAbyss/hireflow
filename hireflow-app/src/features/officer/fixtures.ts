/**
 * FIXTURE: Development Preview Only. Not connected to production database.
 * Independent offline development fixtures for the HireFlow Placement Officer Portal.
 * Strictly validated against the CAP-7 Spring Boot backend contracts.
 */

import type {
  OfficerCompanyDetailDto,
  OfficerDriveDetailDto,
  OfficerApplicationReviewDto,
  OfficerInterviewerDetailDto,
  OfficerRoomDetailDto,
  PagedResponse,
} from '@/types/api'

// ==============================================================================
// 1. Corporate Partner Fixtures
// ==============================================================================

export const FIXTURE_OFFICER_COMPANIES: OfficerCompanyDetailDto[] = [
  {
    id: 'COMP-001',
    name: 'Google India',
    industry: 'Technology & Cloud',
    activeDrivesCount: 2,
    totalDrivesCount: 3,
  },
  {
    id: 'COMP-002',
    name: 'Microsoft IDC',
    industry: 'Software & Systems',
    activeDrivesCount: 1,
    totalDrivesCount: 2,
  },
  {
    id: 'COMP-003',
    name: 'Goldman Sachs',
    industry: 'Financial Services & Quantitative Engineering',
    activeDrivesCount: 1,
    totalDrivesCount: 1,
  },
  {
    id: 'COMP-004',
    name: 'Tata Consultancy Services',
    industry: 'Information Technology & Consulting',
    activeDrivesCount: 0,
    totalDrivesCount: 2,
  },
  {
    id: 'COMP-005',
    name: 'Amazon Web Services',
    industry: 'Cloud Infrastructure & DevOps',
    activeDrivesCount: 1,
    totalDrivesCount: 2,
  },
]

export const FIXTURE_PAGED_COMPANIES: PagedResponse<OfficerCompanyDetailDto> = {
  content: FIXTURE_OFFICER_COMPANIES,
  page: 0,
  size: 20,
  totalElements: FIXTURE_OFFICER_COMPANIES.length,
  totalPages: 1,
  last: true,
}

// ==============================================================================
// 2. Recruitment Drive Fixtures
// ==============================================================================

export const FIXTURE_OFFICER_DRIVES: OfficerDriveDetailDto[] = [
  {
    id: 'DRV-2026-001',
    companyId: 'COMP-001',
    companyName: 'Google India',
    jobTitle: 'Software Development Engineer I',
    minimumCgpa: 8.0,
    eligibleBranches: ['CSE', 'ECE', 'IT'],
    applicationDeadline: '2026-11-15',
    closed: false,
    totalApplications: 4,
    shortlistedApplications: 2,
  },
  {
    id: 'DRV-2026-002',
    companyId: 'COMP-002',
    companyName: 'Microsoft IDC',
    jobTitle: 'Cloud Solutions Architect Associate',
    minimumCgpa: 7.5,
    eligibleBranches: ['CSE', 'ECE', 'EE', 'ME'],
    applicationDeadline: '2026-11-20',
    closed: false,
    totalApplications: 3,
    shortlistedApplications: 1,
  },
  {
    id: 'DRV-2026-003',
    companyId: 'COMP-003',
    companyName: 'Goldman Sachs',
    jobTitle: 'Quantitative Analyst - Global Markets',
    minimumCgpa: 8.5,
    eligibleBranches: ['CSE', 'MATH', 'EE'],
    applicationDeadline: '2026-11-10',
    closed: false,
    totalApplications: 2,
    shortlistedApplications: 2,
  },
  {
    id: 'DRV-2026-004',
    companyId: 'COMP-004',
    companyName: 'Tata Consultancy Services',
    jobTitle: 'Systems Engineer',
    minimumCgpa: 6.5,
    eligibleBranches: ['CSE', 'ECE', 'EE', 'ME', 'CE'],
    applicationDeadline: '2026-10-01',
    closed: true, // Authoritatively closed
    totalApplications: 5,
    shortlistedApplications: 3,
  },
  {
    id: 'DRV-2026-005',
    companyId: 'COMP-005',
    companyName: 'Amazon Web Services',
    jobTitle: 'DevOps & Systems Specialist',
    minimumCgpa: 7.8,
    eligibleBranches: ['CSE', 'IT'],
    applicationDeadline: '2026-11-30',
    closed: false,
    totalApplications: 2,
    shortlistedApplications: 0,
  },
]

export const FIXTURE_PAGED_DRIVES: PagedResponse<OfficerDriveDetailDto> = {
  content: FIXTURE_OFFICER_DRIVES,
  page: 0,
  size: 20,
  totalElements: FIXTURE_OFFICER_DRIVES.length,
  totalPages: 1,
  last: true,
}

// ==============================================================================
// 3. Candidate Review Fixtures (Offset-Free LocalDateTime appliedAt)
// ==============================================================================

export const FIXTURE_OFFICER_APPLICATIONS: OfficerApplicationReviewDto[] = [
  {
    id: 'APP-2026-001',
    studentId: 'STU-2026-042',
    studentName: 'Aarav Sharma',
    studentEmail: 'aarav.sharma@hireflow.local',
    branch: 'Computer Science and Engineering',
    cgpa: 8.92,
    driveId: 'DRV-2026-001',
    jobTitle: 'Software Development Engineer I',
    companyName: 'Google India',
    status: 'APPLIED',
    appliedAt: '2026-10-18T10:15:00', // Offset-free LocalDateTime
    remarks: 'Strong candidate with distributed systems and OS background',
  },
  {
    id: 'APP-2026-002',
    studentId: 'STU-2026-088',
    studentName: 'Priya Iyer',
    studentEmail: 'priya.iyer@hireflow.local',
    branch: 'Computer Science and Engineering',
    cgpa: 9.45,
    driveId: 'DRV-2026-001',
    jobTitle: 'Software Development Engineer I',
    companyName: 'Google India',
    status: 'SHORTLISTED',
    appliedAt: '2026-10-18T11:30:00',
    remarks: 'Dean honor list, algorithms competitive programming gold medalist',
  },
  {
    id: 'APP-2026-003',
    studentId: 'STU-2026-104',
    studentName: 'Rohan Verma',
    studentEmail: 'rohan.verma@hireflow.local',
    branch: 'Electronics and Communication',
    cgpa: 8.12,
    driveId: 'DRV-2026-001',
    jobTitle: 'Software Development Engineer I',
    companyName: 'Google India',
    status: 'SHORTLISTED',
    appliedAt: '2026-10-18T14:45:00',
    remarks: 'Embedded firmware and networking projects',
  },
  {
    id: 'APP-2026-004',
    studentId: 'STU-2026-133',
    studentName: 'Ananya Deshmukh',
    studentEmail: 'ananya.deshmukh@hireflow.local',
    branch: 'Information Technology',
    cgpa: 8.35,
    driveId: 'DRV-2026-001',
    jobTitle: 'Software Development Engineer I',
    companyName: 'Google India',
    status: 'APPLIED',
    appliedAt: '2026-10-19T09:00:00',
    remarks: 'Full-stack web application capstone with real-time analytics',
  },
  {
    id: 'APP-2026-005',
    studentId: 'STU-2026-042',
    studentName: 'Aarav Sharma',
    studentEmail: 'aarav.sharma@hireflow.local',
    branch: 'Computer Science and Engineering',
    cgpa: 8.92,
    driveId: 'DRV-2026-002',
    jobTitle: 'Cloud Solutions Architect Associate',
    companyName: 'Microsoft IDC',
    status: 'SHORTLISTED',
    appliedAt: '2026-10-18T16:00:00',
    remarks: 'Certified Azure fundamentals',
  },
  {
    id: 'APP-2026-006',
    studentId: 'STU-2026-077',
    studentName: 'Kavita Nair',
    studentEmail: 'kavita.nair@hireflow.local',
    branch: 'Electrical Engineering',
    cgpa: 7.85,
    driveId: 'DRV-2026-002',
    jobTitle: 'Cloud Solutions Architect Associate',
    companyName: 'Microsoft IDC',
    status: 'APPLIED',
    appliedAt: '2026-10-19T08:20:00',
    remarks: 'Power systems computing and smart grid monitoring thesis',
  },
  {
    id: 'APP-2026-007',
    studentId: 'STU-2026-118',
    studentName: 'Vikram Joshi',
    studentEmail: 'vikram.joshi@hireflow.local',
    branch: 'Mechanical Engineering',
    cgpa: 7.62,
    driveId: 'DRV-2026-002',
    jobTitle: 'Cloud Solutions Architect Associate',
    companyName: 'Microsoft IDC',
    status: 'APPLIED',
    appliedAt: '2026-10-19T10:10:00',
    remarks: 'CAD automation and cloud simulation pipeline',
  },
]

// ==============================================================================
// 4. Resource Administration Fixtures (Raw JSON Arrays)
// ==============================================================================

export const FIXTURE_OFFICER_INTERVIEWERS: OfficerInterviewerDetailDto[] = [
  {
    id: 'INTV-001',
    name: 'Dr. Sarah Jenkins',
    companyId: 'COMP-001',
    companyName: 'Google India',
  },
  {
    id: 'INTV-002',
    name: 'David Chen',
    companyId: 'COMP-001',
    companyName: 'Google India',
  },
  {
    id: 'INTV-003',
    name: 'Pooja Reddy',
    companyId: 'COMP-002',
    companyName: 'Microsoft IDC',
  },
  {
    id: 'INTV-004',
    name: 'Marcus Vance',
    companyId: 'COMP-003',
    companyName: 'Goldman Sachs',
  },
  {
    id: 'INTV-005',
    name: 'Elena Rostova',
    companyId: 'COMP-005',
    companyName: 'Amazon Web Services',
  },
]

export const FIXTURE_OFFICER_ROOMS: OfficerRoomDetailDto[] = [
  {
    id: 'RM-101',
    name: 'Interview Suite A (Oak Room)',
    capacity: 4,
  },
  {
    id: 'RM-102',
    name: 'Interview Suite B (Pine Room)',
    capacity: 4,
  },
  {
    id: 'RM-201',
    name: 'Executive Boardroom 1',
    capacity: 10,
  },
  {
    id: 'RM-202',
    name: 'Conference Lab West',
    capacity: 6,
  },
  {
    id: 'RM-305',
    name: 'Placement Annex Pod 3',
    capacity: 2,
  },
]
