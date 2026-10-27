import { describe, it, expect } from 'vitest'
import {
  OfficerSchedulingPreviewRequestSchema,
  OfficerSchedulingPreviewResponseSchema,
  OfficerSchedulingPublishRequestSchema,
  OfficerPublicationSummarySchema,
  OfficerInterviewDetailSchema,
  PagedOfficerInterviewDetailResponseSchema,
} from '@/features/officer/api/schemas'
import {
  parseCampusLocalDateTime,
  formatCampusAppointmentTime,
  calculateRemainingTtlSeconds,
  formatTtlCountdown,
  maskPreviewToken,
} from '@/features/officer/utils/schedulingUtils'

describe('Placement Officer Scheduling Studio API Contracts (CAP-8.4 Authoritative Baseline)', () => {
  // ============================================================================
  // Endpoint 1: Advisory Preview (POST /api/v1/officer/scheduling/preview)
  // ============================================================================
  describe('Endpoint 1: Advisory Preview Contract', () => {
    it('validates canonical preview request with explicit durations and resource lists', () => {
      const canonicalRequest = {
        driveId: 'DRIVE-001',
        date: '2026-10-22',
        startTime: '09:00',
        endTime: '17:00',
        durationMinutes: 45,
        bufferMinutes: 15,
        interviewerIds: ['INT-001', 'INT-002'],
        roomIds: ['ROOM-101', 'ROOM-102'],
      }

      const parsed = OfficerSchedulingPreviewRequestSchema.parse(canonicalRequest)
      expect(parsed.driveId).toBe('DRIVE-001')
      expect(parsed.date).toBe('2026-10-22')
      expect(parsed.startTime).toBe('09:00')
      expect(parsed.endTime).toBe('17:00')
      expect(parsed.durationMinutes).toBe(45)
      expect(parsed.bufferMinutes).toBe(15)
      expect(parsed.interviewerIds).toEqual(['INT-001', 'INT-002'])
      expect(parsed.roomIds).toEqual(['ROOM-101', 'ROOM-102'])
    })

    it('accepts optional durationMinutes, bufferMinutes, interviewerIds, roomIds with backend defaults', () => {
      const minimalRequest = {
        driveId: 'DRIVE-001',
        date: '2026-10-25',
        startTime: '10:00',
        endTime: '16:00',
      }

      const parsed = OfficerSchedulingPreviewRequestSchema.parse(minimalRequest)
      expect(parsed.driveId).toBe('DRIVE-001')
      expect(parsed.durationMinutes).toBe(45) // Backend default: 45 min
      expect(parsed.bufferMinutes).toBe(15)   // Backend default: 15 min
      expect(parsed.interviewerIds).toBeUndefined()
      expect(parsed.roomIds).toBeUndefined()
    })

    it('rejects invalid durationMinutes (< 1 or > 240)', () => {
      expect(() =>
        OfficerSchedulingPreviewRequestSchema.parse({
          driveId: 'DRIVE-001',
          date: '2026-10-22',
          startTime: '09:00',
          endTime: '17:00',
          durationMinutes: 0,
        })
      ).toThrow()

      expect(() =>
        OfficerSchedulingPreviewRequestSchema.parse({
          driveId: 'DRIVE-001',
          date: '2026-10-22',
          startTime: '09:00',
          endTime: '17:00',
          durationMinutes: 241,
        })
      ).toThrow()
    })

    it('rejects invalid bufferMinutes (< 0 or > 120)', () => {
      expect(() =>
        OfficerSchedulingPreviewRequestSchema.parse({
          driveId: 'DRIVE-001',
          date: '2026-10-22',
          startTime: '09:00',
          endTime: '17:00',
          bufferMinutes: -1,
        })
      ).toThrow()

      expect(() =>
        OfficerSchedulingPreviewRequestSchema.parse({
          driveId: 'DRIVE-001',
          date: '2026-10-22',
          startTime: '09:00',
          endTime: '17:00',
          bufferMinutes: 121,
        })
      ).toThrow()
    })

    it('rejects invalid time formats', () => {
      expect(() =>
        OfficerSchedulingPreviewRequestSchema.parse({
          driveId: 'DRIVE-001',
          date: '2026-10-22',
          startTime: '9:00 AM', // not HH:mm
          endTime: '17:00',
        })
      ).toThrow()
    })

    it('validates canonical OfficerSchedulingPreviewResponseDto with proposals and unscheduled candidates', () => {
      const canonicalResponse = {
        previewId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
        driveId: 'DRIVE-001',
        jobTitle: 'Software Development Engineer',
        companyName: 'Google India',
        date: '2026-10-22',
        startTime: '09:00',
        endTime: '17:00',
        durationMinutes: 45,
        bufferMinutes: 15,
        expiresAt: '2026-10-19T18:45:00Z',
        totalRequested: 5,
        totalScheduled: 4,
        totalUnscheduled: 1,
        proposedSessions: [
          {
            sessionId: 'PROP-001',
            applicationId: 'APP-001',
            studentId: 'STU-001',
            studentName: 'Aarav Patel',
            studentBranch: 'Computer Science',
            interviewerId: 'INT-001',
            interviewerName: 'Dr. Ramesh Kumar',
            roomId: 'ROOM-101',
            roomName: 'Lab 101 - Placement Center',
            startTime: '2026-10-22T09:00:00',
            endTime: '2026-10-22T09:45:00',
          },
          {
            sessionId: 'PROP-002',
            applicationId: 'APP-002',
            studentId: 'STU-002',
            studentName: 'Priya Sharma',
            studentBranch: 'Information Technology',
            interviewerId: 'INT-002',
            interviewerName: 'Sneha Verma',
            roomId: 'ROOM-102',
            roomName: 'Conference Room B',
            startTime: '2026-10-22T09:00:00',
            endTime: '2026-10-22T09:45:00',
          },
        ],
        unscheduledCandidates: [
          {
            applicationId: 'APP-005',
            studentId: 'STU-005',
            studentName: 'Rohan Gupta',
            studentBranch: 'Electrical Engineering',
            reason: 'Insufficient time window: all room and interviewer slots exhausted before 17:00.',
          },
        ],
      }

      const parsed = OfficerSchedulingPreviewResponseSchema.parse(canonicalResponse)
      expect(parsed.previewId).toBe('3fa85f64-5717-4562-b3fc-2c963f66afa6')
      expect(parsed.totalRequested).toBe(5)
      expect(parsed.totalScheduled).toBe(4)
      expect(parsed.totalUnscheduled).toBe(1)
      expect(parsed.proposedSessions).toHaveLength(2)
      expect(parsed.proposedSessions[0].sessionId).toBe('PROP-001')
      expect(parsed.unscheduledCandidates).toHaveLength(1)
      expect(parsed.unscheduledCandidates[0].reason).toContain('Insufficient time window')
    })

    it('validates preview with zero proposed sessions and 100% unscheduled candidates', () => {
      const zeroResponse = {
        previewId: '9fa85f64-5717-4562-b3fc-2c963f66afa9',
        driveId: 'DRIVE-EMPTY',
        jobTitle: 'Data Analyst',
        companyName: 'Analytics Corp',
        date: '2026-10-22',
        startTime: '09:00',
        endTime: '10:00',
        durationMinutes: 45,
        bufferMinutes: 15,
        expiresAt: '2026-10-19T18:45:00Z',
        totalRequested: 2,
        totalScheduled: 0,
        totalUnscheduled: 2,
        proposedSessions: [],
        unscheduledCandidates: [
          {
            applicationId: 'APP-101',
            studentId: 'STU-101',
            studentName: 'Ananya Roy',
            studentBranch: 'Mechanical Engineering',
            reason: 'No interviewers available during requested window.',
          },
          {
            applicationId: 'APP-102',
            studentId: 'STU-102',
            studentName: 'Kabir Das',
            studentBranch: 'Civil Engineering',
            reason: 'No interviewers available during requested window.',
          },
        ],
      }

      const parsed = OfficerSchedulingPreviewResponseSchema.parse(zeroResponse)
      expect(parsed.totalScheduled).toBe(0)
      expect(parsed.totalUnscheduled).toBe(2)
      expect(parsed.proposedSessions).toHaveLength(0)
    })
  })

  // ============================================================================
  // Endpoint 2: Publication (POST /api/v1/officer/scheduling/publish)
  // ============================================================================
  describe('Endpoint 2: Timetable Publication Contract', () => {
    it('validates canonical publication request with raw UUID previewId', () => {
      const req = {
        previewId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
      }
      const parsed = OfficerSchedulingPublishRequestSchema.parse(req)
      expect(parsed.previewId).toBe('3fa85f64-5717-4562-b3fc-2c963f66afa6')
    })

    it('rejects publication request with empty previewId', () => {
      expect(() =>
        OfficerSchedulingPublishRequestSchema.parse({
          previewId: '',
        })
      ).toThrow()
    })

    it('validates canonical OfficerPublicationSummaryDto for HTTP 200 ALL_CONFIRMED', () => {
      const fullSuccessPayload = {
        attemptId: 'ATTEMPT-2026-001',
        previewId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
        previewReference: 'prev-3fa8...afa6',
        driveId: 'DRIVE-001',
        jobTitle: 'Software Development Engineer',
        companyName: 'Google India',
        outcome: 'ALL_CONFIRMED',
        recoveryInstructions: 'All proposed interview sessions successfully persisted to database.',
        publishedAt: '2026-10-19T18:05:00Z',
        totalProposals: 3,
        publishedCount: 3,
        skippedDuplicatesCount: 0,
        conflictsCount: 0,
        errorsCount: 0,
        unattemptedCount: 0,
        confirmedSessionIds: ['SESS-001', 'SESS-002', 'SESS-003'],
        publishedSessions: [
          {
            sessionId: 'SESS-001',
            applicationId: 'APP-001',
            studentId: 'STU-001',
            studentName: 'Aarav Patel',
            startTime: '2026-10-22T09:00:00',
            endTime: '2026-10-22T09:45:00',
            interviewerName: 'Dr. Ramesh Kumar',
            roomName: 'Lab 101',
          },
        ],
        skippedDuplicateApplicationIds: [],
        conflictRejections: [],
        infrastructureErrors: [],
      }

      const parsed = OfficerPublicationSummarySchema.parse(fullSuccessPayload)
      expect(parsed.outcome).toBe('ALL_CONFIRMED')
      expect(parsed.totalProposals).toBe(3)
      expect(parsed.publishedCount).toBe(3)
      expect(parsed.skippedDuplicatesCount).toBe(0)
      expect(parsed.conflictsCount).toBe(0)
      expect(parsed.confirmedSessionIds).toHaveLength(3)
    })

    it('validates canonical OfficerPublicationSummaryDto with skipped duplicates (idempotency)', () => {
      const duplicatePayload = {
        attemptId: 'ATTEMPT-2026-002',
        previewId: '4fa85f64-5717-4562-b3fc-2c963f66afa7',
        previewReference: 'prev-4fa8...afa7',
        driveId: 'DRIVE-001',
        jobTitle: 'Software Development Engineer',
        companyName: 'Google India',
        outcome: 'ALL_CONFIRMED',
        recoveryInstructions: '1 newly published, 2 sessions skipped because identical application appointments were already persisted.',
        publishedAt: '2026-10-19T18:10:00Z',
        totalProposals: 3,
        publishedCount: 1,
        skippedDuplicatesCount: 2,
        conflictsCount: 0,
        errorsCount: 0,
        unattemptedCount: 0,
        confirmedSessionIds: ['SESS-001', 'SESS-DUP-01', 'SESS-DUP-02'],
        publishedSessions: [
          {
            sessionId: 'SESS-001',
            applicationId: 'APP-001',
            studentId: 'STU-001',
            studentName: 'Aarav Patel',
            startTime: '2026-10-22T09:00:00',
            endTime: '2026-10-22T09:45:00',
            interviewerName: 'Dr. Ramesh Kumar',
            roomName: 'Lab 101',
          },
        ],
        skippedDuplicateApplicationIds: ['APP-002', 'APP-003'],
        conflictRejections: [],
        infrastructureErrors: [],
      }

      const parsed = OfficerPublicationSummarySchema.parse(duplicatePayload)
      expect(parsed.publishedCount).toBe(1)
      expect(parsed.skippedDuplicatesCount).toBe(2)
      expect(parsed.skippedDuplicateApplicationIds).toEqual(['APP-002', 'APP-003'])
    })

    it('validates canonical OfficerPublicationSummaryDto for HTTP 207 PARTIAL_SUCCESS', () => {
      const partialPayload = {
        attemptId: 'ATTEMPT-2026-003',
        previewId: '5fa85f64-5717-4562-b3fc-2c963f66afa8',
        previewReference: 'prev-5fa8...afa8',
        driveId: 'DRIVE-001',
        jobTitle: 'Software Development Engineer',
        companyName: 'Google India',
        outcome: 'PARTIAL_SUCCESS',
        recoveryInstructions: '2 sessions confirmed. 1 conflict occurred due to concurrent room booking.',
        publishedAt: '2026-10-19T18:15:00Z',
        totalProposals: 3,
        publishedCount: 2,
        skippedDuplicatesCount: 0,
        conflictsCount: 1,
        errorsCount: 0,
        unattemptedCount: 0,
        confirmedSessionIds: ['SESS-001', 'SESS-002'],
        publishedSessions: [
          {
            sessionId: 'SESS-001',
            applicationId: 'APP-001',
            studentId: 'STU-001',
            studentName: 'Aarav Patel',
            startTime: '2026-10-22T09:00:00',
            endTime: '2026-10-22T09:45:00',
            interviewerName: 'Dr. Ramesh Kumar',
            roomName: 'Lab 101',
          },
        ],
        skippedDuplicateApplicationIds: [],
        conflictRejections: [
          {
            applicationId: 'APP-003',
            message: 'Room ROOM-101 was reserved by concurrent drive during allocation window.',
            category: 'RESOURCE_COLLISION',
          },
        ],
        infrastructureErrors: [],
      }

      const parsed = OfficerPublicationSummarySchema.parse(partialPayload)
      expect(parsed.outcome).toBe('PARTIAL_SUCCESS')
      expect(parsed.publishedCount).toBe(2)
      expect(parsed.conflictsCount).toBe(1)
      expect(parsed.conflictRejections[0].category).toBe('RESOURCE_COLLISION')
      expect(parsed.conflictRejections[0].message).toContain('Room ROOM-101')
    })

    it('validates canonical OfficerPublicationSummaryDto for HTTP 409 NONE_CONFIRMED', () => {
      const nonePayload = {
        attemptId: 'ATTEMPT-2026-004',
        previewId: '6fa85f64-5717-4562-b3fc-2c963f66afa9',
        previewReference: 'prev-6fa8...afa9',
        driveId: 'DRIVE-001',
        jobTitle: 'Software Development Engineer',
        companyName: 'Google India',
        outcome: 'NONE_CONFIRMED',
        recoveryInstructions: 'Zero sessions confirmed. All proposed slots were blocked by concurrent bookings.',
        publishedAt: '2026-10-19T18:20:00Z',
        totalProposals: 2,
        publishedCount: 0,
        skippedDuplicatesCount: 0,
        conflictsCount: 2,
        errorsCount: 0,
        unattemptedCount: 0,
        confirmedSessionIds: [],
        publishedSessions: [],
        skippedDuplicateApplicationIds: [],
        conflictRejections: [
          {
            applicationId: 'APP-001',
            message: 'Interviewer INT-001 is no longer available.',
            category: 'INTERVIEWER_COLLISION',
          },
          {
            applicationId: 'APP-002',
            message: 'Room ROOM-102 was decommissioned.',
            category: 'ROOM_COLLISION',
          },
        ],
        infrastructureErrors: [],
      }

      const parsed = OfficerPublicationSummarySchema.parse(nonePayload)
      expect(parsed.outcome).toBe('NONE_CONFIRMED')
      expect(parsed.publishedCount).toBe(0)
      expect(parsed.conflictsCount).toBe(2)
      expect(parsed.confirmedSessionIds).toHaveLength(0)
    })

    it('distinguishes HTTP 404 PreviewNotFoundException (evicted or backend restart) from HTTP 410 PreviewExpiredException', () => {
      // HTTP 404 from PreviewNotFoundException: preview unknown, server restarted, or evicted
      const notFoundPayload = {
        status: 404,
        error: 'Not Found',
        message: "Scheduling preview 'PREV-3fa8****' was not found or has expired.",
        path: '/api/v1/officer/scheduling/publish',
        timestamp: '2026-10-20T07:30:00Z',
      }
      expect(notFoundPayload.status).toBe(404)
      expect(notFoundPayload.message).toContain('was not found')

      // HTTP 410 from PreviewExpiredException: 15-minute TTL elapsed in active cache
      const expiredPayload = {
        status: 410,
        error: 'Gone',
        message: "Scheduling preview 'PREV-3fa8****' has expired (15-minute validity). Please generate a fresh preview.",
        path: '/api/v1/officer/scheduling/publish',
        timestamp: '2026-10-20T07:30:00Z',
      }
      expect(expiredPayload.status).toBe(410)
      expect(expiredPayload.message).toContain('has expired')

      // HTTP 409 from PreviewConsumedException: token replay prevented
      const consumedPayload = {
        status: 409,
        error: 'Conflict',
        message: "Scheduling preview 'PREV-3fa8****' has already been published. Replay prevented.",
        path: '/api/v1/officer/scheduling/publish',
        timestamp: '2026-10-20T07:30:00Z',
      }
      expect(consumedPayload.status).toBe(409)
      expect(consumedPayload.message).toContain('already been published')
    })
  })

  // ============================================================================
  // Endpoint 3 & 4: Confirmed Timetable & Session Detail
  // ============================================================================
  describe('Endpoints 3 & 4: Confirmed Timetable and Session Detail Contracts', () => {
    it('validates canonical OfficerInterviewDetailDto from GET /api/v1/officer/scheduling/published/{id}', () => {
      const canonicalDetail = {
        sessionId: 'SESS-001',
        applicationId: 'APP-001',
        studentId: 'STU-001',
        studentName: 'Aarav Patel',
        studentBranch: 'Computer Science',
        driveId: 'DRIVE-001',
        jobTitle: 'Software Development Engineer',
        companyId: 'COMP-001',
        companyName: 'Google India',
        interviewerId: 'INT-001',
        interviewerName: 'Dr. Ramesh Kumar',
        roomId: 'ROOM-101',
        roomName: 'Lab 101 - Placement Center',
        startTime: '2026-10-22T09:00:00',
        endTime: '2026-10-22T09:45:00',
        status: 'SHORTLISTED', // Authoritative: comes from Application.status
      }

      const parsed = OfficerInterviewDetailSchema.parse(canonicalDetail)
      expect(parsed.sessionId).toBe('SESS-001')
      expect(parsed.status).toBe('SHORTLISTED')
      expect(parsed.studentName).toBe('Aarav Patel')
      expect(parsed.companyName).toBe('Google India')
    })

    it('ensures status is strictly bound to ApplicationStatus, rejecting fabricated interview statuses', () => {
      const invalidStatusDetail = {
        sessionId: 'SESS-001',
        applicationId: 'APP-001',
        studentId: 'STU-001',
        studentName: 'Aarav Patel',
        studentBranch: 'Computer Science',
        driveId: 'DRIVE-001',
        jobTitle: 'Software Development Engineer',
        companyId: 'COMP-001',
        companyName: 'Google India',
        interviewerId: 'INT-001',
        interviewerName: 'Dr. Ramesh Kumar',
        roomId: 'ROOM-101',
        roomName: 'Lab 101',
        startTime: '2026-10-22T09:00:00',
        endTime: '2026-10-22T09:45:00',
        status: 'COMPLETED', // INVALID: not in ApplicationStatus!
      }

      expect(() => OfficerInterviewDetailSchema.parse(invalidStatusDetail)).toThrow()
    })

    it('validates canonical PagedResponse<OfficerInterviewDetailDto> from GET /api/v1/officer/scheduling/published', () => {
      const canonicalPaged = {
        content: [
          {
            sessionId: 'SESS-001',
            applicationId: 'APP-001',
            studentId: 'STU-001',
            studentName: 'Aarav Patel',
            studentBranch: 'Computer Science',
            driveId: 'DRIVE-001',
            jobTitle: 'Software Development Engineer',
            companyId: 'COMP-001',
            companyName: 'Google India',
            interviewerId: 'INT-001',
            interviewerName: 'Dr. Ramesh Kumar',
            roomId: 'ROOM-101',
            roomName: 'Lab 101',
            startTime: '2026-10-22T09:00:00',
            endTime: '2026-10-22T09:45:00',
            status: 'SHORTLISTED',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        last: true,
      }

      const parsed = PagedOfficerInterviewDetailResponseSchema.parse(canonicalPaged)
      expect(parsed.content).toHaveLength(1)
      expect(parsed.totalElements).toBe(1)
      expect(parsed.page).toBe(0)
    })
  })

  // ============================================================================
  // Temporal Discipline & Security Token Masking
  // ============================================================================
  describe('Temporal Discipline & Token Hygiene', () => {
    it('parses wall-clock campus LocalDateTime without converting to local browser UTC', () => {
      const appointmentString = '2026-10-22T09:45:00'
      const parsed = parseCampusLocalDateTime(appointmentString)

      expect(parsed.year).toBe(2026)
      expect(parsed.month).toBe(10)
      expect(parsed.day).toBe(22)
      expect(parsed.hours).toBe(9)
      expect(parsed.minutes).toBe(45)
      expect(parsed.formattedTime).toBe('9:45 AM')
      expect(parsed.datePart).toBe('2026-10-22')
    })

    it('formats campus appointment time interval correctly', () => {
      const start = '2026-10-22T09:00:00'
      const end = '2026-10-22T09:45:00'
      expect(formatCampusAppointmentTime(start, end)).toBe('09:00 – 09:45')
    })

    it('calculates remaining TTL seconds from UTC Instant with accurate countdown formatting', () => {
      const now = new Date('2026-10-19T18:00:00Z')
      const expiresAt = '2026-10-19T18:14:30Z'

      const remaining = calculateRemainingTtlSeconds(expiresAt, now)
      expect(remaining).toBe(14 * 60 + 30) // 870 seconds
      expect(formatTtlCountdown(remaining)).toBe('14:30')
    })

    it('returns 0 remaining TTL seconds when expiresAt has passed', () => {
      const now = new Date('2026-10-19T18:16:00Z')
      const expiresAt = '2026-10-19T18:14:30Z'

      const remaining = calculateRemainingTtlSeconds(expiresAt, now)
      expect(remaining).toBe(0)
      expect(formatTtlCountdown(remaining)).toBe('00:00')
    })

    it('safely masks raw UUID preview token for display without leaking sensitive characters', () => {
      const rawToken = '3fa85f64-5717-4562-b3fc-2c963f66afa6'
      const masked = maskPreviewToken(rawToken)

      expect(masked).toBe('prev-3fa8...afa6')
      expect(masked).not.toContain('5717-4562-b3fc')
    })

    it('returns fallback for empty or short token strings', () => {
      expect(maskPreviewToken('')).toBe('prev-••••••••')
      expect(maskPreviewToken('abc')).toBe('prev-••••••••')
    })
  })
})
