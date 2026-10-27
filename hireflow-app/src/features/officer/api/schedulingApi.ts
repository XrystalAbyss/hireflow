/**
 * Authoritative API Client and React Query Hooks for HireFlow Interview Scheduling Studio (CAP-8.4).
 * 
 * Implements exactly four REST endpoints backed by the CAP-7 Spring Boot controller:
 * 1. POST /api/v1/officer/scheduling/preview   - Advisory timetable preview (zero DB mutation)
 * 2. POST /api/v1/officer/scheduling/publish   - Transactional timetable publication (200 / 207 / 409)
 * 3. GET  /api/v1/officer/scheduling/published - Confirmed interview timetable with filters & pagination
 * 4. GET  /api/v1/officer/scheduling/published/{id} - Confirmed individual session inspection
 * 
 * STRICT INVARIANTS:
 * - When simulation mode is OFF (default), real HTTP requests are dispatched. If unreachable or errored,
 *   real backend error states (4xx, 5xx, or network failure) are propagated. NO SILENT FALLBACK.
 * - When simulation mode is ON (explicit developer opt-in), deterministic local simulations execute.
 * - Raw preview tokens are kept strictly in memory and are never serialized to URLs or persistent storage.
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient, ApiError } from '@/core/api/client'
import type {
  OfficerSchedulingPreviewRequestDto,
  OfficerSchedulingPreviewResponseDto,
  OfficerSchedulingPublishRequestDto,
  OfficerPublicationSummaryDto,
  OfficerInterviewDetailDto,
  PagedResponse,
  ApiErrorResponse,
} from '@/types/api'
import {
  OfficerSchedulingPreviewRequestSchema,
  OfficerSchedulingPreviewResponseSchema,
  OfficerSchedulingPublishRequestSchema,
  OfficerPublicationSummarySchema,
  OfficerInterviewDetailSchema,
  PagedOfficerInterviewDetailResponseSchema,
} from './schemas'
import {
  isSimulationModeActive,
  simulateSchedulingPreview,
  simulatePublication,
  simulatePublishedTimetable,
  simulatePublishedSessionById,
} from './schedulingSimulation'

export const schedulingQueryKeys = {
  all: ['officer', 'scheduling'] as const,
  published: (filters?: { driveId?: string; companyId?: string; date?: string; page?: number; size?: number }) =>
    ['officer', 'scheduling', 'published', filters] as const,
  session: (id: string) => ['officer', 'scheduling', 'published', 'session', id] as const,
}

// ==============================================================================
// 1. Advisory Preview (POST /api/v1/officer/scheduling/preview)
// ==============================================================================

export async function fetchSchedulingPreview(
  requestDto: OfficerSchedulingPreviewRequestDto
): Promise<OfficerSchedulingPreviewResponseDto> {
  // Validate request payload
  OfficerSchedulingPreviewRequestSchema.parse(requestDto)

  if (isSimulationModeActive()) {
    return simulateSchedulingPreview(requestDto)
  }

  // Real backend call: zero DB mutation on server
  const data = await apiClient.request<OfficerSchedulingPreviewResponseDto>(
    '/api/v1/officer/scheduling/preview',
    {
      method: 'POST',
      body: JSON.stringify(requestDto),
    }
  )

  return OfficerSchedulingPreviewResponseSchema.parse(data)
}

// ==============================================================================
// 2. Timetable Publication (POST /api/v1/officer/scheduling/publish)
// ==============================================================================

export interface PublicationResultEnvelope {
  status: number
  data: OfficerPublicationSummaryDto
}

export async function publishSchedule(
  requestDto: OfficerSchedulingPublishRequestDto
): Promise<PublicationResultEnvelope> {
  // Validate request
  OfficerSchedulingPublishRequestSchema.parse(requestDto)

  if (isSimulationModeActive()) {
    return simulatePublication(requestDto.previewId)
  }

  // Direct fetch call to cleanly preserve and inspect status codes 200, 207, and 409
  // (Both 200 and 207 are successful HTTP responses with OfficerPublicationSummaryDto bodies)
  const csrfToken = await apiClient.fetchCsrfToken()
  const response = await fetch('/api/v1/officer/scheduling/publish', {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-CSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(requestDto),
  })

  // Handle HTTP 200 (ALL_CONFIRMED) & HTTP 207 (PARTIAL_SUCCESS)
  if (response.status === 200 || response.status === 207) {
    const json = await response.json()
    const validated = OfficerPublicationSummarySchema.parse(json)
    return {
      status: response.status,
      data: validated,
    }
  }

  // Handle HTTP 409 (Conflict) - Can be NONE_CONFIRMED outcome OR PreviewConsumedException
  if (response.status === 409) {
    const json = await response.json()
    // Distinguish between OfficerPublicationSummaryDto (NONE_CONFIRMED outcome)
    // and ApiErrorResponse (PreviewConsumedException)
    if (json && json.outcome === 'NONE_CONFIRMED') {
      const validated = OfficerPublicationSummarySchema.parse(json)
      return {
        status: 409,
        data: validated,
      }
    }
    // Otherwise it is PreviewConsumedException or generic 409
    throw new ApiError(json as ApiErrorResponse)
  }

  // Handle all other HTTP error statuses (401, 403, 404, 410, 5xx)
  let errorPayload: ApiErrorResponse
  try {
    errorPayload = (await response.json()) as ApiErrorResponse
  } catch {
    errorPayload = {
      status: response.status,
      error: response.statusText || 'Error',
      message: `Publication failed with HTTP status ${response.status}`,
      path: '/api/v1/officer/scheduling/publish',
      timestamp: new Date().toISOString(),
    }
  }
  throw new ApiError(errorPayload)
}

// ==============================================================================
// 3. Confirmed Timetable (GET /api/v1/officer/scheduling/published)
// ==============================================================================

export async function fetchPublishedSchedule(params: {
  driveId?: string
  companyId?: string
  date?: string
  page?: number
  size?: number
} = {}): Promise<PagedResponse<OfficerInterviewDetailDto>> {
  const page = Math.max(0, params.page ?? 0)
  const size = Math.min(100, Math.max(1, params.size ?? 20))

  if (isSimulationModeActive()) {
    return simulatePublishedTimetable({ ...params, page, size })
  }

  const query = new URLSearchParams({
    page: String(page),
    size: String(size),
  })
  if (params.driveId && params.driveId.trim()) {
    query.set('driveId', params.driveId.trim())
  }
  if (params.companyId && params.companyId.trim()) {
    query.set('companyId', params.companyId.trim())
  }
  if (params.date && params.date.trim()) {
    query.set('date', params.date.trim())
  }

  const data = await apiClient.request<PagedResponse<OfficerInterviewDetailDto>>(
    `/api/v1/officer/scheduling/published?${query.toString()}`
  )

  return PagedOfficerInterviewDetailResponseSchema.parse(data)
}

// ==============================================================================
// 4. Confirmed Session Detail (GET /api/v1/officer/scheduling/published/{id})
// ==============================================================================

export async function fetchPublishedSessionById(
  id: string
): Promise<OfficerInterviewDetailDto> {
  if (!id || id.trim() === '') {
    throw new Error('Interview session ID cannot be empty.')
  }

  const cleanId = id.trim().toUpperCase()

  if (isSimulationModeActive()) {
    return simulatePublishedSessionById(cleanId)
  }

  const data = await apiClient.request<OfficerInterviewDetailDto>(
    `/api/v1/officer/scheduling/published/${encodeURIComponent(cleanId)}`
  )

  return OfficerInterviewDetailSchema.parse(data)
}

// ==============================================================================
// React Query Hooks
// ==============================================================================

export function useSchedulingPreviewMutation() {
  return useMutation({
    mutationFn: (requestDto: OfficerSchedulingPreviewRequestDto) =>
      fetchSchedulingPreview(requestDto),
  })
}

export function usePublishScheduleMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (requestDto: OfficerSchedulingPublishRequestDto) =>
      publishSchedule(requestDto),
    onSuccess: () => {
      // Invalidate confirmed timetables and applications across officer and student queries
      queryClient.invalidateQueries({ queryKey: schedulingQueryKeys.all })
      queryClient.invalidateQueries({ queryKey: ['officer', 'drives'] })
      queryClient.invalidateQueries({ queryKey: ['officer', 'driveApplications'] })
      queryClient.invalidateQueries({ queryKey: ['student', 'interviews'] })
      queryClient.invalidateQueries({ queryKey: ['student', 'dashboard'] })
    },
  })
}

export function usePublishedScheduleQuery(params: {
  driveId?: string
  companyId?: string
  date?: string
  page?: number
  size?: number
} = {}) {
  return useQuery({
    queryKey: schedulingQueryKeys.published(params),
    queryFn: () => fetchPublishedSchedule(params),
  })
}

export function usePublishedSessionQuery(id: string | null) {
  return useQuery({
    queryKey: schedulingQueryKeys.session(id || ''),
    queryFn: () => fetchPublishedSessionById(id!),
    enabled: Boolean(id && id.trim() !== ''),
  })
}
