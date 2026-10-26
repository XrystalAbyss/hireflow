import { describe, it, expect } from 'vitest'
import {
  StudentProfileSchema,
  StudentDriveItemArraySchema,
  StudentDriveEligibilitySchema,
  PagedStudentApplicationResponseSchema,
  StudentApplicationDetailSchema,
  StudentInterviewSchema,
  StudentInterviewArraySchema,
  StudentDashboardSchema,
  ApiErrorResponseSchema,
  ApplicationStatusSchema,
  LocalDateTimeSchema,
} from '@/features/student/api/schemas'
import {
  formatLocalDateTime,
  formatLocalDateOnly,
  parseLocalDateTime,
} from '@/lib/dateUtils'

/**
 * Independent Contract Validation Suite for HireFlow Student Portal (CAP-8.2.1)
 *
 * Validates authoritative Spring Boot DTO schemas (CAP-7 baseline: commit bdbf0e1dce7407ab9034f893712ccea8d7fced56)
 * independently of frontend UI fixtures.
 */
describe('Authoritative Spring Boot Student Contract Validation', () => {
  describe('GET /api/v1/student/profile Contract', () => {
    it('validates canonical profile payload with authoritative id identifier', () => {
      const javaPayload = {
        id: 'STU-2022-CS0144',
        name: 'Aarav Sharma',
        email: 'aarav.sharma@university.edu',
        branch: 'Computer Science',
        cgpa: 9.15,
        graduationYear: 2026,
        active: true,
      }

      const result = StudentProfileSchema.safeParse(javaPayload)
      expect(result.success).toBe(true)
      if (result.success) {
        expect(result.data.id).toBe('STU-2022-CS0144')
      }
    })

    it('fails validation when studentId alias is supplied instead of id', () => {
      const invalidPayload = {
        studentId: 'STU-2022-CS0144',
        name: 'Aarav Sharma',
        email: 'aarav.sharma@university.edu',
        branch: 'Computer Science',
        cgpa: 9.15,
        graduationYear: 2026,
        active: true,
      }

      const result = StudentProfileSchema.safeParse(invalidPayload)
      expect(result.success).toBe(false)
    })
  })

  describe('GET /api/v1/student/drives Contract (Raw JSON Array)', () => {
    it('validates canonical raw array of StudentDriveItemDto with exact Java property names', () => {
      const javaPayload = [
        {
          id: 'DRV-2026-001',
          companyId: 'CMP-001',
          companyName: 'Apex Innovations',
          jobTitle: 'Associate Software Engineer',
          minimumCgpa: 8.0,
          eligibleBranches: ['Computer Science', 'Information Technology'],
          applicationDeadline: '2026-11-15',
          closed: false,
          deadlinePassed: false,
          hasApplied: true,
          existingApplicationId: 'APP-2026-001',
          existingApplicationStatus: 'SHORTLISTED',
          academicallyEligible: true,
          acceptingApplications: true,
          canApply: false,
          eligibilityReasons: ['Already applied'],
        },
        {
          id: 'DRV-2026-002',
          companyId: 'CMP-002',
          companyName: 'Stratum Quant Labs',
          jobTitle: 'Quantitative Systems Analyst',
          minimumCgpa: 8.5,
          eligibleBranches: ['Computer Science', 'Mathematics'],
          applicationDeadline: '2026-11-20',
          closed: false,
          deadlinePassed: false,
          hasApplied: false,
          existingApplicationId: null,
          existingApplicationStatus: null,
          academicallyEligible: true,
          acceptingApplications: true,
          canApply: true,
          eligibilityReasons: [],
        },
      ]

      const result = StudentDriveItemArraySchema.safeParse(javaPayload)
      expect(result.success).toBe(true)
      if (result.success) {
        expect(result.data).toHaveLength(2)
        expect(result.data[0].id).toBe('DRV-2026-001')
        expect(result.data[0].canApply).toBe(false)
        expect(result.data[0].hasApplied).toBe(true)
        expect(result.data[1].canApply).toBe(true)
        expect(result.data[1].existingApplicationId).toBeNull()
      }
    })

    it('accepts an empty array without crashing', () => {
      const result = StudentDriveItemArraySchema.safeParse([])
      expect(result.success).toBe(true)
      if (result.success) {
        expect(result.data).toEqual([])
      }
    })

    it('strictly rejects a PagedResponse envelope mistakenly returned for drives', () => {
      // Backend returns ResponseEntity<List<StudentDriveItemDto>>, NOT PagedResponse!
      const invalidPagedEnvelope = {
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        last: true,
      }

      const result = StudentDriveItemArraySchema.safeParse(invalidPagedEnvelope)
      expect(result.success).toBe(false)
    })

    it('fails validation when driveId is used instead of id', () => {
      const itemWithDriveId = [
        {
          driveId: 'DRV-2026-001',
          companyId: 'CMP-001',
          companyName: 'Apex Innovations',
          jobTitle: 'Associate Software Engineer',
          minimumCgpa: 8.0,
          eligibleBranches: ['Computer Science'],
          applicationDeadline: '2026-11-15',
          closed: false,
          deadlinePassed: false,
          hasApplied: false,
          existingApplicationId: null,
          existingApplicationStatus: null,
          academicallyEligible: true,
          acceptingApplications: true,
          canApply: true,
          eligibilityReasons: [],
        },
      ]

      const result = StudentDriveItemArraySchema.safeParse(itemWithDriveId)
      expect(result.success).toBe(false)
    })

    it('fails validation when eligible is substituted for canApply', () => {
      const itemWithEligible = [
        {
          id: 'DRV-2026-001',
          companyId: 'CMP-001',
          companyName: 'Apex Innovations',
          jobTitle: 'Associate Software Engineer',
          minimumCgpa: 8.0,
          eligibleBranches: ['Computer Science'],
          applicationDeadline: '2026-11-15',
          closed: false,
          deadlinePassed: false,
          hasApplied: false,
          existingApplicationId: null,
          existingApplicationStatus: null,
          academicallyEligible: true,
          acceptingApplications: true,
          eligible: true, // Invalid: Java record uses canApply!
          eligibilityReasons: [],
        },
      ]

      const result = StudentDriveItemArraySchema.safeParse(itemWithEligible)
      expect(result.success).toBe(false)
    })
  })

  describe('GET /api/v1/student/drives/{id}/eligibility Contract', () => {
    it('validates canonical StudentDriveEligibilityDto with reasons and alreadyApplied fields', () => {
      const javaPayload = {
        driveId: 'DRV-2026-002',
        studentId: 'STU-2022-CS0144',
        academicallyEligible: true,
        acceptingApplications: true,
        alreadyApplied: false,
        canApply: true,
        existingApplicationId: null,
        existingApplicationStatus: null,
        minimumCgpa: 8.5,
        studentCgpa: 9.15,
        eligibleBranches: ['Computer Science', 'Mathematics & Computing'],
        studentBranch: 'Computer Science',
        applicationDeadline: '2026-11-20',
        closed: false,
        deadlinePassed: false,
        reasons: [],
      }

      const result = StudentDriveEligibilitySchema.safeParse(javaPayload)
      expect(result.success).toBe(true)
      if (result.success) {
        expect(result.data.driveId).toBe('DRV-2026-002')
        expect(result.data.studentId).toBe('STU-2022-CS0144')
        expect(result.data.alreadyApplied).toBe(false)
        expect(result.data.reasons).toEqual([])
      }
    })

    it('validates itemized reasons for disqualified candidate', () => {
      const ineligiblePayload = {
        driveId: 'DRV-2026-005',
        studentId: 'STU-2022-CS0144',
        academicallyEligible: false,
        acceptingApplications: true,
        alreadyApplied: false,
        canApply: false,
        existingApplicationId: null,
        existingApplicationStatus: null,
        minimumCgpa: 9.5,
        studentCgpa: 9.15,
        eligibleBranches: ['Computer Science'],
        studentBranch: 'Computer Science',
        applicationDeadline: '2026-11-05',
        closed: false,
        deadlinePassed: false,
        reasons: ['Candidate cumulative CGPA 9.15 does not satisfy minimum required CGPA of 9.50.'],
      }

      const result = StudentDriveEligibilitySchema.safeParse(ineligiblePayload)
      expect(result.success).toBe(true)
      if (result.success) {
        expect(result.data.canApply).toBe(false)
        expect(result.data.reasons).toHaveLength(1)
        expect(result.data.reasons[0]).toContain('CGPA')
      }
    })

    it('fails validation when reasons is renamed to eligibilityReasons on this DTO', () => {
      const invalidPayload = {
        driveId: 'DRV-2026-002',
        studentId: 'STU-2022-CS0144',
        academicallyEligible: true,
        acceptingApplications: true,
        alreadyApplied: false,
        canApply: true,
        existingApplicationId: null,
        existingApplicationStatus: null,
        minimumCgpa: 8.5,
        studentCgpa: 9.15,
        eligibleBranches: ['Computer Science'],
        studentBranch: 'Computer Science',
        applicationDeadline: '2026-11-20',
        closed: false,
        deadlinePassed: false,
        eligibilityReasons: [], // Invalid: Java record uses 'reasons'!
      }

      const result = StudentDriveEligibilitySchema.safeParse(invalidPayload)
      expect(result.success).toBe(false)
    })
  })

  describe('GET /api/v1/student/applications Contract (PagedResponse)', () => {
    it('validates canonical PagedResponse<StudentApplicationDetailDto>', () => {
      const javaPayload = {
        content: [
          {
            id: 'APP-2026-001',
            studentId: 'STU-2022-CS0144',
            driveId: 'DRV-2026-001',
            companyId: 'CMP-001',
            companyName: 'Apex Innovations',
            jobTitle: 'Associate Software Engineer',
            status: 'SHORTLISTED',
            appliedAt: '2026-10-12T14:30:00',
            remarks: 'Profile shortlisted for technical evaluation panel.',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        last: true,
      }

      const result = PagedStudentApplicationResponseSchema.safeParse(javaPayload)
      expect(result.success).toBe(true)
      if (result.success) {
        expect(result.data.content[0].id).toBe('APP-2026-001')
        expect(result.data.content[0].status).toBe('SHORTLISTED')
        expect(result.data.totalElements).toBe(1)
      }
    })

    it('validates empty paginated result', () => {
      const emptyPaged = {
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        last: true,
      }

      const result = PagedStudentApplicationResponseSchema.safeParse(emptyPaged)
      expect(result.success).toBe(true)
      if (result.success) {
        expect(result.data.content).toHaveLength(0)
        expect(result.data.totalElements).toBe(0)
      }
    })

    it('fails validation when application items use applicationId instead of id', () => {
      const invalidAppPayload = {
        content: [
          {
            applicationId: 'APP-2026-001', // Invalid: Java DTO uses id!
            studentId: 'STU-2022-CS0144',
            driveId: 'DRV-2026-001',
            companyId: 'CMP-001',
            companyName: 'Apex Innovations',
            jobTitle: 'Associate Software Engineer',
            status: 'APPLIED',
            appliedAt: '2026-10-12T14:30:00',
            remarks: null,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        last: true,
      }

      const result = PagedStudentApplicationResponseSchema.safeParse(invalidAppPayload)
      expect(result.success).toBe(false)
    })
  })

  describe('POST /api/v1/student/applications Contract & Submission ID Verification', () => {
    it('validates 201 Created response shape and extracts authoritative id', () => {
      const createdAppPayload = {
        id: 'APP-2026-789',
        studentId: 'STU-2022-CS0144',
        driveId: 'DRV-2026-002',
        companyId: 'CMP-002',
        companyName: 'Stratum Quant Labs',
        jobTitle: 'Quantitative Systems Analyst',
        status: 'APPLIED',
        appliedAt: '2026-10-19T10:15:30',
        remarks: null,
      }

      const result = StudentApplicationDetailSchema.safeParse(createdAppPayload)
      expect(result.success).toBe(true)
      if (result.success) {
        expect(result.data.id).toBe('APP-2026-789')
        expect(result.data.status).toBe('APPLIED')
        // Must never accept fabricated or missing id
        expect(result.data.id.length).toBeGreaterThan(0)
      }
    })
  })

  describe('All 7 ApplicationStatus Enum Values', () => {
    const statuses = ['APPLIED', 'ELIGIBLE', 'INELIGIBLE', 'SHORTLISTED', 'SCHEDULED', 'REJECTED', 'SELECTED']

    statuses.forEach((status) => {
      it(`validates ApplicationStatus: ${status}`, () => {
        expect(ApplicationStatusSchema.safeParse(status).success).toBe(true)
      })
    })

    it('rejects ungrounded or non-canonical statuses', () => {
      expect(ApplicationStatusSchema.safeParse('PENDING').success).toBe(false)
      expect(ApplicationStatusSchema.safeParse('CONFIRMED').success).toBe(false)
      expect(ApplicationStatusSchema.safeParse('CANCELLED').success).toBe(false)
      expect(ApplicationStatusSchema.safeParse('PASSED').success).toBe(false)
    })
  })

  describe('GET /api/v1/student/interviews Contract (Raw JSON Array)', () => {
    it('validates canonical raw array of StudentInterviewDto', () => {
      const javaPayload = [
        {
          sessionId: 'SES-001',
          applicationId: 'APP-2026-007',
          studentId: 'STU-2022-CS0144',
          studentName: 'Aarav Sharma',
          studentBranch: 'Computer Science',
          driveId: 'DRV-2026-007',
          jobTitle: 'Full-Stack Developer Intern',
          companyName: 'Crestline Systems',
          interviewerId: 'INT-001',
          interviewerName: 'Dr. Michael Chen',
          roomId: 'RM-302',
          roomName: 'Placement Suite 302',
          startTime: '2026-10-24T10:00:00',
          endTime: '2026-10-24T10:45:00',
          status: 'SCHEDULED',
        },
      ]

      const result = StudentInterviewArraySchema.safeParse(javaPayload)
      expect(result.success).toBe(true)
      if (result.success) {
        expect(result.data).toHaveLength(1)
        expect(result.data[0].sessionId).toBe('SES-001')
        expect(result.data[0].applicationId).toBe('APP-2026-007')
        expect(result.data[0].interviewerName).toBe('Dr. Michael Chen')
      }
    })

    it('accepts an empty array for student with no scheduled interviews', () => {
      const result = StudentInterviewArraySchema.safeParse([])
      expect(result.success).toBe(true)
      if (result.success) {
        expect(result.data).toEqual([])
      }
    })

    it('strictly rejects a PagedResponse envelope for interviews', () => {
      // Backend returns List<StudentInterviewDto>, not PagedResponse!
      const pagedPayload = {
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        last: true,
      }

      const result = StudentInterviewArraySchema.safeParse(pagedPayload)
      expect(result.success).toBe(false)
    })
  })

  describe('Discrepancy A: Application appliedAt and Java LocalDateTime Wire Format', () => {
    it('accepts valid offset-free Java LocalDateTime string (e.g. 2026-10-19T01:30:00)', () => {
      const result = LocalDateTimeSchema.safeParse('2026-10-19T01:30:00')
      expect(result.success).toBe(true)
    })

    it('accepts valid offset-free Java LocalDateTime with subsecond fractions', () => {
      const result = LocalDateTimeSchema.safeParse('2026-10-19T01:30:00.123456')
      expect(result.success).toBe(true)
    })

    it('rejects timestamps with trailing UTC Z suffix', () => {
      // Backend LocalDateTime does not contain timezone offset or Z
      const result = LocalDateTimeSchema.safeParse('2026-10-19T01:30:00Z')
      expect(result.success).toBe(false)
    })

    it('rejects timestamps with numeric timezone offset', () => {
      const result = LocalDateTimeSchema.safeParse('2026-10-19T01:30:00+05:30')
      expect(result.success).toBe(false)
    })

    it('rejects invalid calendar dates (e.g. Feb 30)', () => {
      const result = LocalDateTimeSchema.safeParse('2026-02-30T10:00:00')
      expect(result.success).toBe(false)
    })

    it('rejects invalid time components (e.g. hour 25 or minute 65)', () => {
      expect(LocalDateTimeSchema.safeParse('2026-10-19T25:00:00').success).toBe(false)
      expect(LocalDateTimeSchema.safeParse('2026-10-19T10:65:00').success).toBe(false)
    })

    it('rejects malformed non-ISO date strings', () => {
      expect(LocalDateTimeSchema.safeParse('not-a-timestamp').success).toBe(false)
      expect(LocalDateTimeSchema.safeParse('2026/10/19 10:00:00').success).toBe(false)
      expect(LocalDateTimeSchema.safeParse('').success).toBe(false)
    })

    it('formats offset-free LocalDateTime deterministically without timezone shift', () => {
      // Verifies that 01:30 is formatted as 1:30 AM and Oct 19, 2026 regardless of environment
      const formatted = formatLocalDateTime('2026-10-19T01:30:00')
      expect(formatted).toContain('Oct 19, 2026')
      expect(formatted).toContain('1:30 AM')

      const dateOnly = formatLocalDateOnly('2026-10-19T01:30:00')
      expect(dateOnly).toBe('Oct 19, 2026')

      const parsed = parseLocalDateTime('2026-10-19T01:30:00')
      expect(parsed).not.toBeNull()
      expect(parsed?.getFullYear()).toBe(2026)
      expect(parsed?.getMonth()).toBe(9) // 0-indexed month 9 is October
      expect(parsed?.getDate()).toBe(19)
      expect(parsed?.getHours()).toBe(1)
      expect(parsed?.getMinutes()).toBe(30)
    })
  })

  describe('Discrepancy B: StudentInterviewDto status Derived from ApplicationStatus', () => {
    const baseInterview = {
      sessionId: 'SES-001',
      applicationId: 'APP-2026-007',
      studentId: 'STU-2022-CS0144',
      studentName: 'Aarav Sharma',
      studentBranch: 'Computer Science',
      driveId: 'DRV-2026-007',
      jobTitle: 'Full-Stack Developer Intern',
      companyName: 'Crestline Systems',
      interviewerId: 'INT-001',
      interviewerName: 'Dr. Michael Chen',
      roomId: 'RM-302',
      roomName: 'Placement Suite 302',
      startTime: '2026-10-24T10:00:00',
      endTime: '2026-10-24T10:45:00',
    }

    it('validates interview response with SCHEDULED status', () => {
      const interview = { ...baseInterview, status: 'SCHEDULED' }
      const result = StudentInterviewSchema.safeParse(interview)
      expect(result.success).toBe(true)
      if (result.success) {
        expect(result.data.status).toBe('SCHEDULED')
      }
    })

    it('validates interview response with SELECTED status', () => {
      const interview = { ...baseInterview, status: 'SELECTED' }
      const result = StudentInterviewSchema.safeParse(interview)
      expect(result.success).toBe(true)
      if (result.success) {
        expect(result.data.status).toBe('SELECTED')
      }
    })

    const otherStatuses = ['APPLIED', 'SHORTLISTED', 'REJECTED', 'ELIGIBLE', 'INELIGIBLE'] as const
    otherStatuses.forEach((status) => {
      it(`validates interview response with application-derived status: ${status}`, () => {
        const interview = { ...baseInterview, status }
        const result = StudentInterviewSchema.safeParse(interview)
        expect(result.success).toBe(true)
        if (result.success) {
          expect(result.data.status).toBe(status)
        }
      })
    })

    it('rejects invented interview-status strings (COMPLETED, CANCELLED, CONFIRMED)', () => {
      // Backend status is app.getStatus().name() (ApplicationStatus enum) - these invented values do not exist
      expect(StudentInterviewSchema.safeParse({ ...baseInterview, status: 'COMPLETED' }).success).toBe(false)
      expect(StudentInterviewSchema.safeParse({ ...baseInterview, status: 'CANCELLED' }).success).toBe(false)
      expect(StudentInterviewSchema.safeParse({ ...baseInterview, status: 'CONFIRMED' }).success).toBe(false)
    })

    it('rejects unknown status strings and does NOT silently coerce to SCHEDULED', () => {
      const result = StudentInterviewSchema.safeParse({ ...baseInterview, status: 'UNKNOWN_STATUS' })
      expect(result.success).toBe(false)
    })

    it('rejects empty or missing status', () => {
      expect(StudentInterviewSchema.safeParse({ ...baseInterview, status: '' }).success).toBe(false)
      expect(StudentInterviewSchema.safeParse({ ...baseInterview, status: null }).success).toBe(false)
    })
  })

  describe('GET /api/v1/student/dashboard Contract', () => {
    it('validates canonical StudentDashboardDto with exactly 5 fields', () => {
      const javaPayload = {
        profile: {
          id: 'STU-2022-CS0144',
          name: 'Aarav Sharma',
          email: 'aarav.sharma@university.edu',
          branch: 'Computer Science',
          cgpa: 9.15,
          graduationYear: 2026,
          active: true,
        },
        totalEligibleDrives: 4,
        totalApplications: 2,
        shortlistedCount: 1,
        recentApplications: [
          {
            id: 'APP-2026-001',
            studentId: 'STU-2022-CS0144',
            driveId: 'DRV-2026-001',
            companyId: 'CMP-001',
            companyName: 'Apex Innovations',
            jobTitle: 'Associate Software Engineer',
            status: 'SHORTLISTED',
            appliedAt: '2026-10-12T14:30:00',
            remarks: null,
          },
        ],
      }

      const result = StudentDashboardSchema.safeParse(javaPayload)
      expect(result.success).toBe(true)
      if (result.success) {
        expect(result.data.profile.id).toBe('STU-2022-CS0144')
        expect(result.data.totalEligibleDrives).toBe(4)
        expect(result.data.totalApplications).toBe(2)
        expect(result.data.shortlistedCount).toBe(1)
        expect(result.data.recentApplications).toHaveLength(1)
      }
    })
  })

  describe('Uniform ApiErrorResponse Contract (400, 401, 403, 404, 409)', () => {
    it('validates 400 Bad Request error envelope', () => {
      const error400 = {
        status: 400,
        error: 'Bad Request',
        message: 'Drive closed for applications.',
        path: '/api/v1/student/applications',
        timestamp: '2026-10-19T10:00:00Z',
      }
      expect(ApiErrorResponseSchema.safeParse(error400).success).toBe(true)
    })

    it('validates 401 Unauthorized error envelope', () => {
      const error401 = {
        status: 401,
        error: 'Unauthorized',
        message: 'Full authentication with STUDENT role is required.',
        path: '/api/v1/student/profile',
        timestamp: '2026-10-19T10:00:00Z',
      }
      expect(ApiErrorResponseSchema.safeParse(error401).success).toBe(true)
    })

    it('validates 403 Forbidden error envelope', () => {
      const error403 = {
        status: 403,
        error: 'Forbidden',
        message: 'Access Denied: Candidate operations require an authenticated STUDENT account.',
        path: '/api/v1/student/dashboard',
        timestamp: '2026-10-19T10:00:00Z',
      }
      expect(ApiErrorResponseSchema.safeParse(error403).success).toBe(true)
    })

    it('validates 404 Not Found error envelope (cross-student isolation)', () => {
      const error404 = {
        status: 404,
        error: 'Not Found',
        message: "Application with ID 'APP-OTHER-STUDENT' was not found.",
        path: '/api/v1/student/applications/APP-OTHER-STUDENT',
        timestamp: '2026-10-19T10:00:00Z',
      }
      expect(ApiErrorResponseSchema.safeParse(error404).success).toBe(true)
    })

    it('validates 409 Conflict error envelope (duplicate submission)', () => {
      const error409 = {
        status: 409,
        error: 'Conflict',
        message: 'Student has already applied to recruitment drive DRV-2026-001.',
        path: '/api/v1/student/applications',
        timestamp: '2026-10-19T10:00:00Z',
      }
      expect(ApiErrorResponseSchema.safeParse(error409).success).toBe(true)
    })
  })
})
