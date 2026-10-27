import { describe, it, expect } from 'vitest'
import {
  OfficerCompanyDetailSchema,
  PagedOfficerCompanyResponseSchema,
  OfficerCompanyCreateSchema,
  OfficerCompanyUpdateSchema,
  OfficerDriveDetailSchema,
  PagedOfficerDriveResponseSchema,
  OfficerDriveCreateSchema,
  OfficerDriveUpdateSchema,
  OfficerApplicationReviewSchema,
  PagedOfficerApplicationReviewResponseSchema,
  OfficerInterviewerArraySchema,
  OfficerInterviewerCreateSchema,
  OfficerRoomArraySchema,
  OfficerRoomCreateSchema,
  OfficerRoomUpdateSchema,
} from '@/features/officer/api/schemas'

describe('Placement Officer Management API Contracts (CAP-7 Baseline — 18 Endpoints)', () => {
  // ============================================================================
  // 1. Company Management (4 Endpoints)
  // ============================================================================
  describe('Group 1: Company Management Contracts', () => {
    it('validates canonical PagedResponse<OfficerCompanyDetailDto> from GET /api/v1/officer/companies', () => {
      const canonicalPayload = {
        content: [
          {
            id: 'COMP-001',
            name: 'Google India',
            industry: 'Technology',
            activeDrivesCount: 2,
            totalDrivesCount: 3,
          },
          {
            id: 'COMP-002',
            name: 'Microsoft IDC',
            industry: 'Software Engineering',
            activeDrivesCount: 1,
            totalDrivesCount: 2,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
        last: true,
      }

      const parsed = PagedOfficerCompanyResponseSchema.parse(canonicalPayload)
      expect(parsed.content).toHaveLength(2)
      expect(parsed.content[0].id).toBe('COMP-001')
      expect(parsed.content[0].activeDrivesCount).toBe(2)
      expect(parsed.totalElements).toBe(2)
    })

    it('validates canonical OfficerCompanyDetailDto from GET /api/v1/officer/companies/{id}', () => {
      const payload = {
        id: 'COMP-003',
        name: 'Goldman Sachs',
        industry: 'Investment Banking',
        activeDrivesCount: 1,
        totalDrivesCount: 1,
      }

      const parsed = OfficerCompanyDetailSchema.parse(payload)
      expect(parsed.id).toBe('COMP-003')
      expect(parsed.name).toBe('Goldman Sachs')
      expect(parsed.totalDrivesCount).toBe(1)
    })

    it('validates OfficerCompanyCreateDto for POST /api/v1/officer/companies', () => {
      const validPayload = {
        id: 'COMP-NEW',
        name: 'Stripe India',
        industry: 'FinTech',
      }
      expect(OfficerCompanyCreateSchema.parse(validPayload)).toEqual(validPayload)

      // Rejects empty ID
      expect(() =>
        OfficerCompanyCreateSchema.parse({ id: '', name: 'Stripe', industry: 'FinTech' })
      ).toThrow()

      // Rejects ID exceeding 20 chars
      expect(() =>
        OfficerCompanyCreateSchema.parse({
          id: 'COMP-A-VERY-LONG-IDENTIFIER-EXCEEDING-TWENTY',
          name: 'Stripe',
          industry: 'FinTech',
        })
      ).toThrow()
    })

    it('validates OfficerCompanyUpdateDto for PUT /api/v1/officer/companies/{id}', () => {
      const validPayload = {
        name: 'Google Cloud India',
        industry: 'Cloud Infrastructure',
      }
      const parsed = OfficerCompanyUpdateSchema.parse(validPayload)
      expect(parsed.name).toBe('Google Cloud India')

      // ID must not be present in update body (it is in the URL path)
      const payloadWithId = {
        id: 'COMP-001',
        name: 'Google Cloud India',
        industry: 'Cloud Infrastructure',
      }
      // Schema will strip or ignore extra keys, but core validation passes
      expect(OfficerCompanyUpdateSchema.parse(payloadWithId).name).toBe('Google Cloud India')
    })

    it('rejects company with negative drive counts or invalid types', () => {
      const invalid = {
        id: 'COMP-ERR',
        name: 'Bad Corp',
        industry: 'Testing',
        activeDrivesCount: -1,
        totalDrivesCount: 0,
      }
      expect(() => OfficerCompanyDetailSchema.parse(invalid)).toThrow()
    })
  })

  // ============================================================================
  // 2. Recruitment Drive Management (5 Endpoints)
  // ============================================================================
  describe('Group 2: Recruitment Drive Management Contracts', () => {
    it('validates canonical PagedResponse<OfficerDriveDetailDto> from GET /api/v1/officer/drives', () => {
      const canonicalPayload = {
        content: [
          {
            id: 'DRV-2026-001',
            companyId: 'COMP-001',
            companyName: 'Google India',
            jobTitle: 'Software Development Engineer I',
            minimumCgpa: 8.0,
            eligibleBranches: ['CSE', 'ECE'],
            applicationDeadline: '2026-11-15',
            closed: false,
            totalApplications: 12,
            shortlistedApplications: 5,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        last: true,
      }

      const parsed = PagedOfficerDriveResponseSchema.parse(canonicalPayload)
      expect(parsed.content[0].id).toBe('DRV-2026-001')
      expect(parsed.content[0].minimumCgpa).toBe(8.0)
      expect(parsed.content[0].eligibleBranches).toContain('CSE')
      expect(parsed.content[0].closed).toBe(false)
    })

    it('accepts Java Set<String> serialized as array for eligibleBranches', () => {
      const drive = {
        id: 'DRV-2026-002',
        companyId: 'COMP-002',
        companyName: 'Microsoft IDC',
        jobTitle: 'Cloud Architect',
        minimumCgpa: 7.5,
        eligibleBranches: ['CSE', 'IT', 'EE'],
        applicationDeadline: '2026-11-20',
        closed: false,
        totalApplications: 8,
        shortlistedApplications: 2,
      }
      const parsed = OfficerDriveDetailSchema.parse(drive)
      expect(parsed.eligibleBranches).toEqual(['CSE', 'IT', 'EE'])
    })

    it('validates drive closure state from POST /api/v1/officer/drives/{id}/close', () => {
      const closedDrive = {
        id: 'DRV-2026-001',
        companyId: 'COMP-001',
        companyName: 'Google India',
        jobTitle: 'Software Development Engineer I',
        minimumCgpa: 8.0,
        eligibleBranches: ['CSE'],
        applicationDeadline: '2026-11-15',
        closed: true, // Authoritatively closed
        totalApplications: 12,
        shortlistedApplications: 5,
      }
      const parsed = OfficerDriveDetailSchema.parse(closedDrive)
      expect(parsed.closed).toBe(true)
    })

    it('validates OfficerDriveCreateDto for POST /api/v1/officer/drives', () => {
      const validCreate = {
        id: 'DRV-2026-099',
        companyId: 'COMP-001',
        jobTitle: 'Site Reliability Engineer',
        minimumCgpa: 7.0,
        eligibleBranches: ['CSE', 'IT', 'ECE'],
        applicationDeadline: '2026-12-01',
      }
      const parsed = OfficerDriveCreateSchema.parse(validCreate)
      expect(parsed.id).toBe('DRV-2026-099')
      expect(parsed.minimumCgpa).toBe(7.0)

      // Rejects invalid CGPA > 10.0
      expect(() =>
        OfficerDriveCreateSchema.parse({
          ...validCreate,
          minimumCgpa: 10.5,
        })
      ).toThrow()

      // Rejects empty eligible branches
      expect(() =>
        OfficerDriveCreateSchema.parse({
          ...validCreate,
          eligibleBranches: [],
        })
      ).toThrow()

      // Rejects invalid calendar date (e.g., Feb 31)
      expect(() =>
        OfficerDriveCreateSchema.parse({
          ...validCreate,
          applicationDeadline: '2026-02-31',
        })
      ).toThrow()
    })

    it('validates OfficerDriveUpdateDto for PUT /api/v1/officer/drives/{id}', () => {
      const validUpdate = {
        jobTitle: 'Senior Software Engineer',
        minimumCgpa: 8.5,
        eligibleBranches: ['CSE'],
        applicationDeadline: '2026-11-30',
      }
      const parsed = OfficerDriveUpdateSchema.parse(validUpdate)
      expect(parsed.jobTitle).toBe('Senior Software Engineer')
      expect(parsed.minimumCgpa).toBe(8.5)
    })
  })

  // ============================================================================
  // 3. Candidate Review & Shortlisting (3 Endpoints)
  // ============================================================================
  describe('Group 3: Candidate Review & Shortlisting Contracts', () => {
    it('validates PagedResponse<OfficerApplicationReviewDto> from GET /api/v1/officer/drives/{id}/applications', () => {
      const canonicalPayload = {
        content: [
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
            appliedAt: '2026-10-18T10:15:00', // Offset-free LocalDateTime!
            remarks: 'Strong candidate',
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
            remarks: null,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
        last: true,
      }

      const parsed = PagedOfficerApplicationReviewResponseSchema.parse(canonicalPayload)
      expect(parsed.content).toHaveLength(2)
      expect(parsed.content[0].appliedAt).toBe('2026-10-18T10:15:00')
      expect(parsed.content[0].status).toBe('APPLIED')
      expect(parsed.content[1].status).toBe('SHORTLISTED')
      expect(parsed.content[1].remarks).toBeNull()
    })

    it('validates individual candidate review response from GET /api/v1/officer/applications/{id}', () => {
      const singleApp = {
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
        appliedAt: '2026-10-18T10:15:00',
        remarks: 'Candidate notes',
      }
      const parsed = OfficerApplicationReviewSchema.parse(singleApp)
      expect(parsed.id).toBe('APP-2026-001')
      expect(parsed.cgpa).toBe(8.92)
    })

    it('validates shortlisted transition response from POST /api/v1/officer/applications/{id}/shortlist', () => {
      const shortlistedApp = {
        id: 'APP-2026-001',
        studentId: 'STU-2026-042',
        studentName: 'Aarav Sharma',
        studentEmail: 'aarav.sharma@hireflow.local',
        branch: 'Computer Science and Engineering',
        cgpa: 8.92,
        driveId: 'DRV-2026-001',
        jobTitle: 'Software Development Engineer I',
        companyName: 'Google India',
        status: 'SHORTLISTED', // Successfully transitioned
        appliedAt: '2026-10-18T10:15:00',
        remarks: 'Candidate notes',
      }
      const parsed = OfficerApplicationReviewSchema.parse(shortlistedApp)
      expect(parsed.status).toBe('SHORTLISTED')
    })

    it('rejects candidate application with UTC Z appended to appliedAt', () => {
      const badTimestampApp = {
        id: 'APP-2026-001',
        studentId: 'STU-2026-042',
        studentName: 'Aarav Sharma',
        studentEmail: 'aarav.sharma@hireflow.local',
        branch: 'CSE',
        cgpa: 8.92,
        driveId: 'DRV-2026-001',
        jobTitle: 'SDE',
        companyName: 'Google',
        status: 'APPLIED',
        appliedAt: '2026-10-18T10:15:00Z', // REJECTED: must be offset-free
        remarks: null,
      }
      expect(() => OfficerApplicationReviewSchema.parse(badTimestampApp)).toThrow()
    })

    it('rejects invented status strings like PENDING_REVIEW or IN_PROGRESS', () => {
      const invalidStatusApp = {
        id: 'APP-2026-001',
        studentId: 'STU-2026-042',
        studentName: 'Aarav Sharma',
        studentEmail: 'aarav.sharma@hireflow.local',
        branch: 'CSE',
        cgpa: 8.92,
        driveId: 'DRV-2026-001',
        jobTitle: 'SDE',
        companyName: 'Google',
        status: 'PENDING_REVIEW', // Not in ApplicationStatus enum
        appliedAt: '2026-10-18T10:15:00',
        remarks: null,
      }
      expect(() => OfficerApplicationReviewSchema.parse(invalidStatusApp)).toThrow()
    })
  })

  // ============================================================================
  // 4. Resource Administration: Interviewers and Rooms (6 Endpoints)
  // ============================================================================
  describe('Group 4: Resource Administration Contracts', () => {
    it('validates canonical raw JSON array from GET /api/v1/officer/interviewers', () => {
      const canonicalInterviewers = [
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
      ]

      const parsed = OfficerInterviewerArraySchema.parse(canonicalInterviewers)
      expect(parsed).toHaveLength(2)
      expect(parsed[0].id).toBe('INTV-001')
      expect(parsed[0].name).toBe('Dr. Sarah Jenkins')
    })

    it('rejects interviewers collection wrapped in PagedResponse envelope', () => {
      const wrapped = {
        content: [
          {
            id: 'INTV-001',
            name: 'Dr. Sarah Jenkins',
            companyId: 'COMP-001',
            companyName: 'Google India',
          },
        ],
        page: 0,
        size: 20,
      }
      // Must be raw array, not paged object
      expect(() => OfficerInterviewerArraySchema.parse(wrapped)).toThrow()
    })

    it('validates OfficerInterviewerCreateDto for POST /api/v1/officer/interviewers', () => {
      const validCreate = {
        id: 'INTV-009',
        name: 'Anita Roy',
        companyId: 'COMP-002',
      }
      const parsed = OfficerInterviewerCreateSchema.parse(validCreate)
      expect(parsed.id).toBe('INTV-009')
      expect(parsed.name).toBe('Anita Roy')

      // Rejects empty name
      expect(() =>
        OfficerInterviewerCreateSchema.parse({
          id: 'INTV-009',
          name: '',
          companyId: 'COMP-002',
        })
      ).toThrow()
    })

    it('validates canonical raw JSON array from GET /api/v1/officer/rooms', () => {
      const canonicalRooms = [
        {
          id: 'RM-101',
          name: 'Interview Suite A (Oak Room)',
          capacity: 4,
        },
        {
          id: 'RM-201',
          name: 'Executive Boardroom 1',
          capacity: 10,
        },
      ]

      const parsed = OfficerRoomArraySchema.parse(canonicalRooms)
      expect(parsed).toHaveLength(2)
      expect(parsed[0].id).toBe('RM-101')
      expect(parsed[0].capacity).toBe(4)
    })

    it('rejects rooms collection wrapped in PagedResponse envelope', () => {
      const wrapped = {
        content: [
          {
            id: 'RM-101',
            name: 'Interview Suite A',
            capacity: 4,
          },
        ],
        page: 0,
      }
      expect(() => OfficerRoomArraySchema.parse(wrapped)).toThrow()
    })

    it('validates OfficerRoomCreateDto for POST /api/v1/officer/rooms and requires positive capacity', () => {
      const validRoom = {
        id: 'RM-501',
        name: 'Conference Room E',
        capacity: 8,
      }
      const parsed = OfficerRoomCreateSchema.parse(validRoom)
      expect(parsed.capacity).toBe(8)

      // Rejects zero capacity
      expect(() =>
        OfficerRoomCreateSchema.parse({
          id: 'RM-502',
          name: 'Zero Cap Room',
          capacity: 0,
        })
      ).toThrow()

      // Rejects negative capacity
      expect(() =>
        OfficerRoomCreateSchema.parse({
          id: 'RM-503',
          name: 'Neg Cap Room',
          capacity: -2,
        })
      ).toThrow()
    })

    it('validates OfficerRoomUpdateDto for PUT /api/v1/officer/rooms/{id}', () => {
      const validUpdate = {
        name: 'Interview Suite A (Renovated)',
        capacity: 6,
      }
      const parsed = OfficerRoomUpdateSchema.parse(validUpdate)
      expect(parsed.capacity).toBe(6)
    })
  })

  // ============================================================================
  // 5. Empty Collection Handling
  // ============================================================================
  describe('Group 5: Empty Collection Contracts', () => {
    it('accepts empty paged response for companies, drives, applications', () => {
      const emptyPaged = {
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        last: true,
      }

      expect(PagedOfficerCompanyResponseSchema.parse(emptyPaged).content).toHaveLength(0)
      expect(PagedOfficerDriveResponseSchema.parse(emptyPaged).content).toHaveLength(0)
      expect(PagedOfficerApplicationReviewResponseSchema.parse(emptyPaged).content).toHaveLength(0)
    })

    it('accepts empty raw array for interviewers and rooms', () => {
      expect(OfficerInterviewerArraySchema.parse([])).toEqual([])
      expect(OfficerRoomArraySchema.parse([])).toEqual([])
    })
  })
})
