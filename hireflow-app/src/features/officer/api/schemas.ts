import { z } from 'zod'
import { ApplicationStatusSchema, LocalDateTimeSchema } from '@/features/student/api/schemas'

/**
 * Strict ISO LocalDate (YYYY-MM-DD) schema with calendar validation.
 */
export const LocalDateSchema = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, 'Must be a valid ISO date in YYYY-MM-DD format')
  .refine((val) => {
    const [year, month, day] = val.split('-').map(Number)
    if (month < 1 || month > 12) return false
    const daysInMonth = new Date(year, month, 0).getDate()
    return day >= 1 && day <= daysInMonth
  }, 'Must be a valid calendar date')

// ==============================================================================
// 1. Company Management Schemas
// ==============================================================================

export const OfficerCompanyDetailSchema = z.object({
  id: z.string().min(1).max(20),
  name: z.string().min(1).max(100),
  industry: z.string().min(1).max(100),
  activeDrivesCount: z.number().int().nonnegative(),
  totalDrivesCount: z.number().int().nonnegative(),
})

export const PagedOfficerCompanyResponseSchema = z.object({
  content: z.array(OfficerCompanyDetailSchema).max(500),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  last: z.boolean(),
})

export const OfficerCompanyCreateSchema = z.object({
  id: z
    .string()
    .min(1, 'Company ID is required')
    .max(20, 'Company ID cannot exceed 20 characters')
    .regex(/^[A-Za-z0-9_-]+$/, 'ID can only contain letters, numbers, hyphens, and underscores'),
  name: z
    .string()
    .min(1, 'Company name is required')
    .max(100, 'Company name cannot exceed 100 characters'),
  industry: z
    .string()
    .min(1, 'Industry is required')
    .max(100, 'Industry cannot exceed 100 characters'),
})

export const OfficerCompanyUpdateSchema = z.object({
  name: z
    .string()
    .min(1, 'Company name is required')
    .max(100, 'Company name cannot exceed 100 characters'),
  industry: z
    .string()
    .min(1, 'Industry is required')
    .max(100, 'Industry cannot exceed 100 characters'),
})

// ==============================================================================
// 2. Recruitment Drive Management Schemas
// ==============================================================================

export const OfficerDriveDetailSchema = z.object({
  id: z.string().min(1).max(20),
  companyId: z.string().min(1).max(20),
  companyName: z.string().min(1),
  jobTitle: z.string().min(1).max(100),
  minimumCgpa: z.number().min(0.0).max(10.0),
  eligibleBranches: z
    .union([z.array(z.string().min(1).max(50)).max(50), z.set(z.string().min(1).max(50)).max(50)])
    .transform((val) => (Array.isArray(val) ? val : Array.from(val))),
  applicationDeadline: LocalDateSchema,
  closed: z.boolean(),
  totalApplications: z.number().int().nonnegative(),
  shortlistedApplications: z.number().int().nonnegative(),
})

export const PagedOfficerDriveResponseSchema = z.object({
  content: z.array(OfficerDriveDetailSchema).max(500),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  last: z.boolean(),
})

export const OfficerDriveCreateSchema = z.object({
  id: z
    .string()
    .min(1, 'Drive ID is required')
    .max(20, 'Drive ID cannot exceed 20 characters')
    .regex(/^[A-Za-z0-9_-]+$/, 'ID can only contain letters, numbers, hyphens, and underscores'),
  companyId: z
    .string()
    .min(1, 'Company ID is required')
    .max(20, 'Company ID cannot exceed 20 characters'),
  jobTitle: z
    .string()
    .min(1, 'Job title is required')
    .max(100, 'Job title cannot exceed 100 characters'),
  minimumCgpa: z
    .number('Minimum CGPA must be a valid number')
    .min(0.0, 'Minimum CGPA cannot be negative')
    .max(10.0, 'Minimum CGPA cannot exceed 10.0'),
  eligibleBranches: z
    .array(z.string().min(1, 'Branch code cannot be blank').max(50))
    .min(1, 'Select at least one eligible branch')
    .max(50, 'Cannot exceed 50 eligible branches'),
  applicationDeadline: LocalDateSchema,
})

export const OfficerDriveUpdateSchema = z.object({
  jobTitle: z
    .string()
    .min(1, 'Job title is required')
    .max(100, 'Job title cannot exceed 100 characters'),
  minimumCgpa: z
    .number('Minimum CGPA must be a valid number')
    .min(0.0, 'Minimum CGPA cannot be negative')
    .max(10.0, 'Minimum CGPA cannot exceed 10.0'),
  eligibleBranches: z
    .array(z.string().min(1, 'Branch code cannot be blank').max(50))
    .min(1, 'Select at least one eligible branch')
    .max(50, 'Cannot exceed 50 eligible branches'),
  applicationDeadline: LocalDateSchema,
})

// ==============================================================================
// 3. Candidate Review & Shortlisting Schemas
// ==============================================================================

export const OfficerApplicationReviewSchema = z.object({
  id: z.string().min(1),
  studentId: z.string().min(1),
  studentName: z.string().min(1),
  studentEmail: z.string().email(),
  branch: z.string().min(1),
  cgpa: z.number().min(0.0).max(10.0),
  driveId: z.string().min(1),
  jobTitle: z.string().min(1),
  companyName: z.string().min(1),
  status: ApplicationStatusSchema,
  appliedAt: LocalDateTimeSchema, // Strict offset-free LocalDateTime string
  remarks: z.string().nullable(),
})

export const PagedOfficerApplicationReviewResponseSchema = z.object({
  content: z.array(OfficerApplicationReviewSchema).max(1000),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  last: z.boolean(),
})

// ==============================================================================
// 4. Resource Administration Schemas (Interviewers & Rooms)
// ==============================================================================

export const OfficerInterviewerDetailSchema = z.object({
  id: z.string().min(1).max(20),
  name: z.string().min(1).max(100),
  companyId: z.string().min(1).max(20),
  companyName: z.string().min(1),
})

// Raw JSON Array - NOT PagedResponse!
export const OfficerInterviewerArraySchema = z.array(OfficerInterviewerDetailSchema).max(500)

export const OfficerInterviewerCreateSchema = z.object({
  id: z
    .string()
    .min(1, 'Interviewer ID is required')
    .max(20, 'Interviewer ID cannot exceed 20 characters'),
  name: z
    .string()
    .min(1, 'Interviewer name is required')
    .max(100, 'Interviewer name cannot exceed 100 characters'),
  companyId: z
    .string()
    .min(1, 'Company ID is required')
    .max(20, 'Company ID cannot exceed 20 characters'),
})

export const OfficerInterviewerUpdateSchema = z.object({
  name: z
    .string()
    .min(1, 'Interviewer name is required')
    .max(100, 'Interviewer name cannot exceed 100 characters'),
  companyId: z
    .string()
    .min(1, 'Company ID is required')
    .max(20, 'Company ID cannot exceed 20 characters'),
})

export const OfficerRoomDetailSchema = z.object({
  id: z.string().min(1).max(20),
  name: z.string().min(1).max(100),
  capacity: z.number().int().positive('Room capacity must be at least 1'),
})

// Raw JSON Array - NOT PagedResponse!
export const OfficerRoomArraySchema = z.array(OfficerRoomDetailSchema).max(500)

export const OfficerRoomCreateSchema = z.object({
  id: z
    .string()
    .min(1, 'Room ID is required')
    .max(20, 'Room ID cannot exceed 20 characters'),
  name: z
    .string()
    .min(1, 'Room name is required')
    .max(100, 'Room name cannot exceed 100 characters'),
  capacity: z
    .number('Capacity must be a number')
    .int('Capacity must be an integer')
    .min(1, 'Room capacity must be at least 1'),
})

export const OfficerRoomUpdateSchema = z.object({
  name: z
    .string()
    .min(1, 'Room name is required')
    .max(100, 'Room name cannot exceed 100 characters'),
  capacity: z
    .number('Capacity must be a number')
    .int('Capacity must be an integer')
    .min(1, 'Room capacity must be at least 1'),
})

// ==============================================================================
// 5. Interview Scheduling Studio Schemas (CAP-8.4 Authoritative Baseline)
// ==============================================================================

export const LocalTimeSchema = z
  .string()
  .regex(/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/, 'Must be a valid ISO time in HH:mm or HH:mm:ss format')

export const InstantSchema = z
  .string()
  .regex(
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})$/,
    'Must be a valid ISO-8601 Instant with timezone indicator (e.g. 2026-10-19T17:30:00Z)'
  )
  .refine((val) => !isNaN(Date.parse(val)), 'Must be a valid parseable UTC Instant')

export const PublicationOutcomeSchema = z.enum([
  'ALL_CONFIRMED',
  'PARTIAL_SUCCESS',
  'NONE_CONFIRMED',
])

export const ProposedSessionSchema = z.object({
  sessionId: z.string().min(1),
  applicationId: z.string().min(1),
  studentId: z.string().min(1),
  studentName: z.string().min(1),
  studentBranch: z.string().min(1),
  interviewerId: z.string().min(1),
  interviewerName: z.string().min(1),
  roomId: z.string().min(1),
  roomName: z.string().min(1),
  startTime: LocalDateTimeSchema,
  endTime: LocalDateTimeSchema,
})

export const UnscheduledCandidateSchema = z.object({
  applicationId: z.string().min(1),
  studentId: z.string().min(1),
  studentName: z.string().min(1),
  studentBranch: z.string().min(1),
  reason: z.string().min(1),
})

export const OfficerSchedulingPreviewRequestSchema = z
  .object({
    driveId: z.string().min(1, 'Drive ID is required'),
    date: LocalDateSchema,
    startTime: LocalTimeSchema,
    endTime: LocalTimeSchema,
    durationMinutes: z.number().int().min(1).max(240).optional().default(45),
    bufferMinutes: z.number().int().min(0).max(120).optional().default(15),
    interviewerIds: z.array(z.string().min(1)).max(200).optional(),
    roomIds: z.array(z.string().min(1)).max(200).optional(),
  })
  .refine((data) => data.startTime < data.endTime, {
    message: 'Start time must be strictly before end time',
    path: ['startTime'],
  })

export const OfficerSchedulingPreviewResponseSchema = z.object({
  previewId: z.string().min(1),
  driveId: z.string().min(1),
  jobTitle: z.string().min(1),
  companyName: z.string().min(1),
  date: LocalDateSchema,
  startTime: LocalTimeSchema,
  endTime: LocalTimeSchema,
  durationMinutes: z.number().int().positive(),
  bufferMinutes: z.number().int().nonnegative(),
  expiresAt: InstantSchema,
  totalRequested: z.number().int().nonnegative(),
  totalScheduled: z.number().int().nonnegative(),
  totalUnscheduled: z.number().int().nonnegative(),
  proposedSessions: z.array(ProposedSessionSchema).max(2000),
  unscheduledCandidates: z.array(UnscheduledCandidateSchema).max(2000),
})

export const OfficerSchedulingPublishRequestSchema = z.object({
  previewId: z.string().min(1, 'Preview ID is required'),
})

export const PublishedSessionDetailSchema = z.object({
  sessionId: z.string().min(1),
  applicationId: z.string().min(1),
  studentId: z.string().min(1),
  studentName: z.string().min(1),
  interviewerName: z.string().min(1),
  roomName: z.string().min(1),
  startTime: LocalDateTimeSchema,
  endTime: LocalDateTimeSchema,
})

export const PublicationConflictDetailSchema = z.object({
  applicationId: z.string().min(1),
  message: z.string().min(1),
  category: z.string().min(1),
})

export const OfficerPublicationSummarySchema = z.object({
  attemptId: z.string().min(1),
  previewId: z.string().min(1),
  previewReference: z.string().min(1),
  driveId: z.string().min(1),
  jobTitle: z.string().min(1),
  companyName: z.string().min(1),
  outcome: PublicationOutcomeSchema,
  recoveryInstructions: z.string(),
  publishedAt: InstantSchema,
  totalProposals: z.number().int().nonnegative(),
  publishedCount: z.number().int().nonnegative(),
  skippedDuplicatesCount: z.number().int().nonnegative(),
  conflictsCount: z.number().int().nonnegative(),
  errorsCount: z.number().int().nonnegative(),
  unattemptedCount: z.number().int().nonnegative(),
  confirmedSessionIds: z.array(z.string().min(1).max(50)).max(2000),
  publishedSessions: z.array(PublishedSessionDetailSchema).max(2000),
  skippedDuplicateApplicationIds: z.array(z.string().min(1).max(50)).max(2000),
  conflictRejections: z.array(PublicationConflictDetailSchema).max(2000),
  infrastructureErrors: z.array(z.string()).max(500),
})

export const OfficerInterviewDetailSchema = z.object({
  sessionId: z.string().min(1),
  applicationId: z.string().min(1),
  studentId: z.string().min(1),
  studentName: z.string().min(1),
  studentBranch: z.string().min(1),
  driveId: z.string().min(1),
  jobTitle: z.string().min(1),
  companyId: z.string().min(1),
  companyName: z.string().min(1),
  interviewerId: z.string().min(1),
  interviewerName: z.string().min(1),
  roomId: z.string().min(1),
  roomName: z.string().min(1),
  startTime: LocalDateTimeSchema,
  endTime: LocalDateTimeSchema,
  status: ApplicationStatusSchema,
})

export const PagedOfficerInterviewDetailResponseSchema = z.object({
  content: z.array(OfficerInterviewDetailSchema).max(1000),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  last: z.boolean(),
})

