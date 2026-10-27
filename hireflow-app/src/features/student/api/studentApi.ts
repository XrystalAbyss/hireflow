import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient, ApiError } from '@/core/api/client'
import type {
  StudentDashboardDto,
  StudentProfileDto,
  StudentDriveItemDto,
  StudentDriveEligibilityDto,
  StudentApplicationDetailDto,
  StudentInterviewDto,
  PagedResponse,
  ApplicationSubmitRequestDto,
} from '@/types/api'
import {
  FIXTURE_STUDENT_DASHBOARD,
  FIXTURE_STUDENT_PROFILE,
  FIXTURE_STUDENT_DRIVES,
  FIXTURE_DRIVE_ELIGIBILITY,
  FIXTURE_STUDENT_APPLICATIONS,
  FIXTURE_STUDENT_INTERVIEWS,
} from '../fixtures'
import {
  StudentProfileSchema,
  StudentDriveItemArraySchema,
  StudentDriveEligibilitySchema,
  StudentApplicationDetailSchema,
  PagedStudentApplicationResponseSchema,
  StudentInterviewArraySchema,
  StudentInterviewSchema,
  StudentDashboardSchema,
} from './schemas'

// Mutable in-memory store for dev fallback to simulate application submissions
let localFallbackApplications = [...FIXTURE_STUDENT_APPLICATIONS]

/**
 * Helper to determine whether we should fallback to dev fixtures.
 * CRITICAL SECURITY INVARIANT (CAP-8.5.2):
 * - HTTP 401 and HTTP 403 MUST NEVER activate offline fixture fallback in normal operation.
 * - Simulation requires explicit opt-in via window.__HIREFLOW_SIMULATION_MODE__ === true.
 * - Production builds strictly forbid simulation.
 */
function isOfflineNetworkError(error: unknown): boolean {
  if (import.meta.env.PROD || import.meta.env.VITE_HIREFLOW_MODE === 'live') return false

  const isSimulationOptIn = typeof window !== 'undefined' && 
    Boolean((window as any).__HIREFLOW_SIMULATION_MODE__)

  if (error instanceof ApiError) {
    if (error.status === 401 || error.status === 403) {
      // 401/403 must never fall back to fixtures unless explicitly in simulation mode
      return isSimulationOptIn
    }
    if (error.status >= 400 && error.status < 500) {
      // Authoritative 4xx client errors (400, 404, 409, 410, 422) must never be masked
      return false
    }
  }

  // Network connection failure (e.g. backend offline)
  return isSimulationOptIn
}

// ==============================================================================
// Pure API Functions with Boundary Runtime Schema Validation
// ==============================================================================

export async function fetchStudentDashboard(): Promise<StudentDashboardDto> {
  try {
    const data = await apiClient.request<StudentDashboardDto>('/api/v1/student/dashboard')
    return StudentDashboardSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn('[HireFlow Dev] Live backend unreachable; using student dashboard fixture.')
      const fallback: StudentDashboardDto = {
        ...FIXTURE_STUDENT_DASHBOARD,
        recentApplications: localFallbackApplications,
        totalApplications: localFallbackApplications.length,
      }
      return StudentDashboardSchema.parse(fallback)
    }
    throw err
  }
}

export async function fetchStudentProfile(): Promise<StudentProfileDto> {
  try {
    const data = await apiClient.request<StudentProfileDto>('/api/v1/student/profile')
    return StudentProfileSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn('[HireFlow Dev] Live backend unreachable; using student profile fixture.')
      return StudentProfileSchema.parse(FIXTURE_STUDENT_PROFILE)
    }
    throw err
  }
}

export async function fetchStudentDrives(): Promise<StudentDriveItemDto[]> {
  try {
    const data = await apiClient.request<StudentDriveItemDto[]>('/api/v1/student/drives')
    return StudentDriveItemArraySchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn('[HireFlow Dev] Live backend unreachable; using student drives fixture.')
      // Reflect any locally submitted applications
      const fallback = FIXTURE_STUDENT_DRIVES.map((d) => {
        const app = localFallbackApplications.find((a) => a.driveId === d.id)
        if (app) {
          return {
            ...d,
            hasApplied: true,
            existingApplicationId: app.id,
            existingApplicationStatus: app.status,
            canApply: false,
            eligibilityReasons: [`You have already submitted an application for this drive (${app.id}).`],
          }
        }
        return d
      })
      return StudentDriveItemArraySchema.parse(fallback)
    }
    throw err
  }
}

export async function fetchDriveEligibility(driveId: string): Promise<StudentDriveEligibilityDto> {
  try {
    const data = await apiClient.request<StudentDriveEligibilityDto>(`/api/v1/student/drives/${driveId}/eligibility`)
    return StudentDriveEligibilitySchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn(`[HireFlow Dev] Live backend unreachable; using eligibility fixture for ${driveId}.`)
      const app = localFallbackApplications.find((a) => a.driveId === driveId)
      const base = FIXTURE_DRIVE_ELIGIBILITY[driveId] || {
        driveId,
        studentId: FIXTURE_STUDENT_PROFILE.id,
        academicallyEligible: true,
        acceptingApplications: true,
        alreadyApplied: !!app,
        canApply: !app,
        existingApplicationId: app ? app.id : null,
        existingApplicationStatus: app ? app.status : null,
        minimumCgpa: 8.0,
        studentCgpa: FIXTURE_STUDENT_PROFILE.cgpa,
        eligibleBranches: ['Computer Science'],
        studentBranch: FIXTURE_STUDENT_PROFILE.branch,
        applicationDeadline: '2026-11-30',
        closed: false,
        deadlinePassed: false,
        reasons: app ? [`Already applied (${app.id})`] : [],
      }

      if (app) {
        return StudentDriveEligibilitySchema.parse({
          ...base,
          alreadyApplied: true,
          canApply: false,
          existingApplicationId: app.id,
          existingApplicationStatus: app.status,
          reasons: [`You have already submitted an application for this drive (${app.id}).`],
        })
      }
      return StudentDriveEligibilitySchema.parse(base)
    }
    throw err
  }
}

export async function submitStudentApplication(driveId: string): Promise<StudentApplicationDetailDto> {
  try {
    const payload: ApplicationSubmitRequestDto = { driveId }
    const data = await apiClient.request<StudentApplicationDetailDto>('/api/v1/student/applications', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    return StudentApplicationDetailSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn(`[HireFlow Dev] Live backend unreachable; simulating application submission for drive ${driveId}.`)
      const drive = FIXTURE_STUDENT_DRIVES.find((d) => d.id === driveId)
      const existing = localFallbackApplications.find((a) => a.driveId === driveId)
      if (existing) {
        throw new ApiError({
          status: 409,
          error: 'Conflict',
          message: `Application already exists for drive '${driveId}' by this student.`,
          path: '/api/v1/student/applications',
          timestamp: new Date().toISOString(),
        })
      }

      const newApp: StudentApplicationDetailDto = {
        id: `APP-2026-${String(localFallbackApplications.length + 1).padStart(3, '0')}`,
        studentId: FIXTURE_STUDENT_PROFILE.id,
        driveId,
        companyId: drive?.companyId || 'CMP-DEV',
        companyName: drive?.companyName || 'Corporate Partner',
        jobTitle: drive?.jobTitle || 'Placement Opportunity',
        status: 'APPLIED',
        appliedAt: new Date().toISOString().replace('Z', '').slice(0, 19),
        remarks: 'Application submitted and awaiting departmental verification.',
      }
      localFallbackApplications = [newApp, ...localFallbackApplications]
      return StudentApplicationDetailSchema.parse(newApp)
    }
    throw err
  }
}

export async function fetchStudentApplications(
  page: number = 0,
  size: number = 20,
  sort: string = 'appliedAt,desc'
): Promise<PagedResponse<StudentApplicationDetailDto>> {
  try {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
      sort,
    })
    const data = await apiClient.request<PagedResponse<StudentApplicationDetailDto>>(
      `/api/v1/student/applications?${params.toString()}`
    )
    return PagedStudentApplicationResponseSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn('[HireFlow Dev] Live backend unreachable; using applications paged fixture.')
      const totalElements = localFallbackApplications.length
      const totalPages = Math.ceil(totalElements / size) || 1
      const from = page * size
      const to = Math.min(from + size, totalElements)
      const content = localFallbackApplications.slice(from, to)

      const fallback: PagedResponse<StudentApplicationDetailDto> = {
        content,
        page,
        size,
        totalElements,
        totalPages,
        last: page >= totalPages - 1,
      }
      return PagedStudentApplicationResponseSchema.parse(fallback)
    }
    throw err
  }
}

export async function fetchApplicationById(applicationId: string): Promise<StudentApplicationDetailDto> {
  try {
    const data = await apiClient.request<StudentApplicationDetailDto>(`/api/v1/student/applications/${applicationId}`)
    return StudentApplicationDetailSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn(`[HireFlow Dev] Live backend unreachable; finding application fixture for ${applicationId}.`)
      const found = localFallbackApplications.find((a) => a.id.toUpperCase() === applicationId.toUpperCase())
      if (!found) {
        throw new ApiError({
          status: 404,
          error: 'Not Found',
          message: `Application with ID '${applicationId}' was not found.`,
          path: `/api/v1/student/applications/${applicationId}`,
          timestamp: new Date().toISOString(),
        })
      }
      return StudentApplicationDetailSchema.parse(found)
    }
    throw err
  }
}

export async function fetchStudentInterviews(): Promise<StudentInterviewDto[]> {
  try {
    const data = await apiClient.request<StudentInterviewDto[]>('/api/v1/student/interviews')
    return StudentInterviewArraySchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn('[HireFlow Dev] Live backend unreachable; using student interviews fixture.')
      return StudentInterviewArraySchema.parse(FIXTURE_STUDENT_INTERVIEWS)
    }
    throw err
  }
}

export async function fetchInterviewById(sessionId: string): Promise<StudentInterviewDto> {
  try {
    const data = await apiClient.request<StudentInterviewDto>(`/api/v1/student/interviews/${sessionId}`)
    return StudentInterviewSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn(`[HireFlow Dev] Live backend unreachable; finding interview fixture for ${sessionId}.`)
      const found = FIXTURE_STUDENT_INTERVIEWS.find((s) => s.sessionId.toUpperCase() === sessionId.toUpperCase())
      if (!found) {
        throw new ApiError({
          status: 404,
          error: 'Not Found',
          message: `Interview session with ID '${sessionId}' was not found.`,
          path: `/api/v1/student/interviews/${sessionId}`,
          timestamp: new Date().toISOString(),
        })
      }
      return StudentInterviewSchema.parse(found)
    }
    throw err
  }
}

// ==============================================================================
// TanStack Query Hooks
// ==============================================================================

export const studentQueryKeys = {
  all: ['student'] as const,
  dashboard: () => [...studentQueryKeys.all, 'dashboard'] as const,
  profile: () => [...studentQueryKeys.all, 'profile'] as const,
  drives: () => [...studentQueryKeys.all, 'drives'] as const,
  eligibility: (driveId: string) => [...studentQueryKeys.all, 'eligibility', driveId] as const,
  applications: (page: number, size: number, sort: string) =>
    [...studentQueryKeys.all, 'applications', { page, size, sort }] as const,
  applicationDetail: (id: string) => [...studentQueryKeys.all, 'application', id] as const,
  interviews: () => [...studentQueryKeys.all, 'interviews'] as const,
  interviewDetail: (id: string) => [...studentQueryKeys.all, 'interview', id] as const,
}

export function useStudentDashboard() {
  return useQuery({
    queryKey: studentQueryKeys.dashboard(),
    queryFn: fetchStudentDashboard,
    refetchInterval: 30_000,
  })
}

export function useStudentProfile(enabled: boolean = true) {
  return useQuery({
    queryKey: studentQueryKeys.profile(),
    queryFn: fetchStudentProfile,
    enabled,
  })
}

export function useStudentDrives() {
  return useQuery({
    queryKey: studentQueryKeys.drives(),
    queryFn: fetchStudentDrives,
    refetchInterval: 30_000,
  })
}

export function useDriveEligibility(driveId: string | undefined) {
  return useQuery({
    queryKey: studentQueryKeys.eligibility(driveId || ''),
    queryFn: () => fetchDriveEligibility(driveId!),
    enabled: !!driveId,
  })
}

export function useStudentApplications(page: number = 0, size: number = 20, sort: string = 'appliedAt,desc') {
  return useQuery({
    queryKey: studentQueryKeys.applications(page, size, sort),
    queryFn: () => fetchStudentApplications(page, size, sort),
    refetchInterval: 30_000,
  })
}

export function useApplicationDetail(applicationId: string | undefined) {
  return useQuery({
    queryKey: studentQueryKeys.applicationDetail(applicationId || ''),
    queryFn: () => fetchApplicationById(applicationId!),
    enabled: !!applicationId,
  })
}

export function useStudentInterviews() {
  return useQuery({
    queryKey: studentQueryKeys.interviews(),
    queryFn: fetchStudentInterviews,
    refetchInterval: 30_000,
  })
}

export function useInterviewDetail(sessionId: string | undefined) {
  return useQuery({
    queryKey: studentQueryKeys.interviewDetail(sessionId || ''),
    queryFn: () => fetchInterviewById(sessionId!),
    enabled: !!sessionId,
  })
}

export function useSubmitApplication() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (driveId: string) => submitStudentApplication(driveId),
    onSuccess: (_data, driveId) => {
      // Reconcile and invalidate student query caches
      queryClient.invalidateQueries({ queryKey: studentQueryKeys.dashboard() })
      queryClient.invalidateQueries({ queryKey: studentQueryKeys.drives() })
      queryClient.invalidateQueries({ queryKey: studentQueryKeys.eligibility(driveId) })
      queryClient.invalidateQueries({ queryKey: [...studentQueryKeys.all, 'applications'] })
    },
  })
}
