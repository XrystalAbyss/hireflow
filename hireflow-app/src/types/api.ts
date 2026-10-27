/**
 * Machine-Grounded TypeScript API Contract for HireFlow (CAP-7 Backend Baseline)
 * Derived from Spring Boot DTOs and verified via RouteInventoryContractTest.
 */

// ==============================================================================
// Authentication & Principal Types
// ==============================================================================

export type UserRole = 'STUDENT' | 'PLACEMENT_OFFICER' | 'ADMINISTRATOR'

export interface UserProfileDto {
  username: string
  displayName: string
  role: UserRole
  studentId: string | null
  lastLoginAt: string | null
}

export interface LoginRequestDto {
  username: string
  password: string
}

export interface LogoutResponseDto {
  message: string
}

export interface CsrfTokenDto {
  token: string
  headerName: string
  parameterName: string
}

export interface HealthStatusDto {
  status: string
  database: string
  flyway: string
}

// ==============================================================================
// Common Envelope Types
// ==============================================================================

export interface ApiErrorResponse {
  status: number
  error: string
  message: string
  path: string
  timestamp: string
}

export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

// ==============================================================================
// Public Catalogs
// ==============================================================================

export interface CompanyDto {
  id: string
  name: string
  industry: string
  createdAt: string | null
}

export interface RecruitmentDriveDto {
  id: string
  companyId: string | null
  companyName: string | null
  jobTitle: string
  minimumCgpa: number
  eligibleBranches: string[]
  applicationDeadline: string
  isClosed: boolean
  createdAt: string | null
}

// ==============================================================================
// Student Portal Types
// ==============================================================================

export type ApplicationStatus =
  | 'APPLIED'
  | 'SHORTLISTED'
  | 'SCHEDULED'
  | 'REJECTED'
  | 'ELIGIBLE'
  | 'INELIGIBLE'
  | 'SELECTED'

export interface StudentProfileDto {
  id: string
  name: string
  email: string
  branch: string
  cgpa: number
  graduationYear: number
  active: boolean
}

export interface StudentDriveItemDto {
  id: string
  companyId: string
  companyName: string
  jobTitle: string
  minimumCgpa: number
  eligibleBranches: string[]
  applicationDeadline: string
  closed: boolean
  deadlinePassed: boolean
  hasApplied: boolean
  existingApplicationId: string | null
  existingApplicationStatus: ApplicationStatus | null
  academicallyEligible: boolean
  acceptingApplications: boolean
  canApply: boolean
  eligibilityReasons: string[]
}

export interface StudentDriveEligibilityDto {
  driveId: string
  studentId: string
  academicallyEligible: boolean
  acceptingApplications: boolean
  alreadyApplied: boolean
  canApply: boolean
  existingApplicationId: string | null
  existingApplicationStatus: ApplicationStatus | null
  minimumCgpa: number
  studentCgpa: number
  eligibleBranches: string[]
  studentBranch: string
  applicationDeadline: string
  closed: boolean
  deadlinePassed: boolean
  reasons: string[]
}

export interface ApplicationSubmitRequestDto {
  driveId: string
}

export interface StudentApplicationDetailDto {
  id: string
  studentId: string
  driveId: string
  companyId: string | null
  companyName: string
  jobTitle: string
  status: ApplicationStatus
  appliedAt: string // ISO-8601 offset-free LocalDateTime (e.g. "2026-10-19T01:30:00")
  remarks: string | null
}

export interface StudentDashboardDto {
  profile: StudentProfileDto
  totalEligibleDrives: number
  totalApplications: number
  shortlistedCount: number
  recentApplications: StudentApplicationDetailDto[]
}

export interface StudentInterviewDto {
  sessionId: string
  applicationId: string
  studentId: string
  studentName: string
  studentBranch: string
  driveId: string
  jobTitle: string
  companyName: string
  interviewerId: string
  interviewerName: string
  roomId: string
  roomName: string
  startTime: string // ISO-8601 offset-free LocalDateTime (e.g. "2026-10-24T10:00:00")
  endTime: string // ISO-8601 offset-free LocalDateTime (e.g. "2026-10-24T10:45:00")
  status: ApplicationStatus // Derived from related ApplicationStatus.name()
}

// ==============================================================================
// Placement Officer Management Types
// ==============================================================================

export interface OfficerCompanyDetailDto {
  id: string
  name: string
  industry: string
  activeDrivesCount: number
  totalDrivesCount: number
}

export interface OfficerCompanyCreateDto {
  id: string
  name: string
  industry: string
}

export interface OfficerCompanyUpdateDto {
  name: string
  industry: string
}

export interface OfficerDriveDetailDto {
  id: string
  companyId: string
  companyName: string
  jobTitle: string
  minimumCgpa: number
  eligibleBranches: string[]
  applicationDeadline: string
  closed: boolean
  totalApplications: number
  shortlistedApplications: number
}

export interface OfficerDriveCreateDto {
  id: string
  companyId: string
  jobTitle: string
  minimumCgpa: number
  eligibleBranches: string[]
  applicationDeadline: string
}

export interface OfficerDriveUpdateDto {
  jobTitle: string
  minimumCgpa: number
  eligibleBranches: string[]
  applicationDeadline: string
}

export interface OfficerApplicationReviewDto {
  id: string
  studentId: string
  studentName: string
  studentEmail: string
  branch: string
  cgpa: number
  driveId: string
  jobTitle: string
  companyName: string
  status: ApplicationStatus
  appliedAt: string
  remarks: string | null
}

export interface OfficerInterviewerDetailDto {
  id: string
  name: string
  companyId: string
  companyName: string
}

export interface OfficerInterviewerCreateDto {
  id: string
  name: string
  companyId: string
}

export interface OfficerInterviewerUpdateDto {
  name: string
  companyId: string
}

export interface OfficerRoomDetailDto {
  id: string
  name: string
  capacity: number
}

export interface OfficerRoomCreateDto {
  id: string
  name: string
  capacity: number
}

export interface OfficerRoomUpdateDto {
  name: string
  capacity: number
}

// ==============================================================================
// Interview Scheduling Studio Types
// ==============================================================================

export type PublicationOutcome = 'ALL_CONFIRMED' | 'PARTIAL_SUCCESS' | 'NONE_CONFIRMED'

export interface ProposedSessionDto {
  sessionId: string
  applicationId: string
  studentId: string
  studentName: string
  studentBranch: string
  interviewerId: string
  interviewerName: string
  roomId: string
  roomName: string
  startTime: string
  endTime: string
}

export interface UnscheduledCandidateDto {
  applicationId: string
  studentId: string
  studentName: string
  studentBranch: string
  reason: string
}

export interface OfficerSchedulingPreviewRequestDto {
  driveId: string
  date: string // YYYY-MM-DD
  startTime: string // HH:mm
  endTime: string // HH:mm
  durationMinutes?: number
  bufferMinutes?: number
  interviewerIds?: string[]
  roomIds?: string[]
}

export interface OfficerSchedulingPreviewResponseDto {
  previewId: string
  driveId: string
  jobTitle: string
  companyName: string
  date: string
  startTime: string
  endTime: string
  durationMinutes: number
  bufferMinutes: number
  expiresAt: string
  totalRequested: number
  totalScheduled: number
  totalUnscheduled: number
  proposedSessions: ProposedSessionDto[]
  unscheduledCandidates: UnscheduledCandidateDto[]
}

export interface OfficerSchedulingPublishRequestDto {
  previewId: string
}

export interface PublishedSessionDetailDto {
  sessionId: string
  applicationId: string
  studentId: string
  studentName: string
  interviewerName: string
  roomName: string
  startTime: string
  endTime: string
}

export interface PublicationConflictDetailDto {
  applicationId: string
  message: string
  category: string
}

export interface OfficerPublicationSummaryDto {
  attemptId: string
  previewId: string
  previewReference: string
  driveId: string
  jobTitle: string
  companyName: string
  outcome: PublicationOutcome
  recoveryInstructions: string
  publishedAt: string
  totalProposals: number
  publishedCount: number
  skippedDuplicatesCount: number
  conflictsCount: number
  errorsCount: number
  unattemptedCount: number
  confirmedSessionIds: string[]
  publishedSessions: PublishedSessionDetailDto[]
  skippedDuplicateApplicationIds: string[]
  conflictRejections: PublicationConflictDetailDto[]
  infrastructureErrors: string[]
}

export interface OfficerInterviewDetailDto {
  sessionId: string
  applicationId: string
  studentId: string
  studentName: string
  studentBranch: string
  driveId: string
  jobTitle: string
  companyId: string
  companyName: string
  interviewerId: string
  interviewerName: string
  roomId: string
  roomName: string
  startTime: string
  endTime: string
  status: string
}
