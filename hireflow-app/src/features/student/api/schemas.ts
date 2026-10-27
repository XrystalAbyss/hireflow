import { z } from 'zod'

/**
 * Authoritative Zod runtime validation schemas for HireFlow Student Portal.
 * Strictly aligned with Spring Boot CAP-7 Java record contracts.
 */

export const ApplicationStatusSchema = z.enum([
  'APPLIED',
  'ELIGIBLE',
  'INELIGIBLE',
  'SHORTLISTED',
  'SCHEDULED',
  'REJECTED',
  'SELECTED',
])

export const LocalDateSchema = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, 'Must match LocalDate YYYY-MM-DD ISO format')

export const LocalDateTimeSchema = z
  .string()
  .regex(
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?$/,
    'Must match offset-free ISO LocalDateTime format without timezone suffix (e.g. YYYY-MM-DDTHH:mm:ss)'
  )
  .refine((val) => {
    const [datePart, timePart] = val.split('T')
    if (!datePart || !timePart) return false
    const [year, month, day] = datePart.split('-').map(Number)
    const [hour, minute, secondAndFraction] = timePart.split(':')
    const h = Number(hour)
    const m = Number(minute)
    const s = secondAndFraction ? Math.floor(Number(secondAndFraction)) : 0
    if (month < 1 || month > 12) return false
    if (day < 1 || day > 31) return false
    if (h < 0 || h > 23) return false
    if (m < 0 || m > 59) return false
    if (s < 0 || s > 59) return false
    const d = new Date(year, month - 1, day, h, m, s)
    return (
      d.getFullYear() === year &&
      d.getMonth() === month - 1 &&
      d.getDate() === day
    )
  }, 'Must be a valid calendar date and time')

export const StudentProfileSchema = z.object({
  id: z.string().min(1, 'Candidate ID cannot be empty'),
  name: z.string().min(1),
  email: z.string().email(),
  branch: z.string().min(1),
  cgpa: z.number().min(0).max(10),
  graduationYear: z.number().int(),
  active: z.boolean(),
})

export const StudentDriveItemSchema = z.object({
  id: z.string().min(1, 'Drive ID cannot be empty'),
  companyId: z.string().min(1),
  companyName: z.string().min(1),
  jobTitle: z.string().min(1),
  minimumCgpa: z.number().min(0).max(10),
  eligibleBranches: z.array(z.string().min(1).max(50)).max(50),
  applicationDeadline: LocalDateSchema,
  closed: z.boolean(),
  deadlinePassed: z.boolean(),
  hasApplied: z.boolean(),
  existingApplicationId: z.string().nullable(),
  existingApplicationStatus: ApplicationStatusSchema.nullable(),
  academicallyEligible: z.boolean(),
  acceptingApplications: z.boolean(),
  canApply: z.boolean(),
  eligibilityReasons: z.array(z.string()).max(100),
})

export const StudentDriveItemArraySchema = z.array(StudentDriveItemSchema).max(500)

export const StudentDriveEligibilitySchema = z.object({
  driveId: z.string().min(1),
  studentId: z.string().min(1),
  academicallyEligible: z.boolean(),
  acceptingApplications: z.boolean(),
  alreadyApplied: z.boolean(),
  canApply: z.boolean(),
  existingApplicationId: z.string().nullable(),
  existingApplicationStatus: ApplicationStatusSchema.nullable(),
  minimumCgpa: z.number().min(0).max(10),
  studentCgpa: z.number().min(0).max(10),
  eligibleBranches: z.array(z.string().min(1).max(50)).max(50),
  studentBranch: z.string().min(1),
  applicationDeadline: LocalDateSchema,
  closed: z.boolean(),
  deadlinePassed: z.boolean(),
  reasons: z.array(z.string()).max(100),
})

export const StudentApplicationDetailSchema = z.object({
  id: z.string().min(1, 'Application ID cannot be empty'),
  studentId: z.string().min(1),
  driveId: z.string().min(1),
  companyId: z.string().nullable(),
  companyName: z.string().min(1),
  jobTitle: z.string().min(1),
  status: ApplicationStatusSchema,
  appliedAt: LocalDateTimeSchema,
  remarks: z.string().nullable(),
})

export const PagedStudentApplicationResponseSchema = z.object({
  content: z.array(StudentApplicationDetailSchema).max(500),
  page: z.number().int().min(0),
  size: z.number().int().min(1),
  totalElements: z.number().int().min(0),
  totalPages: z.number().int().min(0),
  last: z.boolean(),
})

export const StudentInterviewSchema = z.object({
  sessionId: z.string().min(1),
  applicationId: z.string().min(1),
  studentId: z.string().min(1),
  studentName: z.string().min(1),
  studentBranch: z.string().min(1),
  driveId: z.string().min(1),
  jobTitle: z.string().min(1),
  companyName: z.string().min(1),
  interviewerId: z.string().min(1),
  interviewerName: z.string().min(1),
  roomId: z.string().min(1),
  roomName: z.string().min(1),
  startTime: LocalDateTimeSchema,
  endTime: LocalDateTimeSchema,
  status: ApplicationStatusSchema,
})

export const StudentInterviewArraySchema = z.array(StudentInterviewSchema).max(500)

export const StudentDashboardSchema = z.object({
  profile: StudentProfileSchema,
  totalEligibleDrives: z.number().int().min(0),
  totalApplications: z.number().int().min(0),
  shortlistedCount: z.number().int().min(0),
  recentApplications: z.array(StudentApplicationDetailSchema).max(100),
})

export const ApplicationSubmitRequestSchema = z.object({
  driveId: z.string().min(1).max(32),
})

export const ApiErrorResponseSchema = z.object({
  status: z.number().int(),
  error: z.string(),
  message: z.string(),
  path: z.string(),
  timestamp: z.string(),
})

export type ValidatedStudentProfile = z.infer<typeof StudentProfileSchema>
export type ValidatedStudentDriveItem = z.infer<typeof StudentDriveItemSchema>
export type ValidatedStudentDriveEligibility = z.infer<typeof StudentDriveEligibilitySchema>
export type ValidatedStudentApplicationDetail = z.infer<typeof StudentApplicationDetailSchema>
export type ValidatedPagedApplications = z.infer<typeof PagedStudentApplicationResponseSchema>
export type ValidatedStudentInterview = z.infer<typeof StudentInterviewSchema>
export type ValidatedStudentDashboard = z.infer<typeof StudentDashboardSchema>
