import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient, ApiError } from '@/core/api/client'
import type {
  OfficerCompanyDetailDto,
  OfficerCompanyCreateDto,
  OfficerCompanyUpdateDto,
  OfficerDriveDetailDto,
  OfficerDriveCreateDto,
  OfficerDriveUpdateDto,
  OfficerApplicationReviewDto,
  OfficerInterviewerDetailDto,
  OfficerInterviewerCreateDto,
  OfficerInterviewerUpdateDto,
  OfficerRoomDetailDto,
  OfficerRoomCreateDto,
  OfficerRoomUpdateDto,
  PagedResponse,
} from '@/types/api'
import {
  FIXTURE_OFFICER_COMPANIES,
  FIXTURE_OFFICER_DRIVES,
  FIXTURE_OFFICER_APPLICATIONS,
  FIXTURE_OFFICER_INTERVIEWERS,
  FIXTURE_OFFICER_ROOMS,
} from '../fixtures'
import {
  OfficerCompanyDetailSchema,
  PagedOfficerCompanyResponseSchema,
  OfficerDriveDetailSchema,
  PagedOfficerDriveResponseSchema,
  OfficerApplicationReviewSchema,
  PagedOfficerApplicationReviewResponseSchema,
  OfficerInterviewerDetailSchema,
  OfficerInterviewerArraySchema,
  OfficerRoomDetailSchema,
  OfficerRoomArraySchema,
} from './schemas'

// In-memory mutable simulation stores for offline developer preview
let localCompanies = [...FIXTURE_OFFICER_COMPANIES]
let localDrives = [...FIXTURE_OFFICER_DRIVES]
let localApplications = [...FIXTURE_OFFICER_APPLICATIONS]
let localInterviewers = [...FIXTURE_OFFICER_INTERVIEWERS]
let localRooms = [...FIXTURE_OFFICER_ROOMS]

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
      // Authoritative backend responses (e.g. 404, 409, 410, 422) must not be masked
      return false
    }
  }

  // Network connection failure (e.g. backend offline)
  return isSimulationOptIn
}

// ==============================================================================
// 1. Company Management API Methods (4 Endpoints)
// ==============================================================================

export async function fetchOfficerCompanies(
  page: number = 0,
  size: number = 20,
  search?: string
): Promise<PagedResponse<OfficerCompanyDetailDto>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  })
  if (search && search.trim()) {
    params.set('search', search.trim())
  }

  try {
    const data = await apiClient.request<PagedResponse<OfficerCompanyDetailDto>>(
      `/api/v1/officer/companies?${params.toString()}`
    )
    return PagedOfficerCompanyResponseSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn('[HireFlow Dev] Live backend unreachable; using officer companies fixture.')
      let items = [...localCompanies]
      if (search && search.trim()) {
        const q = search.trim().toLowerCase()
        items = items.filter(
          (c) =>
            c.name.toLowerCase().includes(q) ||
            c.industry.toLowerCase().includes(q) ||
            c.id.toLowerCase().includes(q)
        )
      }
      const totalElements = items.length
      const totalPages = Math.ceil(totalElements / size) || 1
      const fromIndex = page * size
      const pagedContent = items.slice(fromIndex, fromIndex + size)

      return {
        content: pagedContent,
        page,
        size,
        totalElements,
        totalPages,
        last: page >= totalPages - 1,
      }
    }
    throw err
  }
}

export async function fetchOfficerCompanyById(id: string): Promise<OfficerCompanyDetailDto> {
  const normId = id.trim().toUpperCase()
  try {
    const data = await apiClient.request<OfficerCompanyDetailDto>(
      `/api/v1/officer/companies/${normId}`
    )
    return OfficerCompanyDetailSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn(`[HireFlow Dev] Live backend unreachable; using company fixture for ${normId}.`)
      const found = localCompanies.find((c) => c.id.toUpperCase() === normId)
      if (found) return found
      throw new ApiError({
        status: 404,
        error: 'Not Found',
        message: `Company '${normId}' not found in simulation.`,
        path: `/api/v1/officer/companies/${normId}`,
        timestamp: new Date().toISOString(),
      })
    }
    throw err
  }
}

export async function createOfficerCompany(
  dto: OfficerCompanyCreateDto
): Promise<OfficerCompanyDetailDto> {
  try {
    const data = await apiClient.request<OfficerCompanyDetailDto>('/api/v1/officer/companies', {
      method: 'POST',
      body: JSON.stringify(dto),
    })
    return OfficerCompanyDetailSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn('[HireFlow Dev] Live backend unreachable; simulating company creation.')
      const normId = dto.id.trim().toUpperCase()
      if (localCompanies.some((c) => c.id.toUpperCase() === normId)) {
        throw new ApiError({
          status: 409,
          error: 'Conflict',
          message: `Company '${normId}' already exists.`,
          path: '/api/v1/officer/companies',
          timestamp: new Date().toISOString(),
        })
      }
      const newCompany: OfficerCompanyDetailDto = {
        id: normId,
        name: dto.name.trim(),
        industry: dto.industry.trim(),
        activeDrivesCount: 0,
        totalDrivesCount: 0,
      }
      localCompanies = [newCompany, ...localCompanies]
      return newCompany
    }
    throw err
  }
}

export async function updateOfficerCompany(
  id: string,
  dto: OfficerCompanyUpdateDto
): Promise<OfficerCompanyDetailDto> {
  const normId = id.trim().toUpperCase()
  try {
    const data = await apiClient.request<OfficerCompanyDetailDto>(
      `/api/v1/officer/companies/${normId}`,
      {
        method: 'PUT',
        body: JSON.stringify(dto),
      }
    )
    return OfficerCompanyDetailSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn(`[HireFlow Dev] Live backend unreachable; simulating company update for ${normId}.`)
      const index = localCompanies.findIndex((c) => c.id.toUpperCase() === normId)
      if (index === -1) {
        throw new ApiError({
          status: 404,
          error: 'Not Found',
          message: `Company '${normId}' not found.`,
          path: `/api/v1/officer/companies/${normId}`,
          timestamp: new Date().toISOString(),
        })
      }
      const updated: OfficerCompanyDetailDto = {
        ...localCompanies[index],
        name: dto.name.trim(),
        industry: dto.industry.trim(),
      }
      localCompanies = [
        ...localCompanies.slice(0, index),
        updated,
        ...localCompanies.slice(index + 1),
      ]
      return updated
    }
    throw err
  }
}

// ==============================================================================
// 2. Recruitment Drive Management API Methods (5 Endpoints)
// ==============================================================================

export async function fetchOfficerDrives(
  page: number = 0,
  size: number = 20,
  companyId?: string,
  activeOnly: boolean = false
): Promise<PagedResponse<OfficerDriveDetailDto>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    activeOnly: String(activeOnly),
  })
  if (companyId && companyId.trim()) {
    params.set('companyId', companyId.trim())
  }

  try {
    const data = await apiClient.request<PagedResponse<OfficerDriveDetailDto>>(
      `/api/v1/officer/drives?${params.toString()}`
    )
    return PagedOfficerDriveResponseSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn('[HireFlow Dev] Live backend unreachable; using officer drives fixture.')
      let items = [...localDrives]
      if (companyId && companyId.trim()) {
        const normCId = companyId.trim().toUpperCase()
        items = items.filter((d) => d.companyId.toUpperCase() === normCId)
      }
      if (activeOnly) {
        items = items.filter((d) => !d.closed)
      }
      const totalElements = items.length
      const totalPages = Math.ceil(totalElements / size) || 1
      const fromIndex = page * size
      const pagedContent = items.slice(fromIndex, fromIndex + size)

      return {
        content: pagedContent,
        page,
        size,
        totalElements,
        totalPages,
        last: page >= totalPages - 1,
      }
    }
    throw err
  }
}

export async function fetchOfficerDriveById(id: string): Promise<OfficerDriveDetailDto> {
  const normId = id.trim().toUpperCase()
  try {
    const data = await apiClient.request<OfficerDriveDetailDto>(`/api/v1/officer/drives/${normId}`)
    return OfficerDriveDetailSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn(`[HireFlow Dev] Live backend unreachable; using drive fixture for ${normId}.`)
      const found = localDrives.find((d) => d.id.toUpperCase() === normId)
      if (found) return found
      throw new ApiError({
        status: 404,
        error: 'Not Found',
        message: `Recruitment drive '${normId}' not found in simulation.`,
        path: `/api/v1/officer/drives/${normId}`,
        timestamp: new Date().toISOString(),
      })
    }
    throw err
  }
}

export async function createOfficerDrive(
  dto: OfficerDriveCreateDto
): Promise<OfficerDriveDetailDto> {
  try {
    const data = await apiClient.request<OfficerDriveDetailDto>('/api/v1/officer/drives', {
      method: 'POST',
      body: JSON.stringify(dto),
    })
    return OfficerDriveDetailSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn('[HireFlow Dev] Live backend unreachable; simulating drive creation.')
      const normId = dto.id.trim().toUpperCase()
      const normCompanyId = dto.companyId.trim().toUpperCase()

      if (localDrives.some((d) => d.id.toUpperCase() === normId)) {
        throw new ApiError({
          status: 409,
          error: 'Conflict',
          message: `Drive '${normId}' already exists.`,
          path: '/api/v1/officer/drives',
          timestamp: new Date().toISOString(),
        })
      }

      const company = localCompanies.find((c) => c.id.toUpperCase() === normCompanyId)
      const companyName = company ? company.name : normCompanyId

      const newDrive: OfficerDriveDetailDto = {
        id: normId,
        companyId: normCompanyId,
        companyName,
        jobTitle: dto.jobTitle.trim(),
        minimumCgpa: dto.minimumCgpa,
        eligibleBranches: dto.eligibleBranches,
        applicationDeadline: dto.applicationDeadline,
        closed: false,
        totalApplications: 0,
        shortlistedApplications: 0,
      }
      localDrives = [newDrive, ...localDrives]

      // Update company counts
      if (company) {
        company.activeDrivesCount += 1
        company.totalDrivesCount += 1
      }

      return newDrive
    }
    throw err
  }
}

export async function updateOfficerDrive(
  id: string,
  dto: OfficerDriveUpdateDto
): Promise<OfficerDriveDetailDto> {
  const normId = id.trim().toUpperCase()
  try {
    const data = await apiClient.request<OfficerDriveDetailDto>(
      `/api/v1/officer/drives/${normId}`,
      {
        method: 'PUT',
        body: JSON.stringify(dto),
      }
    )
    return OfficerDriveDetailSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn(`[HireFlow Dev] Live backend unreachable; simulating drive update for ${normId}.`)
      const index = localDrives.findIndex((d) => d.id.toUpperCase() === normId)
      if (index === -1) {
        throw new ApiError({
          status: 404,
          error: 'Not Found',
          message: `Drive '${normId}' not found.`,
          path: `/api/v1/officer/drives/${normId}`,
          timestamp: new Date().toISOString(),
        })
      }
      if (localDrives[index].closed) {
        throw new ApiError({
          status: 400,
          error: 'Bad Request',
          message: `Cannot update closed recruitment drive '${normId}'.`,
          path: `/api/v1/officer/drives/${normId}`,
          timestamp: new Date().toISOString(),
        })
      }
      const updated: OfficerDriveDetailDto = {
        ...localDrives[index],
        jobTitle: dto.jobTitle.trim(),
        minimumCgpa: dto.minimumCgpa,
        eligibleBranches: dto.eligibleBranches,
        applicationDeadline: dto.applicationDeadline,
      }
      localDrives = [...localDrives.slice(0, index), updated, ...localDrives.slice(index + 1)]
      return updated
    }
    throw err
  }
}

export async function closeOfficerDrive(id: string): Promise<OfficerDriveDetailDto> {
  const normId = id.trim().toUpperCase()
  try {
    const data = await apiClient.request<OfficerDriveDetailDto>(
      `/api/v1/officer/drives/${normId}/close`,
      {
        method: 'POST',
      }
    )
    return OfficerDriveDetailSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn(`[HireFlow Dev] Live backend unreachable; simulating drive closure for ${normId}.`)
      const index = localDrives.findIndex((d) => d.id.toUpperCase() === normId)
      if (index === -1) {
        throw new ApiError({
          status: 404,
          error: 'Not Found',
          message: `Drive '${normId}' not found.`,
          path: `/api/v1/officer/drives/${normId}/close`,
          timestamp: new Date().toISOString(),
        })
      }
      const closed: OfficerDriveDetailDto = {
        ...localDrives[index],
        closed: true,
      }
      localDrives = [...localDrives.slice(0, index), closed, ...localDrives.slice(index + 1)]

      // Adjust active drive count in company
      const company = localCompanies.find((c) => c.id.toUpperCase() === closed.companyId.toUpperCase())
      if (company && company.activeDrivesCount > 0) {
        company.activeDrivesCount -= 1
      }

      return closed
    }
    throw err
  }
}

// ==============================================================================
// 3. Candidate Review & Shortlisting API Methods (3 Endpoints)
// ==============================================================================

export async function fetchOfficerDriveApplications(
  driveId: string,
  page: number = 0,
  size: number = 20,
  status?: string
): Promise<PagedResponse<OfficerApplicationReviewDto>> {
  const normDriveId = driveId.trim().toUpperCase()
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  })
  if (status && status.trim() && status.trim().toUpperCase() !== 'ALL') {
    params.set('status', status.trim().toUpperCase())
  }

  try {
    const data = await apiClient.request<PagedResponse<OfficerApplicationReviewDto>>(
      `/api/v1/officer/drives/${normDriveId}/applications?${params.toString()}`
    )
    return PagedOfficerApplicationReviewResponseSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn(
        `[HireFlow Dev] Live backend unreachable; using candidate review fixture for ${normDriveId}.`
      )
      let items = localApplications.filter((a) => a.driveId.toUpperCase() === normDriveId)
      if (status && status.trim() && status.trim().toUpperCase() !== 'ALL') {
        const normStatus = status.trim().toUpperCase()
        items = items.filter((a) => a.status === normStatus)
      }
      const totalElements = items.length
      const totalPages = Math.ceil(totalElements / size) || 1
      const fromIndex = page * size
      const pagedContent = items.slice(fromIndex, fromIndex + size)

      return {
        content: pagedContent,
        page,
        size,
        totalElements,
        totalPages,
        last: page >= totalPages - 1,
      }
    }
    throw err
  }
}

export async function fetchOfficerApplicationById(
  id: string
): Promise<OfficerApplicationReviewDto> {
  const normId = id.trim().toUpperCase()
  try {
    const data = await apiClient.request<OfficerApplicationReviewDto>(
      `/api/v1/officer/applications/${normId}`
    )
    return OfficerApplicationReviewSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn(`[HireFlow Dev] Live backend unreachable; using application fixture for ${normId}.`)
      const found = localApplications.find((a) => a.id.toUpperCase() === normId)
      if (found) return found
      throw new ApiError({
        status: 404,
        error: 'Not Found',
        message: `Application '${normId}' not found in simulation.`,
        path: `/api/v1/officer/applications/${normId}`,
        timestamp: new Date().toISOString(),
      })
    }
    throw err
  }
}

export async function shortlistOfficerApplication(
  id: string
): Promise<OfficerApplicationReviewDto> {
  const normId = id.trim().toUpperCase()
  try {
    const data = await apiClient.request<OfficerApplicationReviewDto>(
      `/api/v1/officer/applications/${normId}/shortlist`,
      {
        method: 'POST',
      }
    )
    return OfficerApplicationReviewSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn(`[HireFlow Dev] Live backend unreachable; simulating shortlisting for ${normId}.`)
      const index = localApplications.findIndex((a) => a.id.toUpperCase() === normId)
      if (index === -1) {
        throw new ApiError({
          status: 404,
          error: 'Not Found',
          message: `Application '${normId}' not found.`,
          path: `/api/v1/officer/applications/${normId}/shortlist`,
          timestamp: new Date().toISOString(),
        })
      }
      const existing = localApplications[index]
      if (existing.status === 'SHORTLISTED') {
        return existing
      }
      const updated: OfficerApplicationReviewDto = {
        ...existing,
        status: 'SHORTLISTED',
      }
      localApplications = [
        ...localApplications.slice(0, index),
        updated,
        ...localApplications.slice(index + 1),
      ]

      // Update shortlisted counter on drive
      const drive = localDrives.find((d) => d.id.toUpperCase() === existing.driveId.toUpperCase())
      if (drive) {
        drive.shortlistedApplications += 1
      }

      return updated
    }
    throw err
  }
}

// ==============================================================================
// 4. Resource Administration API Methods (6 Endpoints: Interviewers & Rooms)
// ==============================================================================

export async function fetchOfficerInterviewers(
  companyId?: string
): Promise<OfficerInterviewerDetailDto[]> {
  const params = new URLSearchParams()
  if (companyId && companyId.trim()) {
    params.set('companyId', companyId.trim().toUpperCase())
  }
  const queryStr = params.toString() ? `?${params.toString()}` : ''

  try {
    const data = await apiClient.request<OfficerInterviewerDetailDto[]>(
      `/api/v1/officer/interviewers${queryStr}`
    )
    return OfficerInterviewerArraySchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn('[HireFlow Dev] Live backend unreachable; using interviewers fixture.')
      if (companyId && companyId.trim()) {
        const normCId = companyId.trim().toUpperCase()
        return localInterviewers.filter((i) => i.companyId.toUpperCase() === normCId)
      }
      return [...localInterviewers]
    }
    throw err
  }
}

export async function createOfficerInterviewer(
  dto: OfficerInterviewerCreateDto
): Promise<OfficerInterviewerDetailDto> {
  try {
    const data = await apiClient.request<OfficerInterviewerDetailDto>(
      '/api/v1/officer/interviewers',
      {
        method: 'POST',
        body: JSON.stringify(dto),
      }
    )
    return OfficerInterviewerDetailSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn('[HireFlow Dev] Live backend unreachable; simulating interviewer registration.')
      const normId = dto.id.trim().toUpperCase()
      const normCompanyId = dto.companyId.trim().toUpperCase()

      if (localInterviewers.some((i) => i.id.toUpperCase() === normId)) {
        throw new ApiError({
          status: 409,
          error: 'Conflict',
          message: `Interviewer '${normId}' already exists.`,
          path: '/api/v1/officer/interviewers',
          timestamp: new Date().toISOString(),
        })
      }

      const company = localCompanies.find((c) => c.id.toUpperCase() === normCompanyId)
      const companyName = company ? company.name : normCompanyId

      const newInterviewer: OfficerInterviewerDetailDto = {
        id: normId,
        name: dto.name.trim(),
        companyId: normCompanyId,
        companyName,
      }
      localInterviewers = [newInterviewer, ...localInterviewers]
      return newInterviewer
    }
    throw err
  }
}

export async function updateOfficerInterviewer(
  id: string,
  dto: OfficerInterviewerUpdateDto
): Promise<OfficerInterviewerDetailDto> {
  const normId = id.trim().toUpperCase()
  try {
    const data = await apiClient.request<OfficerInterviewerDetailDto>(
      `/api/v1/officer/interviewers/${normId}`,
      {
        method: 'PUT',
        body: JSON.stringify(dto),
      }
    )
    return OfficerInterviewerDetailSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn(`[HireFlow Dev] Live backend unreachable; simulating interviewer update for ${normId}.`)
      const index = localInterviewers.findIndex((i) => i.id.toUpperCase() === normId)
      if (index === -1) {
        throw new ApiError({
          status: 404,
          error: 'Not Found',
          message: `Interviewer '${normId}' not found.`,
          path: `/api/v1/officer/interviewers/${normId}`,
          timestamp: new Date().toISOString(),
        })
      }
      const normCompanyId = dto.companyId.trim().toUpperCase()
      const company = localCompanies.find((c) => c.id.toUpperCase() === normCompanyId)
      const companyName = company ? company.name : normCompanyId

      const updated: OfficerInterviewerDetailDto = {
        ...localInterviewers[index],
        name: dto.name.trim(),
        companyId: normCompanyId,
        companyName,
      }
      localInterviewers = [
        ...localInterviewers.slice(0, index),
        updated,
        ...localInterviewers.slice(index + 1),
      ]
      return updated
    }
    throw err
  }
}

export async function fetchOfficerRooms(): Promise<OfficerRoomDetailDto[]> {
  try {
    const data = await apiClient.request<OfficerRoomDetailDto[]>('/api/v1/officer/rooms')
    return OfficerRoomArraySchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn('[HireFlow Dev] Live backend unreachable; using rooms fixture.')
      return [...localRooms]
    }
    throw err
  }
}

export async function createOfficerRoom(
  dto: OfficerRoomCreateDto
): Promise<OfficerRoomDetailDto> {
  try {
    const data = await apiClient.request<OfficerRoomDetailDto>('/api/v1/officer/rooms', {
      method: 'POST',
      body: JSON.stringify(dto),
    })
    return OfficerRoomDetailSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn('[HireFlow Dev] Live backend unreachable; simulating room registration.')
      const normId = dto.id.trim().toUpperCase()

      if (localRooms.some((r) => r.id.toUpperCase() === normId)) {
        throw new ApiError({
          status: 409,
          error: 'Conflict',
          message: `Room '${normId}' already exists.`,
          path: '/api/v1/officer/rooms',
          timestamp: new Date().toISOString(),
        })
      }

      const newRoom: OfficerRoomDetailDto = {
        id: normId,
        name: dto.name.trim(),
        capacity: dto.capacity,
      }
      localRooms = [newRoom, ...localRooms]
      return newRoom
    }
    throw err
  }
}

export async function updateOfficerRoom(
  id: string,
  dto: OfficerRoomUpdateDto
): Promise<OfficerRoomDetailDto> {
  const normId = id.trim().toUpperCase()
  try {
    const data = await apiClient.request<OfficerRoomDetailDto>(
      `/api/v1/officer/rooms/${normId}`,
      {
        method: 'PUT',
        body: JSON.stringify(dto),
      }
    )
    return OfficerRoomDetailSchema.parse(data)
  } catch (err) {
    if (isOfflineNetworkError(err)) {
      console.warn(`[HireFlow Dev] Live backend unreachable; simulating room update for ${normId}.`)
      const index = localRooms.findIndex((r) => r.id.toUpperCase() === normId)
      if (index === -1) {
        throw new ApiError({
          status: 404,
          error: 'Not Found',
          message: `Room '${normId}' not found.`,
          path: `/api/v1/officer/rooms/${normId}`,
          timestamp: new Date().toISOString(),
        })
      }

      const updated: OfficerRoomDetailDto = {
        ...localRooms[index],
        name: dto.name.trim(),
        capacity: dto.capacity,
      }
      localRooms = [...localRooms.slice(0, index), updated, ...localRooms.slice(index + 1)]
      return updated
    }
    throw err
  }
}

// ==============================================================================
// 5. TanStack Query Hooks with Comprehensive Cache Governance
// ==============================================================================

export const officerQueryKeys = {
  all: ['officer'] as const,
  companies: (params?: { page?: number; size?: number; search?: string }) =>
    ['officer', 'companies', params] as const,
  company: (id: string) => ['officer', 'company', id] as const,
  drives: (params?: { page?: number; size?: number; companyId?: string; activeOnly?: boolean }) =>
    ['officer', 'drives', params] as const,
  drive: (id: string) => ['officer', 'drive', id] as const,
  driveApplications: (driveId: string, params?: { page?: number; size?: number; status?: string }) =>
    ['officer', 'driveApplications', driveId, params] as const,
  application: (id: string) => ['officer', 'application', id] as const,
  interviewers: (companyId?: string) => ['officer', 'interviewers', companyId] as const,
  rooms: () => ['officer', 'rooms'] as const,
}

// Companies Hooks
export function useOfficerCompanies(page: number = 0, size: number = 20, search?: string) {
  return useQuery({
    queryKey: officerQueryKeys.companies({ page, size, search }),
    queryFn: () => fetchOfficerCompanies(page, size, search),
  })
}

export function useOfficerCompany(id: string) {
  return useQuery({
    queryKey: officerQueryKeys.company(id),
    queryFn: () => fetchOfficerCompanyById(id),
    enabled: Boolean(id),
  })
}

export function useCreateCompanyMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (dto: OfficerCompanyCreateDto) => createOfficerCompany(dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['officer', 'companies'] })
    },
  })
}

export function useUpdateCompanyMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, dto }: { id: string; dto: OfficerCompanyUpdateDto }) =>
      updateOfficerCompany(id, dto),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['officer', 'companies'] })
      queryClient.invalidateQueries({ queryKey: officerQueryKeys.company(variables.id) })
    },
  })
}

// Drives Hooks
export function useOfficerDrives(
  page: number = 0,
  size: number = 20,
  companyId?: string,
  activeOnly: boolean = false
) {
  return useQuery({
    queryKey: officerQueryKeys.drives({ page, size, companyId, activeOnly }),
    queryFn: () => fetchOfficerDrives(page, size, companyId, activeOnly),
    refetchInterval: 30_000,
  })
}

export function useOfficerDrive(id: string) {
  return useQuery({
    queryKey: officerQueryKeys.drive(id),
    queryFn: () => fetchOfficerDriveById(id),
    enabled: Boolean(id),
  })
}

export function useCreateDriveMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (dto: OfficerDriveCreateDto) => createOfficerDrive(dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['officer', 'drives'] })
      queryClient.invalidateQueries({ queryKey: ['officer', 'companies'] })
    },
  })
}

export function useUpdateDriveMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, dto }: { id: string; dto: OfficerDriveUpdateDto }) =>
      updateOfficerDrive(id, dto),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['officer', 'drives'] })
      queryClient.invalidateQueries({ queryKey: officerQueryKeys.drive(variables.id) })
    },
  })
}

export function useCloseDriveMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => closeOfficerDrive(id),
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: ['officer', 'drives'] })
      queryClient.invalidateQueries({ queryKey: officerQueryKeys.drive(id) })
      queryClient.invalidateQueries({ queryKey: ['officer', 'companies'] })
    },
  })
}

// Candidate Review & Shortlisting Hooks
export function useOfficerDriveApplications(
  driveId: string,
  page: number = 0,
  size: number = 20,
  status?: string
) {
  return useQuery({
    queryKey: officerQueryKeys.driveApplications(driveId, { page, size, status }),
    queryFn: () => fetchOfficerDriveApplications(driveId, page, size, status),
    refetchInterval: 30_000,
    enabled: Boolean(driveId),
  })
}

export function useOfficerApplication(id: string) {
  return useQuery({
    queryKey: officerQueryKeys.application(id),
    queryFn: () => fetchOfficerApplicationById(id),
    enabled: Boolean(id),
  })
}

export function useShortlistApplicationMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => shortlistOfficerApplication(id),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['officer', 'driveApplications'] })
      queryClient.invalidateQueries({ queryKey: officerQueryKeys.application(data.id) })
      queryClient.invalidateQueries({ queryKey: ['officer', 'drives'] })
    },
  })
}

// Resource Administration Hooks
export function useOfficerInterviewers(companyId?: string) {
  return useQuery({
    queryKey: officerQueryKeys.interviewers(companyId),
    queryFn: () => fetchOfficerInterviewers(companyId),
  })
}

export function useCreateInterviewerMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (dto: OfficerInterviewerCreateDto) => createOfficerInterviewer(dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['officer', 'interviewers'] })
    },
  })
}

export function useUpdateInterviewerMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, dto }: { id: string; dto: OfficerInterviewerUpdateDto }) =>
      updateOfficerInterviewer(id, dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['officer', 'interviewers'] })
    },
  })
}

export function useOfficerRooms() {
  return useQuery({
    queryKey: officerQueryKeys.rooms(),
    queryFn: () => fetchOfficerRooms(),
  })
}

export function useCreateRoomMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (dto: OfficerRoomCreateDto) => createOfficerRoom(dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['officer', 'rooms'] })
    },
  })
}

export function useUpdateRoomMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, dto }: { id: string; dto: OfficerRoomUpdateDto }) =>
      updateOfficerRoom(id, dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['officer', 'rooms'] })
    },
  })
}
